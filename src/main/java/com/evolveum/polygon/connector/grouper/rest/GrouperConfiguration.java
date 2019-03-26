/**
 * Copyright (c) 2016 Evolveum
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

	private static final String DEFAULT_GROUP_SOURCE_ID = "g:gsa";

	private String baseUrl;
	private String username;
	private GuardedString password;
	private String superGroup;
	private String[] groupIncludePattern;
	private String[] groupExcludePattern;
	private Boolean ignoreSslValidation;
	private String subjectSource;
	private String groupSource;

	// getter and setter methods for "baseUrl" attribute:
	@ConfigurationProperty(order = 1, displayMessageKey = "baseUrl.display", helpMessageKey = "baseUrl.help", required = true)
	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	// getter and setter methods for "username" attribute:
	@ConfigurationProperty(order = 2, displayMessageKey = "username.display", helpMessageKey = "username.help", required = true)
	public String getUsername() {
		return username;
	}

	public void setUsername(String name) {
		this.username = name;
	}

	private String stringPassword = "";

	@ConfigurationProperty(order = 3, displayMessageKey = "password.display", helpMessageKey = "password.help", required = true, confidential = false)
	public GuardedString getPassword() {
		return password;
	}

	public void setPassword(GuardedString password) {
		this.password = password;
	}
	
	public String getStringPassword() {
		password.access(new GuardedString.Accessor() {
			@Override
			public void access(char[] clearChars) {
				stringPassword = new String(clearChars);
			}
		});
		return stringPassword;
	}

	@ConfigurationProperty(order = 4, displayMessageKey = "superGroup.display", helpMessageKey = "superGroup.help", required = true)
	public String getSuperGroup() {
		return superGroup;
	}

	public void setSuperGroup(String superGroup) {
		this.superGroup = superGroup;
	}

	@ConfigurationProperty(order = 5, displayMessageKey = "groupIncludePattern.display", helpMessageKey = "groupIncludePattern.help", required = true)
	public String[] getGroupIncludePattern() {
		return groupIncludePattern;
	}

	public void setGroupIncludePattern(String[] groupIncludePattern) {
		this.groupIncludePattern = groupIncludePattern;
	}

	@ConfigurationProperty(order = 6, displayMessageKey = "groupExcludePattern.display", helpMessageKey = "groupExcludePattern.help", required = true)
	public String[] getGroupExcludePattern() {
		return groupExcludePattern;
	}

	public void setGroupExcludePattern(String[] groupExcludePattern) {
		this.groupExcludePattern = groupExcludePattern;
	}

	@ConfigurationProperty(order = 7, displayMessageKey = "ignoreSslValidation.display", helpMessageKey = "ignoreSslValidation.help")
	public Boolean getIgnoreSslValidation() {
		return ignoreSslValidation;
	}

	public void setIgnoreSslValidation(Boolean ignoreSslValidation) {
		this.ignoreSslValidation = ignoreSslValidation;
	}

	@ConfigurationProperty(order = 8, displayMessageKey = "subjectSource.display", helpMessageKey = "subjectSource.help", required = true)
	public String getSubjectSource() {
		return subjectSource;
	}

	public void setSubjectSource(String subjectSource) {
		this.subjectSource = subjectSource;
	}

	@ConfigurationProperty(order = 9, displayMessageKey = "groupSource.display", helpMessageKey = "groupSource.help")
	public String getGroupSource() {
		return groupSource != null ? groupSource : DEFAULT_GROUP_SOURCE_ID;
	}

	public void setGroupSource(String groupSource) {
		this.groupSource = groupSource;
	}

	@Override
	public void validate() {
		String exceptionMsg;
		if (baseUrl == null || StringUtil.isBlank(baseUrl)) {
			exceptionMsg = "Base url is not provided.";
		} else if (username == null || StringUtil.isBlank(username)) {
			exceptionMsg = "Name is not provided.";
		} else if (password == null) {
			exceptionMsg = "Password is not provided.";
		} else if (superGroup == null) {
			exceptionMsg = "Super group is not provided.";
		} else if (groupIncludePattern == null || groupIncludePattern.length == 0) {
			exceptionMsg = "Group include pattern is not provided.";
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
		LOG.info("The release of configuration resources is being performed");
		this.baseUrl = null;
		this.password = null;
		this.username = null;
		this.superGroup = null;
		this.groupIncludePattern = null;
		this.groupExcludePattern = null;
		this.subjectSource = null;
		this.groupSource = null;
	}

	@Override
	public String toString() {
		return "GrouperConfiguration{" +
				"baseUrl='" + baseUrl + '\'' +
				", username='" + username + '\'' +
				", superGroup='" + superGroup + '\'' +
				", groupIncludePattern=" + Arrays.toString(groupIncludePattern) +
				", groupExcludePattern=" + Arrays.toString(groupExcludePattern) +
				", ignoreSslValidation=" + ignoreSslValidation +
				", subjectSource='" + subjectSource + '\'' +
				", groupSource='" + groupSource + '\'' +
				'}';
	}
}
