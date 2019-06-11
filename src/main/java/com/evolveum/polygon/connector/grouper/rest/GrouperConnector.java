/**
 * Copyright (c) 2017 Evolveum
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

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.*;

import java.util.List;

/**
 * @author surmanek
 * @author mederly
 *
 */
@ConnectorClass(displayNameKey = "GrouperConnector.rest.display", configurationClass = GrouperConfiguration.class)
public class GrouperConnector implements TestOp, SchemaOp, Connector, SearchOp<Filter> {

	private static final Log LOG = Log.getLog(GrouperConnector.class);
	private GrouperConfiguration configuration;
	private Processor processor;
	private AccountProcessor accountProcessor;
	private StandardGroupProcessor standardGroupProcessor;
	private PlainGroupProcessor plainGroupProcessor;

	private static final String PROJECT_NAME = "PROJECT";
	private static final String ATTR_GROUPS = "groups";

	@Override
	public GrouperConfiguration getConfiguration() {
		return configuration;
	}

	@Override
	public void init(Configuration configuration) {
		if (configuration == null) {
			LOG.error("Initialization of the configuration failed: Configuration is not provided.");
			throw new ConfigurationException(
					"Initialization of the configuration failed: Configuration is not provided.");
		}
		this.configuration = (GrouperConfiguration) configuration;
		this.configuration.validate();
		this.processor = new Processor(this.configuration);
		this.accountProcessor = new AccountProcessor(processor);
		this.standardGroupProcessor = new StandardGroupProcessor(processor);
		this.plainGroupProcessor = new PlainGroupProcessor(processor);
	}

	@Override
	public void dispose() {
		configuration = null;
		processor = null;
		accountProcessor = null;
		standardGroupProcessor = null;
		plainGroupProcessor = null;
	}

	@Override
	public void test() {
		LOG.info("Testing connection...");
		standardGroupProcessor.test();
		LOG.ok("Testing finished successfully.");
	}

	@Override
	public Schema schema() {
		SchemaBuilder schemaBuilder = new SchemaBuilder(GrouperConnector.class);

		schemaBuilder.defineObjectClass(accountProcessor.buildSchema().build());
		schemaBuilder.defineObjectClass(standardGroupProcessor.buildSchema().build());
		schemaBuilder.defineObjectClass(plainGroupProcessor.buildSchema().build());

		return schemaBuilder.build();
	}

	@Override
	public FilterTranslator<Filter> createFilterTranslator(ObjectClass arg0, OperationOptions arg1) {
		return new FilterTranslator<Filter>() {
			@Override
			public List<Filter> translate(Filter filter) {
				return CollectionUtil.newList(filter);
			}
		};
	}

	@Override
	public void executeQuery(ObjectClass objClass, Filter filter, ResultsHandler handler, OperationOptions options) {
		LOG.info("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Execute Query-Parameters~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		if (objClass == null) {
			LOG.error("Get operation failed: Attribute Object Class is not provided.");
			throw new InvalidAttributeValueException("Attribute Object Class is not provided.");
		} else
			LOG.info("ObjectClass: {0}", objClass.toString());

		if (handler == null) {
			LOG.error("Get operation failed: Attribute Result Handler is not provided.");
			throw new InvalidAttributeValueException("Attribute Result Handler is not provided.");
		} else
			LOG.info("Execute Query-Handler: {0}", handler.toString());

		if (options == null) {
			LOG.error("Get operation failed: Attribute Options is not provided.");
			throw new InvalidAttributeValueException("Attribute Options is not provided.");
		} else
			LOG.info("Options: {0}", options.toString());

		LOG.info("Filter: {0}", filter);
		LOG.info("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

		if (objClass.is(ObjectClass.ACCOUNT_NAME)) {
			accountProcessor.read(filter, handler, options);
		} else if (objClass.is(ObjectClass.GROUP_NAME)) {
			standardGroupProcessor.read(filter, handler, options);
		} else if (objClass.is(plainGroupProcessor.getObjectClass().getObjectClassValue())) {
			plainGroupProcessor.read(filter, handler, options);
		}
	}


}
