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
import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.persistence.domain.DeviceSearchRequest;
import com.hmdm.rest.json.PaginatedData;
import com.hmdm.rest.json.Response;
import com.hmdm.service.DeviceEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * <p>A REST resource for managing devices.</p>
 */
@Singleton
@Path("/rest/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceResource {

    private static final Logger log = LoggerFactory.getLogger(DeviceResource.class);

    private final DeviceEnrollmentService enrollmentService;
    private final DeviceDAO deviceDAO;
    private final UnsecureDAO unsecureDAO;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public DeviceResource() {
        this.enrollmentService = null;
        this.deviceDAO = null;
        this.unsecureDAO = null;
    }

    @Inject
    public DeviceResource(DeviceEnrollmentService enrollmentService,
                          DeviceDAO deviceDAO,
                          UnsecureDAO unsecureDAO) {
        this.enrollmentService = enrollmentService;
        this.deviceDAO = deviceDAO;
        this.unsecureDAO = unsecureDAO;
    }

    // =================================================================================================================
    /**
     * <p>Device enrollment / check-in endpoint. Called by the device itself (unauthenticated).</p>
     */
    @PUT
    @Path("/public/{deviceNumber}")
    public Response enrollDevice(@PathParam("deviceNumber") String deviceNumber,
                                 Device payload) {
        log.debug("#enrollDevice: deviceNumber = {}", deviceNumber);
        try {
            Device device = enrollmentService.enrollDevice(deviceNumber, null);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }

            if (payload != null) {
                if (payload.getInfo() != null) {
                    unsecureDAO.updateDeviceInfo(
                            device.getId(),
                            payload.getInfo(),
                            payload.getImeiUpdateTs(),
                            payload.getPublicIp()
                    );
                }
                if (payload.getCustom1() != null || payload.getCustom2() != null || payload.getCustom3() != null) {
                    unsecureDAO.updateDeviceCustomProperties(device.getId(), payload);
                }
            }

            // Re-fetch to get the latest state after updates
            device = unsecureDAO.getDeviceByNumber(deviceNumber);
            if (device == null) {
                device = unsecureDAO.getDeviceByOldNumber(deviceNumber);
            }

            return Response.OK(device);
        } catch (Exception e) {
            log.error("Unexpected error during device enrollment for: {}", deviceNumber, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Creates a new device record (admin, authenticated).</p>
     */
    @POST
    @Path("/private")
    public Response insertDevice(Device device) {
        log.debug("#insertDevice: device = {}", device.getNumber());
        try {
            deviceDAO.insertDevice(device);
            return Response.OK(device);
        } catch (Exception e) {
            log.error("Unexpected error when inserting device: {}", device, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Updates an existing device record (admin, authenticated).</p>
     */
    @PUT
    @Path("/private")
    public Response updateDevice(Device device) {
        log.debug("#updateDevice: deviceId = {}", device.getId());
        try {
            deviceDAO.updateDevice(device);
            return Response.OK(device);
        } catch (Exception e) {
            log.error("Unexpected error when updating device: {}", device.getId(), e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Deletes a device by ID (admin, authenticated).</p>
     */
    @DELETE
    @Path("/private/{id}")
    public Response removeDevice(@PathParam("id") Integer id) {
        log.debug("#removeDevice: id = {}", id);
        try {
            deviceDAO.removeDeviceById(id);
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when removing device: {}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Gets a device by ID (admin, authenticated).</p>
     */
    @GET
    @Path("/private/{id}")
    public Response getDeviceById(@PathParam("id") Integer id) {
        log.debug("#getDeviceById: id = {}", id);
        try {
            Device device = deviceDAO.getDeviceById(id);
            if (device == null) {
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            return Response.OK(device);
        } catch (Exception e) {
            log.error("Unexpected error when getting device: {}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Searches devices with pagination (admin, authenticated).</p>
     */
    @POST
    @Path("/private/search")
    public Response searchDevices(DeviceSearchRequest request) {
        log.debug("#searchDevices: request = {}", request);
        try {
            PaginatedData<Device> result = deviceDAO.getAllDevices(request);
            return Response.OK(result);
        } catch (Exception e) {
            log.error("Unexpected error when searching devices", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
