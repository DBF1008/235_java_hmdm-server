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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.util.BackgroundTaskRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * <p>A Guice module that registers file-related background tasks and schedules them
 * for periodic execution.</p>
 *
 * <p>Currently schedules:
 * <ul>
 *   <li>{@link FileCheckTask} — runs every 6 hours (1 hour initial delay) to verify
 *       file integrity across all tenants.</li>
 * </ul>
 * </p>
 */
public class FileCheckTaskModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(FileCheckTaskModule.class);

    @Override
    protected void configure() {
        bind(FileCheckTaskScheduler.class).asEagerSingleton();
    }

    /**
     * <p>Helper class that schedules the {@link FileCheckTask} for periodic execution
     * upon application startup.</p>
     */
    @Singleton
    public static class FileCheckTaskScheduler {

        /**
         * <p>Constructs new scheduler and registers the file check task.</p>
         *
         * @param taskRunner the background task runner service.
         * @param fileCheckTask the file check task to schedule.
         */
        @Inject
        public FileCheckTaskScheduler(BackgroundTaskRunnerService taskRunner,
                                      FileCheckTask fileCheckTask) {
            log.info("Scheduling FileCheckTask: initial delay=1h, period=6h");
            taskRunner.submitRepeatableTask(fileCheckTask, 1, 6, TimeUnit.HOURS);
        }
    }
}
