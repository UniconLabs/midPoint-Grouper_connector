package com.evolveum.polygon.connector.grouper.test;
/*******************************************************************************
 * Copyright 2017 Evolveum
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License.  
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  
 * See the License for the specific language governing permissions and limitations under the License.
 ******************************************************************************/

import com.evolveum.polygon.connector.grouper.rest.GrouperConfiguration;
import com.evolveum.polygon.connector.grouper.rest.GrouperConnector;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.spi.SearchResultsHandler;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author surmanek
 * @author mederly
 */
public class GrouperTestHelper {
	
	private static final String BASE_URL = "https://192.168.56.101:9443";
	private static final String ADMIN_USERNAME = "banderson";
	private static final String ADMIN_PASSWORD = "password";
	private static final String SUPER_GROUP = "etc:sysadmingroup";
	private static final String ROOT_STEM = "etc";
	private static final String SUBJECT_SOURCE = "ldap";

	protected final GrouperConnector grouperConnector = new GrouperConnector();
	protected final OperationOptions options = new OperationOptions(new HashMap<String, Object>());
	protected final ObjectClass accountObjectClass = ObjectClass.ACCOUNT;

	protected final ArrayList<ConnectorObject> results = new ArrayList<>();
	protected SearchResultsHandler handler = new SearchResultsHandler() {
		@Override
		public boolean handle(ConnectorObject connectorObject) {
			results.add(connectorObject);
			return true;
		}

		@Override
		public void handleResult(SearchResult result) {
		}
	};
	
	//group variables:
	protected static final ObjectClass groupObjectClass = ObjectClass.GROUP;
	protected final ArrayList<ConnectorObject> groupResults = new ArrayList<>();
	protected SearchResultsHandler groupHandler = new SearchResultsHandler() {
		@Override
		public boolean handle(ConnectorObject connectorObject) {
			groupResults.add(connectorObject);
			return true;
		}

		@Override
		public void handleResult(SearchResult result) {
		}
	};
	
	//project variables:
	protected static final ObjectClass projectObjectClass = new ObjectClass("PROJECT");
	protected final ArrayList<ConnectorObject> projectResults = new ArrayList<>();
	protected SearchResultsHandler projectHandler = new SearchResultsHandler() {
		@Override
		public boolean handle(ConnectorObject connectorObject) {
			projectResults.add(connectorObject);
			return true;
		}

		@Override
		public void handleResult(SearchResult result) {
		}
	};
	
	protected GrouperConfiguration getConfiguration() {
		GrouperConfiguration config = new GrouperConfiguration();
		config.setBaseUrl(BASE_URL);
		config.setUsername(ADMIN_USERNAME);
		config.setPassword(new GuardedString(ADMIN_PASSWORD.toCharArray()));
		config.setSuperGroup(SUPER_GROUP);
		config.setGroupIncludePattern(new String[] { SUPER_GROUP });
		config.setIgnoreSslValidation(true);
		config.setSubjectSource(SUBJECT_SOURCE);
		config.setExportStem("ref");
		return config;
	}
}
