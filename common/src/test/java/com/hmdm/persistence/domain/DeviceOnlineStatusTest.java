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

import org.junit.Assert;
import org.junit.Test;

/**
 * <p>Tests for {@link DeviceOnlineStatus} enum, verifying that the unified thresholds
 * are correctly defined and that boundary calculations are consistent.</p>
 */
public class DeviceOnlineStatusTest {

    public DeviceOnlineStatusTest() {
    }

    @Test
    public void testOnlineThresholdIsTwoHours() {
        Assert.assertEquals("ONLINE threshold should be 2 hours (7,200,000 ms)",
                2 * 3600 * 1000L, DeviceOnlineStatus.ONLINE.getThresholdMs());
    }

    @Test
    public void testStaleThresholdIsFourHours() {
        Assert.assertEquals("STALE threshold should be 4 hours (14,400,000 ms)",
                4 * 3600 * 1000L, DeviceOnlineStatus.STALE.getThresholdMs());
    }

    @Test
    public void testOfflineThresholdIsMaxValue() {
        Assert.assertEquals("OFFLINE threshold should be Long.MAX_VALUE",
                Long.MAX_VALUE, DeviceOnlineStatus.OFFLINE.getThresholdMs());
    }

    @Test
    public void testStatusCodes() {
        Assert.assertEquals("ONLINE status code should be 'green'",
                "green", DeviceOnlineStatus.ONLINE.getStatusCode());
        Assert.assertEquals("STALE status code should be 'yellow'",
                "yellow", DeviceOnlineStatus.STALE.getStatusCode());
        Assert.assertEquals("OFFLINE status code should be 'red'",
                "red", DeviceOnlineStatus.OFFLINE.getStatusCode());
    }

    @Test
    public void testBoundariesAreConsistent() {
        long before = System.currentTimeMillis();
        long onlineBoundary = DeviceOnlineStatus.getOnlineBoundary();
        long staleBoundary = DeviceOnlineStatus.getStaleBoundary();
        long after = System.currentTimeMillis();

        // onlineBoundary should be approximately now - 2 hours
        long expectedOnlineMin = before - 2 * 3600 * 1000L;
        long expectedOnlineMax = after - 2 * 3600 * 1000L;
        Assert.assertTrue("onlineBoundary should be >= now - 2h",
                onlineBoundary >= expectedOnlineMin);
        Assert.assertTrue("onlineBoundary should be <= now - 2h",
                onlineBoundary <= expectedOnlineMax);

        // staleBoundary should be approximately now - 4 hours
        long expectedStaleMin = before - 4 * 3600 * 1000L;
        long expectedStaleMax = after - 4 * 3600 * 1000L;
        Assert.assertTrue("staleBoundary should be >= now - 4h",
                staleBoundary >= expectedStaleMin);
        Assert.assertTrue("staleBoundary should be <= now - 4h",
                staleBoundary <= expectedStaleMax);

        // onlineBoundary must be greater than staleBoundary (more recent timestamp)
        Assert.assertTrue("onlineBoundary must be > staleBoundary",
                onlineBoundary > staleBoundary);
    }

    @Test
    public void testClassifyOnlineDevice() {
        long now = System.currentTimeMillis();
        // Device updated 1 hour ago -> ONLINE
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(now - 1 * 3600 * 1000L);
        Assert.assertEquals("Device updated 1h ago should be ONLINE",
                DeviceOnlineStatus.ONLINE, status);
    }

    @Test
    public void testClassifyOnlineDeviceJustUpdated() {
        long now = System.currentTimeMillis();
        // Device updated just now -> ONLINE
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(now);
        Assert.assertEquals("Device updated now should be ONLINE",
                DeviceOnlineStatus.ONLINE, status);
    }

    @Test
    public void testClassifyStaleDevice() {
        long now = System.currentTimeMillis();
        // Device updated 3 hours ago -> STALE
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(now - 3 * 3600 * 1000L);
        Assert.assertEquals("Device updated 3h ago should be STALE",
                DeviceOnlineStatus.STALE, status);
    }

    @Test
    public void testClassifyOfflineDevice() {
        long now = System.currentTimeMillis();
        // Device updated 5 hours ago -> OFFLINE
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(now - 5 * 3600 * 1000L);
        Assert.assertEquals("Device updated 5h ago should be OFFLINE",
                DeviceOnlineStatus.OFFLINE, status);
    }

    @Test
    public void testClassifyVeryOldDevice() {
        // Device updated 100 hours ago -> OFFLINE (this was the 4000h bug scenario)
        long now = System.currentTimeMillis();
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(now - 100 * 3600 * 1000L);
        Assert.assertEquals("Device updated 100h ago should be OFFLINE",
                DeviceOnlineStatus.OFFLINE, status);
    }

    @Test
    public void testClassifyBoundaryEdge_OnlineToStale() {
        long now = System.currentTimeMillis();
        // Device updated exactly 2 hours ago - should be STALE (>= 2h means not online)
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(now - 2 * 3600 * 1000L);
        Assert.assertEquals("Device updated exactly 2h ago should be STALE",
                DeviceOnlineStatus.STALE, status);
    }

    @Test
    public void testClassifyBoundaryEdge_StaleToOffline() {
        long now = System.currentTimeMillis();
        // Device updated exactly 4 hours ago - should be OFFLINE (>= 4h means not stale)
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(now - 4 * 3600 * 1000L);
        Assert.assertEquals("Device updated exactly 4h ago should be OFFLINE",
                DeviceOnlineStatus.OFFLINE, status);
    }

    @Test
    public void testFromStatusCode() {
        Assert.assertEquals("fromStatusCode('green') should be ONLINE",
                DeviceOnlineStatus.ONLINE, DeviceOnlineStatus.fromStatusCode("green"));
        Assert.assertEquals("fromStatusCode('yellow') should be STALE",
                DeviceOnlineStatus.STALE, DeviceOnlineStatus.fromStatusCode("yellow"));
        Assert.assertEquals("fromStatusCode('red') should be OFFLINE",
                DeviceOnlineStatus.OFFLINE, DeviceOnlineStatus.fromStatusCode("red"));
        Assert.assertEquals("fromStatusCode(null) should be OFFLINE",
                DeviceOnlineStatus.OFFLINE, DeviceOnlineStatus.fromStatusCode(null));
        Assert.assertEquals("fromStatusCode('unknown') should be OFFLINE",
                DeviceOnlineStatus.OFFLINE, DeviceOnlineStatus.fromStatusCode("unknown"));
    }

    @Test
    public void testThresholdsAreNotOneHour() {
        // Verify the old buggy 1-hour threshold is NOT used
        Assert.assertNotEquals("ONLINE threshold must NOT be 1 hour (old bug)",
                3600 * 1000L, DeviceOnlineStatus.ONLINE.getThresholdMs());
    }

    @Test
    public void testThresholdsAreNot4000Hours() {
        // Verify the 4000-hour bug is not present
        Assert.assertNotEquals("STALE threshold must NOT be 4000 hours (old bug)",
                4000 * 3600 * 1000L, DeviceOnlineStatus.STALE.getThresholdMs());
    }
}
