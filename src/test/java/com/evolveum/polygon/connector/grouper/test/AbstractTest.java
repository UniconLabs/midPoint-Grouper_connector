/*
 * Copyright (c) 2019 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.polygon.connector.grouper.test;

import com.evolveum.polygon.connector.grouper.rest.GrouperConfiguration;
import com.evolveum.polygon.connector.grouper.rest.GrouperConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.spi.SearchResultsHandler;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Superclass for connector tests. These tests require running Grouper instance, with a group of TEST_GROUP having
 * a single user TEST_USER in subject source SUBJECT_SOURCE.
 */
class AbstractTest {

	// Test configuration
	static final String TEST_USER = "banderson";
	static final String TEST_GROUP = "etc:sysadmingroup";
	static final String TEST_GROUP_NON_EXISTENT = "etc:thisGroupDoesNotExist";
	static final String TEST_UUID_NON_EXISTENT = "dd089842948329438249284928289XXX";

	// Connector configuration
	private static final String BASE_URL = "https://localhost:9443";
	private static final String ADMIN_USERNAME = TEST_USER;
	private static final String ADMIN_PASSWORD = "password";
	private static final String BASE_STEM = "etc";
	private static final String[] GROUP_INCLUDE_PATTERN = { ".*" };
	private static final String[] GROUP_EXCLUDE_PATTERN = { ".*_(includes|excludes|systemOfRecord|systemOfRecordAndIncludes)" };
	private static final String SUBJECT_SOURCE = "ldap";
	private static final String TEST_STEM = ":";
	private static final Integer PAGE_SIZE = 5;
	private static final String CONTENT_TYPE = "application/json; charset=utf-8";
	private static final String URI_BASE_PATH = "/grouper-ws/servicesRest/json/v2_4_000";
	private static final Boolean IGNORE_SSL_VALIDATION = true;
	private static final Boolean LOG_REQUEST_RESPONSES = true;
	private static final String GROUP_ATTRIBUTE = "control:attr:DeliverTo||midpoint";

	final GrouperConnector grouperConnector = new GrouperConnector();
	final OperationOptions options = new OperationOptions(new HashMap<>());

	final ArrayList<ConnectorObject> results = new ArrayList<>();
	SearchResultsHandler handler = new SearchResultsHandler() {
		@Override
		public boolean handle(ConnectorObject connectorObject) {
			results.add(connectorObject);
			return true;
		}

		@Override
		public void handleResult(SearchResult result) {
		}
	};
	
	GrouperConfiguration getConfigurationBaseStem() {
		GrouperConfiguration config = new GrouperConfiguration();
		config.setBaseUrl(BASE_URL);
		config.setUsername(ADMIN_USERNAME);
		config.setPassword(new GuardedString(ADMIN_PASSWORD.toCharArray()));
		config.setIgnoreSslValidation(true);
		config.setBaseStem(BASE_STEM);
		config.setGroupIncludePattern(GROUP_INCLUDE_PATTERN);
		config.setGroupExcludePattern(GROUP_EXCLUDE_PATTERN);
		config.setSubjectSource(SUBJECT_SOURCE);
		config.setTestStem(TEST_STEM);
		config.setTestGroup(TEST_GROUP);
		config.setPageSize(PAGE_SIZE);
		config.setUriBasePath(URI_BASE_PATH);
		config.setContentType(CONTENT_TYPE);
		config.setIgnoreSslValidation(IGNORE_SSL_VALIDATION);
		config.setLogRequestResponses(LOG_REQUEST_RESPONSES);
		return config;
	}

	GrouperConfiguration getConfigurationAttributeFilter() {
		GrouperConfiguration config = new GrouperConfiguration();
		config.setBaseUrl(BASE_URL);
		config.setUsername(ADMIN_USERNAME);
		config.setPassword(new GuardedString(ADMIN_PASSWORD.toCharArray()));
		config.setIgnoreSslValidation(true);
		config.setGroupIncludePattern(GROUP_INCLUDE_PATTERN);
		config.setGroupExcludePattern(GROUP_EXCLUDE_PATTERN);
		config.setSubjectSource(SUBJECT_SOURCE);
		config.setTestStem(TEST_STEM);
		config.setTestGroup(TEST_GROUP);
		config.setPageSize(PAGE_SIZE);
		config.setUriBasePath(URI_BASE_PATH);
		config.setContentType(CONTENT_TYPE);
		config.setIgnoreSslValidation(IGNORE_SSL_VALIDATION);
		config.setLogRequestResponses(LOG_REQUEST_RESPONSES);
		config.setGroupAttribute(new String[]{GROUP_ATTRIBUTE});
		return config;
	}
}
