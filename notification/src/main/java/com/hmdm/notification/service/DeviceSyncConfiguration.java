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

package com.hmdm.notification.service;

import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationSetting;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.ConfigurationFile;

import java.util.Collections;
import java.util.List;

/**
 * <p>The complete configuration payload a device needs to apply on synchronization: the configuration
 * itself (with its application settings), the effective list of applications, the configuration files,
 * and the per-device application setting overrides.</p>
 *
 * <p>This bundles together the four pieces that were previously fetched independently at the sync
 * entry point, so that the <em>first</em> sync and every subsequent sync are assembled from exactly the
 * same place and a freshly registered device can never receive a partial configuration. A REST/sync
 * resource maps this holder onto the wire {@code SyncResponse}.</p>
 *
 * <p>The collection getters never return {@code null}.</p>
 */
public final class DeviceSyncConfiguration {

    private final Configuration configuration;
    private final List<Application> applications;
    private final List<ConfigurationFile> files;
    private final List<ApplicationSetting> applicationSettings;

    public DeviceSyncConfiguration(Configuration configuration,
                                   List<Application> applications,
                                   List<ConfigurationFile> files,
                                   List<ApplicationSetting> applicationSettings) {
        this.configuration = configuration;
        this.applications = applications == null ? Collections.emptyList() : applications;
        this.files = files == null ? Collections.emptyList() : files;
        this.applicationSettings = applicationSettings == null ? Collections.emptyList() : applicationSettings;
    }

    /**
     * @return the configuration to be applied on the device (carrying its application settings).
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * @return the effective list of applications for the device's configuration (never {@code null}).
     */
    public List<Application> getApplications() {
        return applications;
    }

    /**
     * @return the configuration files to be deployed on the device (never {@code null}).
     */
    public List<ConfigurationFile> getFiles() {
        return files;
    }

    /**
     * @return the per-device application setting overrides (never {@code null}).
     */
    public List<ApplicationSetting> getApplicationSettings() {
        return applicationSettings;
    }
}
