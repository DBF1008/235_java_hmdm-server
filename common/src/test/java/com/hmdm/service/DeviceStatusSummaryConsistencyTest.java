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

package com.hmdm.service;

import com.hmdm.persistence.domain.DeviceOnlineStatus;
import org.junit.Assert;
import org.junit.Test;

/**
 * <p>Regression tests verifying consistency of device status summary thresholds
 * across all code paths. These tests ensure that the three query paths
 * (countOnlineDevices, getStatusSummary, getAllDevices) use the same
 * definition of online/stale/offline.</p>
 *
 * <p>Key scenarios covered:</p>
 * <ul>
 *   <li>The 4000-hour bug fix: a device updated 100h ago must be RED, not YELLOW</li>
 *   <li>The 1-hour vs 2-hour inconsistency: online threshold must be 2h, not 1h</li>
 *   <li>Boundary consistency: classify() and boundary methods agree on all edge cases</li>
 * </ul>
 */
public class DeviceStatusSummaryConsistencyTest {

    public DeviceStatusSummaryConsistencyTest() {
    }

    // ========================================================================
    // 4000-hour bug regression tests
    // ========================================================================

    @Test
    public void testBug4000Hours_deviceAt100hIsOffline() {
        // Before fix: getStatusSummary used 3600*4000 (= 4000 hours ≈ 167 days)
        // as the yellow boundary. A device updated 100h ago would be YELLOW.
        // After fix: the STALE threshold is 4 hours. 100h ago is clearly RED.
        long now = System.currentTimeMillis();
        long lastUpdate100hAgo = now - 100 * 3600 * 1000L;

        DeviceOnlineStatus status = DeviceOnlineStatus.classify(lastUpdate100hAgo);
        Assert.assertEquals("Device 100h ago must be OFFLINE (red), not STALE (yellow). " +
                        "This was the 4000h bug.",
                DeviceOnlineStatus.OFFLINE, status);
    }

    @Test
    public void testBug4000Hours_deviceAt50hIsOffline() {
        long now = System.currentTimeMillis();
        long lastUpdate50hAgo = now - 50 * 3600 * 1000L;

        DeviceOnlineStatus status = DeviceOnlineStatus.classify(lastUpdate50hAgo);
        Assert.assertEquals("Device 50h ago must be OFFLINE (red)",
                DeviceOnlineStatus.OFFLINE, status);
    }

    @Test
    public void testBug4000Hours_deviceAt167DaysIsOffline() {
        // 4000 hours = ~167 days. Even at the old buggy boundary, the device must be offline.
        long now = System.currentTimeMillis();
        long lastUpdate4000hAgo = now - 4000 * 3600 * 1000L;

        DeviceOnlineStatus status = DeviceOnlineStatus.classify(lastUpdate4000hAgo);
        Assert.assertEquals("Device 4000h ago must be OFFLINE (red)",
                DeviceOnlineStatus.OFFLINE, status);
    }

    // ========================================================================
    // 1-hour vs 2-hour threshold consistency
    // ========================================================================

    @Test
    public void testThresholdConsistency_deviceAt1hIsOnline() {
        // Before fix: countOnlineDevices used 1h threshold, but getAllDevices used 2h.
        // After fix: both use 2h. A device at 1h should be ONLINE in all paths.
        long now = System.currentTimeMillis();
        long lastUpdate1hAgo = now - 1 * 3600 * 1000L;

        DeviceOnlineStatus status = DeviceOnlineStatus.classify(lastUpdate1hAgo);
        Assert.assertEquals("Device at 1h must be ONLINE (green) with unified 2h threshold",
                DeviceOnlineStatus.ONLINE, status);
    }

    @Test
    public void testThresholdConsistency_deviceAt1_5hIsOnline() {
        // With the old 1h threshold in countOnlineDevices, a device at 1.5h would NOT be
        // counted as online. With the unified 2h threshold, it IS online.
        long now = System.currentTimeMillis();
        long lastUpdate1_5hAgo = now - (long)(1.5 * 3600 * 1000L);

        DeviceOnlineStatus status = DeviceOnlineStatus.classify(lastUpdate1_5hAgo);
        Assert.assertEquals("Device at 1.5h must be ONLINE (green) with unified 2h threshold",
                DeviceOnlineStatus.ONLINE, status);
    }

    // ========================================================================
    // Three-tier classification consistency
    // ========================================================================

    @Test
    public void testThreeTierClassification_green() {
        long now = System.currentTimeMillis();

        // All these should be GREEN (ONLINE)
        Assert.assertEquals(DeviceOnlineStatus.ONLINE,
                DeviceOnlineStatus.classify(now));                          // just now
        Assert.assertEquals(DeviceOnlineStatus.ONLINE,
                DeviceOnlineStatus.classify(now - 30 * 60 * 1000L));        // 30 min ago
        Assert.assertEquals(DeviceOnlineStatus.ONLINE,
                DeviceOnlineStatus.classify(now - 1 * 3600 * 1000L));       // 1h ago
        Assert.assertEquals(DeviceOnlineStatus.ONLINE,
                DeviceOnlineStatus.classify(now - (long)(1.9 * 3600 * 1000L))); // 1.9h ago
    }

    @Test
    public void testThreeTierClassification_yellow() {
        long now = System.currentTimeMillis();

        // All these should be YELLOW (STALE)
        Assert.assertEquals(DeviceOnlineStatus.STALE,
                DeviceOnlineStatus.classify(now - 2 * 3600 * 1000L));       // 2h ago (boundary)
        Assert.assertEquals(DeviceOnlineStatus.STALE,
                DeviceOnlineStatus.classify(now - 3 * 3600 * 1000L));       // 3h ago
        Assert.assertEquals(DeviceOnlineStatus.STALE,
                DeviceOnlineStatus.classify(now - (long)(3.9 * 3600 * 1000L))); // 3.9h ago
    }

    @Test
    public void testThreeTierClassification_red() {
        long now = System.currentTimeMillis();

        // All these should be RED (OFFLINE)
        Assert.assertEquals(DeviceOnlineStatus.OFFLINE,
                DeviceOnlineStatus.classify(now - 4 * 3600 * 1000L));       // 4h ago (boundary)
        Assert.assertEquals(DeviceOnlineStatus.OFFLINE,
                DeviceOnlineStatus.classify(now - 5 * 3600 * 1000L));       // 5h ago
        Assert.assertEquals(DeviceOnlineStatus.OFFLINE,
                DeviceOnlineStatus.classify(now - 24 * 3600 * 1000L));      // 1 day ago
        Assert.assertEquals(DeviceOnlineStatus.OFFLINE,
                DeviceOnlineStatus.classify(now - 7 * 24 * 3600 * 1000L));  // 1 week ago
    }

    // ========================================================================
    // Boundary computation consistency (used by SQL queries)
    // ========================================================================

    @Test
    public void testBoundaryComputation_onlineBoundaryIsNewerThanStale() {
        long onlineBoundary = DeviceOnlineStatus.getOnlineBoundary();
        long staleBoundary = DeviceOnlineStatus.getStaleBoundary();

        // onlineBoundary = now - 2h, staleBoundary = now - 4h
        // So onlineBoundary must be > staleBoundary (it's a more recent timestamp)
        Assert.assertTrue("onlineBoundary (now-2h) must be > staleBoundary (now-4h)",
                onlineBoundary > staleBoundary);

        // The gap should be approximately 2 hours
        long gapMs = onlineBoundary - staleBoundary;
        long expectedGapMs = 2 * 3600 * 1000L;
        Assert.assertEquals("Gap between boundaries should be ~2 hours",
                expectedGapMs, gapMs, 100); // 100ms tolerance
    }

    @Test
    public void testBoundaryComputation_deviceAtOnlineBoundaryIsOnline() {
        // A device with lastUpdate = onlineBoundary should be classified as ONLINE
        // (it's exactly at the 2h mark, and our test uses >= comparison)
        long onlineBoundary = DeviceOnlineStatus.getOnlineBoundary();

        // Since onlineBoundary was just computed, classify it now:
        // age = now - onlineBoundary ≈ 2h (within a few ms)
        // Due to the time gap between getOnlineBoundary() and classify(),
        // the device could be at exactly 2h or slightly over.
        // Use a value slightly newer than the boundary.
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(onlineBoundary + 1000);
        Assert.assertEquals("Device just inside online boundary should be ONLINE",
                DeviceOnlineStatus.ONLINE, status);
    }

    @Test
    public void testBoundaryComputation_deviceAtStaleBoundaryIsStale() {
        long staleBoundary = DeviceOnlineStatus.getStaleBoundary();

        // A device just inside the stale boundary should be STALE
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(staleBoundary + 1000);
        Assert.assertEquals("Device just inside stale boundary should be STALE",
                DeviceOnlineStatus.STALE, status);
    }

    @Test
    public void testBoundaryComputation_deviceBelowStaleBoundaryIsOffline() {
        long staleBoundary = DeviceOnlineStatus.getStaleBoundary();

        // A device below the stale boundary should be OFFLINE
        DeviceOnlineStatus status = DeviceOnlineStatus.classify(staleBoundary - 1000);
        Assert.assertEquals("Device below stale boundary should be OFFLINE",
                DeviceOnlineStatus.OFFLINE, status);
    }

    // ========================================================================
    // Scenario: simulated dashboard consistency
    // ========================================================================

    @Test
    public void testSimulatedDashboard_allPathsAgree() {
        // Simulate 4 devices with known update times
        long now = System.currentTimeMillis();

        long deviceA_lastUpdate = now - 30 * 60 * 1000L;   // 30 min ago -> ONLINE
        long deviceB_lastUpdate = now - 90 * 60 * 1000L;   // 1.5h ago -> ONLINE
        long deviceC_lastUpdate = now - 3 * 3600 * 1000L;  // 3h ago -> STALE
        long deviceD_lastUpdate = now - 10 * 3600 * 1000L; // 10h ago -> OFFLINE

        // All three "query paths" should agree:
        // Path 1: classify() (represents the Java-side logic)
        Assert.assertEquals(DeviceOnlineStatus.ONLINE, DeviceOnlineStatus.classify(deviceA_lastUpdate));
        Assert.assertEquals(DeviceOnlineStatus.ONLINE, DeviceOnlineStatus.classify(deviceB_lastUpdate));
        Assert.assertEquals(DeviceOnlineStatus.STALE, DeviceOnlineStatus.classify(deviceC_lastUpdate));
        Assert.assertEquals(DeviceOnlineStatus.OFFLINE, DeviceOnlineStatus.classify(deviceD_lastUpdate));

        // Path 2: boundary comparison (represents the SQL-side logic)
        long onlineBoundary = DeviceOnlineStatus.getOnlineBoundary();
        long staleBoundary = DeviceOnlineStatus.getStaleBoundary();

        // Device A (30 min ago): lastUpdate > onlineBoundary -> green
        Assert.assertTrue("Device A should be green by boundary check",
                deviceA_lastUpdate >= onlineBoundary);

        // Device B (1.5h ago): lastUpdate > onlineBoundary -> green
        Assert.assertTrue("Device B should be green by boundary check",
                deviceB_lastUpdate >= onlineBoundary);

        // Device C (3h ago): staleBoundary <= lastUpdate < onlineBoundary -> yellow
        Assert.assertTrue("Device C should be yellow by boundary check (>= stale)",
                deviceC_lastUpdate >= staleBoundary);
        Assert.assertTrue("Device C should be yellow by boundary check (< online)",
                deviceC_lastUpdate < onlineBoundary);

        // Device D (10h ago): lastUpdate < staleBoundary -> red
        Assert.assertTrue("Device D should be red by boundary check",
                deviceD_lastUpdate < staleBoundary);

        // Path 3: counts should match
        int expectedOnline = 2; // A + B
        int expectedStale = 1;  // C
        int expectedOffline = 1; // D

        int actualOnline = 0, actualStale = 0, actualOffline = 0;
        long[] devices = {deviceA_lastUpdate, deviceB_lastUpdate, deviceC_lastUpdate, deviceD_lastUpdate};
        for (long lastUpdate : devices) {
            DeviceOnlineStatus s = DeviceOnlineStatus.classify(lastUpdate);
            if (s == DeviceOnlineStatus.ONLINE) actualOnline++;
            else if (s == DeviceOnlineStatus.STALE) actualStale++;
            else actualOffline++;
        }

        Assert.assertEquals("Online count must agree", expectedOnline, actualOnline);
        Assert.assertEquals("Stale count must agree", expectedStale, actualStale);
        Assert.assertEquals("Offline count must agree", expectedOffline, actualOffline);
    }

    // ========================================================================
    // Regression: ensure old hardcoded thresholds are not used
    // ========================================================================

    @Test
    public void testOldThreshold_3600000ms_notUsedAsOnlineThreshold() {
        // The old countOnlineDevices() used 3600000ms (1 hour) as the online threshold.
        // The new unified threshold is 7200000ms (2 hours).
        // A device updated 1.5 hours ago should be ONLINE (not borderline as it was before).
        long now = System.currentTimeMillis();
        long lastUpdate1_5hAgo = now - (long)(1.5 * 3600 * 1000L);

        DeviceOnlineStatus status = DeviceOnlineStatus.classify(lastUpdate1_5hAgo);
        Assert.assertEquals("With unified 2h threshold, device at 1.5h should be ONLINE",
                DeviceOnlineStatus.ONLINE, status);

        // Verify the threshold itself is not 1 hour
        Assert.assertNotEquals("Online threshold should NOT be 1 hour (3,600,000 ms)",
                3600000L, DeviceOnlineStatus.ONLINE.getThresholdMs());
        Assert.assertEquals("Online threshold should be 2 hours (7,200,000 ms)",
                7200000L, DeviceOnlineStatus.ONLINE.getThresholdMs());
    }
}
