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

package com.hmdm.persistence.domain;

/**
 * <p>Unified device online status tiers with canonical thresholds.
 * All statistics queries (device list, summary charts, global counts, scheduled tasks)
 * MUST use these thresholds to ensure consistency across the application.</p>
 *
 * <ul>
 *   <li>{@link #ONLINE} (green): device checked in within the last 2 hours</li>
 *   <li>{@link #STALE} (yellow): device checked in 2–4 hours ago</li>
 *   <li>{@link #OFFLINE} (red): device checked in more than 4 hours ago</li>
 * </ul>
 */
public enum DeviceOnlineStatus {

    ONLINE(2 * 3600 * 1000L, "green"),
    STALE(4 * 3600 * 1000L, "yellow"),
    OFFLINE(Long.MAX_VALUE, "red");

    /**
     * The maximum age (in milliseconds from current time) of {@code lastUpdate} for a device
     * to be classified in this tier. For {@code OFFLINE}, this is {@link Long#MAX_VALUE}.
     */
    private final long thresholdMs;

    /**
     * The status code string used in SQL queries and API responses.
     */
    private final String statusCode;

    DeviceOnlineStatus(long thresholdMs, String statusCode) {
        this.thresholdMs = thresholdMs;
        this.statusCode = statusCode;
    }

    /**
     * Gets the threshold in milliseconds for this status tier.
     *
     * @return threshold in ms
     */
    public long getThresholdMs() {
        return thresholdMs;
    }

    /**
     * Gets the status code string (e.g. "green", "yellow", "red").
     *
     * @return status code string
     */
    public String getStatusCode() {
        return statusCode;
    }

    /**
     * Computes the absolute epoch-millis boundary for the ONLINE tier.
     * A device with {@code lastUpdate >= getOnlineBoundary()} is considered ONLINE (green).
     *
     * @return epoch millis = now - 2 hours
     */
    public static long getOnlineBoundary() {
        return System.currentTimeMillis() - ONLINE.thresholdMs;
    }

    /**
     * Computes the absolute epoch-millis boundary for the STALE tier.
     * A device with {@code lastUpdate >= getStaleBoundary()} but {@code < getOnlineBoundary()}
     * is considered STALE (yellow). Below this boundary is OFFLINE (red).
     *
     * @return epoch millis = now - 4 hours
     */
    public static long getStaleBoundary() {
        return System.currentTimeMillis() - STALE.thresholdMs;
    }

    /**
     * Resolves a status code string to the corresponding enum value.
     *
     * @param statusCode the status code (e.g. "green")
     * @return the matching enum value, or {@link #OFFLINE} if not recognized
     */
    public static DeviceOnlineStatus fromStatusCode(String statusCode) {
        if (statusCode == null) {
            return OFFLINE;
        }
        for (DeviceOnlineStatus status : values()) {
            if (status.statusCode.equals(statusCode)) {
                return status;
            }
        }
        return OFFLINE;
    }

    /**
     * Classifies a device's online status based on its last update timestamp.
     *
     * @param lastUpdate the device's last update time in epoch millis
     * @return the computed {@link DeviceOnlineStatus}
     */
    public static DeviceOnlineStatus classify(long lastUpdate) {
        long now = System.currentTimeMillis();
        long age = now - lastUpdate;
        if (age < ONLINE.thresholdMs) {
            return ONLINE;
        } else if (age < STALE.thresholdMs) {
            return STALE;
        } else {
            return OFFLINE;
        }
    }
}
