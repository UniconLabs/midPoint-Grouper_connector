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
public class AccountTests extends GrouperTestHelper {
	
	@Test(priority = 1)
	public void initTest() {
		grouperConnector.init(getConfiguration());
		cleanUp();
	}
	
	@Test(priority = 3)
	public void schemaTest() {
		grouperConnector.schema();
	}

	@Test(priority = 3)
	public void testTest() {
		grouperConnector.test();
	}

	@Test(priority = 4)
	public void nameEqualsFilteringForAccountsTest() {
		// filtering:
		results.clear();
		AttributeFilter filter = (EqualsFilter) FilterBuilder
				.equalTo(AttributeBuilder.build(Name.NAME, "banderson"));

		grouperConnector.executeQuery(accountObjectClass, filter, handler, options);
		assertEquals("Wrong # of users retrieved", results.size(), 1);
		ConnectorObject user = results.get(0);
		System.out.println("Found user: " + user);
	}
	
	@Test(priority = 6)
	public void listingAccountsTest() {
		results.clear();
		grouperConnector.executeQuery(accountObjectClass, null, handler, options);

		assertEquals("Wrong # of users retrieved", results.size(), 1);
		ConnectorObject user = results.get(0);
		System.out.println("Found user: " + user);
	}

	
	@Test(priority = 20)
	public void disposeTest() {
		 grouperConnector.dispose();
	}
	
	private void cleanUp() {
		results.clear();
	}
}
