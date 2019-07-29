package com.evolveum.polygon.connector.grouper.rest;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;

import static com.evolveum.polygon.connector.grouper.rest.Processor.*;

/**
 *
 */
public abstract class AbstractGroupProcessor {

	protected static final String ATTR_EXTENSION = "extension";
	protected final Processor processor;

	public AbstractGroupProcessor(Processor processor) {
		this.processor = processor;
	}

	void read(Filter filter, ResultsHandler handler, OperationOptions options) {
		if (filter == null) {
			getAllGroups(handler, options);
		} else if (filter instanceof EqualsFilter && ((EqualsFilter) filter).getAttribute() instanceof Name) {
			Attribute name = ((EqualsFilter) filter).getAttribute();
			if (name != null) {
				if (name.getValue() == null || name.getValue().isEmpty()) {
					throw new IllegalArgumentException("No group name to look for");
				} else if (name.getValue().size() > 1) {
					throw new IllegalArgumentException("More than one group name to look for: " + name.getValue());
				} else {
					getGroupByName((String) name.getValue().get(0), handler, options);
				}
			} else {
				processor.throwNullAttrException(filter);
			}
		} else if (filter instanceof EqualsFilter && ((EqualsFilter) filter).getAttribute() instanceof Uid) {
			Attribute name = ((EqualsFilter) filter).getAttribute();
			if (name != null) {
				if (name.getValue() == null || name.getValue().isEmpty()) {
					throw new IllegalArgumentException("No group UUID to look for");
				} else if (name.getValue().size() > 1) {
					throw new IllegalArgumentException("More than one group UUID to look for: " + name.getValue());
				} else {
					getGroupByUuid((String) name.getValue().get(0), handler, options);
				}
			} else {
				processor.throwNullAttrException(filter);
			}
		} else {
			throw new IllegalArgumentException("Unsupported filter: " + filter);
		}
	}

	boolean getGroupByUuid(String uuid, ResultsHandler handler, OperationOptions options) {
		return getGroupByUuid(uuid, handler);
	}

	void getGroupByName(String name, ResultsHandler handler, OperationOptions options) {
		getGroupByName(name, handler);
	}

	abstract void getAllGroups(ResultsHandler handler, OperationOptions options);

	boolean executeFindGroupsResponse(HttpPost request, JSONObject body, ResultsHandler handler) {
		System.out.println("Request = " + body.toString());
		JSONObject response = processor.callRequest(request, body, true, CONTENT_TYPE_JSON);
		System.out.println("Got response: " + response);
		processor.checkSuccess(response, "WsFindGroupsResults");
		JSONArray groups = processor.getArray(response, false, "WsFindGroupsResults", "groupResults");
		if (groups != null) {
			for (Object group : groups) {
				if (!handleGroupJsonObject(group, handler)) {
					return false;
				}
			}
		}
		return true;
	}

	void executeFindGroupsAsMembersResponse(HttpPost request, JSONObject body, ResultsHandler handler) {
		System.out.println("Request = " + body.toString());
		JSONObject response = processor.callRequest(request, body, true, CONTENT_TYPE_JSON);
		System.out.println("Got response: " + response);
		processor.checkSuccess(response, WS_GET_MEMBERS_RESULTS);
		JSONArray groups = processor.getArray(response, WS_GET_MEMBERS_RESULTS, RESULTS, WS_SUBJECTS);
		for (Object group : groups) {
			if (!handleGroupAsMemberJsonObject(group, handler)) {
				return;
			}
		}
	}

	void getGroupByName(String groupName, ResultsHandler handler) {
		URIBuilder uriBuilder = processor.getURIBuilder()
				.setPath(URI_BASE_PATH + PATH_GROUPS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put("WsRestFindGroupsRequest", new JSONObject()
							.put("wsGroupLookups", new JSONObject[] { new JSONObject()
									.put("groupName", groupName) }));
			executeFindGroupsResponse(request, body, handler);
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Get all groups");
		}
	}

	boolean getGroupByUuid(String groupUuid, ResultsHandler handler) {
		URIBuilder uriBuilder = processor.getURIBuilder()
				.setPath(URI_BASE_PATH + PATH_GROUPS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put("WsRestFindGroupsRequest", new JSONObject()
							.put("wsGroupLookups", new JSONObject[] { new JSONObject()
									.put("uuid", groupUuid) }));
			return executeFindGroupsResponse(request, body, handler);
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Get all groups");
		}
	}

	private boolean handleGroupJsonObject(Object group, ResultsHandler handler) {
		if (group instanceof JSONObject) {
			JSONObject gObject = (JSONObject) group;
			String name = processor.getStringOrNull(gObject, "name");
			if (processor.groupNameMatches(name)) {
				String extension = processor.getStringOrNull(gObject, "extension");
				String uuid = processor.getStringOrNull(gObject, "uuid");
				ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
				builder.setObjectClass(getObjectClass());
				builder.setUid(uuid);
				builder.setName(name);
				builder.addAttribute(ATTR_EXTENSION, extension);
				return handler.handle(builder.build());
			} else {
				return true;
			}
		} else {
			throw new IllegalStateException("Expected group as JSONObject, got " + group);
		}
	}

	private boolean handleGroupAsMemberJsonObject(Object group, ResultsHandler handler) {
		if (group instanceof JSONObject) {
			JSONObject gObject = (JSONObject) group;
			String sourceId = processor.getStringOrNull(gObject, "sourceId");
			if (sourceId == null || !sourceId.equals(getConfiguration().getGroupSource())) {
				LOG.info("Skipping non-group member (source={0})", sourceId);
				return true;
			}
			String name = processor.getStringOrNull(gObject, "name");
			if (processor.groupNameMatches(name)) {
				String id = processor.getStringOrNull(gObject, "id");
				ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
				builder.setObjectClass(getObjectClass());
				builder.setUid(id);
				builder.setName(name);
				return handler.handle(builder.build());
			} else {
				return true;
			}
		} else {
			throw new IllegalStateException("Expected group as JSONObject, got " + group);
		}
	}

	protected abstract ObjectClass getObjectClass();

	protected GrouperConfiguration getConfiguration() {
		return processor.configuration;
	}

	void test() {
		URIBuilder uriBuilder = processor.getURIBuilder().setPath(URI_BASE_PATH + PATH_GROUPS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put("WsRestGetMembersRequest", new JSONObject()
							.put("wsGroupLookups", new JSONObject[] { new JSONObject()
									.put("groupName", getConfiguration().getSuperGroup()) })
							.put("includeSubjectDetail", true)
							.put("memberFilter", "Immediate"));
			System.out.println("Request = " + body.toString());
			JSONObject response = processor.callRequest(request, body, true, CONTENT_TYPE_JSON);
			System.out.println("Got response: " + response);
			processor.checkSuccess(response, WS_GET_MEMBERS_RESULTS);
			JSONArray groups = processor.getArray(response, WS_GET_MEMBERS_RESULTS, RESULTS, WS_SUBJECTS);
			System.out.println("Super-group members found: " + groups.length());
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Test");
		}
	}
}
