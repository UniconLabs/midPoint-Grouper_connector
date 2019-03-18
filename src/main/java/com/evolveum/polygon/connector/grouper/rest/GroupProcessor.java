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
 * @author surmanek
 * @author mederly
 *
 */
public class GroupProcessor {

	private final Processor processor;

	private static final String ATTR_EXTENSION = "extension";

	public GroupProcessor(Processor processor) {
		this.processor = processor;
	}

	ObjectClassInfoBuilder buildSchema() {
		ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();

		builder.setType(ObjectClass.GROUP_NAME);
		AttributeInfoBuilder extension = new AttributeInfoBuilder(ATTR_EXTENSION, String.class);
		builder.addAttributeInfo(extension.build());

		return builder;
	}

	void read(Filter filter, ResultsHandler handler, OperationOptions options) {
		if (filter == null) {
			getAllGroups(handler);
		} else if (filter instanceof EqualsFilter && ((EqualsFilter) filter).getAttribute() instanceof Name) {
			Attribute name = ((EqualsFilter) filter).getAttribute();
			if (name != null) {
				if (name.getValue() == null || name.getValue().isEmpty()) {
					throw new IllegalArgumentException("No group name to look for");
				} else if (name.getValue().size() > 1) {
					throw new IllegalArgumentException("More than one group name to look for: " + name.getValue());
				} else {
					getGroupByName((String) name.getValue().get(0), handler);
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
					getGroupByUuid((String) name.getValue().get(0), handler);
				}
			} else {
				processor.throwNullAttrException(filter);
			}
		} else {
			throw new IllegalArgumentException("Unsupported filter: " + filter);
		}
	}

	private void getAllGroups(ResultsHandler handler) {
		URIBuilder uriBuilder = processor.getURIBuilder()
				.setPath(URI_BASE_PATH + PATH_GROUPS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put("WsRestFindGroupsRequest", new JSONObject()
							.put("wsQueryFilter", new JSONObject()
									.put("queryFilterType", "FIND_BY_STEM_NAME")
									.put("stemName", getConfiguration().getRootStem())));
			executeFindGroupsResponse(request, body, handler);
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Get all groups");
		}
	}

	private void executeFindGroupsResponse(HttpPost request, JSONObject body, ResultsHandler handler) {
		System.out.println("Request = " + body.toString());
		JSONObject response = processor.callRequest(request, body, true, CONTENT_TYPE_JSON);
		System.out.println("Got response: " + response);
		processor.checkSuccess(response, "WsFindGroupsResults");
		JSONArray groups = processor.getArray(response, "WsFindGroupsResults", "groupResults");
		for (Object group : groups) {
			if (!handlerGroupJsonObject(group, handler)) {
				return;
			}
		}
	}

	private void getGroupByName(String groupName, ResultsHandler handler) {
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

	private void getGroupByUuid(String groupUuid, ResultsHandler handler) {
		URIBuilder uriBuilder = processor.getURIBuilder()
				.setPath(URI_BASE_PATH + PATH_GROUPS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put("WsRestFindGroupsRequest", new JSONObject()
							.put("wsGroupLookups", new JSONObject[] { new JSONObject()
									.put("uuid", groupUuid) }));
			executeFindGroupsResponse(request, body, handler);
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Get all groups");
		}
	}

	private boolean handlerGroupJsonObject(Object group, ResultsHandler handler) {
		if (group instanceof JSONObject) {
			JSONObject gObject = (JSONObject) group;
			String name = processor.getStringOrNull(gObject, "name");
			String extension = processor.getStringOrNull(gObject, "extension");
			String uuid = processor.getStringOrNull(gObject, "uuid");
			ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
			builder.setObjectClass(ObjectClass.GROUP);
			builder.setUid(uuid);
			builder.setName(name);
			builder.addAttribute(ATTR_EXTENSION, extension);
			return handler.handle(builder.build());
		} else {
			throw new IllegalStateException("Expected group as JSONObject, got " + group);
		}
	}

	private GrouperConfiguration getConfiguration() {
		return processor.configuration;
	}

}
