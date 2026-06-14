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

import com.hmdm.persistence.domain.Customer;
import com.hmdm.test.FileTestSupport;
import com.hmdm.util.FileExistsException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * <p>Unit tests for {@link FileUploadService}.</p>
 */
public class FileUploadServiceTest extends FileTestSupport {

    private static final String BASE_URL = "https://mdm.example.com";

    private FileUploadService uploadService;
    private FileHashService hashService;

    @Before
    public void setUp() {
        hashService = new FileHashService();
        uploadService = new FileUploadService(getFilesDirectory(), BASE_URL, hashService);
    }

    // ─── publishApkFile tests ────────────────────────────────

    @Test
    public void testPublishApkFile_success() throws IOException {
        Customer customer = createCustomerWithDir(1);

        // Create a temp file with the expected delimiter
        File tmp = createTmpWithDelimiter("myapp.apk", "APK binary content");

        PublishedFile result = uploadService.publishApkFile(customer, tmp.getAbsolutePath());

        Assert.assertNotNull("Result should not be null", result);
        Assert.assertNotNull("URL should be set", result.getUrl());
        Assert.assertTrue("URL should contain base URL",
                result.getUrl().startsWith(BASE_URL));
        Assert.assertTrue("URL should contain customer dir",
                result.getUrl().contains(customer.getFilesDir()));
        Assert.assertNotNull("Hash should be set", result.getSha256Hash());
        Assert.assertEquals("Hash should be 64 chars", 64, result.getSha256Hash().length());
        Assert.assertNotNull("File name should be set", result.getFileName());
        Assert.assertEquals("File name should be myapp.apk", "myapp.apk", result.getFileName());
        Assert.assertNotNull("Physical file should be set", result.getPhysicalFile());
        Assert.assertTrue("Physical file should exist", result.getPhysicalFile().exists());
        Assert.assertFalse("Temp file should be gone", tmp.exists());
    }

    @Test
    public void testPublishApkFile_hashIsCorrect() throws IOException {
        Customer customer = createCustomerWithDir(1);
        String content = "APK binary content for hash verification";
        File tmp = createTmpWithDelimiter("hashtest.apk", content);

        PublishedFile result = uploadService.publishApkFile(customer, tmp.getAbsolutePath());

        // Independently compute the hash to verify
        String expectedHash = hashService.computeSha256(result.getPhysicalFile());
        Assert.assertEquals("Published hash should match independently computed hash",
                expectedHash, result.getSha256Hash());
    }

    @Test
    public void testPublishApkFile_urlIsCorrect() throws IOException {
        Customer customer = createCustomerWithDir(1);
        File tmp = createTmpWithDelimiter("test-app.apk", "content");

        PublishedFile result = uploadService.publishApkFile(customer, tmp.getAbsolutePath());

        String expectedUrl = BASE_URL + "/files/" + customer.getFilesDir() + "/test-app.apk";
        Assert.assertEquals("URL should follow expected pattern", expectedUrl, result.getUrl());
    }

    @Test
    public void testPublishApkFile_fileExistsConflict() throws IOException {
        Customer customer = createCustomerWithDir(1);

        // Pre-create a file with the same name at the destination
        File destDir = new File(getFilesDirectory(), customer.getFilesDir());
        destDir.mkdirs();
        File existing = new File(destDir, "duplicate.apk");
        Files.write(existing.toPath(), "old content".getBytes(StandardCharsets.UTF_8));

        // Create a temp file with the same name
        File tmp = createTmpWithDelimiter("duplicate.apk", "new content");

        // Should succeed because FileUploadService handles FileExistsException by delete+retry
        PublishedFile result = uploadService.publishApkFile(customer, tmp.getAbsolutePath());
        Assert.assertNotNull("Should succeed despite conflict", result);

        String actualContent = new String(Files.readAllBytes(result.getPhysicalFile().toPath()),
                StandardCharsets.UTF_8);
        Assert.assertEquals("Content should be the new content", "new content", actualContent);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPublishApkFile_nullTmpPath() throws IOException {
        Customer customer = createCustomerWithDir(1);
        uploadService.publishApkFile(customer, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPublishApkFile_emptyTmpPath() throws IOException {
        Customer customer = createCustomerWithDir(1);
        uploadService.publishApkFile(customer, "");
    }

    @Test(expected = IOException.class)
    public void testPublishApkFile_nonExistentFile() throws IOException {
        Customer customer = createCustomerWithDir(1);
        uploadService.publishApkFile(customer, "/non/existent/file.apk");
    }

    // ─── publishConfigFile tests ─────────────────────────────

    @Test
    public void testPublishConfigFile_success() throws IOException {
        Customer customer = createCustomerWithDir(1);
        File tmp = createTmpWithDelimiter("config.xml", "<config>data</config>");

        PublishedFile result = uploadService.publishConfigFile(customer, tmp.getAbsolutePath(), "configs");

        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("URL should contain subdir",
                result.getUrl().contains("configs/"));
        Assert.assertTrue("Physical file should be in subdir",
                result.getPhysicalFile().getAbsolutePath().contains("configs"));
    }

    @Test
    public void testPublishConfigFile_withoutSubdir() throws IOException {
        Customer customer = createCustomerWithDir(1);
        File tmp = createTmpWithDelimiter("config.xml", "<config>data</config>");

        PublishedFile result = uploadService.publishConfigFile(customer, tmp.getAbsolutePath(), null);

        Assert.assertNotNull("Result should not be null", result);
        Assert.assertFalse("URL should not contain extra subdir",
                result.getUrl().contains("//config"));
    }

    // ─── unpublishFile tests ─────────────────────────────────

    @Test
    public void testUnpublishFile_deletesPhysicalFile() throws IOException {
        Customer customer = createCustomerWithDir(1);

        // Create a file in the customer directory
        File customerDir = new File(getFilesDirectory(), customer.getFilesDir());
        File toDelete = new File(customerDir, "to-delete.apk");
        Files.write(toDelete.toPath(), "delete me".getBytes(StandardCharsets.UTF_8));

        boolean result = uploadService.unpublishFile(customer, "to-delete.apk");

        Assert.assertTrue("Unpublish should succeed", result);
        Assert.assertFalse("File should be deleted", toDelete.exists());
    }

    @Test
    public void testUnpublishFile_handlesMissingFile() {
        Customer customer = createCustomerWithDir(1);

        boolean result = uploadService.unpublishFile(customer, "nonexistent.apk");

        Assert.assertFalse("Unpublish should return false for missing file", result);
    }

    @Test
    public void testUnpublishFile_rejectsNullPath() {
        Customer customer = createCustomerWithDir(1);

        boolean result = uploadService.unpublishFile(customer, null);

        Assert.assertFalse("Unpublish should return false for null path", result);
    }

    @Test
    public void testUnpublishFile_rejectsPathTraversal() throws IOException {
        Customer customer = createCustomerWithDir(1);

        boolean result = uploadService.unpublishFile(customer, "../../etc/passwd");

        Assert.assertFalse("Unpublish should reject path traversal", result);
    }

    // ─── resolvePhysicalFile tests ───────────────────────────

    @Test
    public void testResolvePhysicalFile_success() throws IOException {
        Customer customer = createCustomerWithDir(1);

        // Create a file in the customer directory
        File customerDir = new File(getFilesDirectory(), customer.getFilesDir());
        File target = new File(customerDir, "resolve-test.apk");
        Files.write(target.toPath(), "test content".getBytes(StandardCharsets.UTF_8));

        String url = BASE_URL + "/files/" + customer.getFilesDir() + "/resolve-test.apk";
        File resolved = uploadService.resolvePhysicalFile(customer, url);

        Assert.assertNotNull("Resolved file should not be null", resolved);
        Assert.assertTrue("Resolved file should exist", resolved.exists());
    }

    @Test
    public void testResolvePhysicalFile_returnsNullForNullUrl() {
        Customer customer = createCustomerWithDir(1);

        File resolved = uploadService.resolvePhysicalFile(customer, null);

        Assert.assertNull("Should return null for null URL", resolved);
    }

    @Test
    public void testResolvePhysicalFile_returnsNullForMismatchedUrl() {
        Customer customer = createCustomerWithDir(1);

        File resolved = uploadService.resolvePhysicalFile(customer,
                "https://other.server.com/files/something.apk");

        Assert.assertNull("Should return null for non-matching URL", resolved);
    }

    // ─── verifyFileIntegrity tests ───────────────────────────

    @Test
    public void testVerifyFileIntegrity_validFile() throws IOException {
        Customer customer = createCustomerWithDir(1);
        String content = "integrity test content";

        // Publish a file to get a known hash
        File tmp = createTmpWithDelimiter("integrity.apk", content);
        PublishedFile published = uploadService.publishApkFile(customer, tmp.getAbsolutePath());

        // Verify the published file
        boolean valid = uploadService.verifyFileIntegrity(customer, published.getUrl(),
                published.getSha256Hash());

        Assert.assertTrue("Integrity check should pass for valid file", valid);
    }

    @Test
    public void testVerifyFileIntegrity_corruptFile() throws IOException {
        Customer customer = createCustomerWithDir(1);

        // Publish a file
        File tmp = createTmpWithDelimiter("corrupt-test.apk", "original content");
        PublishedFile published = uploadService.publishApkFile(customer, tmp.getAbsolutePath());

        // Tamper with the file
        Files.write(published.getPhysicalFile().toPath(), "tampered content".getBytes(StandardCharsets.UTF_8));

        // Verify should detect corruption
        boolean valid = uploadService.verifyFileIntegrity(customer, published.getUrl(),
                published.getSha256Hash());

        Assert.assertFalse("Integrity check should fail for corrupted file", valid);
    }

    @Test
    public void testVerifyFileIntegrity_missingFile() throws IOException {
        Customer customer = createCustomerWithDir(1);

        String url = BASE_URL + "/files/" + customer.getFilesDir() + "/missing.apk";
        boolean valid = uploadService.verifyFileIntegrity(customer, url, "somehash");

        Assert.assertFalse("Integrity check should fail for missing file", valid);
    }

    // ─── migrateFile tests ───────────────────────────────────

    @Test
    public void testMigrateFile_success() throws IOException {
        Customer sourceCustomer = createCustomerWithDir(1);
        Customer targetCustomer = createCustomerWithDir(2);

        // Publish a file to the source
        File tmp = createTmpWithDelimiter("migrate-me.apk", "migration content");
        PublishedFile published = uploadService.publishApkFile(sourceCustomer, tmp.getAbsolutePath());

        // Migrate to target
        String newUrl = uploadService.migrateFile(sourceCustomer, published.getUrl(), targetCustomer);

        Assert.assertNotNull("New URL should not be null", newUrl);
        Assert.assertTrue("New URL should reference target customer dir",
                newUrl.contains(targetCustomer.getFilesDir()));
        Assert.assertTrue("Source file should still exist", published.getPhysicalFile().exists());

        // Verify target file has correct content
        File targetFile = uploadService.resolvePhysicalFile(targetCustomer, newUrl);
        Assert.assertNotNull("Target file should be resolvable", targetFile);
        Assert.assertTrue("Target file should exist", targetFile.exists());
        String content = new String(Files.readAllBytes(targetFile.toPath()), StandardCharsets.UTF_8);
        Assert.assertEquals("Content should match", "migration content", content);
    }

    @Test
    public void testMigrateFile_idempotent() throws IOException {
        Customer sourceCustomer = createCustomerWithDir(1);
        Customer targetCustomer = createCustomerWithDir(2);

        // Publish and migrate
        File tmp = createTmpWithDelimiter("idempotent.apk", "content");
        PublishedFile published = uploadService.publishApkFile(sourceCustomer, tmp.getAbsolutePath());
        String url1 = uploadService.migrateFile(sourceCustomer, published.getUrl(), targetCustomer);

        // Migrate again - should be idempotent
        String url2 = uploadService.migrateFile(sourceCustomer, published.getUrl(), targetCustomer);

        Assert.assertEquals("URLs should be the same on idempotent re-run", url1, url2);
    }

    // ─── Helper methods ──────────────────────────────────────

    /**
     * Creates a temp file with the FileUtil delimiter pattern.
     */
    private File createTmpWithDelimiter(String name, String content) throws IOException {
        File tmp = new File(tempFolder.getRoot(), name + "1111111" + System.nanoTime() + ".temp");
        Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return tmp;
    }
}
