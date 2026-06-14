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

package com.hmdm.notification.service;

import com.hmdm.notification.PushService;
import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationSetting;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.ConfigurationFile;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.json.DeviceCreateOptions;
import com.hmdm.service.DeviceStatusService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>Regression tests for {@link DeviceSyncService}, pinning the state boundaries that were previously
 * scattered/decoupled across the device on-boarding and sync chain.</p>
 *
 * <p>The collaborators ({@link UnsecureDAO}, {@link DeviceStatusService}, {@link PushService},
 * {@link DeviceDAO}) are mocked, so these tests exercise the orchestration / coupling without a
 * database. They cover the three scenarios the refactor must keep correct: first registration,
 * repeated sync, and configuration change.</p>
 */
public class DeviceSyncServiceTest {

    @Mock
    private UnsecureDAO unsecureDAO;
    @Mock
    private DeviceStatusService deviceStatusService;
    @Mock
    private PushService pushService;
    @Mock
    private DeviceDAO deviceDAO;

    private AutoCloseable mocks;
    private DeviceSyncService service;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new DeviceSyncService(unsecureDAO, deviceStatusService, pushService, deviceDAO);
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    /**
     * First registration: an unknown device is created on demand, and its very first sync assembles a
     * <em>complete</em> configuration (configuration + applications + files + per-device settings) from
     * the single assembly point — guarding against the "registered but first sync has no complete
     * config" failure mode.
     */
    @Test
    public void firstRegistration_createsDevice_andFirstSyncBuildsCompleteConfiguration() {
        final String number = "ELM-1001";
        when(unsecureDAO.getDeviceByNumber(number)).thenReturn(null);
        final Device newDevice = device(10, number, 5, 100, 0L);
        when(unsecureDAO.createNewDeviceOnDemand(number)).thenReturn(newDevice);

        final DeviceEnrollment enrollment = service.enrollDevice(number, null);

        assertTrue("a previously unknown device must be created", enrollment.isCreated());
        assertTrue(enrollment.isPresent());
        assertSame(newDevice, enrollment.getDevice());

        // First sync must gather all four parts of the configuration in one place.
        final Configuration configuration = org.mockito.Mockito.mock(Configuration.class);
        when(unsecureDAO.getConfigurationByIdWithAppSettings(5)).thenReturn(configuration);
        when(unsecureDAO.getPlainConfigurationApplications(100, 5))
                .thenReturn(Collections.singletonList(org.mockito.Mockito.mock(Application.class)));
        when(unsecureDAO.getConfigurationFiles(newDevice))
                .thenReturn(Collections.singletonList(org.mockito.Mockito.mock(ConfigurationFile.class)));
        when(unsecureDAO.getDeviceAppSettings(10))
                .thenReturn(Collections.singletonList(org.mockito.Mockito.mock(ApplicationSetting.class)));

        final DeviceSyncConfiguration syncConfig = service.getSyncConfiguration(newDevice);

        assertSame(configuration, syncConfig.getConfiguration());
        assertEquals(1, syncConfig.getApplications().size());
        assertEquals(1, syncConfig.getFiles().size());
        assertEquals(1, syncConfig.getApplicationSettings().size());
        verify(unsecureDAO).getConfigurationByIdWithAppSettings(5);
        verify(unsecureDAO).getPlainConfigurationApplications(100, 5);
        verify(unsecureDAO).getConfigurationFiles(newDevice);
        verify(unsecureDAO).getDeviceAppSettings(10);
    }

    /**
     * A device that was registered without a configuration must not silently sync an empty payload —
     * the assembly boundary fails fast instead.
     */
    @Test(expected = IllegalStateException.class)
    public void getSyncConfiguration_failsFast_whenDeviceHasNoConfiguration() {
        final Device device = device(11, "ELM-NOCONF", null, 100, 0L);
        service.getSyncConfiguration(device);
    }

    /**
     * Repeated sync: an already-known device is never recreated, and each sync couples persisting the
     * reported info with recalculating the status, so the persisted "sync view" cannot drift from what
     * the device reported.
     */
    @Test
    public void repeatedSync_doesNotRecreateDevice_andCouplesInfoWithStatus() {
        final String number = "ELM-2002";
        final Device existing = device(20, number, 5, 100, 1_700_000_000_000L);
        when(unsecureDAO.getDeviceByNumber(number)).thenReturn(existing);

        final DeviceEnrollment enrollment = service.enrollDevice(number, null);

        assertFalse("an existing device must not be recreated", enrollment.isCreated());
        assertSame(existing, enrollment.getDevice());
        verify(unsecureDAO, never()).createNewDeviceOnDemand(anyString());
        verify(unsecureDAO, never()).createNewDeviceOnDemand(anyString(), any(DeviceCreateOptions.class));

        final String info = "{\"applications\":[]}";
        final boolean firstSync = service.completeSync(existing, info, 0L, "203.0.113.7");

        assertFalse("an already-synced device reports firstSync=false", firstSync);
        verify(unsecureDAO).updateDeviceInfo(20, info, 0L, "203.0.113.7");
        verify(deviceStatusService).recalcDeviceStatuses(20);

        // A second sync stays consistent: info and status advance together every time.
        service.completeSync(existing, info, 0L, "203.0.113.7");
        verify(unsecureDAO, times(2)).updateDeviceInfo(20, info, 0L, "203.0.113.7");
        verify(deviceStatusService, times(2)).recalcDeviceStatuses(20);
    }

    /**
     * Configuration change: recalculating the sync view and pushing the affected devices happen
     * together over one consistent device set — guarding against the "config changed but push and sync
     * view out of sync" failure mode.
     */
    @Test
    public void configurationChange_recalculatesSyncViewAndPushes_together() {
        final Integer configurationId = 5;
        final Device d1 = device(31, "ELM-3001", 5, 100, 1L);
        final Device d2 = device(32, "ELM-3002", 5, 100, 1L);
        when(deviceDAO.getDeviceIdsByConfigurationId(5)).thenReturn(Arrays.asList(d1, d2));

        service.applyConfigurationChange(configurationId);

        // Sync view recalculated for every device on the configuration ...
        verify(deviceStatusService).recalcDeviceStatuses(31);
        verify(deviceStatusService).recalcDeviceStatuses(32);
        // ... and the push fan-out is triggered for the same configuration.
        verify(pushService).notifyDevicesOnUpdate(5);
    }

    private static Device device(Integer id, String number, Integer configurationId, int customerId, Long lastUpdate) {
        final Device device = new Device();
        device.setId(id);
        device.setNumber(number);
        device.setConfigurationId(configurationId);
        device.setCustomerId(customerId);
        device.setLastUpdate(lastUpdate);
        return device;
    }
}
