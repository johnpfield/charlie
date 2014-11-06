/**
 *
 * "Checkpoint Charlie" POC
 * 
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 **/

package io.pivotal.aspects.checkpoint;

/**
 * @author jfield@pivotal.io
 *
 */

import com.vmware.vim25.*;

import java.rmi.RemoteException;
import java.util.*;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.xml.ws.*;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.log4j.*;

public class VMSnapshotUtil {

	/**
	 * This class opens a connection to the indicated ESX server, using the supplied credentials, and then 
	 * creates a snapshot of the specified Virtual Machine.  
	 * 
	 * TODO: Known issues:
	 * 
	 *  a)  Right now the credentials and the name of the ESX server to connect to, as well as the specific 
	 *  VM to snapshot, are all hard-coded.  This is just a demonstration of feasibility, and so this should
	 *  not be construed as production code.  This software is not to be used for any customer's production 
	 *  deployment.  These limitations are easy to fix if a production-ready version is needed.
	 * 
	 *  b) This class makes use of some sample code found in the VMware vSphere SDK. This is because this is 
	 *  just a POC and good programmers are lazy, and because there really aren't that many choices in how one would 
	 *  implement this function anyway.  AFIAK, this use is perfectly consistent with the original VMware SDK 
	 *  license terms of use. In any case, some portions must be re-implemented if this effort were to move beyond 
	 *  the developer POC stage.  In particular:  build a real trust manager, improve traversal spec, improve 
	 *  connection logic (to provide HA fail-over and/or pooling), etc. 
	 * 
	 **/

	private static Logger snapshotLogger = Logger
			.getLogger("io.pivotal.aspects.checkpoint.VMSnapshotUtil");

	// Neuter the trust manager because nobody cares about security (tm).
	private static class TrustAllTrustManager implements
			javax.net.ssl.TrustManager, javax.net.ssl.X509TrustManager {

		public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		@SuppressWarnings("unused")
		public boolean isServerTrusted(
				java.security.cert.X509Certificate[] certs) {
			return true;
		}

		@SuppressWarnings("unused")
		public boolean isClientTrusted(
				java.security.cert.X509Certificate[] certs) {
			return true;
		}

		public void checkServerTrusted(
				java.security.cert.X509Certificate[] certs, String authType)
				throws java.security.cert.CertificateException {
			return;
		}

		public void checkClientTrusted(
				java.security.cert.X509Certificate[] certs, String authType)
				throws java.security.cert.CertificateException {
			return;
		}
	}

	private static final ManagedObjectReference SVC_INST_REF = new ManagedObjectReference();
	private static final String SVC_INST_NAME = "ServiceInstance";

	private static ManagedObjectReference propCollectorRef;
	private static ManagedObjectReference rootRef;
	private static VimService vimService;
	private static VimPortType vimPort;
	private static ServiceContent serviceContent;

	// TODO: Security professionals don't hard-code credentials.
	private static final String url = "https://myVcenterHostname.com/sdk";
	private static final String userName = "Domain\\Username";
	private static final String password = "password";
	private static boolean isConnected = false;
	
	private static String snapshotbasename = "Aspect Snapshot ";
	private static String description = "This snapshot was created by Checkpoint Charlie aspect.";

	// TODO:  implement a valid trust management strategy.
	private static void trustAllHttpsCertificates() throws Exception {
		// Create a trust manager that does not validate certificate chains:
		javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[1];
		javax.net.ssl.TrustManager tm = new TrustAllTrustManager();
		trustAllCerts[0] = tm;
		javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
		javax.net.ssl.SSLSessionContext sslsc = sc.getServerSessionContext();
		sslsc.setSessionTimeout(0);
		sc.init(null, trustAllCerts, null);
		javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	}

	/**
	 * Establishes session with the virtual center server.
	 *
	 * @throws Exception
	 *             the exception
	 */
	private static void connect() throws Exception {

		HostnameVerifier hv = new HostnameVerifier() {
			public boolean verify(String urlHostName, SSLSession session) {
				return true;
			}
		};
		trustAllHttpsCertificates();
		HttpsURLConnection.setDefaultHostnameVerifier(hv);

		SVC_INST_REF.setType(SVC_INST_NAME);
		SVC_INST_REF.setValue(SVC_INST_NAME);

		vimService = new VimService();
		vimPort = vimService.getVimPort();
		Map<String, Object> ctxt = ((BindingProvider) vimPort)
				.getRequestContext();

		ctxt.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
		ctxt.put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);

		serviceContent = vimPort.retrieveServiceContent(SVC_INST_REF);
		vimPort.login(serviceContent.getSessionManager(), userName, password,
				null);
		isConnected = true;

		propCollectorRef = serviceContent.getPropertyCollector();
		rootRef = serviceContent.getRootFolder();
	}

	/**
	 * Disconnects the user session.
	 *
	 * @throws Exception
	 */
	private static void disconnect() throws Exception {
		if (isConnected) {
			vimPort.logout(serviceContent.getSessionManager());
		}
		isConnected = false;
	}

	/**
	 *
	 * @return TraversalSpec to get to the VirtualMachine managed entity.
	 */
	private static TraversalSpec getVMTraversalSpec() {
		// Create a traversal spec that starts from the 'root' objects
		// and traverses the inventory tree to get to the VirtualMachines.
		// Build the traversal specs bottoms-up

		// Traversal to get to the VM in a VApp
		TraversalSpec vAppToVM = new TraversalSpec();
		vAppToVM.setName("vAppToVM");
		vAppToVM.setType("VirtualApp");
		vAppToVM.setPath("vm");

		// Traversal spec for VApp to VApp
		TraversalSpec vAppToVApp = new TraversalSpec();
		vAppToVApp.setName("vAppToVApp");
		vAppToVApp.setType("VirtualApp");
		vAppToVApp.setPath("resourcePool");
		// SelectionSpec for VApp to VApp recursion
		SelectionSpec vAppRecursion = new SelectionSpec();
		vAppRecursion.setName("vAppToVApp");
		// SelectionSpec to get to a VM in the VApp
		SelectionSpec vmInVApp = new SelectionSpec();
		vmInVApp.setName("vAppToVM");
		// SelectionSpec for both VApp to VApp and VApp to VM
		List<SelectionSpec> vAppToVMSS = new ArrayList<SelectionSpec>();
		vAppToVMSS.add(vAppRecursion);
		vAppToVMSS.add(vmInVApp);
		vAppToVApp.getSelectSet().addAll(vAppToVMSS);

		// This SelectionSpec is used for recursion for Folder recursion
		SelectionSpec sSpec = new SelectionSpec();
		sSpec.setName("VisitFolders");

		// Traversal to get to the vmFolder from DataCenter
		TraversalSpec dataCenterToVMFolder = new TraversalSpec();
		dataCenterToVMFolder.setName("DataCenterToVMFolder");
		dataCenterToVMFolder.setType("Datacenter");
		dataCenterToVMFolder.setPath("vmFolder");
		dataCenterToVMFolder.setSkip(false);
		dataCenterToVMFolder.getSelectSet().add(sSpec);

		// TraversalSpec to get to the DataCenter from rootFolder
		TraversalSpec traversalSpec = new TraversalSpec();
		traversalSpec.setName("VisitFolders");
		traversalSpec.setType("Folder");
		traversalSpec.setPath("childEntity");
		traversalSpec.setSkip(false);
		List<SelectionSpec> sSpecArr = new ArrayList<SelectionSpec>();
		sSpecArr.add(sSpec);
		sSpecArr.add(dataCenterToVMFolder);
		sSpecArr.add(vAppToVM);
		sSpecArr.add(vAppToVApp);
		traversalSpec.getSelectSet().addAll(sSpecArr);
		return traversalSpec;
	}

	/**
	 * Uses the new RetrievePropertiesEx method to emulate the now deprecated
	 * RetrieveProperties method
	 *
	 * @param listpfs
	 * @return list of object content
	 * @throws Exception
	 */
	private static List<ObjectContent> retrievePropertiesAllObjects(
			List<PropertyFilterSpec> listpfs) {

		RetrieveOptions propObjectRetrieveOpts = new RetrieveOptions();

		List<ObjectContent> listobjcontent = new ArrayList<ObjectContent>();

		try {
			RetrieveResult rslts = vimPort.retrievePropertiesEx(
					propCollectorRef, listpfs, propObjectRetrieveOpts);
			if (rslts != null && rslts.getObjects() != null
					&& !rslts.getObjects().isEmpty()) {
				listobjcontent.addAll(rslts.getObjects());
			}
			String token = null;
			if (rslts != null && rslts.getToken() != null) {
				token = rslts.getToken();
			}
			while (token != null && !token.isEmpty()) {
				rslts = vimPort.continueRetrievePropertiesEx(propCollectorRef,
						token);
				token = null;
				if (rslts != null) {
					token = rslts.getToken();
					if (rslts.getObjects() != null
							&& !rslts.getObjects().isEmpty()) {
						listobjcontent.addAll(rslts.getObjects());
					}
				}
			}
		} catch (SOAPFaultException sfe) {
			printSoapFaultException(sfe);
		} catch (Exception e) {
			snapshotLogger.log(Level.INFO, " : Failed Getting Contents");
			e.printStackTrace();
		}

		return listobjcontent;
	}

	/**
	 * Get the MOR of the Virtual Machine by its name.
	 * 
	 * @param vmName
	 *            The name of the Virtual Machine
	 * @return The Managed Object reference for this VM
	 */
	private static ManagedObjectReference getVmByVMname(String vmName) {

		ManagedObjectReference retVal = null;

		try {
			TraversalSpec tSpec = getVMTraversalSpec();
			// Create Property Spec
			PropertySpec propertySpec = new PropertySpec();
			propertySpec.setAll(Boolean.FALSE);
			propertySpec.getPathSet().add("name");
			propertySpec.setType("VirtualMachine");

			// Now create Object Spec
			ObjectSpec objectSpec = new ObjectSpec();
			objectSpec.setObj(rootRef);
			objectSpec.setSkip(Boolean.TRUE);
			objectSpec.getSelectSet().add(tSpec);

			// Create PropertyFilterSpec using the PropertySpec and ObjectPec
			// created above.
			PropertyFilterSpec propertyFilterSpec = new PropertyFilterSpec();
			propertyFilterSpec.getPropSet().add(propertySpec);
			propertyFilterSpec.getObjectSet().add(objectSpec);

			List<PropertyFilterSpec> listpfs = new ArrayList<PropertyFilterSpec>(
					1);
			listpfs.add(propertyFilterSpec);
			List<ObjectContent> listobjcont = retrievePropertiesAllObjects(listpfs);

			if (listobjcont != null) {
				for (ObjectContent oc : listobjcont) {
					ManagedObjectReference mr = oc.getObj();
					String vmnm = null;
					List<DynamicProperty> dps = oc.getPropSet();
					if (dps != null) {
						for (DynamicProperty dp : dps) {
							vmnm = (String) dp.getVal();
						}
					}
					if (vmnm != null && vmnm.equals(vmName)) {
						retVal = mr;
						break;
					}
				}
			}
		} catch (SOAPFaultException sfe) {
			printSoapFaultException(sfe);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return retVal;
	}

	/**
	 * 
	 * @param vmMor  a virtual machine managed object reference object
	 * @param snapshotLabel a String label to concatenate to the snapshot base name
	 * @return
	 * @throws Exception
	 */

	private static boolean createSnapshot(ManagedObjectReference vmMor, String snapshotLabel) throws Exception {
		
		String snapshotName = snapshotbasename.concat(snapshotLabel);
		String desc = description;
		
		snapshotLogger.log(Level.INFO, "Call vimPort.createSnapshotTask");
		
		ManagedObjectReference taskMor = vimPort.createSnapshotTask(vmMor, snapshotName, desc, false, false);
		if (taskMor != null) {
			
			snapshotLogger.log(Level.INFO, "vimPort.createSnapshotTask OK");
			
			String[] opts = new String[] { "info.state", "info.error", "info.progress" };
			String[] opt = new String[] { "state" };
			
			snapshotLogger.log(Level.INFO, "Call waitForValues");
			
			Object[] results = waitForValues(taskMor, opts, opt,
					new Object[][] { new Object[] { TaskInfoState.SUCCESS,
							TaskInfoState.ERROR } });

			// Wait till the task completes.
			if (results[0].equals(TaskInfoState.SUCCESS)) {
				snapshotLogger.log(Level.INFO, "Creating Snapshot " + snapshotName + " Successful.");
				return Boolean.TRUE;
			} else {
				snapshotLogger.log(Level.INFO, "Creating Snapshot " + snapshotName + " Failure.");
				return Boolean.FALSE;
			}
		}
		return false;
	}

	/**
	 * 
	 * @param test the option which is to be tested 
	 * @return
	 */
	@SuppressWarnings("unused")
	private static boolean isOptionSet(String test) {
		return (test == null) ? Boolean.FALSE : Boolean.TRUE;
	}

	/**
	 * 
	 * @param objmor a managed object reference object
	 * @param filterProps an array of filterProperty strings 
	 * @param endWaitProps 
	 * @param expectedVals
	 * @return
	 * @throws RemoteException
	 * @throws Exception
	 */
	private static Object[] waitForValues(ManagedObjectReference objmor,
			String[] filterProps, String[] endWaitProps, Object[][] expectedVals)
			throws RemoteException, Exception {
		
		// version string is initially null
		String version = "";
		
		Object[] endVals = new Object[endWaitProps.length];
		Object[] filterVals = new Object[filterProps.length];

		PropertyFilterSpec spec = new PropertyFilterSpec();

		spec.getObjectSet().add(new ObjectSpec());
		spec.getObjectSet().get(0).setObj(objmor);

		spec.getPropSet().addAll(
				Arrays.asList(new PropertySpec[] { new PropertySpec() }));
		spec.getPropSet().get(0).getPathSet()
				.addAll(Arrays.asList(filterProps));
		spec.getPropSet().get(0).setType(objmor.getType());

//		spec.getObjectSet().get(0).getSelectSet().add(null);
		spec.getObjectSet().get(0).getSelectSet().add(getVMTraversalSpec());
		spec.getObjectSet().get(0).setSkip(Boolean.FALSE);

		ManagedObjectReference filterSpecRef = vimPort.createFilter(
				propCollectorRef, spec, true);

		boolean reached = false;

		UpdateSet updateset = null;
		PropertyFilterUpdate[] filtupary = null;
		PropertyFilterUpdate filtup = null;
		ObjectUpdate[] objupary = null;
		ObjectUpdate objup = null;
		PropertyChange[] propchgary = null;
		PropertyChange propchg = null;
		while (!reached) {
			boolean retry = true;
			while (retry) {
				try {
					updateset = vimPort.waitForUpdates(propCollectorRef,
							version);
					retry = false;
				} catch (SOAPFaultException sfe) {
					printSoapFaultException(sfe);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (updateset != null) {
				version = updateset.getVersion();
			}
			if (updateset == null || updateset.getFilterSet() == null) {
				continue;
			}
			List<PropertyFilterUpdate> listprfup = updateset.getFilterSet();
			filtupary = listprfup.toArray(new PropertyFilterUpdate[listprfup
					.size()]);
			filtup = null;
			for (int fi = 0; fi < filtupary.length; fi++) {
				filtup = filtupary[fi];
				List<ObjectUpdate> listobjup = filtup.getObjectSet();
				objupary = listobjup
						.toArray(new ObjectUpdate[listobjup.size()]);
				objup = null;
				propchgary = null;
				for (int oi = 0; oi < objupary.length; oi++) {
					objup = objupary[oi];
					if (objup.getKind() == ObjectUpdateKind.MODIFY
							|| objup.getKind() == ObjectUpdateKind.ENTER
							|| objup.getKind() == ObjectUpdateKind.LEAVE) {
						List<PropertyChange> listchset = objup.getChangeSet();
						propchgary = listchset
								.toArray(new PropertyChange[listchset.size()]);
						for (int ci = 0; ci < propchgary.length; ci++) {
							propchg = propchgary[ci];
							updateValues(endWaitProps, endVals, propchg);
							updateValues(filterProps, filterVals, propchg);
						}
					}
				}
			}
			Object expctdval = null;
			// Check if the expected values have been reached and exit the loop if done.
			// Also exit the WaitForUpdates loop if this is the case.
			for (int chgi = 0; chgi < endVals.length && !reached; chgi++) {
				for (int vali = 0; vali < expectedVals[chgi].length && !reached; vali++) {
					expctdval = expectedVals[chgi][vali];
					reached = expctdval.equals(endVals[chgi]) || reached;
				}
			}
		}

		// Destroy the filter when we are done.
		vimPort.destroyPropertyFilter(filterSpecRef);
		return filterVals;
	}

	private static void updateValues(String[] props, Object[] vals,
			PropertyChange propchg) {
		for (int findi = 0; findi < props.length; findi++) {
			if (propchg.getName().lastIndexOf(props[findi]) >= 0) {
				if (propchg.getOp() == PropertyChangeOp.REMOVE) {
					vals[findi] = "";
				} else {
					vals[findi] = propchg.getVal();
				}
			}
		}
	}

	/**
	 * 
	 * @param sfe  the SOAP fault exception to be printed
	 */
	private static void printSoapFaultException(SOAPFaultException sfe) {
		snapshotLogger.log(Level.INFO, "SOAP Fault -");
		if (sfe.getFault().hasDetail()) {
			snapshotLogger.log(Level.INFO, sfe.getFault().getDetail()
					.getFirstChild().getLocalName());
		}
		if (sfe.getFault().getFaultString() != null) {
			System.out
					.println("\n Message: " + sfe.getFault().getFaultString());
		}
	}

	/**
	 * Connects to the vSphere server and takes a snapshot of the indicated Virtual Machine.
	 * 
	 * @param virtualMachineName  a String containing the virtual machine name whose snapshot is to be taken
	 * @param snapshotLabel a String containing a label that is to be concatenated to the snapshot name, i.e. a sequence number or UUID
	 */
	public static boolean takeSnapshot(String virtualMachineName, String snapshotLabel) {

		boolean result = false;

		try {

			snapshotLogger.log(Level.INFO, "Call Connect");
			connect();
			snapshotLogger.log(Level.INFO, "Connect OK");
			snapshotLogger.log(Level.INFO, "Call getVmByVmName");
			ManagedObjectReference virtmacmor = getVmByVMname(virtualMachineName);
			snapshotLogger.log(Level.INFO, "getVmByVmName OK");

			if (virtmacmor != null) {
				try {
					snapshotLogger.log(Level.INFO, "Call createSnapshot");
					result = createSnapshot(virtmacmor, snapshotLabel);
					snapshotLogger.log(Level.INFO, "createSnapshot OK");
				} catch (SOAPFaultException sfe) {
					snapshotLogger.log(Level.INFO, "SOAP Fault on Call createSnapshot");
					printSoapFaultException(sfe);
				} catch (Exception ex) {
					snapshotLogger.log(Level.INFO, "Error encountered: " + ex);
				}
				if (result) {
					snapshotLogger.log(Level.INFO, "Create snapshot completed sucessfully");
				}
			} else {
				snapshotLogger.log(Level.INFO, "Virtual Machine " + virtualMachineName + " not found.");
				return result;  // false; no snapshot taken. 
			}
		} catch (SOAPFaultException sfe) {
			printSoapFaultException(sfe);
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				disconnect();
			} catch (SOAPFaultException sfe) {
				printSoapFaultException(sfe);
			} catch (Exception e) {
				snapshotLogger.log(Level.INFO, "Failed to disconnect - " + e.getMessage());
				e.printStackTrace();
			}
		}
		
		return result;
	}
}