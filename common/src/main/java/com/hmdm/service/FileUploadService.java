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
import com.hmdm.persistence.DAOException;
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

/**
 * <p>The single convergence point ("收口") for publishing uploaded files, exposing them for download,
 * relocating them during background migration, and inspecting them during background file checks.</p>
 *
 * <p>Historically these concerns were scattered across {@link FileUtil} static helpers and inline code in the
 * persistence layer (notably {@code ApplicationDAO}), which made it easy to create a DB record while the physical
 * file was not yet in place, or to leave the stored download URL pointing at a file that a migration never actually
 * moved. This service centralizes the contract so that:</p>
 * <ul>
 *     <li>a file's <em>physical location</em> and its <em>download URL / stored relative path</em> are always
 *         derived from one place ({@link #buildFileUrl} / {@link #resolveUrlToRelativePath}), so they cannot drift;</li>
 *     <li>publishing an uploaded file is a single verified operation that returns the physical file together with its
 *         relative path and URL ({@link PublishedFile}), and never silently half-succeeds — see
 *         {@link #publishUploadedFile};</li>
 *     <li>a failed publish/migration is a hard, explicit failure ({@link DAOException}) rather than a {@code null}
 *         return or a swallowed exception, so callers can compensate (e.g. roll back) deterministically;</li>
 *     <li>migration computes the new URL and performs the physical move from the <em>same</em> derivation
 *         ({@link #resolveMigration} + {@link #migrateFileVerified}), so a committed URL always matches a file that
 *         was actually moved;</li>
 *     <li>background inspection can ask whether a record is actually backed by a file
 *         ({@link #isUrlBackedByFile} / {@link #isPublishedFilePresent}).</li>
 * </ul>
 *
 * <p>The service is a Guice {@link Singleton} but is intentionally constructible directly (the constructor merely
 * takes the files directory and the base URL) so it can be unit-tested against a temporary directory without a DI
 * container or a database.</p>
 *
 * @author isv
 */
@Singleton
public class FileUploadService {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadService.class);

    /**
     * <p>A path to a base directory where all the files maintained by the application are stored.</p>
     */
    private final String filesDirectory;

    /**
     * <p>The base URL of the application, used to build and parse public file download URLs.</p>
     */
    private final String baseUrl;

    /**
     * <p>Constructs new <code>FileUploadService</code> instance.</p>
     *
     * @param filesDirectory a path to a base directory where all the files maintained by the application are stored.
     * @param baseUrl the base URL of the application.
     */
    @Inject
    public FileUploadService(@Named("files.directory") String filesDirectory,
                             @Named("base.url") String baseUrl) {
        this.filesDirectory = filesDirectory;
        this.baseUrl = baseUrl;
    }

    //
    // The single URL <-> relative-path contract
    //

    /**
     * <p>Builds the canonical public download URL for a file stored at the specified path relative to the customer's
     * files area. This is the single forward implementation of the URL contract for internal callers.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param relativePath a path to the file relative to the customer's files area (may contain sub-directories).
     * @return the canonical download URL referencing the file.
     */
    public String buildFileUrl(Customer customer, String relativePath) {
        return FileUtil.createFileUrl(this.baseUrl, customer.getFilesDir(), normalizeToUrl(relativePath));
    }

    /**
     * <p>Resolves a public download URL back to a path relative to the customer's files area. This is the single
     * reverse implementation of the URL contract for internal callers.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param url a public download URL.
     * @return the path relative to the customer's files area (using <code>/</code> separators), or <code>null</code>
     *         if the URL does not reference a file in the customer's files area.
     */
    public String resolveUrlToRelativePath(Customer customer, String url) {
        final String path = FileUtil.translateURLToLocalFilePath(customer, url, this.baseUrl);
        return path == null ? null : normalizeToUrl(path);
    }

    //
    // Publishing uploaded files (upload + record creation convergence)
    //

    /**
     * <p>Publishes an uploaded temporary file into the customer's files area, deriving the target file name from the
     * temporary file name. Fails (does not overwrite) if a file with the same name already exists.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param tmpFilePath a path to the uploaded temporary file.
     * @return a {@link PublishedFile} describing the physical file, its relative path and its download URL.
     * @throws FileExistsException if a file with the same name already exists in the customer's files area.
     * @throws DAOException if the file could not be moved into place.
     */
    public PublishedFile publishUploadedFile(Customer customer, String tmpFilePath) {
        return publishUploadedFile(customer, null, tmpFilePath, null, false);
    }

    /**
     * <p>Publishes an uploaded temporary file into the customer's files area, deriving the target file name from the
     * temporary file name.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param tmpFilePath a path to the uploaded temporary file.
     * @param overwrite if <code>true</code>, an existing file with the same name is replaced; if <code>false</code>,
     *                  a {@link FileExistsException} is thrown when the target already exists.
     * @return a {@link PublishedFile} describing the physical file, its relative path and its download URL.
     * @throws FileExistsException if {@code overwrite} is {@code false} and the target already exists.
     * @throws DAOException if the file could not be moved into place.
     */
    public PublishedFile publishUploadedFile(Customer customer, String tmpFilePath, boolean overwrite) {
        return publishUploadedFile(customer, null, tmpFilePath, null, overwrite);
    }

    /**
     * <p>Publishes an uploaded temporary file into the customer's files area.</p>
     *
     * <p>This is the single operation that makes an uploaded file available; on success the returned
     * {@link PublishedFile} carries the physical file, its relative path and its URL all derived from the same place,
     * so callers set their record fields from one consistent source. On failure it throws (it never returns
     * <code>null</code>), so a caller that has already created — or is about to create — a record can compensate.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param localSubdir an optional sub-directory (relative to the customer's files area) to place the file into.
     * @param tmpFilePath a path to the uploaded temporary file.
     * @param newName an optional explicit target file name; if <code>null</code> the name is derived from the
     *                temporary file name.
     * @param overwrite if <code>true</code>, an existing file with the same name is replaced.
     * @return a {@link PublishedFile} describing the physical file, its relative path and its download URL.
     * @throws FileExistsException if {@code overwrite} is {@code false} and the target already exists.
     * @throws DAOException if the file could not be moved into place.
     */
    public PublishedFile publishUploadedFile(Customer customer, String localSubdir, String tmpFilePath,
                                             String newName, boolean overwrite) {
        File movedFile;
        try {
            movedFile = FileUtil.moveFile(customer, this.filesDirectory, localSubdir, tmpFilePath, newName);
        } catch (FileExistsException e) {
            if (!overwrite) {
                throw e;
            }
            // Reproduce the historical "delete then retry" semantics in one place.
            final String targetName = newName != null ? newName : FileUtil.getNameFromTmpPath(tmpFilePath);
            FileUtil.deleteFile(customer, this.filesDirectory, joinRelative(localSubdir, targetName));
            movedFile = FileUtil.moveFile(customer, this.filesDirectory, localSubdir, tmpFilePath, newName);
        }

        if (movedFile == null) {
            throw new DAOException("Could not move the uploaded file into place: " + tmpFilePath);
        }

        final String relativePath = relativizeToCustomerArea(customer, movedFile);
        final String url = buildFileUrl(customer, relativePath);
        return new PublishedFile(movedFile, relativePath, url);
    }

    /**
     * <p>Deletes a published file given its path relative to the customer's files area. Intended for compensating a
     * failed record creation, or for cleaning up a file whose record has been removed.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param relativePath a path to the file relative to the customer's files area.
     * @return <code>true</code> if the file was deleted; <code>false</code> otherwise.
     */
    public boolean deletePublishedFile(Customer customer, String relativePath) {
        return FileUtil.deleteFile(customer, this.filesDirectory, relativePath);
    }

    /**
     * <p>Deletes exactly the file referenced by a previously returned {@link PublishedFile}. This is the compensating
     * action a caller uses to undo a publish when a subsequent step (e.g. a DB insert) fails.</p>
     *
     * @param publishedFile a previously published file.
     * @return <code>true</code> if the file was deleted; <code>false</code> otherwise.
     */
    public boolean deletePublishedFile(PublishedFile publishedFile) {
        if (publishedFile == null || publishedFile.getFile() == null) {
            return false;
        }
        return publishedFile.getFile().delete();
    }

    /**
     * <p>Deletes a published file referenced by a public download URL.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param url a public download URL referencing the file.
     * @return <code>true</code> if the file was deleted; <code>false</code> otherwise (including when the URL does not
     *         reference a file in the customer's files area).
     */
    public boolean deletePublishedFileByUrl(Customer customer, String url) {
        final String relativePath = resolveUrlToRelativePath(customer, url);
        if (relativePath == null) {
            return false;
        }
        return deletePublishedFile(customer, relativePath);
    }

    //
    // Migration (relocating files between customer accounts) convergence
    //

    /**
     * <p>Computes the migration of a file referenced by the given URL from one customer's files area to another. This
     * is a pure, side-effect-free computation safe to run inside a transaction: it returns the new download URL
     * together with the source and target physical files, all derived from the same logic, so the URL written to the
     * record and the file later moved by {@link #migrateFileVerified} cannot disagree.</p>
     *
     * <p>Note that the relative path (including the source customer's directory) is preserved inside the target
     * customer's files area, matching the historical behavior.</p>
     *
     * @param fromCustomer the customer account the file currently belongs to.
     * @param toCustomer the customer account the file is being migrated to.
     * @param url the current download URL of the file.
     * @return a {@link MigrationTarget}, or <code>null</code> if the URL does not reference a file in the source
     *         customer's files area.
     */
    public MigrationTarget resolveMigration(Customer fromCustomer, Customer toCustomer, String url) {
        if (url == null) {
            return null;
        }
        // Here fromCustomer.getFilesDir() is not supposed to be empty because migration works in multi-tenant mode.
        final String fromCustomerDirUrlPart = "/" + fromCustomer.getFilesDir() + "/";
        final int pos = url.indexOf(fromCustomerDirUrlPart);
        if (pos < 0) {
            logger.warn("Cannot migrate file: URL does not contain the source customer files directory: {}", url);
            return null;
        }

        // The relative path intentionally keeps the source customer directory as a prefix.
        final String relativeFilePath = url.substring(pos + 1);
        final File source = new File(this.filesDirectory, relativeFilePath);
        final File toCustomerBaseDir = new File(this.filesDirectory, toCustomer.getFilesDir());
        final File dest = new File(toCustomerBaseDir, relativeFilePath);
        final String newUrl = this.baseUrl + "/files/" + toCustomer.getFilesDir() + "/" + relativeFilePath;

        return new MigrationTarget(newUrl, source, dest);
    }

    /**
     * <p>Physically moves a file from {@code source} to {@code dest}, verifying that the copy succeeded (the target
     * exists and its size matches the source) before removing the source. Unlike the previous best-effort migration
     * loop, this method fails loudly: it throws {@link DAOException} when the source is missing, when the target
     * already exists, or when the copy could not be verified, so a migration cannot silently leave a record pointing
     * at a file that was never moved.</p>
     *
     * @param source the file to move.
     * @param dest the location to move the file to.
     * @throws DAOException if the source is missing or not a regular file, the target already exists, or the move
     *         could not be verified.
     */
    public void migrateFileVerified(File source, File dest) {
        if (source == null || !source.exists()) {
            throw new DAOException("Migration source file does not exist: " + source);
        }
        if (!source.isFile()) {
            throw new DAOException("Migration source is not a regular file: " + source);
        }
        if (dest.exists()) {
            throw new DAOException("Migration target file already exists: " + dest.getAbsolutePath());
        }

        final long sourceLength = source.length();
        try {
            final Path destDir = dest.toPath().getParent();
            if (destDir != null) {
                Files.createDirectories(destDir);
            }
            Files.copy(source.toPath(), dest.toPath());
        } catch (IOException e) {
            throw new DAOException("Failed to copy file during migration: " + source.getAbsolutePath()
                    + " -> " + dest.getAbsolutePath() + " (" + e.getMessage() + ")");
        }

        if (!dest.exists() || dest.length() != sourceLength) {
            // Do not leave a partial/incorrect copy behind, and do not delete the source.
            if (dest.exists() && !dest.delete()) {
                logger.error("Failed to remove an unverified migrated file: {}", dest.getAbsolutePath());
            }
            throw new DAOException("Migration copy verification failed: " + source.getAbsolutePath()
                    + " -> " + dest.getAbsolutePath());
        }

        if (!source.delete()) {
            logger.error("Migrated file copied to {} but the source file {} could not be deleted",
                    dest.getAbsolutePath(), source.getAbsolutePath());
        } else {
            logger.debug("Migrated file: {} -> {}", source.getAbsolutePath(), dest.getAbsolutePath());
        }
    }

    //
    // Background inspection (巡检) convergence
    //

    /**
     * <p>Reports whether the file referenced by the given download URL is actually present on disk. Intended for use
     * by the background file-check task to detect records whose physical file is missing.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param url a public download URL referencing the file.
     * @return <code>true</code> if the URL resolves to a file in the customer's files area and that file exists.
     */
    public boolean isUrlBackedByFile(Customer customer, String url) {
        final String relativePath = resolveUrlToRelativePath(customer, url);
        if (relativePath == null) {
            return false;
        }
        return isPublishedFilePresent(customer, relativePath);
    }

    /**
     * <p>Reports whether a file exists at the given path relative to the customer's files area.</p>
     *
     * @param customer a customer account which the file belongs to.
     * @param relativePath a path to the file relative to the customer's files area.
     * @return <code>true</code> if a regular file exists at that location.
     */
    public boolean isPublishedFilePresent(Customer customer, String relativePath) {
        final File file = new File(customerFilesBaseDir(customer), relativePath.replace("/", File.separator));
        return file.isFile();
    }

    //
    // Helpers
    //

    private File customerFilesBaseDir(Customer customer) {
        final String customerDir = customer.getFilesDir();
        if (customerDir != null && !customerDir.isEmpty()) {
            return new File(this.filesDirectory, customerDir);
        }
        return new File(this.filesDirectory);
    }

    private String relativizeToCustomerArea(Customer customer, File movedFile) {
        final Path base = customerFilesBaseDir(customer).toPath().toAbsolutePath().normalize();
        final Path moved = movedFile.toPath().toAbsolutePath().normalize();
        return base.relativize(moved).toString().replace(File.separator, "/");
    }

    private static String normalizeToUrl(String path) {
        return path == null ? null : path.replace(File.separator, "/");
    }

    private static String joinRelative(String localSubdir, String name) {
        if (localSubdir == null || localSubdir.isEmpty()) {
            return name;
        }
        return localSubdir + "/" + name;
    }

    /**
     * <p>The result of publishing a file: the physical file together with its path relative to the customer's files
     * area and its public download URL, all derived from the same place so they are guaranteed to be consistent.</p>
     */
    public static final class PublishedFile {
        private final File file;
        private final String relativePath;
        private final String url;

        public PublishedFile(File file, String relativePath, String url) {
            this.file = file;
            this.relativePath = relativePath;
            this.url = url;
        }

        /** @return the physical file on disk. */
        public File getFile() {
            return file;
        }

        /** @return the path to the file relative to the customer's files area (using <code>/</code> separators). */
        public String getRelativePath() {
            return relativePath;
        }

        /** @return the canonical public download URL referencing the file. */
        public String getUrl() {
            return url;
        }

        @Override
        public String toString() {
            return "PublishedFile{file=" + file + ", relativePath='" + relativePath + "', url='" + url + "'}";
        }
    }

    /**
     * <p>A computed migration of a single file: the new download URL plus the source and target physical files. The
     * URL and the files are derived from the same logic so that the metadata written to a record and the file later
     * moved on disk cannot disagree.</p>
     */
    public static final class MigrationTarget {
        private final String newUrl;
        private final File source;
        private final File dest;

        public MigrationTarget(String newUrl, File source, File dest) {
            this.newUrl = newUrl;
            this.source = source;
            this.dest = dest;
        }

        /** @return the new download URL for the migrated file. */
        public String getNewUrl() {
            return newUrl;
        }

        /** @return the current location of the file. */
        public File getSource() {
            return source;
        }

        /** @return the location the file is to be moved to. */
        public File getDest() {
            return dest;
        }

        @Override
        public String toString() {
            return "MigrationTarget{newUrl='" + newUrl + "', source=" + source + ", dest=" + dest + "}";
        }
    }
}
