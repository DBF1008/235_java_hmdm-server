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

package com.hmdm.rest.resource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.rest.json.Response;
import com.hmdm.service.DeviceEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * <p>A REST resource for triggering push notifications to devices. Allows administrators
 * to force configuration update pushes to individual devices or to all devices sharing
 * a configuration.</p>
 */
@Singleton
@Path("/rest/push")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PushApiResource {

    private static final Logger log = LoggerFactory.getLogger(PushApiResource.class);

    private final DeviceEnrollmentService enrollmentService;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public PushApiResource() {
        this.enrollmentService = null;
    }

    @Inject
    public PushApiResource(DeviceEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    // =================================================================================================================
    /**
     * <p>Pushes a configuration update notification to a single device (admin, authenticated).</p>
     */
    @POST
    @Path("/private/device/{deviceId}")
    public Response pushToDevice(@PathParam("deviceId") Integer deviceId) {
        log.debug("#pushToDevice: deviceId = {}", deviceId);
        try {
            enrollmentService.triggerConfigPush(deviceId);
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when pushing config to device: {}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Pushes a configuration update notification to all devices using the specified configuration
     * (admin, authenticated).</p>
     */
    @POST
    @Path("/private/config/{configId}")
    public Response pushToConfiguration(@PathParam("configId") Integer configId) {
        log.debug("#pushToConfiguration: configId = {}", configId);
        try {
            enrollmentService.onConfigurationChanged(configId);
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when pushing config update for configuration: {}", configId, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
