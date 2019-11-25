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

import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contains logic for handling operations on Group object class.
 */
public class GroupProcessor extends Processor {

	public static final String OBJECT_CLASS_NAME = "Group";
	public static final String ATTR_NAME = "name";
	public static final String ATTR_UUID = "uuid";
	private static final String ATTR_EXTENSION = J_EXTENSION;
	public static final String ATTR_MEMBER = "member";

	private static final String DEFAULT_BASE_STEM = ":";

	GroupProcessor(GrouperConfiguration configuration) {
		super(configuration);
	}

	ObjectClass getObjectClass() {
		return new ObjectClass(OBJECT_CLASS_NAME);
	}

	void read(Filter filter, ResultsHandler handler, OperationOptions options) {
		if (filter == null) {
			getAllGroups(handler, options);
		} else if (filter instanceof EqualsFilter) {
			Attribute attribute = ((EqualsFilter) filter).getAttribute();
			if (attribute != null) {
				List<Object> values = attribute.getValue();
				if (values == null || values.isEmpty()) {
					throw new IllegalArgumentException("No attribute value to look for: " + attribute);
				} else if (values.size() > 1) {
					throw new IllegalArgumentException("More than one attribute value to look for: " + attribute);
				}
				if (attribute.is(Name.NAME) || attribute.is(ATTR_NAME)) {
					getGroupByName((String) values.get(0), handler, options);
				} else if (attribute.is(Uid.NAME) || attribute.is(ATTR_UUID)) {
					getGroupByUuid((String) values.get(0), handler, options);
				} else {
					throw new IllegalArgumentException("Equal filter used on unsupported attribute: " + attribute);
				}
			} else {
				throw new IllegalArgumentException("Equal filter used with no attribute: " + filter);
			}
		} else {
			throw new IllegalArgumentException("Unsupported filter: " + filter);
		}
	}

	private void getGroupByName(String name, ResultsHandler handler, OperationOptions options) {
		URIBuilder uriBuilder = getUriBuilderForGroups();
		if (!isGetMembers(options)) {
			try {
				HttpPost request = new HttpPost(uriBuilder.build());
				JSONObject body = new JSONObject()
						.put(J_WS_REST_FIND_GROUPS_REQUEST, new JSONObject()
								.put(J_WS_GROUP_LOOKUPS, new JSONObject[] { new JSONObject()
										.put(J_GROUP_NAME, name) }));
				executeFindGroups(request, body, handler);
			} catch (RuntimeException | URISyntaxException e) {
				throw processException(e, uriBuilder, "Get group by name (no members)");
			}
		} else {
			try {
				HttpPost request = new HttpPost(uriBuilder.build());
				JSONObject body = new JSONObject()
						.put(J_WS_REST_GET_MEMBERS_REQUEST, new JSONObject()
								.put(J_WS_GROUP_LOOKUPS, new JSONObject[] { new JSONObject()
										.put(J_GROUP_NAME, name) })
								.put(J_INCLUDE_SUBJECT_DETAIL, true));
				executeGetMembers(request, body, handler);
			} catch (RuntimeException | URISyntaxException e) {
				throw processException(e, uriBuilder, "Get group by name (with members)");
			}
		}
	}

	private boolean getGroupByUuid(String uuid, ResultsHandler handler, OperationOptions options) {
		if (!isGetMembers(options)) {
			URIBuilder uriBuilder = getUriBuilderForGroups();
			try {
				HttpPost request = new HttpPost(uriBuilder.build());
				JSONObject body = new JSONObject()
						.put(J_WS_REST_FIND_GROUPS_REQUEST, new JSONObject()
								.put(J_WS_GROUP_LOOKUPS, new JSONObject[] { new JSONObject()
										.put(J_UUID, uuid) }));
				return executeFindGroups(request, body, handler);
			} catch (RuntimeException | URISyntaxException e) {
				throw processException(e, uriBuilder, "Get group by UUID (no members)");
			}
		} else {
			URIBuilder uriBuilder = getUriBuilderForGroups();
			try {
				HttpPost request = new HttpPost(uriBuilder.build());
				JSONObject body = new JSONObject()
						.put(J_WS_REST_GET_MEMBERS_REQUEST, new JSONObject()
								.put(J_WS_GROUP_LOOKUPS, new JSONObject[] { new JSONObject()
										.put(J_UUID, uuid) })
								.put(J_INCLUDE_SUBJECT_DETAIL, true));
				return executeGetMembers(request, body, handler);
			} catch (RuntimeException | URISyntaxException e) {
				throw processException(e, uriBuilder, "Get group by UUID (with members)");
			}
		}
	}

	private void getAllGroups(final ResultsHandler handler, final OperationOptions options) {
		boolean getMembers = isGetMembers(options);
		if (!getMembers) {
			getAllGroupsNoMembers(handler);
		} else {
			ResultsHandler localHandler = connectorObject -> getGroupByUuid(connectorObject.getUid().getUidValue(), handler, options);
			getAllGroupsNoMembers(localHandler);
		}
	}

	private void getAllGroupsNoMembers(ResultsHandler handler) {
		URIBuilder uriBuilder = getUriBuilderForGroups();
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			String configuredBaseStem = configuration.getBaseStem();
			JSONObject body = new JSONObject()
					.put(J_WS_REST_FIND_GROUPS_REQUEST, new JSONObject()
							.put(J_WS_QUERY_FILTER, new JSONObject()
									.put(J_QUERY_FILTER_TYPE, VAL_FIND_BY_STEM_NAME)
									.put(J_STEM_NAME, configuredBaseStem != null ? configuredBaseStem : DEFAULT_BASE_STEM)
									.put(J_STEM_NAME_SCOPE, VAL_ALL_IN_SUBTREE)));
			executeFindGroups(request, body, handler);
		} catch (RuntimeException | URISyntaxException e) {
			throw processException(e, uriBuilder, "Get all groups");
		}
	}

	private boolean executeFindGroups(HttpPost request, JSONObject body, ResultsHandler handler) {
		JSONObject response = callRequest(request, body);
		checkSuccess(response, J_WS_FIND_GROUPS_RESULTS);
		JSONArray groups = getArray(response, false, J_WS_FIND_GROUPS_RESULTS, J_GROUP_RESULTS);
		if (groups != null) {
			for (Object group : groups) {
				if (!handleGroupJsonObject(group, handler)) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean executeGetMembers(HttpPost request, JSONObject body, ResultsHandler handler) {
		JSONObject response = callRequest(request, body);
		checkSuccess(response, J_WS_GET_MEMBERS_RESULTS);
		JSONObject gObject = (JSONObject) get(response, J_WS_GET_MEMBERS_RESULTS, J_RESULTS, J_WS_GROUP);
		String name = getStringOrNull(gObject, J_NAME);
		if (groupNameMatches(name)) {
			ConnectorObjectBuilder builder = startGroupObjectBuilding(gObject, name);
			List<String> members = new ArrayList<>();
			JSONArray membersJsonArray = getArray(response, false, J_WS_GET_MEMBERS_RESULTS, J_RESULTS, J_WS_SUBJECTS);
			if (membersJsonArray != null) {
				for (Object memberObject : membersJsonArray) {
					JSONObject member = (JSONObject) memberObject;
					String sourceId = getStringOrNull(member, J_SOURCE_ID);
					if (sourceId == null || !sourceId.equals(configuration.getSubjectSource())) {
						LOG.info("Skipping member with wrong source (e.g. one that is not a person) (source={0})", sourceId);
					} else {
						String subjectId = getStringOrNull(member, J_ID);
						if (subjectId != null) {
							members.add(subjectId);
						} else {
							LOG.info("Skipping unnamed member (source={0})", member);
						}
					}
				}
				builder.addAttribute(ATTR_MEMBER, members);
			}
			return handler.handle(builder.build());
		} else {
			return true;
		}
	}

	private boolean handleGroupJsonObject(Object group, ResultsHandler handler) {
		if (group instanceof JSONObject) {
			JSONObject gObject = (JSONObject) group;
			String name = getStringOrNull(gObject, J_NAME);
			if (groupNameMatches(name)) {
				return handler.handle(startGroupObjectBuilding(gObject, name).build());
			} else {
				return true;
			}
		} else {
			throw new IllegalStateException("Expected group as JSONObject, got " + group);
		}
	}

	private ConnectorObjectBuilder startGroupObjectBuilding(JSONObject gObject, String name) {
		String extension = getStringOrNull(gObject, J_EXTENSION);
		String uuid = getStringOrNull(gObject, J_UUID);
		ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
		builder.setObjectClass(getObjectClass());
		builder.setUid(uuid);
		builder.setName(name);
		builder.addAttribute(ATTR_EXTENSION, extension);
		return builder;
	}

	void test() {
		if (configuration.getTestStem() != null) {
			checkStemExists(configuration.getTestStem());
		}
		if (configuration.getTestGroup() != null) {
			checkGroupExists(configuration.getTestGroup());
		}
	}

	private void checkStemExists(String stemName) {
		URIBuilder uriBuilder = getUriBuilderForStems();
		JSONArray stems;
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put(J_WS_REST_FIND_STEMS_REQUEST, new JSONObject()
							.put(J_WS_STEM_QUERY_FILTER, new JSONObject()
									.put(J_STEM_QUERY_FILTER_TYPE, VAL_FIND_BY_STEM_NAME)
									.put(J_STEM_NAME, stemName)));
			JSONObject response = callRequest(request, body);
			checkSuccess(response, J_WS_FIND_STEMS_RESULTS);
			stems = getArray(response, true, J_WS_FIND_STEMS_RESULTS, J_STEM_RESULTS);
		} catch (RuntimeException | URISyntaxException e) {
			throw processException(e, uriBuilder, "Find stems request");
		}
		if (stems.length() == 0) {
			throw new ConnectorException("Expected to find the stem '" + stemName + "', found none");
		}
	}

	private void checkGroupExists(String groupName) {
		List<ConnectorObject> groups = new ArrayList<>();
		getGroupByName(groupName, groups::add, null);
		LOG.info("getGroupByName found {0} group(s): {1}", groups.size(), groups);
		if (groups.isEmpty()) {
			throw new ConnectorException("Expected to find the group '" + groupName + "', found none");
		}
	}

	ObjectClassInfoBuilder buildSchema() {
		ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
		builder.setType(OBJECT_CLASS_NAME);
		builder.addAttributeInfo(
				new AttributeInfoBuilder(Name.NAME, String.class)
						.setNativeName(ATTR_NAME)
						.setRequired(true)
						.build());
		builder.addAttributeInfo(
				new AttributeInfoBuilder(Uid.NAME, String.class)
						.setNativeName(ATTR_UUID)
						.setRequired(true)
						.build());
		builder.addAttributeInfo(
				new AttributeInfoBuilder(ATTR_EXTENSION, String.class)
						.build());
		builder.addAttributeInfo(
				new AttributeInfoBuilder(ATTR_MEMBER, String.class)
						.setMultiValued(true)
						.setReturnedByDefault(false)
						.build());
		return builder;
	}

	private boolean isGetMembers(OperationOptions options) {
		String[] attrs = options != null ? options.getAttributesToGet() : null;
		return attrs != null && Arrays.asList(attrs).contains(ATTR_MEMBER);
	}
}
