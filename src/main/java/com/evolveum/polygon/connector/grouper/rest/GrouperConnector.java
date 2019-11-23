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

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;

/**
 * Configuration for the Grouper connector.
 */
@ConnectorClass(displayNameKey = "GrouperConnector.rest.display", configurationClass = GrouperConfiguration.class)
public class GrouperConnector implements TestOp, SchemaOp, Connector, SearchOp<Filter> {

    private static final Log LOG = Log.getLog(GrouperConnector.class);

    private GrouperConfiguration configuration;
    private GroupProcessor groupProcessor;

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
        this.groupProcessor = new GroupProcessor(this.configuration);
    }

    @Override
    public void dispose() {
        configuration = null;
        groupProcessor = null;
    }

    @Override
    public void test() {
        LOG.info("Testing connection...");
        groupProcessor.test();
        LOG.ok("Testing finished successfully.");
    }

    @Override
    public Schema schema() {
        SchemaBuilder schemaBuilder = new SchemaBuilder(GrouperConnector.class);
        schemaBuilder.defineObjectClass(groupProcessor.buildSchema().build());
        return schemaBuilder.build();
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass arg0, OperationOptions arg1) {
        return CollectionUtil::newList;
    }

    @Override
    public void executeQuery(ObjectClass objClass, Filter filter, ResultsHandler handler, OperationOptions options) {
        LOG.info("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Execute Query-Parameters~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        if (objClass == null) {
            LOG.error("Get operation failed: object class is not provided.");
            throw new InvalidAttributeValueException("Object class is not provided.");
        } else if (!objClass.is(groupProcessor.getObjectClass().getObjectClassValue())) {
            throw new IllegalArgumentException("Unsupported object class: " + objClass);
        } else {
            LOG.info("ObjectClass: {0}", objClass);
        }

        if (handler == null) {
            LOG.error("Get operation failed: result handler is not provided.");
            throw new InvalidAttributeValueException("Result handler is not provided.");
        } else {
            LOG.info("Execute Query-Handler: {0}", handler);
        }

        if (options == null) {
            LOG.error("Get operation failed: options are not provided.");
            throw new InvalidAttributeValueException("Options are not provided.");
        } else {
            LOG.info("Options: {0}", options);
        }

        LOG.info("Filter: {0}", filter);
        LOG.info("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        groupProcessor.read(filter, handler, options);
    }
}
