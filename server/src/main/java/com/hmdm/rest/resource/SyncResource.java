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
import com.hmdm.event.DeviceInfoUpdatedEvent;
import com.hmdm.event.EventService;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.json.Response;
import com.hmdm.rest.json.SyncRequest;
import com.hmdm.rest.json.SyncResponseHook;
import com.hmdm.rest.json.SyncResponseInt;
import com.hmdm.service.DeviceEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * <p>A REST resource for device configuration synchronization. This is the critical path
 * endpoint called by devices to retrieve their full configuration.</p>
 */
@Singleton
@Path("/rest/sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SyncResource {

    private static final Logger log = LoggerFactory.getLogger(SyncResource.class);

    private final DeviceEnrollmentService enrollmentService;
    private final UnsecureDAO unsecureDAO;
    private final EventService eventService;
    private final Set<SyncResponseHook> syncResponseHooks;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public SyncResource() {
        this.enrollmentService = null;
        this.unsecureDAO = null;
        this.eventService = null;
        this.syncResponseHooks = null;
    }

    @Inject
    public SyncResource(DeviceEnrollmentService enrollmentService,
                        UnsecureDAO unsecureDAO,
                        EventService eventService,
                        @com.google.inject.Inject(optional = true) Set<SyncResponseHook> syncResponseHooks) {
        this.enrollmentService = enrollmentService;
        this.unsecureDAO = unsecureDAO;
        this.eventService = eventService;
        this.syncResponseHooks = syncResponseHooks;
    }

    // =================================================================================================================
    /**
     * <p>Full configuration sync endpoint. Called by the device itself (unauthenticated).</p>
     *
     * <p>This endpoint performs the following steps:</p>
     * <ol>
     *   <li>Resolves or creates the device via enrollment service</li>
     *   <li>Updates device info (state JSON, IMEI timestamp, public IP)</li>
     *   <li>Updates custom properties if provided</li>
     *   <li>Saves device-specific application settings if provided</li>
     *   <li>Builds the complete sync response</li>
     *   <li>Applies any registered SyncResponseHook extensions</li>
     *   <li>Fires a DeviceInfoUpdatedEvent for downstream listeners</li>
     * </ol>
     */
    @POST
    @Path("/public/{deviceNumber}")
    public Response syncConfiguration(@PathParam("deviceNumber") String deviceNumber,
                                      SyncRequest request) {
        log.debug("#syncConfiguration: deviceNumber = {}", deviceNumber);
        try {
            // 1. Resolve or create device
            Device device = enrollmentService.enrollDevice(
                    deviceNumber,
                    request != null ? request.getCreateOptions() : null
            );
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }

            // 2. Update device info
            if (request != null) {
                unsecureDAO.updateDeviceInfo(
                        device.getId(),
                        request.getInfo(),
                        request.getImeiUpdateTs(),
                        request.getPublicIp()
                );

                // 3. Update custom properties if any are provided
                if (request.getCustom1() != null || request.getCustom2() != null || request.getCustom3() != null) {
                    Device customPropsDevice = new Device();
                    customPropsDevice.setId(device.getId());
                    customPropsDevice.setCustom1(request.getCustom1());
                    customPropsDevice.setCustom2(request.getCustom2());
                    customPropsDevice.setCustom3(request.getCustom3());
                    unsecureDAO.updateDeviceCustomProperties(device.getId(), customPropsDevice);
                }

                // 4. Save device-specific application settings if provided
                if (request.getApplicationSettings() != null && !request.getApplicationSettings().isEmpty()) {
                    unsecureDAO.saveDeviceApplicationSettings(device, request.getApplicationSettings());
                }
            }

            // 5. Build the complete sync response
            SyncResponseInt syncResponse = enrollmentService.buildSyncResponse(device);
            if (syncResponse == null) {
                return Response.ERROR("Configuration not found");
            }

            // 6. Apply SyncResponseHook chain
            if (syncResponseHooks != null && !syncResponseHooks.isEmpty()) {
                for (SyncResponseHook hook : syncResponseHooks) {
                    try {
                        syncResponse = hook.handle(device.getId(), syncResponse);
                    } catch (Exception e) {
                        log.error("Error applying SyncResponseHook: {}", hook.getClass().getName(), e);
                    }
                }
            }

            // 7. Fire device info updated event
            eventService.fireEvent(new DeviceInfoUpdatedEvent(device.getId()));

            return Response.OK(syncResponse);
        } catch (Exception e) {
            log.error("Unexpected error during sync for device: {}", deviceNumber, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
