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

import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author surmanek
 * @author mederly
 *
 */
public class StandardGroupTests extends GrouperTestHelper {

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

		grouperConnector.executeQuery(ObjectClass.GROUP, filter, handler, options);
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
		uuid = group.getUid().getUidValue();
	}
	
	@Test(priority = 5)
	public void findByGroupUuid() {
		// filtering:
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(Uid.NAME, uuid));

		grouperConnector.executeQuery(ObjectClass.GROUP, filter, handler, options);
		assertEquals("Wrong # of groups retrieved", results.size(), 1);
		ConnectorObject group = results.get(0);
		System.out.println("Found group: " + group);
	}

	@Test(priority = 6)
	public void allGroupsTest() {
		results.clear();
		grouperConnector.executeQuery(ObjectClass.GROUP, null, handler, options);
		// most probably here will be no groups, as etc:sysadmingroup has no direct group members
		for (ConnectorObject group : results) {
			System.out.println("Found group: " + group);
		}
	}

	
	@Test(priority = 20)
	public void disposeTest() {
		 grouperConnector.dispose();
	}
	
	private void cleanUp() {
		results.clear();
	}
}
