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

package com.hmdm.util;

import com.hmdm.persistence.domain.Customer;
import com.hmdm.test.FileTestSupport;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * <p>Unit tests for {@link FileUtil}.</p>
 */
public class FileUtilTest extends FileTestSupport {

    // ─── writeToFile tests ──────────────────────────────────

    @Test
    public void testWriteToFile_success() throws IOException {
        String content = "Hello, World!";
        File target = tempFolder.newFile("output.txt");
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        FileUtil.writeToFile(is, target.getAbsolutePath());

        String actual = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
        Assert.assertEquals("File content should match", content, actual);
    }

    @Test(expected = IOException.class)
    public void testWriteToFile_propagatesIOException() throws IOException {
        // Try to write to a non-existent directory - should throw IOException
        FileUtil.writeToFile(
                new ByteArrayInputStream("test".getBytes()),
                "/non/existent/directory/file.txt"
        );
    }

    // ─── isSafePath(String) tests ────────────────────────────

    @Test
    public void testIsSafePath_allowsNullPath() {
        Assert.assertTrue("Null path should be safe", FileUtil.isSafePath(null));
    }

    @Test
    public void testIsSafePath_allowsNormalPath() {
        Assert.assertTrue("Normal path should be safe", FileUtil.isSafePath("subdir/file.txt"));
    }

    @Test
    public void testIsSafePath_blocksPathTraversal() {
        Assert.assertFalse("Path with .. should be unsafe", FileUtil.isSafePath("../etc/passwd"));
        Assert.assertFalse("Path with .. should be unsafe", FileUtil.isSafePath("dir/../../etc/passwd"));
        Assert.assertFalse("Path with .. should be unsafe", FileUtil.isSafePath(".."));
    }

    // ─── isSafePath(String, String) tests ────────────────────

    @Test
    public void testIsSafePathTwoArg_allowsPathWithinBase() {
        Assert.assertTrue("Path within base should be safe",
                FileUtil.isSafePath("/tmp/files", "/tmp/files/customer/file.txt"));
    }

    @Test
    public void testIsSafePathTwoArg_blocksPathOutsideBase() {
        Assert.assertFalse("Path escaping base should be unsafe",
                FileUtil.isSafePath("/tmp/files", "/tmp/files/../etc/passwd"));
    }

    @Test
    public void testIsSafePathTwoArg_blocksNullArgs() {
        Assert.assertFalse("Null base should be unsafe",
                FileUtil.isSafePath(null, "/some/path"));
        Assert.assertFalse("Null target should be unsafe",
                FileUtil.isSafePath("/some/base", null));
    }

    @Test
    public void testIsSafePathTwoArg_handlesNormalizedPaths() {
        Assert.assertTrue("Normalized path within base should be safe",
                FileUtil.isSafePath("/tmp/files", "/tmp/files/customer/subdir/../../customer/file.txt"));
    }

    // ─── adjustFileName tests ────────────────────────────────

    @Test
    public void testAdjustFileName_sanitizesSpecialChars() {
        Assert.assertEquals("Spaces should become underscores", "my_file.apk",
                FileUtil.adjustFileName("my file.apk"));
        Assert.assertEquals("Plus should become underscore", "file_name.apk",
                FileUtil.adjustFileName("file+name.apk"));
        Assert.assertEquals("Percent should become underscore", "file_100.apk",
                FileUtil.adjustFileName("file%100.apk"));
        Assert.assertEquals("Parens should be stripped", "file.apk",
                FileUtil.adjustFileName("file(1).apk"));
    }

    @Test
    public void testAdjustFileName_preservesCleanNames() {
        Assert.assertEquals("Clean name should be unchanged", "clean-name_v1.0.apk",
                FileUtil.adjustFileName("clean-name_v1.0.apk"));
    }

    // ─── createFileUrl tests ─────────────────────────────────

    @Test
    public void testCreateFileUrl_withCustomerDir() {
        String url = FileUtil.createFileUrl("https://mdm.example.com", "abc-123", "app.apk");
        Assert.assertEquals("URL should include customer dir",
                "https://mdm.example.com/files/abc-123/app.apk", url);
    }

    @Test
    public void testCreateFileUrl_withoutCustomerDir() {
        String url = FileUtil.createFileUrl("https://mdm.example.com", null, "app.apk");
        Assert.assertEquals("URL should not include customer dir when null",
                "https://mdm.example.com/files/app.apk", url);
    }

    @Test
    public void testCreateFileUrl_withEmptyCustomerDir() {
        String url = FileUtil.createFileUrl("https://mdm.example.com", "", "app.apk");
        Assert.assertEquals("URL should not include customer dir when empty",
                "https://mdm.example.com/files/app.apk", url);
    }

    @Test
    public void testCreateFileUrl_withSubdirectory() {
        String url = FileUtil.createFileUrl("https://mdm.example.com", "cust-dir", "subdir/config.xml");
        Assert.assertEquals("URL should include subdirectory",
                "https://mdm.example.com/files/cust-dir/subdir/config.xml", url);
    }

    // ─── createTempFile tests ────────────────────────────────

    @Test
    public void testCreateTempFile_createsFileWithDelimiter() throws IOException {
        File tmp = FileUtil.createTempFile("myapp");
        Assert.assertTrue("Temp file should exist", tmp.exists());
        Assert.assertTrue("Temp file name should contain delimiter",
                tmp.getName().contains("1111111"));
        Assert.assertTrue("Temp file name should start with original name",
                tmp.getName().startsWith("myapp"));
        tmp.delete();
    }

    // ─── getNameFromTmpPath tests ────────────────────────────

    @Test
    public void testGetNameFromTmpPath_extractsOriginalName() {
        String name = FileUtil.getNameFromTmpPath("/tmp/myapp.apk1111111123456.temp");
        Assert.assertEquals("Should extract original name", "myapp.apk", name);
    }

    @Test(expected = RuntimeException.class)
    public void testGetNameFromTmpPath_throwsOnInvalidPath() {
        FileUtil.getNameFromTmpPath("/tmp/no-delimiter-here.apk");
    }

    // ─── moveFile tests ──────────────────────────────────────

    @Test
    public void testMoveFile_success() throws IOException {
        Customer customer = createCustomerWithDir(1);
        String filesDir = getFilesDirectory();

        // Create a temp file with delimiter
        File tmp = new File(tempFolder.getRoot(), "test.apk111111112345.temp");
        Files.write(tmp.toPath(), "APK content".getBytes(StandardCharsets.UTF_8));

        File moved = FileUtil.moveFile(customer, filesDir, null, tmp.getAbsolutePath());

        Assert.assertNotNull("Moved file should not be null", moved);
        Assert.assertTrue("Moved file should exist", moved.exists());
        Assert.assertFalse("Temp file should be gone", tmp.exists());
        Assert.assertEquals("File content should be preserved",
                "APK content",
                new String(Files.readAllBytes(moved.toPath()), StandardCharsets.UTF_8));
    }

    @Test(expected = FileExistsException.class)
    public void testMoveFile_throwsFileExistsException() throws IOException {
        Customer customer = createCustomerWithDir(1);
        String filesDir = getFilesDirectory();

        // Create target file first
        File targetDir = new File(filesDir, customer.getFilesDir());
        targetDir.mkdirs();
        File existing = new File(targetDir, "existing.apk");
        Files.write(existing.toPath(), "existing".getBytes());

        // Create temp file
        File tmp = new File(tempFolder.getRoot(), "existing.apk111111112345.temp");
        Files.write(tmp.toPath(), "new content".getBytes());

        // Should throw FileExistsException
        FileUtil.moveFile(customer, filesDir, null, tmp.getAbsolutePath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMoveFile_rejectsPathTraversal() throws IOException {
        Customer customer = createCustomer(1, "../../etc");
        String filesDir = getFilesDirectory();

        File tmp = new File(tempFolder.getRoot(), "evil.apk111111112345.temp");
        Files.write(tmp.toPath(), "evil content".getBytes());

        FileUtil.moveFile(customer, filesDir, null, tmp.getAbsolutePath());
    }

    // ─── translateURLToLocalFilePath tests ───────────────────

    @Test
    public void testTranslateURLToLocalFilePath_success() {
        Customer customer = createCustomer(1, "cust-uuid");
        String result = FileUtil.translateURLToLocalFilePath(customer,
                "https://mdm.example.com/files/cust-uuid/myapp.apk",
                "https://mdm.example.com");
        Assert.assertEquals("Should extract relative path", "myapp.apk", result);
    }

    @Test
    public void testTranslateURLToLocalFilePath_withSubdir() {
        Customer customer = createCustomer(1, "cust-uuid");
        String result = FileUtil.translateURLToLocalFilePath(customer,
                "https://mdm.example.com/files/cust-uuid/subdir/config.xml",
                "https://mdm.example.com");
        Assert.assertEquals("Should extract relative path with subdir",
                "subdir" + File.separator + "config.xml", result);
    }

    @Test
    public void testTranslateURLToLocalFilePath_returnsNullForMismatch() {
        Customer customer = createCustomer(1, "cust-uuid");
        String result = FileUtil.translateURLToLocalFilePath(customer,
                "https://other.server.com/files/something.apk",
                "https://mdm.example.com");
        Assert.assertNull("Should return null for non-matching URL", result);
    }

    // ─── deleteFile tests ────────────────────────────────────

    @Test
    public void testDeleteFile_success() throws IOException {
        Customer customer = createCustomerWithDir(1);
        String filesDir = getFilesDirectory();

        // Create a file in the customer directory
        File customerDir = new File(filesDir, customer.getFilesDir());
        File toDelete = new File(customerDir, "to-delete.txt");
        Files.write(toDelete.toPath(), "delete me".getBytes());

        boolean result = FileUtil.deleteFile(customer, filesDir, "to-delete.txt");
        Assert.assertTrue("Delete should succeed", result);
        Assert.assertFalse("File should be gone", toDelete.exists());
    }

    @Test
    public void testDeleteFile_returnsFalseForMissingFile() {
        Customer customer = createCustomerWithDir(1);
        String filesDir = getFilesDirectory();

        boolean result = FileUtil.deleteFile(customer, filesDir, "nonexistent.txt");
        Assert.assertFalse("Delete should return false for missing file", result);
    }
}
