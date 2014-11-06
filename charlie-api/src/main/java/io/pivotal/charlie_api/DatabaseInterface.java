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

package io.pivotal.charlie_api;

import java.sql.Connection;

public interface DatabaseInterface {

	public void close();

	public void executeEmployeeQuery();

	public void printRecords();

	public void insertNewRecord();

	public void deleteRecord(int position);

	public void updateRecords(String column, int delta);

	public void updateCurrentRecord(String column, int delta);
	
	public Connection getConn();
	
	public void setConn(Connection conn);


}