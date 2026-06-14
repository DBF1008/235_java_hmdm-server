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

import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.domain.DeviceOnlineStatus;
import com.hmdm.persistence.domain.DeviceSummaryRequest;
import com.hmdm.persistence.domain.SummaryConfigItem;
import com.hmdm.rest.json.ChartItem;
import com.hmdm.rest.json.Response;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * <p>REST resource exposing device summary and dashboard endpoints.
 * All summary queries use the canonical thresholds defined in {@link DeviceOnlineStatus}
 * to ensure consistency across the management panel.</p>
 */
@Api(tags = {"Summary"})
@Singleton
@Path("/rest/summary")
public class SummaryResource {

    private static final Logger log = LoggerFactory.getLogger(SummaryResource.class);

    private DeviceDAO deviceDAO;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public SummaryResource() {
    }

    /**
     * <p>Constructs new <code>SummaryResource</code> instance.</p>
     */
    @Inject
    public SummaryResource(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    /**
     * <p>Gets the application installation status summary (SUCCESS/VERSION_MISMATCH/FAILURE).</p>
     *
     * @return a list of {@link ChartItem} with installation status breakdown
     */
    @ApiOperation(
            value = "Get installation summary",
            notes = "Returns the count of devices in each application installation status tier."
    )
    @Path("/install")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getInstallSummary() {
        try {
            List<ChartItem> summary = this.deviceDAO.getInstallSummary();
            if (summary == null) {
                return Response.PERMISSION_DENIED();
            }
            return Response.OK(summary);
        } catch (Exception e) {
            log.error("Failed to get install summary", e);
            return Response.INTERNAL_ERROR();
        }
    }

    /**
     * <p>Gets the device count grouped by configuration, optionally filtered by status criteria.
     * Uses the unified {@link DeviceOnlineStatus} thresholds for online time filtering.</p>
     *
     * @param condition a filter request for narrowing the summary scope
     * @return a list of {@link SummaryConfigItem} with per-configuration device counts
     */
    @ApiOperation(
            value = "Get summary by configuration",
            notes = "Returns device counts grouped by configuration, with optional status filters."
    )
    @Path("/by-config")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSummaryByConfig(DeviceSummaryRequest condition) {
        try {
            if (condition == null) {
                condition = new DeviceSummaryRequest();
            }
            List<SummaryConfigItem> result = this.deviceDAO.getSummaryByConfig(condition, null);
            if (result == null) {
                return Response.PERMISSION_DENIED();
            }
            return Response.OK(result);
        } catch (Exception e) {
            log.error("Failed to get summary by config", e);
            return Response.INTERNAL_ERROR();
        }
    }

    /**
     * <p>Gets the count of devices enrolled after the specified timestamp.</p>
     *
     * @param condition a filter request containing the enrollment time boundary
     * @return the count of recently enrolled devices
     */
    @ApiOperation(
            value = "Get enrolled device count",
            notes = "Returns the count of devices enrolled after a specified time."
    )
    @Path("/enrolled")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response countEnrolled(DeviceSummaryRequest condition) {
        try {
            long enrollTime = condition != null && condition.getMinEnrollTime() != null
                    ? condition.getMinEnrollTime() : 0L;
            Long count = this.deviceDAO.countEnrolled(enrollTime);
            return Response.OK(count);
        } catch (Exception e) {
            log.error("Failed to count enrolled devices", e);
            return Response.INTERNAL_ERROR();
        }
    }

    /**
     * <p>Gets monthly enrollment data for the past 12 months.</p>
     *
     * @return a list of {@link ChartItem} with monthly enrollment counts
     */
    @ApiOperation(
            value = "Get monthly enrollment chart",
            notes = "Returns device enrollment counts grouped by month for the past 12 months."
    )
    @Path("/enrolled-monthly")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDevicesEnrolledMonthly() {
        try {
            List<ChartItem> result = this.deviceDAO.getDevicesEnrolledMonthly();
            if (result == null) {
                return Response.PERMISSION_DENIED();
            }
            return Response.OK(result);
        } catch (Exception e) {
            log.error("Failed to get monthly enrollment data", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
