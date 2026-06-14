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
import com.hmdm.rest.json.DeviceCreateOptions;
import com.hmdm.rest.json.SyncResponseInt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DeviceEnrollmentService}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceEnrollmentServiceTest {

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

    @InjectMocks
    private DeviceEnrollmentService service;

    // =========================================================================
    // enrollDevice tests
    // =========================================================================

    @Test
    public void testEnrollDevice_existingDevice_foundByNumber() {
        Device testDevice = createTestDevice(1, "DEV001", 100, 1);
        when(unsecureDAO.getDeviceByNumber("DEV001")).thenReturn(testDevice);

        Device result = service.enrollDevice("DEV001", null);

        assertNotNull(result);
        assertEquals(testDevice, result);
        verify(unsecureDAO, never()).getDeviceByOldNumber(anyString());
    }

    @Test
    public void testEnrollDevice_notFound_resolvedByOldNumber() {
        Device testDevice = createTestDevice(1, "DEV001", 100, 1);
        when(unsecureDAO.getDeviceByNumber("OLD_NUM")).thenReturn(null);
        when(unsecureDAO.getDeviceByOldNumber("OLD_NUM")).thenReturn(testDevice);

        Device result = service.enrollDevice("OLD_NUM", null);

        assertNotNull(result);
        verify(unsecureDAO, never()).createNewDeviceOnDemand(anyString());
    }

    @Test
    public void testEnrollDevice_newDevice_autoCreateSingleTenant() {
        Device testDevice = createTestDevice(1, "NEW_DEV", 100, 1);
        when(unsecureDAO.getDeviceByNumber("NEW_DEV")).thenReturn(null);
        when(unsecureDAO.getDeviceByOldNumber("NEW_DEV")).thenReturn(null);
        when(unsecureDAO.createNewDeviceOnDemand("NEW_DEV")).thenReturn(testDevice);

        Device result = service.enrollDevice("NEW_DEV", null);

        assertNotNull(result);
        verify(unsecureDAO).createNewDeviceOnDemand("NEW_DEV");
    }

    @Test
    public void testEnrollDevice_newDevice_autoCreateMultiTenant() {
        Device testDevice = createTestDevice(1, "NEW_DEV", 100, 1);
        DeviceCreateOptions createOptions = new DeviceCreateOptions();
        createOptions.setCustomer("TestCustomer");
        createOptions.setConfiguration("test-config-key");

        when(unsecureDAO.getDeviceByNumber("NEW_DEV")).thenReturn(null);
        when(unsecureDAO.getDeviceByOldNumber("NEW_DEV")).thenReturn(null);
        when(unsecureDAO.createNewDeviceOnDemand(eq("NEW_DEV"), eq(createOptions))).thenReturn(testDevice);

        Device result = service.enrollDevice("NEW_DEV", createOptions);

        assertNotNull(result);
        verify(unsecureDAO).createNewDeviceOnDemand("NEW_DEV", createOptions);
    }

    @Test
    public void testEnrollDevice_autoCreateDisabled_returnsNull() {
        when(unsecureDAO.getDeviceByNumber("UNKNOWN")).thenReturn(null);
        when(unsecureDAO.getDeviceByOldNumber("UNKNOWN")).thenReturn(null);
        when(unsecureDAO.createNewDeviceOnDemand("UNKNOWN")).thenReturn(null);

        Device result = service.enrollDevice("UNKNOWN", null);

        assertNull(result);
    }

    // =========================================================================
    // buildSyncResponse tests
    // =========================================================================

    @Test
    public void testBuildSyncResponse_completeConfig() {
        Device device = createTestDevice(1, "DEV001", 100, 1);
        Configuration config = createTestConfiguration(100);

        List<Application> apps = Arrays.asList(
                createTestApplication(1, "com.example.app1", "1.0"),
                createTestApplication(2, "com.example.app2", "2.0")
        );
        List<ConfigurationFile> files = Collections.singletonList(
                createTestConfigurationFile("/sdcard/test.txt")
        );
        List<ApplicationSetting> appSettings = Collections.singletonList(
                createTestApplicationSetting("com.example.app1", "setting1")
        );
        Settings settings = createTestSettings();

        when(unsecureDAO.getConfigurationByIdWithAppSettings(100)).thenReturn(config);
        when(unsecureDAO.getPlainConfigurationApplications(1, 100)).thenReturn(apps);
        when(unsecureDAO.getConfigurationFiles(device)).thenReturn(files);
        when(unsecureDAO.getDeviceAppSettings(1)).thenReturn(appSettings);
        when(unsecureDAO.getSettings(1)).thenReturn(settings);
        when(configurationDAO.getBaseUrl()).thenReturn("http://localhost:8080");

        SyncResponseInt result = service.buildSyncResponse(device);

        assertNotNull(result);
        assertNotNull(result.getApplications());
        assertEquals(2, result.getApplications().size());
        assertNotNull(result.getFiles());
        assertEquals(1, result.getFiles().size());
        assertNotNull(result.getApplicationSettings());
        assertEquals(1, result.getApplicationSettings().size());
    }

    @Test
    public void testBuildSyncResponse_configNotFound_returnsNull() {
        Device device = createTestDevice(1, "DEV001", 999, 1);
        when(unsecureDAO.getConfigurationByIdWithAppSettings(999)).thenReturn(null);

        SyncResponseInt result = service.buildSyncResponse(device);

        assertNull(result);
    }

    // =========================================================================
    // onConfigurationChanged tests
    // =========================================================================

    @Test
    public void testOnConfigurationChanged_triggersPush() {
        service.onConfigurationChanged(100);

        verify(pushService, times(1)).notifyDevicesOnUpdate(100);
    }

    @Test
    public void testOnConfigurationChanged_pushFails_doesNotThrow() {
        doThrow(new RuntimeException("MQTT down")).when(pushService).notifyDevicesOnUpdate(anyInt());

        // Should not throw
        service.onConfigurationChanged(100);

        verify(pushService).notifyDevicesOnUpdate(100);
    }

    // =========================================================================
    // triggerConfigPush tests
    // =========================================================================

    @Test
    public void testTriggerConfigPush_singleDevice() {
        service.triggerConfigPush(42);

        verify(pushService).notifyDeviceOnSettingUpdate(42);
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
        config.setName("Test Config");
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

    private ConfigurationFile createTestConfigurationFile(String path) {
        ConfigurationFile file = new ConfigurationFile();
        file.setId(1);
        file.setDevicePath(path);
        return file;
    }

    private ApplicationSetting createTestApplicationSetting(String pkg, String name) {
        ApplicationSetting setting = new ApplicationSetting();
        setting.setId(1);
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
