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

import com.hmdm.persistence.domain.Device;

/**
 * <p>The outcome of {@link DeviceSyncService#enrollDevice}. It carries the resolved device together
 * with the single piece of state a caller needs at the registration boundary: whether the device was
 * just created during this enrollment or already existed.</p>
 *
 * <p>{@link #getDevice()} may be {@code null} when on-demand creation was declined (creation disabled
 * by settings, customer/configuration not resolvable, or device limit reached) — callers must check
 * {@link #isPresent()} before using the device.</p>
 */
public final class DeviceEnrollment {

    private final Device device;
    private final boolean created;

    public DeviceEnrollment(Device device, boolean created) {
        this.device = device;
        this.created = created;
    }

    /**
     * @return the enrolled device, or {@code null} if on-demand creation was declined.
     */
    public Device getDevice() {
        return device;
    }

    /**
     * @return {@code true} if the device was created during this enrollment, {@code false} if it
     * already existed.
     */
    public boolean isCreated() {
        return created;
    }

    /**
     * @return {@code true} if a device was resolved (either pre-existing or newly created).
     */
    public boolean isPresent() {
        return device != null;
    }

    @Override
    public String toString() {
        return "DeviceEnrollment{device=" + device + ", created=" + created + '}';
    }
}
