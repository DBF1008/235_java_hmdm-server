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

package com.hmdm.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.event.CustomerStatusUpdatedEvent;
import com.hmdm.event.EventService;
import com.hmdm.persistence.CustomerDAO;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.persistence.domain.DeviceOnlineStatus;
import com.hmdm.persistence.mapper.DeviceMapper;
import com.hmdm.util.BackgroundTaskRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>A scheduled task that periodically aggregates device health status for each customer
 * using the unified {@link DeviceOnlineStatus} thresholds. This ensures that customer-level
 * status snapshots are consistent with the device list view and dashboard summary charts.</p>
 *
 * <p>The task runs every 5 minutes and:</p>
 * <ol>
 *   <li>Iterates all customers</li>
 *   <li>Computes per-customer device counts (online/stale/offline) using canonical thresholds</li>
 *   <li>Publishes a {@link CustomerStatusUpdatedEvent} for each customer</li>
 * </ol>
 */
@Singleton
public class CustomerStatusTask {

    private static final Logger logger = LoggerFactory.getLogger(CustomerStatusTask.class);

    /**
     * <p>Interval between successive aggregations (5 minutes).</p>
     */
    private static final long INTERVAL_MINUTES = 5;

    /**
     * <p>Initial delay before the first aggregation (1 minute).</p>
     */
    private static final long INITIAL_DELAY_MINUTES = 1;

    private final CustomerDAO customerDAO;
    private final DeviceMapper deviceMapper;
    private final EventService eventService;
    private final BackgroundTaskRunnerService taskRunner;

    /**
     * <p>Constructs new <code>CustomerStatusTask</code> instance and schedules periodic execution.</p>
     */
    @Inject
    public CustomerStatusTask(CustomerDAO customerDAO,
                              DeviceMapper deviceMapper,
                              EventService eventService,
                              BackgroundTaskRunnerService taskRunner) {
        this.customerDAO = customerDAO;
        this.deviceMapper = deviceMapper;
        this.eventService = eventService;
        this.taskRunner = taskRunner;
    }

    /**
     * <p>Starts the periodic aggregation task. Should be called during application initialization.</p>
     */
    public void start() {
        logger.info("Scheduling CustomerStatusTask to run every {} minutes", INTERVAL_MINUTES);
        this.taskRunner.submitRepeatableTask(
                this::aggregateAllCustomers,
                INITIAL_DELAY_MINUTES,
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * <p>Aggregates device health status for all customers using unified thresholds
     * from {@link DeviceOnlineStatus}. For each customer, computes online/stale/offline
     * device counts and publishes a {@link CustomerStatusUpdatedEvent}.</p>
     */
    void aggregateAllCustomers() {
        logger.debug("Starting customer status aggregation");
        long onlineBoundary = DeviceOnlineStatus.getOnlineBoundary();
        long staleBoundary = DeviceOnlineStatus.getStaleBoundary();
        long snapshotTime = System.currentTimeMillis();

        try {
            List<Customer> customers = this.customerDAO.getAllCustomers();
            int processedCount = 0;

            for (Customer customer : customers) {
                try {
                    aggregateCustomerStatus(customer, onlineBoundary, staleBoundary, snapshotTime);
                    processedCount++;
                } catch (Exception e) {
                    logger.warn("Failed to aggregate status for customer {}: {}",
                            customer.getId(), e.getMessage());
                }
            }

            logger.info("Customer status aggregation completed: {} customers processed", processedCount);
        } catch (Exception e) {
            logger.error("Failed to run customer status aggregation", e);
        }
    }

    /**
     * <p>Aggregates device status for a single customer and publishes the result event.</p>
     */
    private void aggregateCustomerStatus(Customer customer, long onlineBoundary,
                                         long staleBoundary, long snapshotTime) {
        int customerId = customer.getId();

        Long totalDevices = this.deviceMapper.countDevicesForCustomer(customerId);
        int total = totalDevices != null ? totalDevices.intValue() : 0;

        Long onlineDevices = this.deviceMapper.countOnlineDevicesForCustomer(customerId, onlineBoundary);
        int online = onlineDevices != null ? onlineDevices.intValue() : 0;

        Long staleDevices = this.deviceMapper.countStaleDevicesForCustomer(
                customerId, onlineBoundary, staleBoundary);
        int stale = staleDevices != null ? staleDevices.intValue() : 0;

        int offline = Math.max(0, total - online - stale);

        CustomerStatusUpdatedEvent event = new CustomerStatusUpdatedEvent(
                customerId, online, stale, offline, total, snapshotTime);
        this.eventService.fireEvent(event);

        logger.debug("Customer {} status: total={}, online={}, stale={}, offline={}",
                customerId, total, online, stale, offline);
    }
}
