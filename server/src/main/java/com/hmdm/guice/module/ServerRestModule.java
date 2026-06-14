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

package com.hmdm.guice.module;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.servlet.ServletModule;
import com.hmdm.rest.filter.AuthFilter;
import com.hmdm.rest.json.SyncResponseHook;
import com.hmdm.rest.resource.ConfigurationResource;
import com.hmdm.rest.resource.DeviceResource;
import com.hmdm.rest.resource.PushApiResource;
import com.hmdm.rest.resource.SyncResource;
import com.hmdm.service.DeviceEnrollmentService;

/**
 * <p>A Guice module wiring the core server REST resources and orchestration service.</p>
 *
 * <p>Binds the four REST resources (DeviceResource, SyncResource, ConfigurationResource,
 * PushApiResource) and the DeviceEnrollmentService orchestration layer. Also sets up
 * the AuthFilter for private endpoints and initializes the SyncResponseHook multibinder
 * for plugin extension.</p>
 */
public class ServerRestModule extends ServletModule {

    public ServerRestModule() {
    }

    @Override
    protected void configureServlets() {
        // Auth filter for all private endpoints
        filter("/rest/devices/private/*").through(AuthFilter.class);
        filter("/rest/configurations/private/*").through(AuthFilter.class);
        filter("/rest/push/private/*").through(AuthFilter.class);
        // /public/* endpoints are NOT filtered (device-facing, no auth required)

        // Bind REST resources
        bind(DeviceResource.class);
        bind(SyncResource.class);
        bind(ConfigurationResource.class);
        bind(PushApiResource.class);

        // Bind orchestration service
        bind(DeviceEnrollmentService.class);

        // Initialize SyncResponseHook multibinder for plugin extensions
        Multibinder.newSetBinder(binder(), SyncResponseHook.class);
    }
}
