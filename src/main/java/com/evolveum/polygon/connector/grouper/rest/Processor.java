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
package com.evolveum.polygon.connector.grouper.rest;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
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
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author surmanek
 *
 */
public class Processor {

	static final Log LOG = Log.getLog(GrouperConnector.class);
	static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
	public static final String WS_FIND_STEMS_RESULTS = "WsFindStemsResults";
	public static final String RESULT_METADATA = "resultMetadata";
	public static final String SUCCESS = "success";
	public static final String STEM_RESULTS = "stemResults";
	public static final String PATH_STEMS = "/stems";
	public static final String WS_GET_MEMBERS_RESULTS = "WsGetMembersResults";
	public static final String RESULTS = "results";
	public static final String WS_SUBJECTS = "wsSubjects";
	public static final String WS_REST_GET_MEMBERS_REQUEST = "WsRestGetMembersRequest";
	public static final String WS_GROUP_LOOKUPS = "wsGroupLookups";
	public static final String GROUP_NAME = "groupName";
	public static final String WS_GET_GROUPS_RESULTS = "WsGetGroupsResults";
	public static final String WS_GROUPS = "wsGroups";
	public static final String WS_GROUP = "wsGroup";
	GrouperConfiguration configuration;

	public static final String URI_BASE_PATH = "/grouper-ws/servicesRest/json/v2_4_000";
	public static final String PATH_GROUPS = "/groups";
	public static final String PATH_SUBJECTS = "/subjects";

	public Processor(GrouperConfiguration configuration) {
		this.configuration = configuration;
	}

	//put objects of array1 to the end of array2
	private JSONArray concatJSONArrays(JSONArray array1, JSONArray array2){
		for (Object obj : array1){
			array2.put(obj);
		}
		return array2;
	}

	JSONObject callRequest(HttpEntityEnclosingRequestBase request, JSONObject jo, Boolean parseResult,
			String contentType) {
		// don't log request here - password field !!!
		if (contentType != null)
			request.addHeader("Content-Type", contentType);
		request.addHeader("Authorization", "Basic " + authEncoding());
		HttpEntity entity;
		try {
			entity = new ByteArrayEntity(jo.toString().getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e1) {
			String exceptionMsg = "Creating request entity failed: problem occurred during entity encoding.";
			LOG.error("{0}", exceptionMsg);
			throw new ConnectorIOException(exceptionMsg);
		}
		request.setEntity(entity);
		try (CloseableHttpResponse response = execute(request)){
			
			
			
			//LOG.ok("Request: {0}", request.toString());
			//response = execute(request);
			LOG.ok("Response: {0}", response);
			processResponseErrors(response);

			if (!parseResult) {
				return null;
			}
			
			String result = EntityUtils.toString(response.getEntity());

			LOG.ok("Response body: {0}", result);
			return new JSONObject(result);
		} catch (IOException e) {
			StringBuilder exceptionMsg = new StringBuilder();
			exceptionMsg.append("Request failed: problem occured during execute request with uri: ")
					.append(request.getURI()).append(": \n\t").append(e.getLocalizedMessage());
			LOG.error("{0}", exceptionMsg.toString());
			throw new ConnectorIOException(exceptionMsg.toString(), e);
		}
	}

	JSONObject callRequest(HttpRequestBase request, Boolean parseResult, String contentType) {
		// don't log request here - password field !!!
		//CloseableHttpResponse response = null;
		LOG.ok("request URI: {0}", request.getURI());
		request.addHeader("Content-Type", contentType);
		request.addHeader("Authorization", "Basic " + authEncoding());
		try (CloseableHttpResponse response = execute(request)){
			
			LOG.ok("Response: {0}", response);
			processResponseErrors(response);

			if (!parseResult) {
				//closeResponse(response);
				return null;
			}
			// DO NOT USE getEntity() TWICE!!!
			String result = EntityUtils.toString(response.getEntity());
			//closeResponse(response);
			LOG.ok("Response body: {0}", result);
			return new JSONObject(result);
		} catch (IOException e) {
			StringBuilder exceptionMsg = new StringBuilder();
			exceptionMsg.append("Request failed: problem occured during execute request with uri: ")
					.append(request.getURI()).append(": \n\t").append(e.getLocalizedMessage());
			//closeResponse(response);
			LOG.error("{0}", exceptionMsg.toString());
			throw new ConnectorIOException(exceptionMsg.toString(), e);
		}
	}

	String authEncoding() {
		String username = configuration.getUsername();
		String password = configuration.getStringPassword();
		if (username == null || username.equals("")) {
			LOG.error("Authentication failed: Username is not provided.");
			throw new InvalidCredentialException("Authentication failed: Username is not provided.");
		}
		if (password == null || password.equals("")) {
			LOG.error("Authentication failed: Password is not provided.");
			throw new InvalidPasswordException("Authentication failed: Password is not provided.");
		}
		StringBuilder nameAndPasswd = new StringBuilder();
		nameAndPasswd.append(username).append(":").append(password);
		// String nameAndPasswd = "administrator:training"
		String encoding = Base64.encodeBase64String(nameAndPasswd.toString().getBytes());
		return encoding;
	}

	CloseableHttpResponse execute(HttpUriRequest request) {
		try {
			HttpClientBuilder clientBuilder = HttpClientBuilder.create();
			if (Boolean.TRUE.equals(configuration.getIgnoreSslValidation())) {
				SSLContextBuilder sslCtxBuilder = new SSLContextBuilder();
				sslCtxBuilder.loadTrustMaterial(null, new TrustStrategy() {
					public boolean isTrusted(X509Certificate[] chain, String authType) {
						return true;
					}
				});
				SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(sslCtxBuilder.build(), NoopHostnameVerifier.INSTANCE);
				clientBuilder.setSSLSocketFactory(factory);
				System.out.println("Ignoring SSL validation");
			}
			CloseableHttpClient client = clientBuilder.build();
			CloseableHttpResponse response = client.execute(request);
			// print response code:
			LOG.ok("response code: {0}", String.valueOf(response.getStatusLine().getStatusCode()));
			// client.close();
			// DO NOT CLOSE response HERE !!!
			return response;
		} catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
			StringBuilder exceptionMsg = new StringBuilder();
			exceptionMsg.append("Execution of the request failed: problem occurred during HTTP client execution: \n\t")
					.append(e.getLocalizedMessage());
			LOG.error("{0}", exceptionMsg.toString(), e);
			e.printStackTrace();
			throw new ConnectorIOException(exceptionMsg.toString());
		}
	}

	/**
	 * Checks HTTP response for errors. If the response is an error then the
	 * method throws the ConnId exception that is the most appropriate match for
	 * the error.
	 */
	void processResponseErrors(CloseableHttpResponse response) {
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode >= 200 && statusCode <= 299) {
			return;
		}
		String responseBody = null;
		try {
			responseBody = EntityUtils.toString(response.getEntity());
		} catch (IOException e) {
			LOG.warn("cannot read response body: {0}", e, e);
		}

		StringBuilder message = new StringBuilder();
		message.append("HTTP error ").append(statusCode).append(" ").append(response.getStatusLine().getReasonPhrase())
				.append(" : ").append(responseBody);
		if (statusCode == 401 || statusCode == 403) {
			StringBuilder anauthorizedMessage = new StringBuilder(); // response
																		// body
																		// of
																		// status
																		// code
																		// 401
																		// contains
																		// binary
																		// data.
			anauthorizedMessage.append("HTTP error ").append(statusCode).append(" ")
					.append(response.getStatusLine().getReasonPhrase())
					.append(" : Provided credentials are incorrect.");
			closeResponse(response);
			LOG.error("{0}", anauthorizedMessage.toString());
			throw new InvalidCredentialException(anauthorizedMessage.toString());
		}
		LOG.error("{0}", message.toString());
		if ((statusCode == 400 || statusCode == 404) && message.toString().contains("already")) {
			closeResponse(response);
			LOG.error("{0}", message.toString());
			throw new AlreadyExistsException(message.toString());
		}
		if (statusCode == 400 || statusCode == 405 || statusCode == 406) {
			closeResponse(response);
			LOG.error("{0}", message.toString());
			throw new ConnectorIOException(message.toString());
		}
		if (statusCode == 402 || statusCode == 407) {
			closeResponse(response);
			LOG.error("{0}", message.toString());
			throw new PermissionDeniedException(message.toString());
		}
		if (statusCode == 404 || statusCode == 410) {
			closeResponse(response);
			LOG.error("{0}", message.toString());
			throw new UnknownUidException(message.toString());
		}
		if (statusCode == 408) {
			closeResponse(response);
			LOG.error("{0}", message.toString());
			throw new OperationTimeoutException(message.toString());
		}
		if (statusCode == 412) {
			closeResponse(response);
			LOG.error("{0}", message.toString());
			throw new PreconditionFailedException(message.toString());
		}
		if (statusCode == 418) {
			closeResponse(response);
			LOG.error("{0}", message.toString());
			throw new UnsupportedOperationException("Sorry, no coffee: " + message.toString());
		}

		closeResponse(response);
		LOG.error("{0}", message.toString());
		throw new ConnectorException(message.toString());
	}

	void closeResponse(CloseableHttpResponse response) {
		// to avoid pool waiting
		if (response == null)
			return;
		try {
			response.close();
		} catch (IOException e) {
			LOG.warn("Failed to close response: {0}", response, e);
		}
	}

	// filter json objects by substring:
	JSONArray substringFiltering(JSONArray inputJsonArray, String attrName, String subValue) {
		JSONArray jsonArrayOut = new JSONArray();
		// String attrName = attribute.getName().toString();
		// LOGGER.info("\n\tSubstring filtering: {0} ({1})", attrName,
		// subValue);
		for (int i = 0; i < inputJsonArray.length(); i++) {
			JSONObject jsonObject = inputJsonArray.getJSONObject(i);
			if (!jsonObject.has(attrName)) {
				LOG.warn("\n\tProcessing JSON Object does not contain attribute {0}.", attrName);
				return null;
			}
			if (jsonObject.has(attrName) && (jsonObject.get(attrName)).toString().contains(subValue)) {
				// LOG.ok("value: {0}, subValue: {1} - MATCH: {2}",
				// jsonObject.get(attrName).toString(), subValue, "YES");
				jsonArrayOut.put(jsonObject);
			}
			// else LOG.ok("value: {0}, subValue: {1} - MATCH: {2}",
			// jsonObject.getString(attrName), subValue, "NO");
		}
		return jsonArrayOut;
	}

	// method called when attribute of query filter is null:
	void throwNullAttrException(Filter query) {
		StringBuilder exceptionMsg = new StringBuilder();
		exceptionMsg
				.append("Get operation failed: problem occurred because of not provided attribute of query filter: ")
				.append(query);
		LOG.error("{0}", exceptionMsg.toString());
		throw new InvalidAttributeValueException(exceptionMsg.toString());
	}

	// create uri from base host:
	URIBuilder getURIBuilder() {
		try {
			URIBuilder uri = new URIBuilder(configuration.getBaseUrl());
			uri.setPath(URI_BASE_PATH);
			return uri;
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e.getMessage(), e);     // todo
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	<T> T addAttr(ConnectorObjectBuilder builder, String attrName, T attrVal) {
		if (attrVal != null) {
			builder.addAttribute(attrName, attrVal);
		}
		return attrVal;
	}

	String getStringAttr(Set<Attribute> attributes, String attrName) throws InvalidAttributeValueException {
		return getAttr(attributes, attrName, String.class);
	}

	<T> T getAttr(Set<Attribute> attributes, String attrName, Class<T> type)
			throws InvalidAttributeValueException {
		return getAttr(attributes, attrName, type, null);
	}


	@SuppressWarnings("unchecked")
	private <T> T getAttr(Set<Attribute> attributes, String attrName, Class<T> type, T defaultVal)
			throws InvalidAttributeValueException {
		for (Attribute attr : attributes) {
			if (attrName.equals(attr.getName())) {
				List<Object> vals = attr.getValue();
				if (vals == null || vals.isEmpty()) {
					// set empty value
					return null;
				}
				if (vals.size() == 1) {
					Object val = vals.get(0);
					if (val == null) {
						// set empty value
						return null;
					}
					if (type.isAssignableFrom(val.getClass())) {
						return (T) val;
					}
					StringBuilder exceptionMsg = new StringBuilder();
					exceptionMsg.append("Unsupported type ").append(val.getClass()).append(" for attribute ")
							.append(attrName).append(", value: ").append(val);
					LOG.error("{0}", exceptionMsg.toString());
					throw new InvalidAttributeValueException(exceptionMsg.toString());
				}
				StringBuilder exceptionMsg = new StringBuilder();
				exceptionMsg.append("More than one value for attribute ").append(attrName).append(", values: ")
						.append(vals);
				LOG.error("{0}", exceptionMsg.toString());
				throw new InvalidAttributeValueException(exceptionMsg.toString());
			}
		}
		// set default value when attrName not in changed attributes
		return defaultVal;
	}

	void getIfExists(JSONObject jsonObj, String attr, ConnectorObjectBuilder builder, boolean isMultiValue) {
		if (jsonObj.has(attr) && jsonObj.get(attr) != null && !JSONObject.NULL.equals(jsonObj.get(attr))) {
			if (isMultiValue) {
				JSONArray attrJSONArray = jsonObj.getJSONArray(attr);
				if (attrJSONArray != null) {
					int size = attrJSONArray.length();
					ArrayList<String> attrStringArray = new ArrayList<String>();
					for (int i = 0; i < size; i++) {
						attrStringArray.add(attrJSONArray.get(i).toString());
					}
					builder.addAttribute(attr, attrStringArray.toArray());
				}
			} else
				addAttr(builder, attr, jsonObj.get(attr));
		}
	}

	public void checkSuccess(JSONObject response, String rootName) {
		Object success = get(response, rootName, RESULT_METADATA, SUCCESS);
		if (!"T".equals(success)) {
			throw new IllegalStateException("Request was not successful: " + success);
		}
	}

	public Object getIfExists(JSONObject object, String... items) {
		return get(object, false, items);
	}

	public Object get(JSONObject object, String... items) {
		return get(object, true, items);
	}

	public Object get(JSONObject object, boolean mustExist, String... items) {
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

	public JSONArray getArray(JSONObject object, String... items) {
		return getArray(object, true, items);
	}

	public JSONArray getArray(JSONObject object, boolean mustExist, String... items) {
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

	public ConnectorException processException(Exception e, URIBuilder uriBuilder, final String operationName) {
		StringBuilder exceptionMsg = new StringBuilder();
		exceptionMsg.append(operationName).append(" failed: problem occurred during executing URI: ").append(uriBuilder)
				.append("\n\t").append(e.getLocalizedMessage());
		LOG.error("{0}", exceptionMsg.toString());
		return new ConnectorException(exceptionMsg.toString(), e);
	}

	public boolean isSuccess(JSONObject object) {
		return "T".equals(getStringOrNull(object, SUCCESS));
	}

	public String getStringOrNull(JSONObject object, String item) {
		if (object.has(item)) {
			return getString(object, item);
		} else {
			return null;
		}
	}

	public String getString(JSONObject object, String item) {
		return (String) get(object, item);  // todo error handling
	}
}
