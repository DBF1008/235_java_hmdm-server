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
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.persistence.domain.UploadedFile;
import com.hmdm.persistence.mapper.ApplicationMapper;
import com.hmdm.persistence.mapper.CustomerMapper;
import com.hmdm.persistence.mapper.UploadedFileMapper;
import com.hmdm.service.FileHashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * <p>Background task that migrates files from one storage directory to another.
 * Uses a copy-verify-update-delete strategy to ensure data safety during migration.</p>
 *
 * <p>This task is designed to be triggered manually or scheduled for a one-time migration window.
 * It is idempotent: re-running the task will skip files that have already been migrated.</p>
 */
@Singleton
public class FileMigrateTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(FileMigrateTask.class);

    private final CustomerMapper customerMapper;
    private final ApplicationMapper applicationMapper;
    private final UploadedFileMapper uploadedFileMapper;
    private final FileHashService fileHashService;
    private final String sourceDirectory;
    private final String targetDirectory;

    /**
     * <p>Constructs new {@code FileMigrateTask} instance.</p>
     *
     * @param customerMapper the customer mapper.
     * @param applicationMapper the application mapper.
     * @param uploadedFileMapper the uploaded file mapper.
     * @param fileHashService the file hash service.
     * @param sourceDirectory the source base directory to migrate from.
     * @param targetDirectory the target base directory to migrate to.
     */
    @Inject
    public FileMigrateTask(CustomerMapper customerMapper,
                           ApplicationMapper applicationMapper,
                           UploadedFileMapper uploadedFileMapper,
                           FileHashService fileHashService,
                           String sourceDirectory,
                           String targetDirectory) {
        this.customerMapper = customerMapper;
        this.applicationMapper = applicationMapper;
        this.uploadedFileMapper = uploadedFileMapper;
        this.fileHashService = fileHashService;
        this.sourceDirectory = sourceDirectory;
        this.targetDirectory = targetDirectory;
    }

    @Override
    public void run() {
        log.info("Starting file migration: {} → {}", sourceDirectory, targetDirectory);

        int migrated = 0;
        int skipped = 0;
        int failed = 0;

        try {
            List<Customer> customers = customerMapper.findAll();
            if (customers == null || customers.isEmpty()) {
                log.info("No customers found, skipping migration");
                return;
            }

            for (Customer customer : customers) {
                try {
                    // Migrate APK files
                    List<Application> applications = applicationMapper.getAllApplications(customer.getId());
                    if (applications != null) {
                        for (Application app : applications) {
                            List<ApplicationVersion> versions = applicationMapper.getApplicationVersions(app.getId());
                            if (versions != null) {
                                for (ApplicationVersion version : versions) {
                                    MigrationResult result = migrateUrl(customer, version.getUrl());
                                    switch (result) {
                                        case MIGRATED:
                                            migrated++;
                                            break;
                                        case SKIPPED:
                                            skipped++;
                                            break;
                                        case FAILED:
                                            failed++;
                                            break;
                                    }

                                    if (version.isSplit()) {
                                        migrateUrl(customer, version.getUrlArmeabi());
                                        migrateUrl(customer, version.getUrlArm64());
                                    }
                                }
                            }
                        }
                    }

                    // Migrate uploaded config files
                    List<UploadedFile> uploadedFiles = uploadedFileMapper.getAll(customer.getId());
                    if (uploadedFiles != null) {
                        for (UploadedFile uf : uploadedFiles) {
                            if (!uf.isExternal()) {
                                MigrationResult result = migrateConfigFile(customer, uf);
                                switch (result) {
                                    case MIGRATED:
                                        migrated++;
                                        break;
                                    case SKIPPED:
                                        skipped++;
                                        break;
                                    case FAILED:
                                        failed++;
                                        break;
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    log.error("Error migrating files for customer {}: {}", customer.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("File migration failed with unexpected error", e);
        }

        log.info("File migration complete: migrated={}, skipped={}, failed={}", migrated, skipped, failed);
    }

    /**
     * <p>Migrates a single file referenced by a URL from source to target directory.</p>
     */
    private MigrationResult migrateUrl(Customer customer, String url) {
        if (url == null || url.trim().isEmpty()) {
            return MigrationResult.SKIPPED;
        }

        // Resolve the relative path within the customer directory
        String customerDir = customer.getFilesDir();
        String prefix = "/files/" + (customerDir != null ? customerDir + "/" : "");
        int prefixPos = url.indexOf(prefix);
        if (prefixPos < 0) {
            log.debug("URL does not contain expected prefix {}: {}", prefix, url);
            return MigrationResult.SKIPPED;
        }

        String relativePath = url.substring(prefixPos + prefix.length());
        Path sourcePath = Paths.get(sourceDirectory, customerDir != null ? customerDir : "", relativePath);
        Path targetPath = Paths.get(targetDirectory, customerDir != null ? customerDir : "", relativePath);

        // Idempotent: skip if target already exists
        if (Files.exists(targetPath)) {
            return MigrationResult.SKIPPED;
        }

        if (!Files.exists(sourcePath)) {
            log.warn("Source file does not exist: {}", sourcePath);
            return MigrationResult.FAILED;
        }

        try {
            // Step 1: Copy
            Files.createDirectories(targetPath.getParent());
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Step 2: Verify integrity
            String sourceHash = fileHashService.computeSha256(sourcePath.toFile());
            String targetHash = fileHashService.computeSha256(targetPath.toFile());
            if (!sourceHash.equals(targetHash)) {
                log.error("Hash mismatch after copy: {} → {}. Rolling back.",
                        sourcePath, targetPath);
                Files.deleteIfExists(targetPath);
                return MigrationResult.FAILED;
            }

            // Step 3: Delete source (only after successful copy + verify)
            Files.delete(sourcePath);

            log.debug("Migrated: {} → {} (hash={})", sourcePath, targetPath, sourceHash);
            return MigrationResult.MIGRATED;

        } catch (IOException e) {
            log.error("Failed to migrate: {} → {}", sourcePath, targetPath, e);
            // Clean up partial copy
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException cleanupEx) {
                log.warn("Failed to clean up partial copy: {}", targetPath, cleanupEx);
            }
            return MigrationResult.FAILED;
        }
    }

    /**
     * <p>Migrates a configuration file from source to target directory.</p>
     */
    private MigrationResult migrateConfigFile(Customer customer, UploadedFile uf) {
        File sourceFile = uf.getFileByPath(sourceDirectory, customer);
        File targetFile = uf.getFileByPath(targetDirectory, customer);

        if (targetFile.exists()) {
            return MigrationResult.SKIPPED;
        }

        if (!sourceFile.exists()) {
            log.warn("Source config file does not exist: {}", sourceFile.getAbsolutePath());
            return MigrationResult.FAILED;
        }

        try {
            // Copy
            targetFile.getParentFile().mkdirs();
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Verify
            String sourceHash = fileHashService.computeSha256(sourceFile);
            String targetHash = fileHashService.computeSha256(targetFile);
            if (!sourceHash.equals(targetHash)) {
                log.error("Hash mismatch after config file copy: {} → {}. Rolling back.",
                        sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
                targetFile.delete();
                return MigrationResult.FAILED;
            }

            // Delete source
            sourceFile.delete();

            log.debug("Migrated config file: {} → {} (hash={})",
                    sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), sourceHash);
            return MigrationResult.MIGRATED;

        } catch (IOException e) {
            log.error("Failed to migrate config file: {} → {}",
                    sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), e);
            targetFile.delete();
            return MigrationResult.FAILED;
        }
    }

    /**
     * <p>Result codes for file migration operations.</p>
     */
    enum MigrationResult {
        /** File was successfully migrated. */
        MIGRATED,
        /** File was skipped (already exists at target or URL is empty). */
        SKIPPED,
        /** Migration failed for this file. */
        FAILED
    }
}
