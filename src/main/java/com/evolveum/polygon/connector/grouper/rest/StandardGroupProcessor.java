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

import static com.evolveum.polygon.connector.grouper.rest.Processor.*;

/**
 * @author surmanek
 * @author mederly
 *
 */
class StandardGroupProcessor extends AbstractGroupProcessor {

	StandardGroupProcessor(Processor processor) {
		super(processor);
	}

	ObjectClassInfoBuilder buildSchema() {
		ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();

		builder.setType(ObjectClass.GROUP_NAME);
		AttributeInfoBuilder extension = new AttributeInfoBuilder(ATTR_EXTENSION, String.class);
		builder.addAttributeInfo(extension.build());

		return builder;
	}

	protected void getAllGroups(ResultsHandler handler, OperationOptions options) {
		URIBuilder uriBuilder = processor.getURIBuilder()
				.setPath(URI_BASE_PATH + PATH_GROUPS);
		try {
			HttpPost request = new HttpPost(uriBuilder.build());
			JSONObject body = new JSONObject()
					.put("WsRestGetMembersRequest", new JSONObject()
							.put("wsGroupLookups", new JSONObject[] { new JSONObject()
									.put("groupName", getConfiguration().getSuperGroup()) })
							.put("includeSubjectDetail", true)
							.put("memberFilter", "Immediate"));
			executeFindGroupsAsMembersResponse(request, body, handler);
		} catch (RuntimeException | URISyntaxException e) {
			throw processor.processException(e, uriBuilder, "Get all groups");
		}
	}

	@Override
	protected ObjectClass getObjectClass() {
		return ObjectClass.GROUP;
	}
}
