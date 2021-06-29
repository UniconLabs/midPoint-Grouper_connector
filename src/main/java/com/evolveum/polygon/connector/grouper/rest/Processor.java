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
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Contains generic logic for handling REST operations.
 */
public class Processor {

	static final Log LOG = Log.getLog(GrouperConnector.class);

	GrouperConfiguration configuration;

	Processor(GrouperConfiguration configuration) {
		this.configuration = configuration;
	}

	CallResponse callRequest(HttpEntityEnclosingRequestBase request, JSONObject payload, ErrorHandler errorHandler) {
		if (!request.containsHeader("Content-Type")) {
			request.addHeader("Content-Type", configuration.getContentType());
		}

		if (!request.containsHeader("Authorization")) {
			request.addHeader("Authorization", "Basic " + getAuthEncoded());
		}

		request.setEntity(new ByteArrayEntity(payload.toString().getBytes(StandardCharsets.UTF_8)));

		if (Boolean.TRUE.equals(configuration.getLogRequestResponses())) {
			LOG.info("Payload: {0}", payload);      // we don't log the whole request, as it contains the (encoded) password
		}
		try (CloseableHttpResponse response = execute(request)) {
			if (Boolean.TRUE.equals(configuration.getLogRequestResponses())) {
				LOG.info("Response: {0}", response);
			}

			return processResponse(response, errorHandler);
		} catch (Exception e) {
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
		} catch (Exception e) {
			String msg = "Execution of the request failed: problem occurred during HTTP client execution: \n\t" + e.getLocalizedMessage();
			LOG.error("{0}", msg, e);
			throw new ConnectorIOException(msg);
		}
	}

	/**
	 * Checks HTTP response for errors. If the response is an error then the
	 * method throws the ConnId exception that is the most appropriate match for
	 * the error.
	 *
	 * @return true if the processing can continue
	 */
	private CallResponse processResponse(CloseableHttpResponse response, ErrorHandler errorHandler) throws IOException {
		int statusCode = response.getStatusLine().getStatusCode();
		LOG.info("Status code: {0}", statusCode);

		String result = null;
		try {
			result = EntityUtils.toString(response.getEntity());
			LOG.info("Response body: {0}", result);
		} catch (IOException e) {
			if (statusCode >= 200 && statusCode <= 299) {
				throw e;
			} else {
				LOG.warn("cannot read response body: {0}", e, e);
			}
		}

		if (statusCode >= 200 && statusCode <= 299) {
			return CallResponse.ok(result);
		}

		if (statusCode == 401 || statusCode == 403) {
			// sometimes there are binary data in responseBody
			closeResponse(response);
			String msg = "HTTP error " + statusCode + " " + response.getStatusLine().getReasonPhrase() + " : Authentication failure.";
			LOG.error("{0}", msg);
			throw new InvalidCredentialException(msg);
		}

		String msg = "HTTP error " + statusCode + " " + response.getStatusLine().getReasonPhrase() + " : " + result;
		closeResponse(response);
		try {
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
			}

			if (errorHandler != null) {
				try {
					CallResponse callResponse = errorHandler.handleError(statusCode, result);
					if (callResponse != null) {
						return callResponse;
					}
				} catch (Exception e) {
					// TODO Consider improving this
					throw new ConnectorException("Exception while handling error. Original message: " +
							msg + ", exception: " + e.getMessage(), e);
				}
			}
			throw new ConnectorException(msg);
		} catch (Exception e) {
			LOG.error("{0}", msg);
			throw e;
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

	public URIBuilder getUriBuilderRelative(String path) {
		try {
			URIBuilder uri = new URIBuilder(configuration.getBaseUrl());
			uri.setPath(configuration.getUriBasePath() + path);
			return uri;
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e.getMessage(), e);     // todo
		}
	}

	@FunctionalInterface
	public interface ErrorHandler {

		/**
		 * Returns null if the error couldn't be handled
		 */
		CallResponse handleError(int statusCode, String responseBody);
	}

	static class CallResponse {
		private final boolean success;
		private final JSONObject response;

		private CallResponse(boolean success, JSONObject response) {
			this.success = success;
			this.response = response;
		}

		static CallResponse ok(String text) {
			return new CallResponse(true, new JSONObject(text));
		}

		static CallResponse error(String text) {
			return new CallResponse(false, new JSONObject(text));
		}

		boolean isSuccess() {
			return success;
		}

		JSONObject getResponse() {
			return response;
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@SuppressWarnings("unused")
	public Object getIfExists(final JSONObject object, final List<String> possibleRootPathObjects, final List<String> potentialObjectNamesToReturn) {
		return get(object, false, possibleRootPathObjects,  potentialObjectNamesToReturn);
	}

	Object get(final JSONObject object, final List<String> possibleRootPathObjects, final List<String> potentialObjectNamesToReturn) {
		return get(object, true, possibleRootPathObjects, potentialObjectNamesToReturn);
	}

	/**
	 * Used for parsing JSON response objects and returns a target JSONObject or JSONArray
	 * @param object
	 * @param possibleRootPathObjects
	 * @param potentialObjectNamesToFind
	 * @return
	 */
	private Object get(final JSONObject object, boolean mustExist, final List<String> possibleRootPathObjects, final List<String> potentialObjectNamesToFind) {
		Object objectToReturn = null;

		if (potentialObjectNamesToFind.isEmpty()) {
			throw new IllegalArgumentException("Empty item search, there is a problem with this connector!");
		}

		final String keyMatch = (possibleRootPathObjects != null) ? object.keySet().stream().filter(possibleRootPathObjects::contains).findFirst().orElse(null) : null;
		if (mustExist && StringUtil.isBlank(keyMatch)) {
			throw new IllegalStateException("Expected one of " + possibleRootPathObjects + "; but none were found in the JSON response!");
		}

		final List<String> keysToCheck;
		if (StringUtil.isNotBlank(keyMatch)) {
			keysToCheck = List.of(keyMatch);

		} else {
			keysToCheck = object.keySet().stream().filter(potentialObjectNamesToFind::contains).collect(Collectors.toList()); //response object has key or is a simple group object

			if (keysToCheck.isEmpty()) {
				keysToCheck.addAll(object.keySet());
			}
		}

		for (String key : keysToCheck) {
			final Object keyObject = object.get(key);

			if (keyObject instanceof JSONObject) {
				final JSONObject o = (JSONObject) keyObject;
				final String match = o.keySet().stream().filter(potentialObjectNamesToFind::contains).findFirst().orElse(null);

				if (StringUtil.isNotBlank(match)) {
					objectToReturn = o.get(match); //second level most desired JSON Objects are going to be found here
 					break;
				} else {
					try {
						final Object possible = get(o, false, possibleRootPathObjects, potentialObjectNamesToFind); //3rd level or more recursive
						if (possible != null) {
							objectToReturn = possible;
							break;
						}
					} catch (JSONException e) {
						//swallow
					}
				}

			} else if (keyObject instanceof JSONArray) {
				final JSONArray o = (JSONArray) keyObject; //TODO not checking depth in JSON arrays at this time, just each element, is there a case in WS where this wouldn't work?
				Object possible = null;

				for (int i = 0; i < o.length(); i++) {
					try {
						final String match = ((JSONObject) o.get(i)).keySet().stream()
								.filter(potentialObjectNamesToFind::contains).findFirst().orElse(null);
						if (match != null) {
							possible = ((JSONObject) o.get(i)).get(match);
							break;
						}
					} catch (JSONException e) {
						//swallow
					}
				}

				if (possible != null) {
					objectToReturn = possible;
					break;
				}
			}
		}

		//TODO the following can likely be refactored/removed if needed.
		if (objectToReturn instanceof JSONArray) {
			final JSONArray array = (JSONArray) objectToReturn;
			if (array.length() == 0) {
				if (mustExist) {
					throw new IllegalStateException("Item " + objectToReturn + " is an empty array");
				} else {
					return null;
				}
		//	} else if (array.length() > 1) {
				//throw new IllegalStateException("Item " + objectToReturn + " is a multi-valued array (length: " + array.length() + ")");
			} else {
				return array;
			}
		} else if (objectToReturn != null && !(objectToReturn instanceof JSONObject)) {
			//throw new IllegalStateException("Item " + objectToReturn + " is neither object nor array; it is " + objectToReturn.getClass());
			LOG.warn("Item " + objectToReturn + " is neither object nor array; it is " + objectToReturn.getClass());
			return null;
		}

		return objectToReturn;
	}

	@SuppressWarnings("unused")
	JSONArray getArray(final JSONObject object, final List<String> items, final List<String> potentialArrayNames) {
		return getArray(object, true, items, potentialArrayNames);
	}

	JSONArray getArray(final JSONObject object, boolean mustExist, final List<String> rootItems, final List<String> potentialArrayNames) {
		final Object rv = get(object, mustExist, rootItems, potentialArrayNames);

		if (rv == null) {
			assert !mustExist;
			return null;
		} else if (rv instanceof JSONArray) {
			return (JSONArray) rv;
		} else {
			throw new IllegalStateException("Item " + Arrays.asList(rootItems) + " should be an array but it's " + rv.getClass());
		}
	}

	ConnectorException processException(Exception e, URIBuilder uriBuilder, final String operationName) {
		String msg = operationName + " failed: problem occurred during executing URI: " + uriBuilder + "\n\t" + e.getMessage();
		LOG.error("{0}", msg);
		return new ConnectorException(msg, e);
	}

	String getStringOrNull(final JSONObject object, final String item) {
		if (object.has(item)) {
			return (String) object.get(item); //TODO any safety or other processing details needed here?!?
		} else {
			return null;
		}
	}
}
