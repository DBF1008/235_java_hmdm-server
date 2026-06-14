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
import com.hmdm.persistence.ConfigurationDAO;
import com.hmdm.persistence.domain.Configuration;
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
import java.util.List;

/**
 * <p>A REST resource for managing configurations.</p>
 */
@Singleton
@Path("/rest/configurations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConfigurationResource {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationResource.class);

    private final ConfigurationDAO configurationDAO;
    private final DeviceEnrollmentService enrollmentService;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public ConfigurationResource() {
        this.configurationDAO = null;
        this.enrollmentService = null;
    }

    @Inject
    public ConfigurationResource(ConfigurationDAO configurationDAO,
                                 DeviceEnrollmentService enrollmentService) {
        this.configurationDAO = configurationDAO;
        this.enrollmentService = enrollmentService;
    }

    // =================================================================================================================
    /**
     * <p>Lists all configurations for the current user's customer (admin, authenticated).</p>
     */
    @GET
    @Path("/private")
    public Response getAllConfigurations() {
        log.debug("#getAllConfigurations");
        try {
            List<Configuration> configs = configurationDAO.getAllConfigurations();
            return Response.OK(configs);
        } catch (Exception e) {
            log.error("Unexpected error when listing configurations", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Gets a full configuration by ID including applications, files, and settings (admin, authenticated).</p>
     */
    @GET
    @Path("/private/{id}")
    public Response getConfigurationById(@PathParam("id") Integer id) {
        log.debug("#getConfigurationById: id = {}", id);
        try {
            Configuration config = configurationDAO.getConfigurationByIdFull(id);
            if (config == null) {
                return Response.OBJECT_NOT_FOUND_ERROR();
            }
            return Response.OK(config);
        } catch (Exception e) {
            log.error("Unexpected error when getting configuration: {}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Creates a new configuration (admin, authenticated).</p>
     */
    @POST
    @Path("/private")
    public Response insertConfiguration(Configuration config) {
        log.debug("#insertConfiguration: name = {}", config.getName());
        try {
            configurationDAO.insertConfiguration(config);
            return Response.OK(config);
        } catch (Exception e) {
            log.error("Unexpected error when inserting configuration: {}", config.getName(), e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Updates an existing configuration (admin, authenticated). This triggers push notification
     * to all devices using this configuration and status recalculation.</p>
     */
    @PUT
    @Path("/private")
    public Response updateConfiguration(Configuration config) {
        log.debug("#updateConfiguration: id = {}", config.getId());
        try {
            // updateConfiguration fires ConfigurationUpdatedEvent internally for status recalculation
            configurationDAO.updateConfiguration(config);

            // Send push notifications to all devices with this configuration
            enrollmentService.onConfigurationChanged(config.getId());

            return Response.OK(config);
        } catch (Exception e) {
            log.error("Unexpected error when updating configuration: {}", config.getId(), e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    /**
     * <p>Deletes a configuration by ID (admin, authenticated).</p>
     */
    @DELETE
    @Path("/private/{id}")
    public Response removeConfiguration(@PathParam("id") Integer id) {
        log.debug("#removeConfiguration: id = {}", id);
        try {
            configurationDAO.removeConfigurationById(id);
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when removing configuration: {}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }
}
