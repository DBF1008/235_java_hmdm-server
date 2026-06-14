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

import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * <p>A single owner of the device on-boarding and configuration-synchronization state machine.</p>
 *
 * <p>Historically the transitions that advance a device's state were spread across (and partly
 * decoupled within) several entry points: a device was built in {@link UnsecureDAO}, the first-sync
 * configuration was assembled from four separate {@code UnsecureDAO} calls, the reported device info
 * was persisted by {@link UnsecureDAO#updateDeviceInfo} <em>without</em> recalculating the device's
 * status, and a configuration change recalculated statuses (via {@code ConfigurationUpdatedEvent})
 * <em>without</em> pushing the affected devices. As a result a freshly registered device could sync
 * and receive an incomplete configuration, the persisted "sync view" (the status columns) could drift
 * away from what a device had actually reported, and a configuration change could update one of the
 * push / sync-view halves while leaving the other stale.</p>
 *
 * <p>This service consolidates those transitions so each one crosses a single, well-defined boundary:</p>
 * <ul>
 *     <li>{@link #enrollDevice} — the registration boundary;</li>
 *     <li>{@link #getSyncConfiguration} — the (single) configuration-assembly boundary, shared by the
 *     first sync and every later sync;</li>
 *     <li>{@link #completeSync} — the sync boundary, which couples persisting reported info with
 *     recalculating the status so the two cannot drift;</li>
 *     <li>{@link #applyConfigurationChange} — the configuration-change boundary, which couples
 *     recalculating the sync view with pushing the affected devices.</li>
 * </ul>
 *
 * <p>It lives in the {@code notification} module because it has to reach both the device persistence /
 * status services (in {@code common}) and {@link PushService} (in {@code notification}); the
 * {@code common} module cannot depend on {@code notification}. It is wired like the other services in
 * this module — a {@link Singleton} with an {@link Inject} constructor resolved by Guice's JIT
 * binding, so no module change is required.</p>
 */
@Singleton
public class DeviceSyncService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceSyncService.class);

    private final UnsecureDAO unsecureDAO;
    private final DeviceStatusService deviceStatusService;
    private final PushService pushService;
    private final DeviceDAO deviceDAO;

    @Inject
    public DeviceSyncService(UnsecureDAO unsecureDAO,
                             DeviceStatusService deviceStatusService,
                             PushService pushService,
                             DeviceDAO deviceDAO) {
        this.unsecureDAO = unsecureDAO;
        this.deviceStatusService = deviceStatusService;
        this.pushService = pushService;
        this.deviceDAO = deviceDAO;
    }

    /**
     * <p>Resolves the device for the given number, creating it on demand if it does not yet exist.
     * This is the registration boundary: a device is built in exactly one place, and the caller is
     * told whether it had to be created.</p>
     *
     * <p>On-demand creation may be declined (creation disabled by settings, unresolvable customer /
     * configuration, or device limit reached), in which case the returned enrollment is not
     * {@link DeviceEnrollment#isPresent() present}.</p>
     *
     * @param number  the device number reported by the client.
     * @param options creation options (customer / configuration / groups); may be {@code null} to use
     *                the single-customer "create new devices" settings.
     * @return the enrollment outcome (never {@code null}).
     */
    public DeviceEnrollment enrollDevice(String number, DeviceCreateOptions options) {
        final Device existing = unsecureDAO.getDeviceByNumber(number);
        if (existing != null) {
            return new DeviceEnrollment(existing, false);
        }

        final Device created = (options == null)
                ? unsecureDAO.createNewDeviceOnDemand(number)
                : unsecureDAO.createNewDeviceOnDemand(number, options);

        if (created == null) {
            logger.warn("On-demand creation declined for device {}", number);
            return new DeviceEnrollment(null, false);
        }
        return new DeviceEnrollment(created, true);
    }

    /**
     * <p>Assembles the complete configuration payload for a device. This is the single place where the
     * configuration, applications, files and per-device application settings are gathered, so the first
     * sync of a freshly registered device produces exactly the same complete payload as any later
     * sync.</p>
     *
     * <p>A device with no configuration assigned cannot produce a complete payload; rather than
     * silently returning an empty configuration (the historical failure mode), this method fails fast
     * so the misconfiguration is surfaced.</p>
     *
     * @param device the device to assemble the configuration for (must have an id and a configuration).
     * @return the complete sync configuration (never {@code null}).
     * @throws IllegalArgumentException if {@code device} or its id is {@code null}.
     * @throws IllegalStateException    if the device has no configuration assigned.
     */
    public DeviceSyncConfiguration getSyncConfiguration(Device device) {
        if (device == null || device.getId() == null) {
            throw new IllegalArgumentException("A persisted device (with an id) is required");
        }
        final Integer configurationId = device.getConfigurationId();
        if (configurationId == null) {
            throw new IllegalStateException("Device " + device.getNumber()
                    + " has no configuration assigned; cannot build a complete sync configuration");
        }

        final Configuration configuration = unsecureDAO.getConfigurationByIdWithAppSettings(configurationId);
        final List<Application> applications =
                unsecureDAO.getPlainConfigurationApplications(device.getCustomerId(), configurationId);
        final List<ConfigurationFile> files = unsecureDAO.getConfigurationFiles(device);
        final List<ApplicationSetting> applicationSettings = unsecureDAO.getDeviceAppSettings(device.getId());

        return new DeviceSyncConfiguration(configuration, applications, files, applicationSettings);
    }

    /**
     * <p>Records a device synchronization. This is the sync boundary: persisting the info reported by
     * the device and recalculating its status are performed together, so the persisted "sync view"
     * always reflects what the device last reported and cannot drift.</p>
     *
     * <p>The persistence layer stamps {@code lastUpdate} (and {@code enrollTime}) as part of the info
     * update, so this method does not touch them; it only reports whether this was the device's first
     * sync, determined from the device's {@code lastUpdate} as loaded before the write.</p>
     *
     * @param device       the device being synchronized (must have an id); its {@code lastUpdate} is
     *                     read to detect the first sync.
     * @param info         the device-reported state (JSON) to persist.
     * @param imeiUpdateTs timestamp of the last IMEI change, or {@code null}.
     * @param publicIp     the device's public IP, or {@code null}.
     * @return {@code true} if this was the device's first synchronization.
     * @throws IllegalArgumentException if {@code device} or its id is {@code null}.
     */
    public boolean completeSync(Device device, String info, Long imeiUpdateTs, String publicIp) {
        if (device == null || device.getId() == null) {
            throw new IllegalArgumentException("A persisted device (with an id) is required");
        }
        final Long lastUpdate = device.getLastUpdate();
        final boolean firstSync = (lastUpdate == null || lastUpdate == 0L);

        unsecureDAO.updateDeviceInfo(device.getId(), info, imeiUpdateTs, publicIp);
        deviceStatusService.recalcDeviceStatuses(device.getId());

        return firstSync;
    }

    /**
     * <p>Applies a configuration change to every device on that configuration. This is the
     * configuration-change boundary: recalculating the sync view and notifying the devices are coupled
     * over a single, consistent device set, so a configuration change can no longer update the push
     * channel while leaving the sync view stale (or vice versa).</p>
     *
     * <p>Status recalculation here is idempotent; it overlaps with — but does not depend on — the
     * recalculation performed by {@code ConfigurationUpdatedEventListener}. The value this method adds
     * is the previously-missing coupling with the push fan-out, in one entry point a resource can call
     * after persisting a configuration change.</p>
     *
     * @param configurationId the id of the changed configuration.
     * @throws IllegalArgumentException if {@code configurationId} is {@code null}.
     */
    public void applyConfigurationChange(Integer configurationId) {
        if (configurationId == null) {
            throw new IllegalArgumentException("configurationId is required");
        }

        final List<Device> devices = deviceDAO.getDeviceIdsByConfigurationId(configurationId);
        if (devices != null) {
            for (Device device : devices) {
                try {
                    deviceStatusService.recalcDeviceStatuses(device.getId());
                } catch (Exception e) {
                    logger.warn("Failed to recalculate statuses for device {} on configuration {} change",
                            device.getId(), configurationId, e);
                }
            }
        }

        pushService.notifyDevicesOnUpdate(configurationId);
    }
}
