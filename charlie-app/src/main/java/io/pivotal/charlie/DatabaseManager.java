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

import java.io.Console;
import java.io.IOException;
import java.sql.*;
import org.apache.log4j.*;
import io.pivotal.charlie_api.DatabaseInterface;

public class DatabaseManager implements DatabaseInterface {

	// JDBC driver name and database URL
	private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	private static final String DB_URL = "jdbc:mysql://localhost/EMP";

	// Database credentials
	private static final String USER = "csagan";
	private static final String PASS = "password";

	private Connection conn;
	
	public Connection getConn() {
		return conn;
	}

	public void setConn(Connection conn) {
		this.conn = conn;
	}

	private Statement stmt;
	private ResultSet rs;

	// Logging
	private Logger dbmLogger = Logger.getLogger(this.getClass().getName());

	// public no argument constructor.
	// Initialize driver, open the connection, create a statement.

	public DatabaseManager() {

		try {

			// Register JDBC driver
			Class.forName(JDBC_DRIVER);

			// Open a connection
			dbmLogger.log(Level.INFO, "Connecting to database...");
			conn = DriverManager.getConnection(DB_URL, USER, PASS);

			// Execute a query to create statement with
			// required arguments for RS example.
			dbmLogger.log(Level.INFO, "Creating statement...");
			stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
					ResultSet.CONCUR_UPDATABLE,
					ResultSet.CLOSE_CURSORS_AT_COMMIT);

			conn.setAutoCommit(false);

		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		} // end try

		return;
	}

	// Commit and close the connection. Free all resources.

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.pivotal.charlie_api.DatabaseInterface#close()
	 */
	public void close() {

		try {
			// Do explicit commit after all updates, then clean-up environment
			conn.commit();

			rs.close();
			stmt.close();
			conn.close();

		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		} finally {
			// finally block used to close resources
			try {
				if (conn != null)
					conn.close();
			} catch (SQLException se) {
				se.printStackTrace();
			}// end finally try
		}// end try

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.pivotal.charlie_api.DatabaseInterface#updateCurrentRecord()
	 */

	public void updateCurrentRecord(String column, int delta) {

		int newSalary;

		try {
			// Extract data from result set and do update of a single row.
			// This is the part will will want to advise.
			// Need after advice to count the number of calls to this method,
			// and
			// if the count is equal to 0 mod 5, then commit, and begin a new
			// transaction.

			newSalary = rs.getInt(column) + delta;
			rs.updateDouble(column, newSalary);
			rs.updateRow();
		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		}
		return;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.pivotal.charlie_api.DatabaseInterface#executeEmployeeQuery()
	 */
	public void executeEmployeeQuery() {

		try {
			// Execute a query
			String sql = "SELECT idEmployees, emp_fname, emp_lname, title, phone, hat_size, department, hired, end_date, salary FROM Employees ORDER BY idEmployees";

			rs = stmt.executeQuery(sql);

		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		}// end try

		return;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.pivotal.charlie_api.DatabaseInterface#printRecords()
	 */
	public void printRecords() {

		try {

			// Ensure we start with first row
			rs.beforeFirst();

			while (rs.next()) {

				// Retrieve by column name
				int id = rs.getInt("idEmployees");

				String emp_lname = rs.getString("emp_lname");
				String emp_fname = rs.getString("emp_fname");

				String title = rs.getString("title");
				String phone = rs.getString("phone");
				String hat_size = rs.getString("hat_size");
				int department = rs.getInt("department");

				// hire date NOT NULL
				// end date can be NULL
				Date hireDate = rs.getDate("hired");
				Date endDate = rs.getDate("end_date");

				int salary = rs.getInt("salary");

				// Display values
				dbmLogger.log(Level.INFO, "ID: " + id);

				dbmLogger.log(Level.INFO, "   Last name: " + emp_lname);
				dbmLogger.log(Level.INFO, "   First Name: " + emp_fname);

//				dbmLogger.log(Level.INFO, "   title: " + title);
//				dbmLogger.log(Level.INFO, "   phone: " + phone);
//				dbmLogger.log(Level.INFO, "   hat size: " + hat_size);
//
//				dbmLogger.log(Level.INFO, "   department: " + department);
//
//				// hire date NOT NULL
//				// end date can be NULL
//				dbmLogger.log(Level.INFO, "   Hired: " + hireDate.toString());
//				if (endDate != null) {
//					dbmLogger.log(Level.INFO,
//							"   End date: " + endDate.toString());
//				} else {
//					dbmLogger.log(Level.INFO, "   End date: " + " <none>");
//				}

				dbmLogger.log(Level.INFO, "   Salary: " + salary);

			}

		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		}// end try

	} // end printRs()

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.pivotal.charlie_api.DatabaseInterface#insertNewRecord()
	 */
	public void insertNewRecord() {

		try {

			rs.last();

			int currentKey = rs.getInt("idEmployees");

			rs.moveToInsertRow();

			rs.updateInt("idEmployees", currentKey + 1);
			rs.updateString("emp_lname", "Star");
			rs.updateString("emp_fname", "Ringo");
			rs.updateString("title", "drummer");
			rs.updateString("phone", "0144712223333");
			rs.updateString("hat_size", "large");
			rs.updateInt("department", 200);

			java.sql.Date hireDate = new java.sql.Date(0);

			rs.updateDate("hired", hireDate);

			rs.updateInt("salary", 20000);

			// Commit row
			rs.insertRow();

		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		}// end try

		return;

	} // end insertNewRecord

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.pivotal.charlie_api.DatabaseInterface#deleteRecord(int)
	 */
	public void deleteRecord(int position) {

		try {
			// Set position to indicated record first
			rs.absolute(position);

			dbmLogger.log(Level.INFO, "List the record before deleting...");

			// Retrieve by column name
			int id = rs.getInt("idEmployees");
			int salary = rs.getInt("salary");
			String emp_fname = rs.getString("emp_fname");
			String emp_lname = rs.getString("emp_lname");

			// Display values
			dbmLogger.log(Level.INFO, "ID: " + id);
			dbmLogger.log(Level.INFO, ", Salary: " + salary);
			dbmLogger.log(Level.INFO, ", First Name: " + emp_fname);
			dbmLogger.log(Level.INFO, ", Last Name: " + emp_lname);
			dbmLogger.log(Level.INFO, "\n");

			// Delete row
			rs.deleteRow();

		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		}// end try

		return;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.pivotal.charlie_api.DatabaseInterface#updateRecords(java.lang.String,
	 * int)
	 */
	public void updateRecords(String column, int delta) {
		
		int myCounter;

		Console myconsole = System.console();
		
		if (myconsole == null) {
			System.err.println("No console.");
			System.exit(-1);
		}

		// Loop through result set and add 5 to salary
		try {
			// Move to BFR position so while-loop works properly
			rs.beforeFirst();
			myCounter = 1;
			// iterate over results set and call the single row update routine.
			// this level of indirection is introduced so that we can inject
			// advice before and after each row.
			while (rs.next()) {
				updateCurrentRecord(column, delta);
				System.out.println("updates done counter = " + myCounter);
		        String someString = myconsole.readLine("Press return to continue: ");
		        myCounter++;
			}

		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		}// end try

		return;
	}

}