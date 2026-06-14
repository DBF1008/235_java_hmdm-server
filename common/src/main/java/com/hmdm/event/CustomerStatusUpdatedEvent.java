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

package com.hmdm.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * <p>An event fired when customer-level device status aggregation has been completed
 * by the {@link com.hmdm.task.CustomerStatusTask}.</p>
 */
@Data
@AllArgsConstructor
@ToString
public class CustomerStatusUpdatedEvent implements Event, Serializable {

    private static final long serialVersionUID = 729145382016482000L;

    /**
     * <p>The ID of the customer whose status was updated.</p>
     */
    private final int customerId;

    /**
     * <p>The count of online (green) devices for this customer.</p>
     */
    private final int onlineDevices;

    /**
     * <p>The count of stale (yellow) devices for this customer.</p>
     */
    private final int staleDevices;

    /**
     * <p>The count of offline (red) devices for this customer.</p>
     */
    private final int offlineDevices;

    /**
     * <p>The total count of devices for this customer.</p>
     */
    private final int totalDevices;

    /**
     * <p>The timestamp (epoch millis) when the snapshot was taken.</p>
     */
    private final long snapshotTime;

    /**
     * <p>Gets the type of the event.</p>
     *
     * @return a type of the event.
     */
    @Override
    public EventType getType() {
        return EventType.CUSTOMER_STATUS_UPDATED;
    }
}
