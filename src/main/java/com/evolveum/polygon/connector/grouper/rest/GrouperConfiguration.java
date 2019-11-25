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

/**
 * @author surmanek
 * @author mederly
 *
 */
@SuppressWarnings("WeakerAccess")
public class GrouperConfiguration extends AbstractConfiguration implements StatefulConfiguration {

    private static final Log LOG = Log.getLog(GrouperConfiguration.class);

    private String baseUrl;
    private String username;
    private GuardedString password;
    private Boolean ignoreSslValidation;

    private String baseStem;
    private String[] groupIncludePattern;
    private String[] groupExcludePattern;
    private String subjectSource;
    private String testStem;
    private String testGroup;

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
     * Used to specify root stem for groups returned by this connector. The default is ":" (the whole tree).
     */
    @ConfigurationProperty(order = 50, displayMessageKey = "baseStem.display", helpMessageKey = "baseStem.help")
    public String getBaseStem() {
        return baseStem;
    }

    public void setBaseStem(String baseStem) {
        this.baseStem = baseStem;
    }

    /**
     * Which groups should be visible to this connector?
     */
    @ConfigurationProperty(order = 60, displayMessageKey = "groupIncludePattern.display", helpMessageKey = "groupIncludePattern.help")
    public String[] getGroupIncludePattern() {
        return groupIncludePattern;
    }

    public void setGroupIncludePattern(String[] groupIncludePattern) {
        this.groupIncludePattern = groupIncludePattern;
    }

    /**
     * Which groups should be hidden (invisible) to this connector?
     */
    @ConfigurationProperty(order = 70, displayMessageKey = "groupExcludePattern.display", helpMessageKey = "groupExcludePattern.help")
    public String[] getGroupExcludePattern() {
        return groupExcludePattern;
    }

    public void setGroupExcludePattern(String[] groupExcludePattern) {
        this.groupExcludePattern = groupExcludePattern;
    }

    /**
     * Used to limit subjects returned by this connector.
     */
    @ConfigurationProperty(order = 80, displayMessageKey = "subjectSource.display", helpMessageKey = "subjectSource.help", required = true)
    public String getSubjectSource() {
        return subjectSource;
    }

    public void setSubjectSource(String subjectSource) {
        this.subjectSource = subjectSource;
    }

    /**
     * Used to specify stem that is fetched during Test Connection (if any).
     */
    @ConfigurationProperty(order = 90, displayMessageKey = "testStem.display", helpMessageKey = "testStem.help")
    public String getTestStem() {
        return testStem;
    }

    public void setTestStem(String testStem) {
        this.testStem = testStem;
    }

    /**
     * Used to specify group that is fetched during Test Connection (if any).
     */
    @ConfigurationProperty(order = 100, displayMessageKey = "testGroup.display", helpMessageKey = "testGroup.help")
    public String getTestGroup() {
        return testGroup;
    }

    public void setTestGroup(String testGroup) {
        this.testGroup = testGroup;
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
        } else if (subjectSource == null) {
            exceptionMsg = "Subject source is not provided.";
        } else {
            return;
        }
        LOG.error("{0}", exceptionMsg);
        throw new ConfigurationException(exceptionMsg);
    }

    @Override
    public void release() {
        this.baseUrl = null;
        this.username = null;
        this.password = null;
        this.ignoreSslValidation = null;
        this.baseStem = null;
        this.groupIncludePattern = null;
        this.groupExcludePattern = null;
        this.subjectSource = null;
        this.testGroup = null;
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
                ", testGroup='" + testGroup + '\'' +
                '}';
    }
}
