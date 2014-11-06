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


package io.pivotal.charlie;

import io.pivotal.charlie_api.DatabaseInterface;
import io.pivotal.charlie.DatabaseManager;
import org.apache.log4j.*;


public class DatabaseApp {

	private static DatabaseInterface dbManager;
	private static Logger mainLogger = Logger.getLogger("io.pivotal.charlie.DatabaseApp");

	
	
	/**
	 * @param args
	 */

	public static void main(String[] args) {

		dbManager = new DatabaseManager();

		if (dbManager == null) {
			mainLogger.log(Level.INFO, "Could not open database. Exiting!");			
			System.exit(-1);
		}

		dbManager.executeEmployeeQuery();
//
//		mainLogger.log(Level.INFO, "List result set for reference....");
//
//		dbManager.printRecords();

		mainLogger.log(Level.INFO, "Update everyone's salary by $5....\n");

		dbManager.updateRecords("salary", 5);

		mainLogger.log(Level.INFO, "List result set showing new salary...");

		dbManager.printRecords();

		// Insert a record into the table.
		// Move to insert row, and then add column data with updateXXX()

//		mainLogger.log(Level.INFO, "Inserting a new record...");
//
//		dbManager.insertNewRecord();
//
//		mainLogger.log(Level.INFO, "List result set showing new set...");
//		dbManager.printRecords();

		// Delete second record from the table.

//		dbManager.deleteRecord(2);
//
//		mainLogger.log(Level.INFO, "List result set after deleting one record...");
//
//		dbManager.printRecords();

		dbManager.close();

		mainLogger.log(Level.INFO, "Done!");

	}// end main
} // end DatabaseApp

