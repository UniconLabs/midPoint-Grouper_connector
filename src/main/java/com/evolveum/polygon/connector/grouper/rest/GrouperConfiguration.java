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

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;
import java.util.Arrays;
import java.util.Map;

/**
 * @author surmanek
 * @author mederly
 *
 */
@SuppressWarnings("WeakerAccess")
public class GrouperConfiguration extends AbstractConfiguration implements StatefulConfiguration {

    private static final Log LOG = Log.getLog(GrouperConfiguration.class);

    private static final String DEFAULT_CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    private static final String DEFAULT_URI_BASE_PATH = "/grouper-ws/servicesRest/json/v2_4_000";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private String baseUrl;
    private String uriBasePath;
    private String username;
    private GuardedString password;
    private Boolean ignoreSslValidation;
    private String contentType;

    private String baseStem;
    private String[] groupIncludePattern;
    private String[] groupExcludePattern;
    private String[] groupAttribute;
    private String subjectSource;
    private String testStem;
    private String testGroup;
    private Integer pageSize;
    private Boolean logRequestResponses;


    @ConfigurationProperty(order = 10, displayMessageKey = "baseUrl.display", helpMessageKey = "baseUrl.help", required = true)
    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @ConfigurationProperty(order = 20, displayMessageKey = "username.display", helpMessageKey = "username.help", required = true)
    public String getUsername() {
        return username;
    }

    public void setUsername(String name) {
        this.username = name;
    }

    @ConfigurationProperty(order = 30, displayMessageKey = "password.display", helpMessageKey = "password.help", required = true, confidential = true)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

    public String getPasswordPlain() {
        StringBuilder plain = new StringBuilder();
        password.access(clearChars -> plain.append(new String(clearChars)));
        return plain.toString();
    }

    /**
     * Should we ignore SSL validation issues when connecting to the Grouper REST service? Do not use in production.
     */
    @ConfigurationProperty(order = 40, displayMessageKey = "ignoreSslValidation.display", helpMessageKey = "ignoreSslValidation.help")
    public Boolean getIgnoreSslValidation() {
        return ignoreSslValidation;
    }

    public void setIgnoreSslValidation(Boolean ignoreSslValidation) {
        this.ignoreSslValidation = ignoreSslValidation;
    }

    /**
     * Used to specify stem that is fetched during Test Connection (if any).
     */
    @ConfigurationProperty(order = 50, displayMessageKey = "testStem.display", helpMessageKey = "testStem.help")
    public String getTestStem() {
        return testStem;
    }

    public void setTestStem(String testStem) {
        this.testStem = testStem;
    }

    /**
     * Used to specify group that is fetched during Test Connection (if any).
     */
    @ConfigurationProperty(order = 60, displayMessageKey = "testGroup.display", helpMessageKey = "testGroup.help")
    public String getTestGroup() {
        return testGroup;
    }

    public void setTestGroup(String testGroup) {
        this.testGroup = testGroup;
    }

    /**
     * Used to specify page size for Grouper WS paging.
     */
    @ConfigurationProperty(order = 70, displayMessageKey = "pageSize.display", helpMessageKey = "pageSize.help")
    public Integer getPageSize() {
        if (pageSize != null) {
            return pageSize;
        } else {
            return DEFAULT_PAGE_SIZE;
        }
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Used in case the Grouper WS Base Path Changes
     * @return
     */
    @ConfigurationProperty(order = 80, displayMessageKey = "uriBasePath.display", helpMessageKey = "uriBasePath.help", required = false)
    public String getUriBasePath() {
        if (uriBasePath != null && !uriBasePath.isBlank()) {
            return uriBasePath;
        } else {
            return DEFAULT_URI_BASE_PATH;
        }
    }

    public void setUriBasePath(String uriBasePath) {
        this.uriBasePath = uriBasePath;
    }

    /**
     * Used in case Grouper WS Content Type Changes
     * @return
     */
    @ConfigurationProperty(order = 90, displayMessageKey = "contentType.display", helpMessageKey = "contentType.help", required = false)
    public String getContentType() {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        } else {
            return DEFAULT_CONTENT_TYPE_JSON;
        }
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }


    //TODO Implement the following in Grouper Query Filter
    /**
     * Used to specify root stem for groups returned by this connector. The default is ":" (the whole tree).
     */
    @ConfigurationProperty(order = 100, displayMessageKey = "baseStem.display", helpMessageKey = "baseStem.help")
    public String getBaseStem() {
        return baseStem;
    }

    public void setBaseStem(String baseStem) {
        this.baseStem = baseStem;
    }

    /**
     * Which groups by attribute name/value should be visible to connector
     */
    @ConfigurationProperty(order = 110, displayMessageKey = "groupAttribute.display", helpMessageKey = "groupAttribute.help")
    public String[] getGroupAttribute() {
        return groupAttribute;
    }

    public void setGroupAttribute(String[] groupAttribute) {
        this.groupAttribute = groupAttribute;
    }

    /**
     * Which groups should be visible to this connector?
     */
    @ConfigurationProperty(order = 120, displayMessageKey = "groupIncludePattern.display", helpMessageKey = "groupIncludePattern.help")
    public String[] getGroupIncludePattern() {
        return groupIncludePattern;
    }

    public void setGroupIncludePattern(String[] groupIncludePattern) {
        this.groupIncludePattern = groupIncludePattern;
    }

    /**
     * Which groups should be hidden (invisible) to this connector?
     */
    @ConfigurationProperty(order = 130, displayMessageKey = "groupExcludePattern.display", helpMessageKey = "groupExcludePattern.help")
    public String[] getGroupExcludePattern() {
        return groupExcludePattern;
    }

    public void setGroupExcludePattern(String[] groupExcludePattern) {
        this.groupExcludePattern = groupExcludePattern;
    }

    /**
     * Used to limit subjects returned by this connector.
     */
    @ConfigurationProperty(order = 140, displayMessageKey = "subjectSource.display", helpMessageKey = "subjectSource.help")
    public String getSubjectSource() {
        return subjectSource;
    }

    public void setSubjectSource(String subjectSource) {
        this.subjectSource = subjectSource;
    }

    /**
     * Should we log request/response logs to/from Grouper WS.
     */
    @ConfigurationProperty(order = 160, displayMessageKey = "logRequestResponses.display", helpMessageKey = "logRequestResponses.help")
    public Boolean getLogRequestResponses() {
        return logRequestResponses;
    }

    public void setLogRequestResponses(Boolean logRequestResponses) {
        this.logRequestResponses = logRequestResponses;
    }


    @Override
    public void validate() {
        String exceptionMsg;
        if (StringUtil.isBlank(baseUrl)) {
            exceptionMsg = "Base URL is not provided.";
        } else if (StringUtil.isBlank(username)) {
            exceptionMsg = "Name is not provided.";
        } else if (password == null) {
            exceptionMsg = "Password is not provided.";
        } else {
            return;
        }
        LOG.error("{0}", exceptionMsg);
        throw new ConfigurationException(exceptionMsg);
    }

    @Override
    public void release() {
        this.baseUrl = null;
        this.uriBasePath = null;
        this.username = null;
        this.password = null;
        this.ignoreSslValidation = null;
        this.contentType = null;
        this.baseStem = null;
        this.groupIncludePattern = null;
        this.groupExcludePattern = null;
        this.groupAttribute = null;
        this.subjectSource = null;
        this.testStem = null;
        this.testGroup = null;
        this.pageSize = null;
        this.logRequestResponses = null;
    }

    @Override
    public String toString() {
        return "GrouperConfiguration{" +
                "baseUrl='" + baseUrl + '\'' +
                ", username='" + username + '\'' +
                ", ignoreSslValidation=" + ignoreSslValidation +
                ", baseStem='" + baseStem + '\'' +
                ", groupIncludePattern=" + Arrays.toString(groupIncludePattern) +
                ", groupExcludePattern=" + Arrays.toString(groupExcludePattern) +
                ", subjectSource='" + subjectSource + '\'' +
                ", testStem='" + testStem + '\'' +
                ", testGroup='" + testGroup + '\'' +
                ", pageSize='" + pageSize + '\'' +
                ", uriBasePath='" + uriBasePath + '\'' +
                ", contentType='" + contentType + '\'' +
                ", groupAttribute='" + groupAttribute + '\'' +
                ", logRequestResponses='" + logRequestResponses + '\'' +
                '}';
    }
}
