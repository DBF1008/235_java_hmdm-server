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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.util.FileExistsException;
import com.hmdm.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * <p>Central service for file publishing operations. Provides a unified entry point for
 * uploading, moving, hashing, URL generation, unpublishing, and integrity verification of files.</p>
 *
 * <p>This service consolidates the file I/O logic that was previously scattered across
 * {@code ApplicationDAO}, {@code UploadedFileDAO}, and {@code FileUtil}, providing
 * consistent path safety checks, hash computation, and error handling.</p>
 */
@Singleton
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final String filesDirectory;
    private final String baseUrl;
    private final FileHashService fileHashService;

    /**
     * <p>Constructs new {@code FileUploadService} instance.</p>
     *
     * @param filesDirectory the base directory for all stored files.
     * @param baseUrl the public-facing base URL of the server.
     * @param fileHashService the service for computing file hashes.
     */
    @Inject
    public FileUploadService(@Named("files.directory") String filesDirectory,
                             @Named("base.url") String baseUrl,
                             FileHashService fileHashService) {
        this.filesDirectory = filesDirectory;
        this.baseUrl = baseUrl;
        this.fileHashService = fileHashService;
    }

    /**
     * <p>Publishes an APK file: moves the temporary upload to permanent storage,
     * computes SHA-256 hash, and generates the download URL.</p>
     *
     * @param customer the customer (tenant) that owns the file.
     * @param tmpFilePath the path to the uploaded temporary file.
     * @return a {@link PublishedFile} containing the URL, hash, and file reference.
     * @throws IOException if the file cannot be moved or hashed.
     * @throws IllegalArgumentException if the file path is unsafe (path traversal).
     * @throws FileExistsException if the target file already exists.
     */
    public PublishedFile publishApkFile(Customer customer, String tmpFilePath) throws IOException {
        return publishFile(customer, tmpFilePath, null);
    }

    /**
     * <p>Publishes a configuration file: moves the temporary upload to permanent storage
     * within the specified subdirectory, computes SHA-256 hash, and generates the download URL.</p>
     *
     * @param customer the customer (tenant) that owns the file.
     * @param tmpFilePath the path to the uploaded temporary file.
     * @param localPath an optional subdirectory within the customer's files directory.
     * @return a {@link PublishedFile} containing the URL, hash, and file reference.
     * @throws IOException if the file cannot be moved or hashed.
     * @throws IllegalArgumentException if the file path is unsafe (path traversal).
     * @throws FileExistsException if the target file already exists.
     */
    public PublishedFile publishConfigFile(Customer customer, String tmpFilePath, String localPath) throws IOException {
        return publishFile(customer, tmpFilePath, localPath);
    }

    /**
     * <p>Internal method that performs the actual file publishing: move → hash → URL.</p>
     */
    private PublishedFile publishFile(Customer customer, String tmpFilePath, String localPath) throws IOException {
        if (tmpFilePath == null || tmpFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Temporary file path must not be null or empty");
        }

        File tmpFile = new File(tmpFilePath);
        if (!tmpFile.exists()) {
            throw new IOException("Temporary file does not exist: " + tmpFilePath);
        }

        // Move the file from temp to permanent storage
        File movedFile;
        try {
            movedFile = FileUtil.moveFile(customer, filesDirectory, localPath, tmpFilePath);
        } catch (FileExistsException e) {
            // File already exists at destination - delete and retry
            String originalName = FileUtil.getNameFromTmpPath(tmpFilePath);
            FileUtil.deleteFile(customer, filesDirectory,
                    (localPath != null && !localPath.isEmpty()) ? localPath + "/" + originalName : originalName);
            movedFile = FileUtil.moveFile(customer, filesDirectory, localPath, tmpFilePath);
        }

        if (movedFile == null) {
            throw new IOException("Failed to move file from " + tmpFilePath + " to permanent storage");
        }

        // Compute SHA-256 hash
        String sha256Hash = fileHashService.computeSha256(movedFile);

        // Generate download URL
        String fileName = movedFile.getName();
        String urlPath = (localPath != null && !localPath.isEmpty())
                ? localPath.replace('\\', '/') + "/" + fileName
                : fileName;
        String url = FileUtil.createFileUrl(baseUrl, customer.getFilesDir(), urlPath);

        log.info("Published file: {} → {} (hash={})", tmpFilePath, url, sha256Hash);

        return new PublishedFile(url, sha256Hash, fileName, movedFile);
    }

    /**
     * <p>Unpublishes (deletes) a physical file from the customer's files directory.</p>
     *
     * @param customer the customer (tenant) that owns the file.
     * @param relativePath the relative path of the file within the customer's directory.
     * @return {@code true} if the file was successfully deleted; {@code false} otherwise.
     */
    public boolean unpublishFile(Customer customer, String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            log.warn("Cannot unpublish file: relativePath is null or empty");
            return false;
        }

        String fullPath = String.format("%s/%s/%s", filesDirectory, customer.getFilesDir(), relativePath);
        File file = new File(fullPath.replace("/", File.separator));

        // Path safety check
        if (!FileUtil.isSafePath(filesDirectory, file.getAbsolutePath())) {
            log.error("Refusing to delete unsafe path: {}", fullPath);
            return false;
        }

        if (!file.exists()) {
            log.warn("File to unpublish does not exist: {}", file.getAbsolutePath());
            return false;
        }

        boolean deleted = file.delete();
        if (deleted) {
            log.info("Unpublished file: {}", file.getAbsolutePath());
        } else {
            log.error("Failed to unpublish file: {}", file.getAbsolutePath());
        }
        return deleted;
    }

    /**
     * <p>Resolves a download URL to a physical file path on the local filesystem.</p>
     *
     * @param customer the customer (tenant) that owns the file.
     * @param url the download URL.
     * @return the physical file, or {@code null} if the URL cannot be resolved.
     */
    public File resolvePhysicalFile(Customer customer, String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        String relativePath = FileUtil.translateURLToLocalFilePath(customer, url, baseUrl);
        if (relativePath == null) {
            return null;
        }

        File file = new File(
                String.format("%s/%s/%s", filesDirectory, customer.getFilesDir(), relativePath)
                        .replace("/", File.separator)
        );
        return file;
    }

    /**
     * <p>Verifies the integrity of a file by comparing its SHA-256 hash against the expected value.</p>
     *
     * @param customer the customer (tenant) that owns the file.
     * @param url the download URL of the file.
     * @param expectedHash the expected SHA-256 hash.
     * @return {@code true} if the file exists and its hash matches; {@code false} otherwise.
     * @throws IOException if the file exists but cannot be read.
     */
    public boolean verifyFileIntegrity(Customer customer, String url, String expectedHash) throws IOException {
        File file = resolvePhysicalFile(customer, url);
        if (file == null || !file.exists()) {
            log.warn("File not found for integrity check: url={}, resolved={}", url,
                    file == null ? "null" : file.getAbsolutePath());
            return false;
        }
        return fileHashService.verify(file, expectedHash);
    }

    /**
     * <p>Copies a file from one customer's directory to another, verifying integrity after copy.</p>
     *
     * @param sourceCustomer the source customer.
     * @param sourceUrl the source file URL.
     * @param targetCustomer the target customer.
     * @return the new URL for the copied file, or {@code null} if copy failed.
     * @throws IOException if the copy operation fails.
     */
    public String migrateFile(Customer sourceCustomer, String sourceUrl, Customer targetCustomer) throws IOException {
        File sourceFile = resolvePhysicalFile(sourceCustomer, sourceUrl);
        if (sourceFile == null || !sourceFile.exists()) {
            log.warn("Source file not found for migration: {}", sourceUrl);
            return null;
        }

        // Compute the relative path within the source customer directory
        String relativePath = FileUtil.translateURLToLocalFilePath(sourceCustomer, sourceUrl, baseUrl);
        if (relativePath == null) {
            log.warn("Cannot resolve relative path from URL: {}", sourceUrl);
            return null;
        }

        // Build target file path
        File targetDir = new File(filesDirectory, targetCustomer.getFilesDir());
        File targetFile = new File(targetDir, relativePath.replace("/", File.separator));

        // Path safety check
        if (!FileUtil.isSafePath(filesDirectory, targetFile.getAbsolutePath())) {
            throw new IllegalArgumentException("Unsafe target path: " + targetFile.getAbsolutePath());
        }

        // Idempotent: skip if target already exists with matching hash
        if (targetFile.exists()) {
            String sourceHash = fileHashService.computeSha256(sourceFile);
            String targetHash = fileHashService.computeSha256(targetFile);
            if (sourceHash.equals(targetHash)) {
                log.info("Migration skipped (already exists with matching hash): {}", targetFile.getAbsolutePath());
                return FileUtil.createFileUrl(baseUrl, targetCustomer.getFilesDir(),
                        relativePath.replace(File.separator, "/"));
            }
        }

        // Copy file
        targetFile.getParentFile().mkdirs();
        Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Verify integrity after copy
        String sourceHash = fileHashService.computeSha256(sourceFile);
        String targetHash = fileHashService.computeSha256(targetFile);
        if (!sourceHash.equals(targetHash)) {
            // Copy corruption detected - clean up
            log.error("Hash mismatch after copy: source={}, target={}, hash_source={}, hash_target={}",
                    sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), sourceHash, targetHash);
            targetFile.delete();
            throw new IOException("Hash mismatch after copy - possible data corruption");
        }

        String newUrl = FileUtil.createFileUrl(baseUrl, targetCustomer.getFilesDir(),
                relativePath.replace(File.separator, "/"));
        log.info("Migrated file: {} → {} (hash={})", sourceFile.getAbsolutePath(),
                targetFile.getAbsolutePath(), sourceHash);

        return newUrl;
    }

    /**
     * <p>Gets the base files directory path.</p>
     *
     * @return the base files directory.
     */
    public String getFilesDirectory() {
        return filesDirectory;
    }

    /**
     * <p>Gets the base URL.</p>
     *
     * @return the base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}
