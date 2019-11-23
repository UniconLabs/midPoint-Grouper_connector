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
package com.evolveum.polygon.connector.grouper.rest;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Contains generic logic for handling REST operations over Grouper.
 */
public class Processor {

	static final Log LOG = Log.getLog(GrouperConnector.class);

	private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

	static final String J_WS_REST_GET_MEMBERS_REQUEST = "WsRestGetMembersRequest";
	static final String J_WS_REST_FIND_GROUPS_REQUEST = "WsRestFindGroupsRequest";
	static final String J_WS_REST_FIND_STEMS_REQUEST = "WsRestFindStemsRequest";

	static final String J_WS_QUERY_FILTER = "wsQueryFilter";
	static final String J_WS_STEM_QUERY_FILTER = "wsStemQueryFilter";
	static final String J_STEM_QUERY_FILTER_TYPE = "stemQueryFilterType";
	static final String J_INCLUDE_SUBJECT_DETAIL = "includeSubjectDetail";
	static final String J_QUERY_FILTER_TYPE = "queryFilterType";
	static final String J_STEM_NAME = "stemName";
	static final String J_STEM_NAME_SCOPE = "stemNameScope";
	static final String J_GROUP_NAME = "groupName";

	static final String J_WS_FIND_GROUPS_RESULTS = "WsFindGroupsResults";
	static final String J_WS_FIND_STEMS_RESULTS = "WsFindStemsResults";
	static final String J_WS_GET_MEMBERS_RESULTS = "WsGetMembersResults";

	static final String J_RESULTS = "results";
	static final String J_STEM_RESULTS = "stemResults";
	static final String J_GROUP_RESULTS = "groupResults";
	static final String J_WS_GROUP_LOOKUPS = "wsGroupLookups";
	private static final String J_RESULT_METADATA = "resultMetadata";
	private static final String J_SUCCESS = "success";

	static final String J_WS_SUBJECTS = "wsSubjects";
	static final String J_WS_GROUP = "wsGroup";

	static final String J_UUID = "uuid";
	static final String J_NAME = "name";
	static final String J_EXTENSION = "extension";
	static final String J_SOURCE_ID = "sourceId";
	static final String J_ID = "id";

	private static final String VAL_T = "T";
	static final String VAL_FIND_BY_STEM_NAME = "FIND_BY_STEM_NAME";
	static final String VAL_ALL_IN_SUBTREE = "ALL_IN_SUBTREE";

	private static final String URI_BASE_PATH = "/grouper-ws/servicesRest/json/v2_4_000";
	private static final String PATH_GROUPS = "/groups";
	private static final String PATH_STEMS = "/stems";

	GrouperConfiguration configuration;

	Processor(GrouperConfiguration configuration) {
		this.configuration = configuration;
	}

	JSONObject callRequest(HttpEntityEnclosingRequestBase request, JSONObject payload) {
		request.addHeader("Content-Type", Processor.CONTENT_TYPE_JSON);
		request.addHeader("Authorization", "Basic " + getAuthEncoded());
		request.setEntity(new ByteArrayEntity(payload.toString().getBytes(StandardCharsets.UTF_8)));
		LOG.info("Payload: {0}", payload);      // we don't log the whole request, as it contains the (encoded) password
		try (CloseableHttpResponse response = execute(request)) {
			LOG.info("Response: {0}", response);
			processResponseErrors(response);

			String result = EntityUtils.toString(response.getEntity());
			LOG.info("Response body: {0}", result);
			return new JSONObject(result);
		} catch (IOException e) {
			String msg = "Request failed: problem occurred during execute request with uri: " + request.getURI() + ": \n\t" + e.getLocalizedMessage();
			LOG.error("{0}", msg);
			throw new ConnectorIOException(msg, e);
		}
	}

	private String getAuthEncoded() {
		String username = configuration.getUsername();
		String password = configuration.getPasswordPlain();
		if (username == null || username.equals("")) {
			String msg = "Authentication failed: No user name specified";
			LOG.error("{0}", msg);
			throw new InvalidCredentialException(msg);
		}
		if (password == null || password.equals("")) {
			String msg = "Authentication failed: No password specified";
			LOG.error("{0}", msg);
			throw new InvalidPasswordException(msg);
		}
		return Base64.encodeBase64String((username + ":" + password).getBytes());
	}

	private CloseableHttpResponse execute(HttpUriRequest request) {
		try {
			HttpClientBuilder clientBuilder = HttpClientBuilder.create();
			if (Boolean.TRUE.equals(configuration.getIgnoreSslValidation())) {
				SSLContextBuilder sslCtxBuilder = new SSLContextBuilder();
				sslCtxBuilder.loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true);
				SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(sslCtxBuilder.build(), NoopHostnameVerifier.INSTANCE);
				clientBuilder.setSSLSocketFactory(factory);
				LOG.warn("Ignoring SSL validation: avoid this in production");
			}
			CloseableHttpClient client = clientBuilder.build();
			CloseableHttpResponse response = client.execute(request);
			LOG.ok("response code: {0}", response.getStatusLine().getStatusCode());
			// DO NOT CLOSE response HERE !!!
			return response;
		} catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
			String msg = "Execution of the request failed: problem occurred during HTTP client execution: \n\t" + e.getLocalizedMessage();
			LOG.error("{0}", msg, e);
			throw new ConnectorIOException(msg);
		}
	}

	/**
	 * Checks HTTP response for errors. If the response is an error then the
	 * method throws the ConnId exception that is the most appropriate match for
	 * the error.
	 */
	private void processResponseErrors(CloseableHttpResponse response) {
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode >= 200 && statusCode <= 299) {
			return;
		}

		if (statusCode == 401 || statusCode == 403) {
			// sometimes there are binary data in responseBody
			closeResponse(response);
			String msg = "HTTP error " + statusCode + " " + response.getStatusLine().getReasonPhrase() + " : Authentication failure.";
			LOG.error("{0}", msg);
			throw new InvalidCredentialException(msg);
		}

		String responseBody = null;
		try {
			responseBody = EntityUtils.toString(response.getEntity());
		} catch (IOException e) {
			LOG.warn("cannot read response body: {0}", e, e);
		}
		String msg = "HTTP error " + statusCode + " " + response.getStatusLine().getReasonPhrase() + " : " + responseBody;
		LOG.error("{0}", msg);
		closeResponse(response);
		if (statusCode == 400 || statusCode == 405 || statusCode == 406) {
			throw new ConnectorIOException(msg);
		} else if (statusCode == 402 || statusCode == 407) {
			throw new PermissionDeniedException(msg);
		} else if (statusCode == 404 || statusCode == 410) {
			throw new UnknownUidException(msg);
		} else if (statusCode == 408) {
			throw new OperationTimeoutException(msg);
		} else if (statusCode == 412) {
			throw new PreconditionFailedException(msg);
		} else if (statusCode == 418) {
			throw new UnsupportedOperationException("Sorry, no coffee: " + msg);
		} else {
			throw new ConnectorException(msg);
		}
	}

	private void closeResponse(CloseableHttpResponse response) {
		// to avoid pool waiting
		try {
			response.close();
		} catch (IOException e) {
			LOG.warn("Failed to close response: {0}", response, e);
		}
	}

	private URIBuilder getUriBuilderRelative(String path) {
		try {
			URIBuilder uri = new URIBuilder(configuration.getBaseUrl());
			uri.setPath(URI_BASE_PATH + path);
			return uri;
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e.getMessage(), e);     // todo
		}
	}

	URIBuilder getUriBuilderForGroups() {
		return getUriBuilderRelative(PATH_GROUPS);
	}

	URIBuilder getUriBuilderForStems() {
		return getUriBuilderRelative(PATH_STEMS);
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	void checkSuccess(JSONObject response, String rootName) {
		Object success = get(response, rootName, J_RESULT_METADATA, J_SUCCESS);
		if (!VAL_T.equals(success)) {
			throw new IllegalStateException("Request was not successful: " + success);
		}
	}

	@SuppressWarnings("unused")
	public Object getIfExists(JSONObject object, String... items) {
		return get(object, false, items);
	}

	Object get(JSONObject object, String... items) {
		return get(object, true, items);
	}

	private Object get(JSONObject object, boolean mustExist, String... items) {
		if (items.length == 0) {
			throw new IllegalArgumentException("Empty item path");
		}
		for (int i = 0; i < items.length - 1; i++) {
			if (!object.has(items[i])) {
				if (mustExist) {
					throw new IllegalStateException("Item " + Arrays.asList(items).subList(0, i) + " was not found");
				} else {
					return null;
				}
			}
			Object o = object.get(items[i]);
			if (o instanceof JSONArray) {
				JSONArray array = (JSONArray) o;
				if (array.length() == 0) {
					if (mustExist) {
						throw new IllegalStateException("Item " + Arrays.asList(items).subList(0, i) + " is an empty array");
					} else {
						return null;
					}
				} else if (array.length() > 1) {
					throw new IllegalStateException("Item " + Arrays.asList(items).subList(0, i) + " is a multi-valued array (length: " + array.length() + ")");
				} else {
					o = array.get(0);
				}
			}
			if (o instanceof JSONObject) {
				object = (JSONObject) o;
			} else {
				throw new IllegalStateException("Item " + Arrays.asList(items).subList(0, i) + " is neither object nor array; it is " + o.getClass());
			}
		}
		String last = items[items.length - 1];
		if (object.has(last)) {
			return object.get(last);
		} else if (mustExist) {
			throw new IllegalStateException("Item " + Arrays.asList(items) + " was not found");
		} else {
			return null;
		}
	}

	@SuppressWarnings("unused")
	JSONArray getArray(JSONObject object, String... items) {
		return getArray(object, true, items);
	}

	JSONArray getArray(JSONObject object, boolean mustExist, String... items) {
		Object rv = get(object, mustExist, items);
		if (rv == null) {
			assert !mustExist;
			return null;
		} else if (rv instanceof JSONArray) {
			return (JSONArray) rv;
		} else {
			throw new IllegalStateException("Item " + Arrays.asList(items) + " should be an array but it's " + rv.getClass());
		}
	}

	ConnectorException processException(Exception e, URIBuilder uriBuilder, final String operationName) {
		String msg = operationName + " failed: problem occurred during executing URI: " + uriBuilder + "\n\t" + e.getMessage();
		LOG.error("{0}", msg);
		return new ConnectorException(msg, e);
	}

	@SuppressWarnings("unused")
	public boolean isSuccess(JSONObject object) {
		return VAL_T.equals(getStringOrNull(object, J_SUCCESS));
	}

	String getStringOrNull(JSONObject object, String item) {
		if (object.has(item)) {
			return getString(object, item);
		} else {
			return null;
		}
	}

	private String getString(JSONObject object, String item) {
		return (String) get(object, item);  // todo error handling
	}

	boolean groupNameMatches(String name) {
		if (name == null) {
			return false;
		}
		String[] includes = configuration.getGroupIncludePattern();
		String[] excludes = configuration.getGroupExcludePattern();
		return (includes == null || includes.length == 0 || groupNameMatches(name, includes)) &&
				!groupNameMatches(name, excludes);
	}

	private boolean groupNameMatches(String name, String[] patterns) {
		if (patterns == null) {
			return false;
		}
		for (String pattern : patterns) {
			Pattern compiled = Pattern.compile(pattern);
			if (compiled.matcher(name).matches()) {
				return true;
			}
		}
		return false;
	}
}
