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
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Contains logic for handling operations on Group object class.
 */
public class GroupProcessor extends Processor {

	private static final String J_WS_REST_GET_MEMBERS_REQUEST = "WsRestGetMembersRequest";
	private static final String J_WS_REST_FIND_GROUPS_REQUEST = "WsRestFindGroupsRequest";
	private static final String J_WS_REST_FIND_STEMS_REQUEST = "WsRestFindStemsRequest";
	private static final String J_WS_REST_ATTRIBUTE_ASSIGNMENT_LITE_REQUEST = "WsRestGetAttributeAssignmentsLiteRequest";

	private static final String J_WS_QUERY_FILTER = "wsQueryFilter";
	private static final String J_WS_STEM_QUERY_FILTER = "wsStemQueryFilter";
	private static final String J_STEM_QUERY_FILTER_TYPE = "stemQueryFilterType";
	private static final String J_INCLUDE_SUBJECT_DETAIL = "includeSubjectDetail";
	private static final String J_QUERY_FILTER_TYPE = "queryFilterType";
	private static final String J_STEM_NAME = "stemName";
	private static final String J_STEM_NAME_SCOPE = "stemNameScope";
	private static final String J_GROUP_NAME = "groupName";

	private static final String J_WS_FIND_GROUPS_RESULTS = "WsFindGroupsResults";
	private static final String J_WS_FIND_STEMS_RESULTS = "WsFindStemsResults";
	private static final String J_WS_GET_MEMBERS_RESULTS = "WsGetMembersResults";
	private static final String J_WS_ATTRIBUTE_ASSIGNMENT_RESULTS = "WsGetAttributeAssignmentsResults";

	private static final String J_RESULTS = "results";
	private static final String J_STEM_RESULTS = "stemResults";
	private static final String J_GROUP_RESULTS = "groupResults";
	private static final String J_WS_GROUP_LOOKUPS = "wsGroupLookups";
	private static final String J_RESULT_METADATA = "resultMetadata";
	private static final String J_RESULT_CODE = "resultCode";
	private static final String J_SUCCESS = "success";
	private static final String J_WS_ATTRIBUTE_ASSIGN_TYPE = "attributeAssignType";

	private static final String J_WS_SUBJECTS = "wsSubjects";
	private static final String J_WS_GROUP = "wsGroup";
	private static final String J_WS_GROUPS = "wsGroups";
	private static final String J_WS_GROUP_TYPE = "group";

	private static final String J_UUID = "uuid";
	private static final String J_NAME = "name";
	private static final String J_EXTENSION = "extension";
	private static final String J_SOURCE_ID = "sourceId";
	private static final String J_ID = "id";
	private static final String J_PAGE_SIZE = "pageSize";
	private static final String J_PAGE_NUMBER = "pageNumber";
	private static final String J_ATTRIBUTE_NAME = "wsAttributeDefNameName";
	private static final String J_ATTRIBUTE_VALUE = "wsAttributeDefValueValue";

	private static final String VAL_T = "T";
	private static final String VAL_FIND_BY_STEM_NAME = "FIND_BY_STEM_NAME";
	private static final String VAL_ALL_IN_SUBTREE = "ALL_IN_SUBTREE";

	private static final String PATH_GROUPS = "/groups";
	private static final String PATH_STEMS = "/stems";
	private static final String PATH_ATTRIBUTES = "/attributeAssignments";

	private static final String ATTR_EXTENSION = J_EXTENSION;
	private static final String DEFAULT_BASE_STEM = ":";

	public static final String OBJECT_CLASS_NAME = "Group";
	public static final String ATTR_NAME = "name";
	public static final String ATTR_UUID = "uuid";
	public static final String ATTR_MEMBER = "member";

	private final Map<String,String> attributeNameValueMap = new HashMap<>();


	GroupProcessor(GrouperConfiguration configuration) {
		super(configuration);

		if (configuration.getGroupAttribute() != null && configuration.getGroupAttribute().length >0) {
			attributeNameValueMap.putAll(
					Arrays.stream(configuration.getGroupAttribute())
							.map(str -> str.split("\\|\\|"))
							.collect(Collectors.toMap(str -> str[0].trim(), str -> str[1].trim()))
					);
		}
	}

	ObjectClass getObjectClass() {
		return new ObjectClass(OBJECT_CLASS_NAME);
	}

	void read(Filter filter, ResultsHandler handler, OperationOptions options) {
		if (filter == null) {
			getAllGroups(handler, options);
		} else if (filter instanceof EqualsFilter || filter instanceof ContainsFilter) {
			Attribute attribute = ((AttributeFilter) filter).getAttribute();
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

	private boolean executeGrouperRequest(final String name, final String uuid, final boolean withMembers, final boolean shouldPage, final ResultsHandler handler) {
		final URIBuilder uriBuilder;

		if (attributeNameValueMap.isEmpty() || StringUtil.isNotBlank(name) || StringUtil.isNotBlank(uuid)) {
			uriBuilder = getUriBuilderForGroups();
		} else {
			uriBuilder = getUriBuilderForAttributes();
		}

		try {
			final HttpPost request = new HttpPost(uriBuilder.build());
			final List<JSONObject> requestBodies = createWsFindGroupsRequest(name, uuid, withMembers);

			final boolean paging = !requestBodies.get(0).has(J_WS_REST_ATTRIBUTE_ASSIGNMENT_LITE_REQUEST) && shouldPage; //TODO Grouper WS WsRestGetAttributeAssignmentsLiteRequest should really implement paging, if/when they do this can be removed!

			boolean result = false;
			for (final JSONObject body : requestBodies) {
				boolean localResult;

				if (withMembers) {
					localResult = executeGetMembers(request, body, handler, paging);
				} else {
					localResult = executeFindGroups(request, body, handler, paging);
				}

				if (!localResult) {
					LOG.info("Problem processing/retrieving results for query {0}!", body.toString());
				} else {
					result = true;
				}
			};

			return result;
		} catch (RuntimeException | URISyntaxException e) {
			throw processException(e, uriBuilder, "Get group by name (with members)");
		}
	}

	private void getGroupByName(String name, ResultsHandler handler, OperationOptions options) {
		if (!isGetMembers(options)) {
			LOG.info("Retrieving single group without membership by name...");
			executeGrouperRequest(name, null, false, false, handler);
		} else {
			LOG.info("Retrieving single group with membership by name...");
			executeGrouperRequest(name, null, true, true, handler);
		}
	}

	private boolean getGroupByUuid(String uuid, ResultsHandler handler, OperationOptions options) {
		if (!isGetMembers(options)) {
			LOG.info("Retrieving single group without membership by UUID...");
			return executeGrouperRequest(null, uuid, false, false, handler);
		} else {
			LOG.info("Retrieving single group with membership by UUID...");
			return executeGrouperRequest(null, uuid, true, true, handler);
		}
	}

	private void getAllGroups(final ResultsHandler handler, final OperationOptions options) {
		boolean getMembers = isGetMembers(options);
		if (!getMembers) {
			LOG.info("Retrieving all groups without memberships...");
			getAllGroupsNoMembers(handler);
		} else {
			LOG.info("Retrieving all groups with memberships...");
			ResultsHandler localHandler = connectorObject -> getGroupByUuid(connectorObject.getUid().getUidValue(), handler, options);
			getAllGroupsNoMembers(localHandler);
		}
	}

	private void getAllGroupsNoMembers(ResultsHandler handler) {
		executeGrouperRequest(null, null, false, true, handler);
	}

	private boolean executeFindGroups(HttpPost request, JSONObject body, ResultsHandler handler, boolean shouldPage) {
		int pageNumber = 1;
		int result = 0;
		boolean done = !shouldPage;

		do {
			addPageNumber(body, pageNumber, shouldPage);
			final JSONObject response = callRequest(request, body, null).getResponse();

			final List<String> responseTypes = List.of(J_WS_FIND_GROUPS_RESULTS, J_WS_ATTRIBUTE_ASSIGNMENT_RESULTS);
			checkSuccess(response, responseTypes);

			final JSONArray groups = getArray(response, false, responseTypes, List.of(J_GROUP_RESULTS, J_WS_GROUPS));

			if (groups != null) {
				for (Object group : groups) {
					if (!handleGroupJsonObject(group, handler)) {
						done = true;
						break;
					}
					result++;
				}
				pageNumber++;

			} else {
				done = true;
			}
		} while (!done);

		LOG.info("Found {0} group(s) in {1} pages!", result, pageNumber-1);
		return result > 0;
	}

	private boolean executeGetMembers(HttpPost request, JSONObject body, ResultsHandler handler, boolean shouldPage) {
		final List<String> members = new ArrayList<>();
		boolean done = !shouldPage;
		int pageNumber = 1;
		ConnectorObjectBuilder builder = null;
		String name;

		do {
			addPageNumber(body, pageNumber, shouldPage);
			final CallResponse callResponse = callRequest(request, body, (statusCode, responseBody) -> {
				final JSONObject errorResponse = new JSONObject(responseBody);
				final JSONObject resultMetadata = (JSONObject) getIfExists(errorResponse, List.of(J_WS_GET_MEMBERS_RESULTS, J_RESULTS), List.of(J_RESULT_METADATA));
				final String resultCode = resultMetadata != null ? getStringOrNull(resultMetadata, J_RESULT_CODE) : null;
				boolean notFound = "GROUP_NOT_FOUND".equals(resultCode);
				if (notFound) {
					return CallResponse.error(responseBody);
				} else {
					return null;
				}
			});

			if (!callResponse.isSuccess()) {
				return true;
			}

			final JSONObject response = callResponse.getResponse();
			checkSuccess(response, Collections.singletonList(J_WS_GET_MEMBERS_RESULTS));
			final JSONObject gObject = (JSONObject) get(response, List.of(J_WS_GET_MEMBERS_RESULTS, J_RESULTS), List.of(J_WS_GROUP));
			name = getStringOrNull(gObject, J_NAME);

			if (groupNameMatches(name)) {
				if (builder == null) {
					builder = startGroupObjectBuilding(gObject, name);
				}
				final JSONArray membersJsonArray = getArray(response, false, List.of(J_WS_GET_MEMBERS_RESULTS, J_RESULTS), List.of(J_WS_SUBJECTS));

				if (membersJsonArray != null) {
					for (Object memberObject : membersJsonArray) {
						handleMemberJsonObject(memberObject, members);
					}
					pageNumber++;

				} else {
					done = true;
				}

			} else {
				break;
			}
		} while (!done);
		LOG.info("Found {0} group member(s) in {1} pages for Group: {2}!", members.size(), pageNumber-1, name);


		if (builder != null) {
			builder.addAttribute(ATTR_MEMBER, members);
			return handler.handle(builder.build());
		}

		return true;
	}

	private List<JSONObject> createWsFindGroupsRequest(final String name, final String uuid, final boolean withMembers) {
		final JSONObject wsRequest = new JSONObject();

		//TODO subject source filtering using WS Object!
//		if (shouldCheckSubjectSource && (sourceId == null || !sourceId.equalsIgnoreCase(configuration.getSubjectSource()))) {
//			LOG.info("Skipping member with wrong source (e.g. one that is not a person) (source={0})", sourceId);
//		} else {
//
//		}

		//TODO Group Name Filtering using WS Object
//		private boolean groupNameMatches(String name) {
//			if (name == null) {
//				return false;
//			}
//			String[] includes = configuration.getGroupIncludePattern();
//			String[] excludes = configuration.getGroupExcludePattern();
//			return (includes == null || includes.length == 0 || groupNameMatches(name, includes)) &&
//					!groupNameMatches(name, excludes);
//		}
//
//		private boolean groupNameMatches(String name, String[] patterns) {
//			if (patterns == null) {
//				return false;
//			}
//			for (String pattern : patterns) {
//				Pattern compiled = Pattern.compile(pattern);
//				if (compiled.matcher(name).matches()) {
//					return true;
//				}
//			}
//			return false;
//		}
//
//		if (StringUtil.isNotBlank(configuration.getSubjectSource())) {
//			shouldCheckSubjectSource = true;
//		}
//
//		if (StringUtil.isNotBlank(configuration.getBaseStem())) {
//			shouldUseBaseStem = true;
//		}


		//Specific group or member requests
		if (StringUtil.isNotBlank(uuid) && !withMembers) {
			return List.of(new JSONObject().put(J_WS_REST_FIND_GROUPS_REQUEST, new JSONObject()
					.put(J_WS_GROUP_LOOKUPS, new JSONObject[] { new JSONObject()
							.put(J_UUID, uuid) })));

		} else if (StringUtil.isNotBlank(uuid)) {
			return List.of(new JSONObject().put(J_WS_REST_GET_MEMBERS_REQUEST, new JSONObject()
					.put(J_PAGE_SIZE, configuration.getPageSize())
					.put(J_WS_GROUP_LOOKUPS, new JSONObject[] { new JSONObject()
							.put(J_UUID, uuid) })
					.put(J_INCLUDE_SUBJECT_DETAIL, true)));
		}

		if (StringUtil.isNotBlank(name) && !withMembers) {
			return List.of(new JSONObject().put(J_WS_REST_FIND_GROUPS_REQUEST, new JSONObject()
					.put(J_WS_GROUP_LOOKUPS, new JSONObject[]{new JSONObject()
							.put(J_GROUP_NAME, name)})));

		} else if (StringUtil.isNotBlank(name)) {
			return List.of(new JSONObject().put(J_WS_REST_GET_MEMBERS_REQUEST, new JSONObject()
					.put(J_PAGE_SIZE, configuration.getPageSize())
					.put(J_WS_GROUP_LOOKUPS, new JSONObject[] { new JSONObject()
							.put(J_GROUP_NAME, name) })
					.put(J_INCLUDE_SUBJECT_DETAIL, true)));
		}

		//Get All Requests
		if (attributeNameValueMap.isEmpty()) {
			return List.of(new JSONObject().put(J_WS_REST_FIND_GROUPS_REQUEST, new JSONObject()
					.put(J_WS_QUERY_FILTER, new JSONObject()
							.put(J_QUERY_FILTER_TYPE, VAL_FIND_BY_STEM_NAME)
							.put(J_STEM_NAME, (configuration.getBaseStem() != null) ? configuration.getBaseStem() : DEFAULT_BASE_STEM)
							.put(J_STEM_NAME_SCOPE, VAL_ALL_IN_SUBTREE)
							.put(J_PAGE_SIZE, configuration.getPageSize()))));
		} else {
			return attributeNameValueMap.entrySet().stream().map(entry ->
				new JSONObject().put(J_WS_REST_ATTRIBUTE_ASSIGNMENT_LITE_REQUEST, new JSONObject()
						.put(J_WS_ATTRIBUTE_ASSIGN_TYPE, J_WS_GROUP_TYPE)
						.put(J_ATTRIBUTE_NAME, entry.getKey())
						.put(J_ATTRIBUTE_VALUE, entry.getValue()))
			).collect(Collectors.toList());
		}
	}

	private void addPageNumber(final JSONObject body, final int pageNumber, final boolean shouldPage) {
		try {
			if (shouldPage) {
				if (body.has(J_WS_REST_GET_MEMBERS_REQUEST)) {
					body.getJSONObject(J_WS_REST_GET_MEMBERS_REQUEST).put(J_PAGE_NUMBER, pageNumber);
				} else if (body.has(J_WS_REST_FIND_GROUPS_REQUEST)) {
					if (body.getJSONObject(J_WS_REST_FIND_GROUPS_REQUEST).has(J_WS_QUERY_FILTER)){
						final Object queryFilterType = body.getJSONObject(J_WS_REST_FIND_GROUPS_REQUEST).getJSONObject(J_WS_QUERY_FILTER).get(J_QUERY_FILTER_TYPE);
						if (String.valueOf(queryFilterType).equalsIgnoreCase(VAL_FIND_BY_STEM_NAME)) { //Currently WS only supports paging on Group FIND by STEM or Approximate Group Name query types ONLY!!
							body.getJSONObject(J_WS_REST_FIND_GROUPS_REQUEST).getJSONObject(J_WS_QUERY_FILTER).put(J_PAGE_NUMBER, pageNumber);
						}
					}
				}
			}
		} catch (JSONException e) {
			LOG.info("Exception adding page number with {0}", e);
			//swallow
		}
	}

	private boolean handleGroupJsonObject(final Object group, final ResultsHandler handler) {
		if (group instanceof JSONObject) {
			final JSONObject gObject = (JSONObject) group;
			final String name = getStringOrNull(gObject, J_NAME);

			if (groupNameMatches(name)) {
				return handler.handle(startGroupObjectBuilding(gObject, name).build());
			} else {
				return true;
			}
		} else {
			throw new IllegalStateException("Expected group as JSONObject, got " + group);
		}
	}

	private void handleMemberJsonObject(final Object memberObject, final List<String> members) {
		if (memberObject instanceof JSONObject) {
			final JSONObject member = (JSONObject) memberObject;
			final String sourceId = getStringOrNull(member, J_SOURCE_ID);

			if (sourceId == null || !sourceId.equals(configuration.getSubjectSource())) {
				LOG.warn("Skipping member with wrong source (e.g. one that is not a person) (source={0})", sourceId);
			} else {
				final String subjectId = getStringOrNull(member, J_ID);
				if (subjectId != null) {
					members.add(subjectId);
				} else {
					LOG.warn("Skipping unnamed member (source={0})", member);
					}
				}
		} else {
			throw new IllegalStateException("Expected member as JSONObject, got " + memberObject);
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

	private void checkStemExists(String stemName) { //TODO do we need to update/refactor this WS Query?
		URIBuilder uriBuilder = getUriBuilderForStems();
		JSONArray stems;
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put(J_WS_REST_FIND_STEMS_REQUEST, new JSONObject()
							.put(J_WS_STEM_QUERY_FILTER, new JSONObject()
									.put(J_STEM_QUERY_FILTER_TYPE, VAL_FIND_BY_STEM_NAME)
									.put(J_STEM_NAME, stemName)));
			JSONObject response = callRequest(request, body, null).getResponse();
			checkSuccess(response, Collections.singletonList(J_WS_FIND_STEMS_RESULTS));
			stems = getArray(response, true, List.of(J_WS_FIND_STEMS_RESULTS), List.of(J_STEM_RESULTS));
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

		if (Boolean.TRUE.equals(configuration.getLogRequestResponses())) {
			LOG.info("Get Group By Name found {0} group(s): {1}", groups.size(), groups);
		} else {
			LOG.info("Get Group By Name found {0} group(s).", groups.size());
		}

		if (groups.isEmpty()) {
			throw new ConnectorException("Expected to find the group '" + groupName + "', but found none");
		}
	}

	private boolean isGetMembers(OperationOptions options) {
		String[] attrs = options != null ? options.getAttributesToGet() : null;
		return attrs != null && Arrays.asList(attrs).contains(ATTR_MEMBER);
	}

	private boolean groupNameMatches(String name) {
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

	public ObjectClassInfoBuilder buildSchema() {
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

	public void checkSuccess(final JSONObject response, final List<String> rootNames) {
		final JSONObject success = (JSONObject) get(response, rootNames, List.of(J_RESULT_METADATA));

		if (!VAL_T.equals(success.get(J_SUCCESS))) {
			throw new IllegalStateException("Request was not successful: " + success);
		}
	}

	public boolean isSuccess(JSONObject object) {
		return VAL_T.equals(getStringOrNull(object, J_SUCCESS));
	}


	public URIBuilder getUriBuilderForGroups() {
		return getUriBuilderRelative(PATH_GROUPS);
	}

	public URIBuilder getUriBuilderForStems() {
		return getUriBuilderRelative(PATH_STEMS);
	}

	public URIBuilder getUriBuilderForAttributes() {
		return getUriBuilderRelative(PATH_ATTRIBUTES);
	}

	public void test() {
		if (configuration.getTestStem() != null) {
			checkStemExists(configuration.getTestStem());
		}
		if (configuration.getTestGroup() != null) {
			checkGroupExists(configuration.getTestGroup());
		}
	}
}
