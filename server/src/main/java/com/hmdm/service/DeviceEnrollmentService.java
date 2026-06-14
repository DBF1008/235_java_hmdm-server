package com.hmdm.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.event.ConfigurationUpdatedEvent;
import com.hmdm.event.DeviceInfoUpdatedEvent;
import com.hmdm.event.EventService;
import com.hmdm.notification.PushService;
import com.hmdm.persistence.ConfigurationDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.*;
import com.hmdm.rest.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central orchestration service for device enrollment and configuration synchronization.
 * Unifies the state transitions that were previously scattered across multiple REST resources:
 * <ul>
 *   <li>Device registration (resolving/creating device records)</li>
 *   <li>Sync response building (assembling complete configuration for device)</li>
 *   <li>Configuration change notification (push + status recalculation)</li>
 *   <li>Push trigger for individual devices</li>
 * </ul>
 */
@Singleton
public class DeviceEnrollmentService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceEnrollmentService.class);

    private final UnsecureDAO unsecureDAO;
    private final ConfigurationDAO configurationDAO;
    private final PushService pushService;
    private final EventService eventService;
    private final DeviceStatusService deviceStatusService;

    @Inject
    public DeviceEnrollmentService(UnsecureDAO unsecureDAO,
                                    ConfigurationDAO configurationDAO,
                                    PushService pushService,
                                    EventService eventService,
                                    DeviceStatusService deviceStatusService) {
        this.unsecureDAO = unsecureDAO;
        this.configurationDAO = configurationDAO;
        this.pushService = pushService;
        this.eventService = eventService;
        this.deviceStatusService = deviceStatusService;
    }

    /**
     * Resolves or creates a device for enrollment/check-in.
     * Handles: number lookup → oldNumber migration → auto-create → null.
     *
     * @param deviceNumber the device number to resolve
     * @param options optional creation options for multi-tenant enrollment (may be null)
     * @return the resolved Device, or null if not found and auto-create is disabled
     */
    public Device enrollDevice(String deviceNumber, DeviceCreateOptions options) {
        // 1. Try primary number
        Device device = unsecureDAO.getDeviceByNumber(deviceNumber);

        // 2. Try oldNumber (migration scenario)
        if (device == null) {
            device = unsecureDAO.getDeviceByOldNumber(deviceNumber);
            if (device != null) {
                logger.info("Device {} resolved via oldNumber migration", deviceNumber);
            }
        }

        // 3. Auto-create if not found
        if (device == null) {
            if (options != null) {
                device = unsecureDAO.createNewDeviceOnDemand(deviceNumber, options);
            } else {
                device = unsecureDAO.createNewDeviceOnDemand(deviceNumber);
            }
            if (device != null) {
                logger.info("New device {} enrolled on demand", deviceNumber);
            } else {
                logger.warn("Device {} not found and auto-create disabled or failed", deviceNumber);
            }
        }

        return device;
    }

    /**
     * Builds a complete SyncResponseInt for a device.
     * Always fetches ALL configuration components (apps, files, settings) regardless of
     * whether the device is new or existing. This guarantees that first sync returns
     * a complete configuration, solving the "first sync incomplete" problem.
     *
     * @param device the device to build sync response for
     * @return a complete SyncResponseInt ready for serialization
     */
    public SyncResponseInt buildSyncResponse(Device device) {
        int configId = device.getConfigurationId();
        int customerId = device.getCustomerId();

        // Fetch all config components — always full, never partial
        Configuration config = unsecureDAO.getConfigurationByIdWithAppSettings(configId);
        if (config == null) {
            logger.error("Configuration {} not found for device {}", configId, device.getNumber());
            return null;
        }

        List<Application> apps = unsecureDAO.getPlainConfigurationApplications(customerId, configId);
        List<ConfigurationFile> files = unsecureDAO.getConfigurationFiles(device);
        List<ApplicationSetting> deviceAppSettings = unsecureDAO.getDeviceAppSettings(device.getId());
        Settings settings = unsecureDAO.getSettings(customerId);

        String baseUrl = configurationDAO.getBaseUrl();

        return SyncResponse.fromConfiguration(device, config, apps, files, deviceAppSettings, settings, baseUrl);
    }

    /**
     * Handles configuration change notification.
     * This is the unified orchestration point that ensures both push notification and
     * status recalculation happen when a configuration is updated.
     *
     * Note: ConfigurationDAO.updateConfiguration() already fires ConfigurationUpdatedEvent
     * which triggers ConfigurationUpdatedEventListener for status recalculation.
     * This method adds the missing push notification step.
     *
     * @param configurationId the ID of the updated configuration
     */
    public void onConfigurationChanged(int configurationId) {
        logger.info("Configuration {} changed, notifying devices", configurationId);
        try {
            pushService.notifyDevicesOnUpdate(configurationId);
        } catch (Exception e) {
            logger.error("Failed to notify devices on configuration {} update", configurationId, e);
        }
    }

    /**
     * Triggers a configuration update push to a single device.
     *
     * @param deviceId the ID of the device to notify
     */
    public void triggerConfigPush(int deviceId) {
        logger.info("Triggering config push for device {}", deviceId);
        try {
            pushService.notifyDeviceOnSettingUpdate(deviceId);
        } catch (Exception e) {
            logger.error("Failed to trigger push for device {}", deviceId, e);
        }
    }
}
