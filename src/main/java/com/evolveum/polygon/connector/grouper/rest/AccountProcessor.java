/*
 ******************************************************************************
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

import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.evolveum.polygon.connector.grouper.rest.Processor.*;

/**
 * @author surmanek
 * @author mederly
 *
 */
class AccountProcessor {

	private final Processor processor;

	private static final String ATTR_GROUP = "group";

	AccountProcessor(Processor processor) {
		this.processor = processor;
	}

	ObjectClassInfoBuilder buildSchema() {
		ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
		AttributeInfoBuilder groups = new AttributeInfoBuilder(ATTR_GROUP, String.class);
		groups.setMultiValued(true);
		builder.addAttributeInfo(groups.build());
		return builder;
	}

	void read(Filter filter, ResultsHandler handler, OperationOptions options) {
		if (filter == null) {
			getAllUsers(handler);
		} else if (filter instanceof EqualsFilter &&
				(((EqualsFilter) filter).getAttribute() instanceof Name || ((EqualsFilter) filter).getAttribute() instanceof Uid)) {
			Attribute name = ((EqualsFilter) filter).getAttribute();
			if (name != null) {
				if (name.getValue() == null || name.getValue().isEmpty()) {
					throw new IllegalArgumentException("No ID to look for");
				} else if (name.getValue().size() > 1) {
					throw new IllegalArgumentException("More than one ID to look for: " + name.getValue());
				} else {
					getUser((String) name.getValue().get(0), handler);
				}
			} else {
				processor.throwNullAttrException(filter);
			}
		} else {
			throw new IllegalArgumentException("Unsupported filter: " + filter);
		}
	}

	private void getAllUsers(ResultsHandler handler) {
		URIBuilder uriBuilder = processor.getURIBuilder()
				.setPath(URI_BASE_PATH + PATH_GROUPS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put(WS_REST_GET_MEMBERS_REQUEST, new JSONObject()
							.put(WS_GROUP_LOOKUPS, new JSONObject[] {
									new JSONObject().put(GROUP_NAME, getConfiguration().getSuperGroup()) }));
			System.out.println("Request = " + body.toString());
			JSONObject response = processor.callRequest(request, body, true, CONTENT_TYPE_JSON);
			System.out.println("Got response: " + response);
			processor.checkSuccess(response, WS_GET_MEMBERS_RESULTS);
			JSONArray subjects = processor.getArray(response, WS_GET_MEMBERS_RESULTS, RESULTS, WS_SUBJECTS);
			List<String> ids = selectSubjectIds(subjects);
			System.out.println("Subject IDs found: " + ids);
			for (String id : ids) {
				ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
				builder.setUid(id);
				builder.setName(id);
				AttributeBuilder groupBuilder = new AttributeBuilder().setName(ATTR_GROUP);
				groupBuilder.setAttributeValueCompleteness(AttributeValueCompleteness.INCOMPLETE);
				builder.addAttribute(groupBuilder.build());
				if (!handler.handle(builder.build())) {
					return;
				}
			}
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Get all users");
		}
	}

	private List<String> selectSubjectIds(JSONArray subjects) {
		List<String> rv = new ArrayList<>(subjects.length());
		for (Object subject : subjects) {
			if (subject instanceof JSONObject) {
				JSONObject subjObject = (JSONObject) subject;
				if (processor.isSuccess(subjObject)) {
					String sourceId = processor.getStringOrNull(subjObject, "sourceId");
					if (getConfiguration().getSubjectSource().equals(sourceId)) {
						rv.add(processor.getString(subjObject, "id"));
					}
				} else {
					LOG.warn("Skipping not-success subject from response: {}", subject);
				}
			} else {
				throw new IllegalStateException("Expected subject as JSONObject, got " + subject);
			}
		}
		return rv;
	}

	private void getUser(String id, ResultsHandler handler) {
		URIBuilder uriBuilder = processor.getURIBuilder()
				.setPath(URI_BASE_PATH + PATH_SUBJECTS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put("WsRestGetGroupsRequest", new JSONObject()
							.put("subjectLookups", new JSONObject[] {
									new JSONObject().put("subjectId", id) }));
			System.out.println("Request = " + body.toString());
			JSONObject response = processor.callRequest(request, body, true, CONTENT_TYPE_JSON);
			System.out.println("Got response: " + response);
			processor.checkSuccess(response, WS_GET_GROUPS_RESULTS);
			JSONArray groups = processor.getArray(response, WS_GET_GROUPS_RESULTS, RESULTS, WS_GROUPS);

			ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
			builder.setUid(id);
			builder.setName(id);
			builder.addAttribute(ATTR_GROUP, selectGroupNames(groups));
			handler.handle(builder.build());
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Get all users");
		}
	}

	private List<String> selectGroupNames(JSONArray groups) {
		List<String> rv = new ArrayList<>();
		for (Object group : groups) {
			if (group instanceof JSONObject) {
				JSONObject gObject = (JSONObject) group;
				String name = processor.getStringOrNull(gObject, "name");
				if (groupNameMatches(name)) {
					rv.add(name);
				}
			} else {
				throw new IllegalStateException("Expected group as JSONObject, got " + group);
			}
		}
		return rv;
	}

	private boolean groupNameMatches(String name) {
		if (name == null) {
			return false;
		}
		return groupNameMatches(name, getConfiguration().getGroupIncludePattern()) &&
				!groupNameMatches(name, getConfiguration().getGroupExcludePattern());
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

	private GrouperConfiguration getConfiguration() {
		return processor.configuration;
	}
}
