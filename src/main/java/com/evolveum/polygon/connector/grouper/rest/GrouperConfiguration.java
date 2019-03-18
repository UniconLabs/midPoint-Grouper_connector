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

/**
 * @author surmanek
 * @author mederly
 *
 */
public class GrouperConfiguration extends AbstractConfiguration implements StatefulConfiguration {
	
	private static final Log LOG = Log.getLog(GrouperConfiguration.class);

	private String name;
	private GuardedString password;
	private String baseUrl;
	private String superGroup;
	private String rootStem;
	private Boolean ignoreSslValidation;
	private String subjectSource;

	// getter and setter methods for "baseUrl" attribute:
	@ConfigurationProperty(order = 1, displayMessageKey = "baseUrl.display", helpMessageKey = "baseUrl.help", required = true)
	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	// getter and setter methods for "name" attribute:
	@ConfigurationProperty(order = 2, displayMessageKey = "username.display", helpMessageKey = "username.help", required = true)
	public String getUsername() {
		return name;
	}

	public void setUsername(String name) {
		this.name = name;
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

	@ConfigurationProperty(order = 5, displayMessageKey = "rootStem.display", helpMessageKey = "superGroup.help", required = true)
	public String getRootStem() {
		return rootStem;
	}

	public void setRootStem(String rootStem) {
		this.rootStem = rootStem;
	}

	@ConfigurationProperty(order = 6, displayMessageKey = "ignoreSslValidation.display", helpMessageKey = "ignoreSslValidation.help", required = false)
	public Boolean getIgnoreSslValidation() {
		return ignoreSslValidation;
	}

	public void setIgnoreSslValidation(Boolean ignoreSslValidation) {
		this.ignoreSslValidation = ignoreSslValidation;
	}

	@ConfigurationProperty(order = 7, displayMessageKey = "subjectSource.display", helpMessageKey = "subjectSource.help", required = false)
	public String getSubjectSource() {
		return subjectSource;
	}

	public void setSubjectSource(String subjectSource) {
		this.subjectSource = subjectSource;
	}

	@Override
	public void validate() {
		String exceptionMsg;
		if (baseUrl == null || StringUtil.isBlank(baseUrl)) {
			exceptionMsg = "Base url is not provided.";
		} else if (name == null || StringUtil.isBlank(name)) {
			exceptionMsg = "Name is not provided.";
		} else if (password == null) {
			exceptionMsg = "Password is not provided.";
		} else if (superGroup == null) {
			exceptionMsg = "Super group is not provided.";
		} else if (rootStem == null) {
			exceptionMsg = "Root stem is not provided.";
		} else if (subjectSource == null) {
			exceptionMsg = "Subject source is not provided.";
		} else {
			return;
		}
		LOG.error(exceptionMsg);
		throw new ConfigurationException(exceptionMsg);
	}
	
	@Override
	public void release() {
		LOG.info("The release of configuration resources is being performed");
		this.password = null;
		this.name = null;
		this.baseUrl = null;
		this.superGroup = null;
		this.rootStem = null;
	}
	
	@Override
	public String toString() {
		return "GrouperConfiguration{" +
				"username='" + name + '\'' +
				", baseUrl='" + baseUrl + '\'' +
				", superGroup='" + superGroup + '\'' +
				", rootStem='" + rootStem + '\'' +
				", ignoreSslValidation='" + ignoreSslValidation + '\'' +
				'}';
	}

}
