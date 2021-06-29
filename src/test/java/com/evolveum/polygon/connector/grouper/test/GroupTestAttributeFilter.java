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

package com.evolveum.polygon.connector.grouper.test;

import com.evolveum.polygon.connector.grouper.rest.GroupProcessor;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.evolveum.polygon.connector.grouper.rest.GroupProcessor.ATTR_NAME;
import static com.evolveum.polygon.connector.grouper.rest.GroupProcessor.ATTR_UUID;
import static org.identityconnectors.framework.common.objects.OperationOptions.OP_ATTRIBUTES_TO_GET;
import static org.testng.AssertJUnit.assertEquals;

/**
 * Tests the group object class using attribute filter config. See the superclass for the environment needed.
 */
public class GroupTestAttributeFilter extends AbstractTest {

	private static final ObjectClass OC_GROUP = new ObjectClass(GroupProcessor.OBJECT_CLASS_NAME);

	private String uuid;

	@Test(priority = 1)
	public void initialization() {
		grouperConnector.init(getConfigurationAttributeFilter());
	}
	
	@Test(priority = 2)
	public void testSchema() {
		grouperConnector.schema();
	}

	@Test(priority = 3)
	public void testTestOperation() {
		grouperConnector.test();
	}

	@Test(priority = 4)
	public void testFindByGroupName() {
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(ATTR_NAME, TEST_GROUP));

		grouperConnector.executeQuery(OC_GROUP, filter, handler, options);
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
		uuid = group.getUid().getUidValue();
	}
	
	@Test(priority = 10)
	public void testFindByGroupNameWithMembers() {
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(ATTR_NAME, TEST_GROUP));
		
		grouperConnector.executeQuery(OC_GROUP, filter, handler, getMembersOptions());
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
		List<String> members = getMembers(group);
		assertEquals("Wrong members", Collections.singletonList(TEST_USER), members);
	}
	
	@Test(priority = 12)
	public void testFindByGroupUuid() {
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(ATTR_UUID, uuid));

		grouperConnector.executeQuery(OC_GROUP, filter, handler, options);
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
	}

	@Test(priority = 13)
	public void testFindByGroupUuidWihMembers() {
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(ATTR_UUID, uuid));

		grouperConnector.executeQuery(OC_GROUP, filter, handler, getMembersOptions());
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
		assertEquals("Wrong members", Collections.singletonList(TEST_USER), getMembers(group));
	}

	@Test(priority = 14)
	public void testGetAllGroups() {
		results.clear();
		grouperConnector.executeQuery(OC_GROUP, null, handler, options);
		for (ConnectorObject group : results) {
			System.out.println("Found group: " + group);
		}
	}

	@Test(priority = 16)
	public void testGetAllGroupsWithMembers() {
		results.clear();
		grouperConnector.executeQuery(OC_GROUP, null, handler, getMembersOptions());
		for (ConnectorObject group : results) {
			System.out.println("Found group: " + group);
		}
	}

	@Test(priority = 20)
	public void dispose() {
		 grouperConnector.dispose();
	}
	
	private OperationOptions getMembersOptions() {
		HashMap<String, Object> map = new HashMap<>();
		map.put(OP_ATTRIBUTES_TO_GET, new String[] { GroupProcessor.ATTR_MEMBER });
		return new OperationOptions(map);
	}

	private List<String> getMembers(ConnectorObject group) {
		Attribute attribute = group.getAttributeByName(GroupProcessor.ATTR_MEMBER);
		//noinspection unchecked
		return attribute != null ? (List<String>) (List) attribute.getValue() : Collections.emptyList();
	}
}