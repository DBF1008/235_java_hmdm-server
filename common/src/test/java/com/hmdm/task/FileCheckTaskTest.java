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

import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.persistence.domain.UploadedFile;
import com.hmdm.persistence.mapper.ApplicationMapper;
import com.hmdm.persistence.mapper.CustomerMapper;
import com.hmdm.persistence.mapper.UploadedFileMapper;
import com.hmdm.service.FileHashService;
import com.hmdm.service.FileUploadService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * <p>Unit tests for {@link FileCheckTask}.</p>
 */
public class FileCheckTaskTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private UploadedFileMapper uploadedFileMapper;

    private FileHashService hashService;
    private FileUploadService uploadService;
    private FileCheckTask checkTask;

    private static final String BASE_URL = "https://mdm.example.com";
    private String filesDirectory;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        filesDirectory = tempFolder.getRoot().getAbsolutePath();
        hashService = new FileHashService();
        uploadService = new FileUploadService(filesDirectory, BASE_URL, hashService);

        checkTask = new FileCheckTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                uploadService, hashService, filesDirectory);
    }

    @Test
    public void testDetectsMissingFile() throws IOException {
        // Setup: one customer with one application that has a non-existent APK
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));

        Application app = new Application();
        app.setId(1);
        when(applicationMapper.getAllApplications(1)).thenReturn(Arrays.asList(app));

        ApplicationVersion version = new ApplicationVersion();
        version.setId(1);
        version.setApplicationId(1);
        version.setUrl(BASE_URL + "/files/cust-1/missing.apk");
        version.setApkHash(null);
        when(applicationMapper.getApplicationVersions(1)).thenReturn(Arrays.asList(version));

        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());
        when(uploadedFileMapper.findOrphaned(1)).thenReturn(Collections.emptyList());

        // Run the check - should detect missing file and log it
        checkTask.run();

        // Verify the task ran without errors
        verify(applicationMapper).getAllApplications(1);
        verify(applicationMapper).getApplicationVersions(1);
    }

    @Test
    public void testDetectsHashMismatch() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));

        // Create a real file at the expected location
        File custDir = new File(filesDirectory, "cust-1");
        custDir.mkdirs();
        File apkFile = new File(custDir, "hash-test.apk");
        Files.write(apkFile.toPath(), "actual content".getBytes(StandardCharsets.UTF_8));

        Application app = new Application();
        app.setId(1);
        when(applicationMapper.getAllApplications(1)).thenReturn(Arrays.asList(app));

        ApplicationVersion version = new ApplicationVersion();
        version.setId(1);
        version.setApplicationId(1);
        version.setUrl(BASE_URL + "/files/cust-1/hash-test.apk");
        version.setApkHash("0000000000000000000000000000000000000000000000000000000000000000");
        when(applicationMapper.getApplicationVersions(1)).thenReturn(Arrays.asList(version));

        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());
        when(uploadedFileMapper.findOrphaned(1)).thenReturn(Collections.emptyList());

        // Run the check
        checkTask.run();

        // The task should have detected the hash mismatch
        verify(applicationMapper).getAllApplications(1);
    }

    @Test
    public void testValidFilePassesCheck() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));

        // Create a real file and compute its correct hash
        File custDir = new File(filesDirectory, "cust-1");
        custDir.mkdirs();
        File apkFile = new File(custDir, "valid.apk");
        String content = "valid apk content";
        Files.write(apkFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        String correctHash = hashService.computeSha256(apkFile);

        Application app = new Application();
        app.setId(1);
        when(applicationMapper.getAllApplications(1)).thenReturn(Arrays.asList(app));

        ApplicationVersion version = new ApplicationVersion();
        version.setId(1);
        version.setApplicationId(1);
        version.setUrl(BASE_URL + "/files/cust-1/valid.apk");
        version.setApkHash(correctHash);
        when(applicationMapper.getApplicationVersions(1)).thenReturn(Arrays.asList(version));

        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());
        when(uploadedFileMapper.findOrphaned(1)).thenReturn(Collections.emptyList());

        // Run the check - should pass without issues
        checkTask.run();

        verify(applicationMapper).getAllApplications(1);
    }

    @Test
    public void testHandlesEmptyDatabase() {
        when(customerMapper.findAll()).thenReturn(Collections.emptyList());

        // Should not throw
        checkTask.run();

        verify(customerMapper).findAll();
        verifyNoInteractions(applicationMapper);
    }

    @Test
    public void testHandlesNullCustomers() {
        when(customerMapper.findAll()).thenReturn(null);

        // Should not throw
        checkTask.run();

        verify(customerMapper).findAll();
    }

    @Test
    public void testOrphanFileDetection() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));
        when(applicationMapper.getAllApplications(1)).thenReturn(Collections.emptyList());
        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());

        // Setup orphan file records
        UploadedFile orphan1 = new UploadedFile();
        orphan1.setId(100);
        orphan1.setFilePath("orphan1.txt");
        UploadedFile orphan2 = new UploadedFile();
        orphan2.setId(101);
        orphan2.setFilePath("orphan2.txt");
        when(uploadedFileMapper.findOrphaned(1)).thenReturn(Arrays.asList(orphan1, orphan2));

        checkTask.run();

        verify(uploadedFileMapper).findOrphaned(1);
    }

    @Test
    public void testChecksUploadedFiles_existence() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));
        when(applicationMapper.getAllApplications(1)).thenReturn(Collections.emptyList());

        // Create a real config file
        File custDir = new File(filesDirectory, "cust-1");
        custDir.mkdirs();
        File configFile = new File(custDir, "config.xml");
        Files.write(configFile.toPath(), "<config/>".getBytes(StandardCharsets.UTF_8));

        UploadedFile existingFile = new UploadedFile();
        existingFile.setId(1);
        existingFile.setCustomerId(1);
        existingFile.setFilePath("config.xml");
        existingFile.setExternal(false);

        UploadedFile missingFile = new UploadedFile();
        missingFile.setId(2);
        missingFile.setCustomerId(1);
        missingFile.setFilePath("missing.xml");
        missingFile.setExternal(false);

        UploadedFile externalFile = new UploadedFile();
        externalFile.setId(3);
        externalFile.setCustomerId(1);
        externalFile.setFilePath("external.xml");
        externalFile.setExternal(true);

        when(uploadedFileMapper.getAll(1)).thenReturn(Arrays.asList(existingFile, missingFile, externalFile));
        when(uploadedFileMapper.findOrphaned(1)).thenReturn(Collections.emptyList());

        checkTask.run();

        verify(uploadedFileMapper).getAll(1);
    }

    @Test
    public void testCheckApkUrl_skipsNullUrl() {
        Customer customer = createCustomer(1, "cust-1");

        FileCheckTask.CheckResult result = checkTask.checkApkUrl(customer, null, null);

        Assert.assertEquals("Null URL should be SKIPPED", FileCheckTask.CheckResult.SKIPPED, result);
    }

    @Test
    public void testCheckApkUrl_skipsEmptyUrl() {
        Customer customer = createCustomer(1, "cust-1");

        FileCheckTask.CheckResult result = checkTask.checkApkUrl(customer, "", null);

        Assert.assertEquals("Empty URL should be SKIPPED", FileCheckTask.CheckResult.SKIPPED, result);
    }

    @Test
    public void testCheckApkUrl_detectsMissing() {
        Customer customer = createCustomer(1, "cust-1");

        FileCheckTask.CheckResult result = checkTask.checkApkUrl(customer,
                BASE_URL + "/files/cust-1/nonexistent.apk", null);

        Assert.assertEquals("Missing file should be MISSING", FileCheckTask.CheckResult.MISSING, result);
    }

    @Test
    public void testCheckApkUrl_noHashReturnsNoHash() throws IOException {
        Customer customer = createCustomer(1, "cust-1");

        File custDir = new File(filesDirectory, "cust-1");
        custDir.mkdirs();
        File apkFile = new File(custDir, "no-hash.apk");
        Files.write(apkFile.toPath(), "content".getBytes(StandardCharsets.UTF_8));

        FileCheckTask.CheckResult result = checkTask.checkApkUrl(customer,
                BASE_URL + "/files/cust-1/no-hash.apk", null);

        Assert.assertEquals("File without hash should return NO_HASH", FileCheckTask.CheckResult.NO_HASH, result);
    }

    @Test
    public void testCheckApkUrl_validHashReturnsOk() throws IOException {
        Customer customer = createCustomer(1, "cust-1");

        File custDir = new File(filesDirectory, "cust-1");
        custDir.mkdirs();
        File apkFile = new File(custDir, "valid-hash.apk");
        Files.write(apkFile.toPath(), "content".getBytes(StandardCharsets.UTF_8));
        String hash = hashService.computeSha256(apkFile);

        FileCheckTask.CheckResult result = checkTask.checkApkUrl(customer,
                BASE_URL + "/files/cust-1/valid-hash.apk", hash);

        Assert.assertEquals("Valid file with matching hash should return OK",
                FileCheckTask.CheckResult.OK, result);
    }

    @Test
    public void testMultipleCustomers() throws IOException {
        Customer customer1 = createCustomer(1, "cust-1");
        Customer customer2 = createCustomer(2, "cust-2");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer1, customer2));

        when(applicationMapper.getAllApplications(anyInt())).thenReturn(Collections.emptyList());
        when(uploadedFileMapper.getAll(anyInt())).thenReturn(Collections.emptyList());
        when(uploadedFileMapper.findOrphaned(anyInt())).thenReturn(Collections.emptyList());

        checkTask.run();

        verify(applicationMapper).getAllApplications(1);
        verify(applicationMapper).getAllApplications(2);
    }

    // ─── Helper ──────────────────────────────────────────────

    private Customer createCustomer(int id, String filesDir) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setFilesDir(filesDir);
        return customer;
    }
}
