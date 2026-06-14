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

import com.hmdm.persistence.DAOException;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.service.FileUploadService.MigrationTarget;
import com.hmdm.service.FileUploadService.PublishedFile;
import com.hmdm.util.FileExistsException;
import com.hmdm.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * <p>A test suite for {@link FileUploadService}, the single convergence point for publishing, exposing, migrating and
 * inspecting files. The suite operates against a real temporary directory and exercises the upload, migration and
 * background-inspection scenarios without a database or a DI container.</p>
 *
 * @author isv
 */
public class FileUploadServiceTest {

    private static final String BASE_URL = "http://localhost";
    private static final String CUSTOMER_DIR = "d12345";

    private Path root;
    private FileUploadService service;
    private Customer customer;          // multi-tenant customer (non-empty files directory)
    private Customer singleTenant;      // single-tenant customer (empty files directory)

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("hmdm-files-test");
        service = new FileUploadService(root.toString(), BASE_URL);
        customer = newCustomer(CUSTOMER_DIR);
        singleTenant = newCustomer("");
    }

    @After
    public void tearDown() {
        deleteRecursively(root.toFile());
    }

    //
    // Upload / publish (upload + record-creation convergence)
    //

    @Test
    public void publish_landsAtExpectedPath_multiTenant() throws IOException {
        final PublishedFile published = service.publishUploadedFile(customer, newUpload("app.apk", "payload"));

        assertEquals("app.apk", published.getRelativePath());
        assertEquals(new File(root.toFile(), CUSTOMER_DIR + File.separator + "app.apk").getAbsolutePath(),
                published.getFile().getAbsolutePath());
        assertTrue("Published file must exist on disk", published.getFile().isFile());
    }

    @Test
    public void publish_landsAtExpectedPath_singleTenant() throws IOException {
        final PublishedFile published = service.publishUploadedFile(singleTenant, newUpload("app.apk", "payload"));

        assertEquals("app.apk", published.getRelativePath());
        assertEquals(new File(root.toFile(), "app.apk").getAbsolutePath(),
                published.getFile().getAbsolutePath());
        assertTrue(published.getFile().isFile());
    }

    @Test
    public void publish_urlMatchesRelativePath() throws IOException {
        // The convergence invariant: the URL carried by a published file is always exactly what the same service
        // would build from that file's relative path. This is what guarantees download URL and physical file agree.
        final PublishedFile published = service.publishUploadedFile(customer, newUpload("app.apk", "payload"));

        assertEquals(service.buildFileUrl(customer, published.getRelativePath()), published.getUrl());
        assertEquals(BASE_URL + "/files/" + CUSTOMER_DIR + "/app.apk", published.getUrl());
    }

    @Test
    public void publish_targetExists_throwsFileExists() throws IOException {
        service.publishUploadedFile(customer, newUpload("app.apk", "first"));
        try {
            service.publishUploadedFile(customer, newUpload("app.apk", "second"));
            fail("Expected FileExistsException when the target already exists and overwrite is false");
        } catch (FileExistsException expected) {
            // expected
        }
    }

    @Test
    public void publish_overwriteReplacesExisting() throws IOException {
        service.publishUploadedFile(customer, newUpload("app.apk", "first"));
        final PublishedFile published = service.publishUploadedFile(customer, newUpload("app.apk", "second"), true);

        assertTrue(published.getFile().isFile());
        assertEquals("second", readFile(published.getFile()));
    }

    @Test
    public void publish_moveFailure_throwsDAOException_notNull() throws IOException {
        // A temporary file path that has the expected delimiter but no longer exists: the move cannot succeed, and the
        // service must surface a hard failure (never a null result, never a half-published file).
        final File tmp = FileUtil.createTempFile("gone.apk");
        final String tmpPath = tmp.getAbsolutePath();
        assertTrue(tmp.delete());

        try {
            service.publishUploadedFile(customer, tmpPath);
            fail("Expected DAOException when the uploaded file cannot be moved into place");
        } catch (DAOException expected) {
            // expected
        }
        assertFalse("No file should be left behind after a failed publish",
                new File(root.toFile(), CUSTOMER_DIR + File.separator + "gone.apk").exists());
    }

    //
    // URL <-> relative-path contract (download exposure)
    //

    @Test
    public void urlRoundTrip_withCustomerDir() {
        final String url = service.buildFileUrl(customer, "sub/app.apk");
        assertEquals(BASE_URL + "/files/" + CUSTOMER_DIR + "/sub/app.apk", url);
        assertEquals("sub/app.apk", service.resolveUrlToRelativePath(customer, url));
    }

    @Test
    public void urlRoundTrip_withoutCustomerDir() {
        final String url = service.buildFileUrl(singleTenant, "sub/app.apk");
        assertEquals(BASE_URL + "/files/sub/app.apk", url);
        assertEquals("sub/app.apk", service.resolveUrlToRelativePath(singleTenant, url));
    }

    @Test
    public void urlRoundTrip_doubledSlashIsNotDeduplicated() {
        // Some stored URLs carry a doubled slash (see ApplicationDAO#getAllApplicationsByUrl). The contract does not
        // silently normalize it - de-duplication is the responsibility of the query layer, not the file service.
        final String url = BASE_URL + "/files/" + CUSTOMER_DIR + "//app.apk";
        assertEquals("/app.apk", service.resolveUrlToRelativePath(customer, url));
    }

    @Test
    public void resolveUrlToRelativePath_foreignUrl_returnsNull() {
        assertEquals(null, service.resolveUrlToRelativePath(customer, "https://example.com/somewhere/app.apk"));
    }

    //
    // Cleanup / compensation (no orphan files)
    //

    @Test
    public void compensatingDelete_removesPublishedFile() throws IOException {
        final PublishedFile published = service.publishUploadedFile(customer, newUpload("app.apk", "payload"));
        assertTrue(published.getFile().isFile());

        assertTrue(service.deletePublishedFile(published));
        assertFalse(published.getFile().exists());
    }

    @Test
    public void deleteByUrl_removesPublishedFile() throws IOException {
        final PublishedFile published = service.publishUploadedFile(customer, newUpload("app.apk", "payload"));

        assertTrue(service.deletePublishedFileByUrl(customer, published.getUrl()));
        assertFalse(published.getFile().exists());
    }

    //
    // Migration (relocating files between customers) - the download-URL / metadata consistency guard
    //

    @Test
    public void migration_movesFileAndPreservesSourceDirInPath() throws IOException {
        final Customer from = newCustomer("d111");
        final Customer to = newCustomer("d999");
        final File source = seedFile("d111/sub/app.apk", "payload");
        final String url = BASE_URL + "/files/d111/sub/app.apk";

        final MigrationTarget migration = service.resolveMigration(from, to, url);
        assertNotNull(migration);

        service.migrateFileVerified(migration.getSource(), migration.getDest());

        final File expectedNewFile = new File(root.toFile(), "d999/d111/sub/app.apk".replace("/", File.separator));
        assertTrue("Migrated file must exist at the new location", expectedNewFile.isFile());
        assertFalse("Source file must be gone after migration", source.exists());
        assertEquals("payload", readFile(expectedNewFile));
        // The new URL must match what the contract builds for the migrated file (source dir kept inside the path).
        assertEquals(service.buildFileUrl(to, "d111/sub/app.apk"), migration.getNewUrl());
    }

    @Test
    public void migration_committedUrlIsBackedByTheMovedFile() throws IOException {
        // The structural guard against "URL committed but file not moved": after the verified move, the URL that the
        // record would have stored resolves to an existing file.
        final Customer from = newCustomer("d111");
        final Customer to = newCustomer("d999");
        seedFile("d111/app.apk", "payload");
        final String url = BASE_URL + "/files/d111/app.apk";

        final MigrationTarget migration = service.resolveMigration(from, to, url);
        service.migrateFileVerified(migration.getSource(), migration.getDest());

        assertTrue(service.isUrlBackedByFile(to, migration.getNewUrl()));
    }

    @Test
    public void migration_sourceMissing_throwsAndCreatesNothing() throws IOException {
        final Customer from = newCustomer("d111");
        final Customer to = newCustomer("d999");
        final String url = BASE_URL + "/files/d111/missing.apk";

        final MigrationTarget migration = service.resolveMigration(from, to, url);
        assertNotNull(migration);
        try {
            service.migrateFileVerified(migration.getSource(), migration.getDest());
            fail("Expected DAOException when the migration source file does not exist");
        } catch (DAOException expected) {
            // expected
        }
        assertFalse("No target file may be created when the source is missing", migration.getDest().exists());
    }

    @Test
    public void migration_targetExists_throws() throws IOException {
        final Customer from = newCustomer("d111");
        final Customer to = newCustomer("d999");
        seedFile("d111/app.apk", "payload");
        seedFile("d999/d111/app.apk", "already there");
        final String url = BASE_URL + "/files/d111/app.apk";

        final MigrationTarget migration = service.resolveMigration(from, to, url);
        try {
            service.migrateFileVerified(migration.getSource(), migration.getDest());
            fail("Expected DAOException when the migration target already exists");
        } catch (DAOException expected) {
            // expected
        }
    }

    @Test
    public void resolveMigration_foreignUrl_returnsNull() {
        final Customer from = newCustomer("d111");
        final Customer to = newCustomer("d999");
        assertEquals(null, service.resolveMigration(from, to, BASE_URL + "/files/dXXX/app.apk"));
        assertEquals(null, service.resolveMigration(from, to, null));
    }

    //
    // Background inspection (巡检)
    //

    @Test
    public void check_presentFile_isDetected() throws IOException {
        final PublishedFile published = service.publishUploadedFile(customer, newUpload("app.apk", "payload"));

        assertTrue(service.isUrlBackedByFile(customer, published.getUrl()));
        assertTrue(service.isPublishedFilePresent(customer, published.getRelativePath()));
    }

    @Test
    public void check_missingFile_isDetected() {
        final String url = service.buildFileUrl(customer, "never-uploaded.apk");

        assertFalse(service.isUrlBackedByFile(customer, url));
        assertFalse(service.isPublishedFilePresent(customer, "never-uploaded.apk"));
    }

    //
    // Helpers
    //

    private static Customer newCustomer(String filesDir) {
        final Customer c = new Customer();
        c.setFilesDir(filesDir);
        return c;
    }

    /**
     * <p>Creates an uploaded temporary file with the delimiter that {@link FileUtil#getNameFromTmpPath(String)}
     * expects, and returns its path. This mimics how the web layer hands an uploaded file to the service.</p>
     */
    private static String newUpload(String name, String content) throws IOException {
        final File tmp = FileUtil.createTempFile(name);
        Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return tmp.getAbsolutePath();
    }

    /**
     * <p>Creates a file at the given path relative to the files root (used to seed migration sources/targets).</p>
     */
    private File seedFile(String relativePath, String content) throws IOException {
        final File file = new File(root.toFile(), relativePath.replace("/", File.separator));
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(File file) {
        if (file == null) {
            return;
        }
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        // Best effort: this is test cleanup.
        file.delete();
    }
}
