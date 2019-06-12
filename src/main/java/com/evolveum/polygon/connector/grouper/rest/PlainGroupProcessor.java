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
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.evolveum.polygon.connector.grouper.rest.Processor.*;

/**
 * @author surmanek
 * @author mederly
 *
 */
public class PlainGroupProcessor extends AbstractGroupProcessor {

	public static final String OBJECT_CLASS_NAME = "PlainGroup";
	public static final String ATTR_MEMBER = "member";

	PlainGroupProcessor(Processor processor) {
		super(processor);
	}

	ObjectClassInfoBuilder buildSchema() {
		ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();

		builder.setType(OBJECT_CLASS_NAME);
		AttributeInfoBuilder extension = new AttributeInfoBuilder(ATTR_EXTENSION, String.class);
		builder.addAttributeInfo(extension.build());

		AttributeInfoBuilder member = new AttributeInfoBuilder(ATTR_MEMBER, String.class);
		member.setMultiValued(true);
		member.setReturnedByDefault(false);
		builder.addAttributeInfo(member.build());

		return builder;
	}

	protected void getAllGroups(final ResultsHandler handler, final OperationOptions options) {
		boolean isGetMembers = isGetMembers(options);
		if (!isGetMembers) {
			getAllGroupsNoMembers(handler);
		} else {
			ResultsHandler localHandler = new ResultsHandler() {
				@Override
				public boolean handle(ConnectorObject connectorObject) {
					return getGroupByUuid(connectorObject.getUid().getUidValue(), handler, options);
				}
			};
			getAllGroupsNoMembers(localHandler);
		}
	}

	private void getAllGroupsNoMembers(ResultsHandler handler) {
		URIBuilder uriBuilder = processor.getURIBuilder()
				.setPath(URI_BASE_PATH + PATH_GROUPS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put("WsRestFindGroupsRequest", new JSONObject()
							.put("wsQueryFilter", new JSONObject()
									.put("queryFilterType", "FIND_BY_STEM_NAME")
									.put("stemName", getConfiguration().getExportStem())
									.put("stemNameScope", "ALL_IN_SUBTREE")));
			executeFindGroupsResponse(request, body, handler);
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Get all groups");
		}
	}

	@Override
	boolean getGroupByUuid(String uuid, ResultsHandler handler, OperationOptions options) {
		if (!isGetMembers(options)) {
			return getGroupByUuid(uuid, handler);
		} else {
			URIBuilder uriBuilder = processor.getURIBuilder()
					.setPath(URI_BASE_PATH + PATH_GROUPS);
			try {
				HttpPost request = new HttpPost(uriBuilder.build());
				JSONObject body = new JSONObject()
						.put("WsRestGetMembersRequest", new JSONObject()
								.put("wsGroupLookups", new JSONObject[] { new JSONObject()
										.put("uuid", uuid) })
								.put("includeSubjectDetail", true));
				return executeGetGroupWithMembersResponse(request, body, handler);
			} catch (RuntimeException | URISyntaxException e) {
				throw processor.processException(e, uriBuilder, "Get all groups");
			}

		}
	}

	@Override
	void getGroupByName(String name, ResultsHandler handler, OperationOptions options) {
		if (!isGetMembers(options)) {
			getGroupByName(name, handler);
		} else {
			URIBuilder uriBuilder = processor.getURIBuilder()
					.setPath(URI_BASE_PATH + PATH_GROUPS);
			try {
				HttpPost request = new HttpPost(uriBuilder.build());
				JSONObject body = new JSONObject()
						.put("WsRestGetMembersRequest", new JSONObject()
								.put("wsGroupLookups", new JSONObject[] { new JSONObject()
										.put("groupName", name) })
								.put("includeSubjectDetail", true));
				executeGetGroupWithMembersResponse(request, body, handler);
			} catch (RuntimeException | URISyntaxException e) {
				throw processor.processException(e, uriBuilder, "Get all groups");
			}
		}
	}

	private boolean executeGetGroupWithMembersResponse(HttpPost request, JSONObject body, ResultsHandler handler) {
		System.out.println("Request = " + body.toString());
		JSONObject response = processor.callRequest(request, body, true, CONTENT_TYPE_JSON);
		System.out.println("Got response: " + response);
		processor.checkSuccess(response, WS_GET_MEMBERS_RESULTS);

		JSONObject gObject = (JSONObject) processor.get(response, WS_GET_MEMBERS_RESULTS, RESULTS, WS_GROUP);
		String name = processor.getStringOrNull(gObject, "name");
		String extension = processor.getStringOrNull(gObject, "extension");
		String uuid = processor.getStringOrNull(gObject, "uuid");
		ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
		builder.setObjectClass(getObjectClass());
		builder.setUid(uuid);
		builder.setName(name);
		builder.addAttribute(ATTR_EXTENSION, extension);

		List<String> subjects = new ArrayList<>();
		JSONArray members = processor.getArray(response, false, WS_GET_MEMBERS_RESULTS, RESULTS, WS_SUBJECTS);
		if (members != null) {
			for (Object memberObject : members) {
				JSONObject member = (JSONObject) memberObject;
				String sourceId = processor.getStringOrNull(member, "sourceId");
				if (sourceId == null || !sourceId.equals(getConfiguration().getSubjectSource())) {
					LOG.info("Skipping non-person member (source={0})", sourceId);
					continue;
				}
				String subjectId = processor.getStringOrNull(member, "id");
				if (subjectId != null) {
					subjects.add(subjectId);
				} else {
					LOG.info("Skipping unnamed member (source={0})", member);
				}
			}
			builder.addAttribute(ATTR_MEMBER, subjects);
		}
		return handler.handle(builder.build());
	}

	@Override
	protected ObjectClass getObjectClass() {
		return new ObjectClass(OBJECT_CLASS_NAME);
	}

	private boolean isGetMembers(OperationOptions options) {
		String[] attrs = options.getAttributesToGet();
		return attrs != null && Arrays.asList(attrs).contains(ATTR_MEMBER);
	}
}
