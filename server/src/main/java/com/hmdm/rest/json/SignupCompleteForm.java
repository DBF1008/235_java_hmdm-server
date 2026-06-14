/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * <p>Request body for completing customer self-signup (step 2: verify token and create account).</p>
 */
@ApiModel(description = "Request to complete customer self-signup")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupCompleteForm implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "Verification token received via email", required = true)
    private String token;

    @ApiModelProperty(value = "Customer (organization) name, used as admin login", required = true)
    private String name;

    @ApiModelProperty(value = "Admin's first name", required = true)
    private String firstName;

    @ApiModelProperty(value = "Admin's last name", required = true)
    private String lastName;

    @ApiModelProperty(value = "Admin password as MD5 hash", required = true)
    private String password;

    @ApiModelProperty(value = "Whether to copy design settings from the master customer")
    private boolean copyDesign;

    @ApiModelProperty(value = "ID of the device configuration to use")
    private Integer deviceConfigurationId;

    @ApiModelProperty(value = "IDs of configurations to copy for this customer")
    private Integer[] configurationIds;

    public SignupCompleteForm() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isCopyDesign() {
        return copyDesign;
    }

    public void setCopyDesign(boolean copyDesign) {
        this.copyDesign = copyDesign;
    }

    public Integer getDeviceConfigurationId() {
        return deviceConfigurationId;
    }

    public void setDeviceConfigurationId(Integer deviceConfigurationId) {
        this.deviceConfigurationId = deviceConfigurationId;
    }

    public Integer[] getConfigurationIds() {
        return configurationIds;
    }

    public void setConfigurationIds(Integer[] configurationIds) {
        this.configurationIds = configurationIds;
    }
}
