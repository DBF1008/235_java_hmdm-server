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

import com.hmdm.event.DeviceInfoUpdatedEvent;
import com.hmdm.event.EventService;
import com.hmdm.notification.PushService;
import com.hmdm.persistence.ConfigurationDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationSetting;
import com.hmdm.persistence.domain.ApplicationSettingType;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.ConfigurationFile;
import com.hmdm.persistence.domain.Device;
import com.hmdm.persistence.domain.Settings;
import com.hmdm.rest.json.Response;
import com.hmdm.rest.json.SyncRequest;
import com.hmdm.rest.json.SyncResponseHook;
import com.hmdm.rest.json.SyncResponseInt;
import com.hmdm.service.DeviceEnrollmentService;
import com.hmdm.service.DeviceStatusService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Chain-level regression tests that exercise the full enrollment → sync → config-change flow
 * through the REST resources using mocked dependencies.
 */
@RunWith(MockitoJUnitRunner.class)
public class SyncChainRegressionTest {

    @Mock
    private UnsecureDAO unsecureDAO;

    @Mock
    private ConfigurationDAO configurationDAO;

    @Mock
    private PushService pushService;

    @Mock
    private EventService eventService;

    @Mock
    private DeviceStatusService deviceStatusService;

    private DeviceEnrollmentService enrollmentService;
    private SyncResource syncResource;
    private ConfigurationResource configurationResource;

    @Before
    public void setUp() {
        enrollmentService = new DeviceEnrollmentService(
                unsecureDAO, configurationDAO, pushService, eventService, deviceStatusService
        );
        syncResource = new SyncResource(enrollmentService, unsecureDAO, eventService, null);
        configurationResource = new ConfigurationResource(configurationDAO, enrollmentService);
    }

    // =========================================================================
    // Test cases
    // =========================================================================

    @Test
    public void testFirstRegistration_fullConfigReturned() {
        // Setup: new device auto-created with configId=100
        Device newDevice = createTestDevice(1, "DEV001", 100, 1);
        when(unsecureDAO.getDeviceByNumber("DEV001")).thenReturn(null);
        when(unsecureDAO.getDeviceByOldNumber("DEV001")).thenReturn(null);
        when(unsecureDAO.createNewDeviceOnDemand("DEV001")).thenReturn(newDevice);

        // Setup: full configuration with 3 apps, 2 files, 1 setting
        Configuration config = createTestConfiguration(100);
        List<Application> apps = Arrays.asList(
                createTestApplication(1, "com.example.app1", "1.0"),
                createTestApplication(2, "com.example.app2", "2.0"),
                createTestApplication(3, "com.example.app3", "3.0")
        );
        List<ConfigurationFile> files = Arrays.asList(
                createTestConfigurationFile(1, "/sdcard/file1.txt"),
                createTestConfigurationFile(2, "/sdcard/file2.txt")
        );
        List<ApplicationSetting> appSettings = Collections.singletonList(
                createTestApplicationSetting(1, "com.example.app1", "setting1")
        );
        Settings settings = createTestSettings();

        when(unsecureDAO.getConfigurationByIdWithAppSettings(100)).thenReturn(config);
        when(unsecureDAO.getPlainConfigurationApplications(1, 100)).thenReturn(apps);
        when(unsecureDAO.getConfigurationFiles(newDevice)).thenReturn(files);
        when(unsecureDAO.getDeviceAppSettings(1)).thenReturn(appSettings);
        when(unsecureDAO.getSettings(1)).thenReturn(settings);
        when(configurationDAO.getBaseUrl()).thenReturn("http://localhost:8080");

        // Execute
        Response response = syncResource.syncConfiguration("DEV001", null);

        // Assert
        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertNotNull(response.getData());
        assertTrue(response.getData() instanceof SyncResponseInt);

        SyncResponseInt syncResponse = (SyncResponseInt) response.getData();
        assertEquals(3, syncResponse.getApplications().size());
        assertEquals(2, syncResponse.getFiles().size());
        assertEquals(1, syncResponse.getApplicationSettings().size());
    }

    @Test
    public void testRepeatSync_updatesLastUpdateAndFiresEvent() {
        // Setup: existing device found
        Device existingDevice = createTestDevice(1, "DEV001", 100, 1);
        when(unsecureDAO.getDeviceByNumber("DEV001")).thenReturn(existingDevice);

        // Setup: configuration
        Configuration config = createTestConfiguration(100);
        when(unsecureDAO.getConfigurationByIdWithAppSettings(100)).thenReturn(config);
        when(unsecureDAO.getPlainConfigurationApplications(1, 100)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getConfigurationFiles(existingDevice)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getDeviceAppSettings(1)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getSettings(1)).thenReturn(createTestSettings());
        when(configurationDAO.getBaseUrl()).thenReturn("http://localhost:8080");

        // Create sync request with info JSON
        SyncRequest syncRequest = new SyncRequest();
        syncRequest.setInfo("{\"battery\":85}");
        syncRequest.setImeiUpdateTs(System.currentTimeMillis());
        syncRequest.setPublicIp("192.168.1.1");

        // Execute
        Response response = syncResource.syncConfiguration("DEV001", syncRequest);

        // Assert
        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        verify(unsecureDAO).updateDeviceInfo(eq(1), eq("{\"battery\":85}"), anyLong(), eq("192.168.1.1"));
        verify(eventService).fireEvent(any(DeviceInfoUpdatedEvent.class));
    }

    @Test
    public void testConfigChange_pushAndRecalc() {
        Configuration config = createTestConfiguration(100);

        // Execute
        Response response = configurationResource.updateConfiguration(config);

        // Assert
        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        verify(configurationDAO).updateConfiguration(config);
        verify(pushService).notifyDevicesOnUpdate(100);
    }

    @Test
    public void testConfigChange_allDevicesNotified() {
        Configuration config = createTestConfiguration(200);

        // Execute
        configurationResource.updateConfiguration(config);

        // Assert: push service was called with the correct config ID
        verify(pushService).notifyDevicesOnUpdate(200);
    }

    @Test
    public void testDeviceNotFound_properError() {
        // Setup: device not found and auto-create disabled
        when(unsecureDAO.getDeviceByNumber("UNKNOWN")).thenReturn(null);
        when(unsecureDAO.getDeviceByOldNumber("UNKNOWN")).thenReturn(null);
        when(unsecureDAO.createNewDeviceOnDemand("UNKNOWN")).thenReturn(null);

        // Execute
        Response response = syncResource.syncConfiguration("UNKNOWN", null);

        // Assert
        assertEquals(Response.ResponseStatus.ERROR, response.getStatus());
        // buildSyncResponse should never be called since enrollDevice returned null
        verify(unsecureDAO, never()).getConfigurationByIdWithAppSettings(anyInt());
    }

    @Test
    public void testSyncHooksApplied() {
        // Setup: device found
        Device device = createTestDevice(42, "DEV042", 100, 1);
        when(unsecureDAO.getDeviceByNumber("DEV042")).thenReturn(device);

        // Setup: configuration
        Configuration config = createTestConfiguration(100);
        when(unsecureDAO.getConfigurationByIdWithAppSettings(100)).thenReturn(config);
        when(unsecureDAO.getPlainConfigurationApplications(1, 100)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getConfigurationFiles(device)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getDeviceAppSettings(42)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getSettings(1)).thenReturn(createTestSettings());
        when(configurationDAO.getBaseUrl()).thenReturn("http://localhost:8080");

        // Create a mock hook
        SyncResponseHook mockHook = mock(SyncResponseHook.class);
        when(mockHook.handle(eq(42), any(SyncResponseInt.class))).thenAnswer(invocation -> invocation.getArgument(1));

        // Rebuild sync resource with hooks
        Set<SyncResponseHook> hooks = new HashSet<>();
        hooks.add(mockHook);
        SyncResource hookedSyncResource = new SyncResource(enrollmentService, unsecureDAO, eventService, hooks);

        // Execute
        Response response = hookedSyncResource.syncConfiguration("DEV042", null);

        // Assert
        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        verify(mockHook).handle(eq(42), any(SyncResponseInt.class));
    }

    @Test
    public void testDeviceEnrollment_customPropertiesUpdated() {
        // Setup: device found
        Device device = createTestDevice(1, "DEV001", 100, 1);
        when(unsecureDAO.getDeviceByNumber("DEV001")).thenReturn(device);

        // Setup: configuration
        Configuration config = createTestConfiguration(100);
        when(unsecureDAO.getConfigurationByIdWithAppSettings(100)).thenReturn(config);
        when(unsecureDAO.getPlainConfigurationApplications(1, 100)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getConfigurationFiles(device)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getDeviceAppSettings(1)).thenReturn(Collections.emptyList());
        when(unsecureDAO.getSettings(1)).thenReturn(createTestSettings());
        when(configurationDAO.getBaseUrl()).thenReturn("http://localhost:8080");

        // Create sync request with custom properties
        SyncRequest request = new SyncRequest();
        request.setCustom1("test-value");

        // Execute
        Response response = syncResource.syncConfiguration("DEV001", request);

        // Assert
        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        verify(unsecureDAO).updateDeviceCustomProperties(eq(1), any(Device.class));
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Device createTestDevice(int id, String number, int configId, int customerId) {
        Device device = new Device();
        device.setId(id);
        device.setNumber(number);
        device.setConfigurationId(configId);
        device.setCustomerId(customerId);
        return device;
    }

    private Configuration createTestConfiguration(int id) {
        Configuration config = new Configuration();
        config.setId(id);
        config.setName("Test Config " + id);
        config.setUseDefaultDesignSettings(false);
        return config;
    }

    private Application createTestApplication(int id, String pkg, String version) {
        Application app = new Application();
        app.setId(id);
        app.setPkg(pkg);
        app.setVersion(version);
        app.setName("Test App " + id);
        return app;
    }

    private ConfigurationFile createTestConfigurationFile(int id, String path) {
        ConfigurationFile file = new ConfigurationFile();
        file.setId(id);
        file.setDevicePath(path);
        return file;
    }

    private ApplicationSetting createTestApplicationSetting(int id, String pkg, String name) {
        ApplicationSetting setting = new ApplicationSetting();
        setting.setId(id);
        setting.setApplicationPkg(pkg);
        setting.setName(name);
        setting.setValue("test-value");
        setting.setType(ApplicationSettingType.STRING);
        return setting;
    }

    private Settings createTestSettings() {
        Settings settings = new Settings();
        settings.setId(1);
        settings.setCustomerId(1);
        settings.setBackgroundColor("#FFFFFF");
        settings.setTextColor("#000000");
        return settings;
    }
}
