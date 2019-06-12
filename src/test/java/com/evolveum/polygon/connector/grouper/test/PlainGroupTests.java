package com.evolveum.polygon.connector.grouper.test;
/*******************************************************************************
 * Copyright 2019 Evolveum
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

import com.evolveum.polygon.connector.grouper.rest.PlainGroupProcessor;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.identityconnectors.framework.common.objects.OperationOptions.OP_ATTRIBUTES_TO_GET;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author surmanek
 * @author mederly
 *
 */
public class PlainGroupTests extends GrouperTestHelper {

	private static final ObjectClass PLAIN_GROUP = new ObjectClass(PlainGroupProcessor.OBJECT_CLASS_NAME);

	private String uuid;

	@Test(priority = 1)
	public void initTest() {
		grouperConnector.init(getConfiguration());
		cleanUp();
	}
	
	@Test(priority = 2)
	public void schemaTest() {
		grouperConnector.schema();
	}

	@Test(priority = 3)
	public void testTest() {
		grouperConnector.test();
	}

	@Test(priority = 4)
	public void findByGroupName() {
		// filtering:
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(Name.NAME, "etc:sysadmingroup"));

		grouperConnector.executeQuery(PLAIN_GROUP, filter, handler, options);
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
		uuid = group.getUid().getUidValue();
	}
	
	@Test(priority = 10)
	public void findByGroupNameWithMembers() {
		// filtering:
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(Name.NAME, "etc:sysadmingroup"));

		grouperConnector.executeQuery(PLAIN_GROUP, filter, handler, getMembersOptions());
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
		List<String> members = getMembers(group);
		assertEquals("Wrong members", Collections.singletonList("banderson"), members);
	}

	@Test(priority = 12)
	public void findByGroupUuid() {
		// filtering:
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(Uid.NAME, uuid));

		grouperConnector.executeQuery(PLAIN_GROUP, filter, handler, options);
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
	}

	@Test(priority = 13)
	public void findByGroupUuidWihMembers() {
		// filtering:
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(Uid.NAME, uuid));

		grouperConnector.executeQuery(PLAIN_GROUP, filter, handler, getMembersOptions());
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
		assertEquals("Wrong members", Collections.singletonList("banderson"), getMembers(group));
	}

	@Test(priority = 14)
	public void allGroups() {
		results.clear();
		grouperConnector.executeQuery(PLAIN_GROUP, null, handler, options);
		for (ConnectorObject group : results) {
			System.out.println("Found group: " + group);
		}
	}

	@Test(priority = 16)
	public void allGroupsWithMembers() {
		results.clear();
		grouperConnector.executeQuery(PLAIN_GROUP, null, handler, getMembersOptions());
		for (ConnectorObject group : results) {
			System.out.println("Found group: " + group);
		}
	}

	@Test(priority = 20)
	public void dispose() {
		 grouperConnector.dispose();
	}
	
	private void cleanUp() {
		results.clear();
	}

	private OperationOptions getMembersOptions() {
		HashMap<String, Object> map = new HashMap<>();
		map.put(OP_ATTRIBUTES_TO_GET, new String[] { PlainGroupProcessor.ATTR_MEMBER });
		return new OperationOptions(map);
	}

	private List<String> getMembers(ConnectorObject group) {
		Attribute attribute = group.getAttributeByName(PlainGroupProcessor.ATTR_MEMBER);
		//noinspection unchecked
		return attribute != null ? (List<String>) (List) attribute.getValue() : Collections.<String>emptyList();
	}
}
