/**
 *
 * "Checkpoint Charlie" POC
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

import org.apache.log4j.*;
import org.apache.log4j.Logger;

import java.sql.*;

import io.pivotal.charlie_api.DatabaseInterface;
import io.pivotal.aspects.checkpoint.VMSnapshotUtil;


privileged public aspect PivotalBatchAspect {

	private Logger aspectLogger = Logger.getLogger(this.getClass().getName());
	
	private int DatabaseInterface.batchCounter;
	
	public void DatabaseInterface.setBatchCounter (int counter) {
		this.batchCounter = counter;
	}

	public int DatabaseInterface.getBatchCounter () {
		return this.batchCounter;
	}
	
	pointcut onUpdate (DatabaseInterface aDb, String aString, int anInt) : 
		call (public void DatabaseInterface+.updateCurrentRecord(String, int)) &&
		target(aDb) &&
		args(aString, anInt);

	before (DatabaseInterface aDb, String aString, int anInt) : onUpdate (aDb, aString, anInt) {
		
		boolean result = false;
		
		aspectLogger.log(Level.INFO, "Before onUpdate()");
		aspectLogger.log(Level.INFO, "Join Point: " + thisJoinPoint.getSignature());
		
		int cnt = aDb.getBatchCounter();
		
		aspectLogger.log(Level.INFO, "Before value of Batch Counter = " + cnt);

		if ((cnt > 0) && (cnt % 5 == 0)) { // TODO: Make the specific number of rows to checkpoint a configurable value.
			
			aspectLogger.log(Level.INFO,"That's 5 records, time to do a snapshot and commit...");
			
			try {
				
				// TODO: Make the name of the VM that we want to snapshot be a configurable value.
				// Presumably we want to snapshot the VM we are running on, i.e. the localhost where this code is
				// executing.  But it's not clear that there is any better way to get the VM name than to 
				// make it a configuration value.  AFAIK, we can't get the VM name from within the VM itself.  
				// TODO: Perhaps this can be done using VMware tools?
				
				result = VMSnapshotUtil.takeSnapshot(new String("My VM Name"), Integer.toString(cnt));

				if (result) {
					aspectLogger.log(Level.INFO,"Snapshot done!");			
					Connection conn = aDb.getConn();
					conn.commit();
					aspectLogger.log(Level.INFO,"Commit done!");			
				}
				else {
					aspectLogger.log(Level.INFO, "Snapshot failed, so DB commit was skipped. No checkpoint done for count = " + cnt);
				}
				
			} catch (SQLException se) {
				// Handle errors for JDBC
				se.printStackTrace();
			} catch (Exception e) {
				// Handle errors for Class.forName
				e.printStackTrace();
			}// end try
			

		}
		return;
	}

	after (DatabaseInterface aDb, String aString, int anInt) : onUpdate (aDb, aString, anInt) {
		
		aspectLogger.log(Level.INFO, "After onUpdate()");
		aspectLogger.log(Level.INFO,  "Join Point: " + thisJoinPoint.getSignature());
		aDb.setBatchCounter(aDb.getBatchCounter() + 1);
		aspectLogger.log(Level.INFO, "After value of Batch Counter = " + aDb.getBatchCounter());

		return;
	}
	
	
}
