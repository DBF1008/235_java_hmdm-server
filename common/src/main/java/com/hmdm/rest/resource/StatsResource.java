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
import com.hmdm.rest.json.ChartItem;
import com.hmdm.rest.json.Response;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>REST resource exposing unified device statistics endpoints.
 * All statistics use the canonical thresholds defined in {@link DeviceOnlineStatus}
 * to ensure consistency with device list views and scheduled aggregation tasks.</p>
 */
@Api(tags = {"Statistics"})
@Singleton
@Path("/rest/stats")
public class StatsResource {

    private static final Logger log = LoggerFactory.getLogger(StatsResource.class);

    private DeviceDAO deviceDAO;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public StatsResource() {
    }

    /**
     * <p>Constructs new <code>StatsResource</code> instance.</p>
     */
    @Inject
    public StatsResource(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    /**
     * <p>Gets a unified status summary with green/yellow/red device counts
     * using the canonical {@link DeviceOnlineStatus} thresholds.</p>
     *
     * @return a list of {@link ChartItem} with stringAttr = "green"/"yellow"/"red" and number = count
     */
    @ApiOperation(
            value = "Get device status summary",
            notes = "Returns the count of devices in each status tier (green/yellow/red) " +
                    "using unified thresholds consistent with device list and scheduled tasks."
    )
    @Path("/summary")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatusSummary() {
        try {
            List<ChartItem> summary = this.deviceDAO.getStatusSummary();
            if (summary == null) {
                return Response.PERMISSION_DENIED();
            }
            return Response.OK(summary);
        } catch (Exception e) {
            log.error("Failed to get status summary", e);
            return Response.INTERNAL_ERROR();
        }
    }

    /**
     * <p>Gets the current count of online devices (those with lastUpdate within the ONLINE threshold).</p>
     *
     * @return a map with "count" key containing the online device count
     */
    @ApiOperation(
            value = "Get online device count",
            notes = "Returns the count of devices currently considered online " +
                    "(lastUpdate within the unified ONLINE threshold)."
    )
    @Path("/online-count")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOnlineDeviceCount() {
        try {
            long count = this.deviceDAO.getOnlineDevicesCount();
            Map<String, Object> result = new HashMap<>();
            result.put("count", count);
            result.put("thresholdMs", DeviceOnlineStatus.ONLINE.getThresholdMs());
            return Response.OK(result);
        } catch (Exception e) {
            log.error("Failed to get online device count", e);
            return Response.INTERNAL_ERROR();
        }
    }

    /**
     * <p>Gets the total count of all devices.</p>
     *
     * @return a map with "count" key containing the total device count
     */
    @ApiOperation(
            value = "Get total device count",
            notes = "Returns the total number of registered devices."
    )
    @Path("/total-count")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTotalDeviceCount() {
        try {
            long count = this.deviceDAO.getTotalDevicesCount();
            Map<String, Object> result = new HashMap<>();
            result.put("count", count);
            return Response.OK(result);
        } catch (Exception e) {
            log.error("Failed to get total device count", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
