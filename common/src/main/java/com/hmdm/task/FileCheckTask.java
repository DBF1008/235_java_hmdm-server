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
import com.hmdm.persistence.ApplicationDAO;
import com.hmdm.persistence.CustomerDAO;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.persistence.domain.UploadedFile;
import com.hmdm.persistence.mapper.ApplicationMapper;
import com.hmdm.persistence.mapper.CustomerMapper;
import com.hmdm.persistence.mapper.UploadedFileMapper;
import com.hmdm.service.FileHashService;
import com.hmdm.service.FileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * <p>Background task that performs periodic integrity checks on all published files.
 * Checks for missing files, hash mismatches, and orphaned files on disk.</p>
 *
 * <p>This task is designed to be scheduled via
 * {@link com.hmdm.util.BackgroundTaskRunnerService#submitRepeatableTask}.</p>
 */
@Singleton
public class FileCheckTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(FileCheckTask.class);

    private final CustomerMapper customerMapper;
    private final ApplicationMapper applicationMapper;
    private final UploadedFileMapper uploadedFileMapper;
    private final FileUploadService fileUploadService;
    private final FileHashService fileHashService;
    private final String filesDirectory;

    /**
     * <p>Constructs new {@code FileCheckTask} instance.</p>
     */
    @Inject
    public FileCheckTask(CustomerMapper customerMapper,
                         ApplicationMapper applicationMapper,
                         UploadedFileMapper uploadedFileMapper,
                         FileUploadService fileUploadService,
                         FileHashService fileHashService,
                         @Named("files.directory") String filesDirectory) {
        this.customerMapper = customerMapper;
        this.applicationMapper = applicationMapper;
        this.uploadedFileMapper = uploadedFileMapper;
        this.fileUploadService = fileUploadService;
        this.fileHashService = fileHashService;
        this.filesDirectory = filesDirectory;
    }

    @Override
    public void run() {
        log.info("Starting file integrity check cycle");

        int apkChecked = 0;
        int apkMissing = 0;
        int apkCorrupted = 0;
        int apkNoHash = 0;
        int fileChecked = 0;
        int fileMissing = 0;
        int orphanCount = 0;

        try {
            List<Customer> customers = customerMapper.findAll();
            if (customers == null || customers.isEmpty()) {
                log.info("No customers found, skipping file check");
                return;
            }

            for (Customer customer : customers) {
                try {
                    // 1. Check APK files for all application versions
                    List<Application> applications = applicationMapper.getAllApplications(customer.getId());
                    if (applications != null) {
                        for (Application app : applications) {
                            List<ApplicationVersion> versions = applicationMapper.getApplicationVersions(app.getId());
                            if (versions != null) {
                                for (ApplicationVersion version : versions) {
                                    // Check main URL
                                    CheckResult result = checkApkUrl(customer, version.getUrl(), version.getApkHash());
                                    apkChecked++;
                                    switch (result) {
                                        case MISSING:
                                            apkMissing++;
                                            log.error("MISSING APK: customerId={}, appId={}, versionId={}, url={}",
                                                    customer.getId(), app.getId(), version.getId(), version.getUrl());
                                            break;
                                        case HASH_MISMATCH:
                                            apkCorrupted++;
                                            log.error("HASH MISMATCH APK: customerId={}, appId={}, versionId={}, url={}",
                                                    customer.getId(), app.getId(), version.getId(), version.getUrl());
                                            break;
                                        case NO_HASH:
                                            apkNoHash++;
                                            break;
                                        case OK:
                                            break;
                                    }

                                    // Check architecture-specific URLs
                                    if (version.isSplit()) {
                                        checkApkUrl(customer, version.getUrlArmeabi(), version.getApkHash());
                                        checkApkUrl(customer, version.getUrlArm64(), version.getApkHash());
                                    }
                                }
                            }
                        }
                    }

                    // 2. Check uploaded config files
                    List<UploadedFile> uploadedFiles = uploadedFileMapper.getAll(customer.getId());
                    if (uploadedFiles != null) {
                        for (UploadedFile uf : uploadedFiles) {
                            if (!uf.isExternal()) {
                                fileChecked++;
                                File physicalFile = uf.getFileByPath(filesDirectory, customer);
                                if (physicalFile == null || !physicalFile.exists()) {
                                    fileMissing++;
                                    log.error("MISSING FILE: customerId={}, fileId={}, filePath={}",
                                            customer.getId(), uf.getId(), uf.getFilePath());
                                }
                            }
                        }
                    }

                    // 3. Detect orphaned files (files in DB not linked to any configuration)
                    List<UploadedFile> orphaned = uploadedFileMapper.findOrphaned(customer.getId());
                    if (orphaned != null) {
                        orphanCount += orphaned.size();
                        for (UploadedFile orphan : orphaned) {
                            log.warn("ORPHAN FILE RECORD: customerId={}, fileId={}, filePath={}",
                                    customer.getId(), orphan.getId(), orphan.getFilePath());
                        }
                    }

                } catch (Exception e) {
                    log.error("Error checking files for customer {}: {}", customer.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("File check cycle failed with unexpected error", e);
        }

        log.info("File check complete: apkChecked={}, apkMissing={}, apkCorrupted={}, apkNoHash={}, " +
                        "fileChecked={}, fileMissing={}, orphanRecords={}",
                apkChecked, apkMissing, apkCorrupted, apkNoHash,
                fileChecked, fileMissing, orphanCount);
    }

    /**
     * <p>Checks a single APK URL for file existence and hash integrity.</p>
     *
     * @param customer the customer that owns the file.
     * @param url the download URL.
     * @param expectedHash the expected SHA-256 hash (may be null).
     * @return the check result.
     */
    CheckResult checkApkUrl(Customer customer, String url, String expectedHash) {
        if (url == null || url.trim().isEmpty()) {
            return CheckResult.SKIPPED;
        }

        File physicalFile = fileUploadService.resolvePhysicalFile(customer, url);
        if (physicalFile == null || !physicalFile.exists()) {
            return CheckResult.MISSING;
        }

        if (expectedHash != null && !expectedHash.isEmpty()) {
            try {
                boolean hashMatch = fileHashService.verify(physicalFile, expectedHash);
                return hashMatch ? CheckResult.OK : CheckResult.HASH_MISMATCH;
            } catch (IOException e) {
                log.warn("Cannot hash file for integrity check: {}", physicalFile.getAbsolutePath(), e);
                return CheckResult.ERROR;
            }
        }

        return CheckResult.NO_HASH;
    }

    /**
     * <p>Result codes for file integrity checks.</p>
     */
    enum CheckResult {
        /** File exists and hash matches (or no hash to compare). */
        OK,
        /** File does not exist at the expected location. */
        MISSING,
        /** File exists but hash does not match the expected value. */
        HASH_MISMATCH,
        /** No hash available for comparison - file existence only. */
        NO_HASH,
        /** URL is null or empty - nothing to check. */
        SKIPPED,
        /** An error occurred during the check. */
        ERROR
    }
}
