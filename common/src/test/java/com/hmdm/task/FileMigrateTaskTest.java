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
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * <p>Unit tests for {@link FileMigrateTask}.</p>
 */
public class FileMigrateTaskTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private UploadedFileMapper uploadedFileMapper;

    private FileHashService hashService;
    private String sourceDirectory;
    private String targetDirectory;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        hashService = new FileHashService();

        File sourceDir = tempFolder.newFolder("source");
        File targetDir = tempFolder.newFolder("target");
        sourceDirectory = sourceDir.getAbsolutePath();
        targetDirectory = targetDir.getAbsolutePath();
    }

    @Test
    public void testSuccessfulMigration() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));

        // Create source file
        File custSourceDir = new File(sourceDirectory, "cust-1");
        custSourceDir.mkdirs();
        File sourceFile = new File(custSourceDir, "migrate-me.apk");
        String content = "migration content";
        Files.write(sourceFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

        // Setup mock data
        Application app = new Application();
        app.setId(1);
        when(applicationMapper.getAllApplications(1)).thenReturn(Arrays.asList(app));

        ApplicationVersion version = new ApplicationVersion();
        version.setId(1);
        version.setApplicationId(1);
        version.setUrl("https://mdm.example.com/files/cust-1/migrate-me.apk");
        when(applicationMapper.getApplicationVersions(1)).thenReturn(Arrays.asList(version));

        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());

        FileMigrateTask migrateTask = new FileMigrateTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                hashService, sourceDirectory, targetDirectory);

        migrateTask.run();

        // Verify: target file should exist
        File targetFile = new File(new File(targetDirectory, "cust-1"), "migrate-me.apk");
        Assert.assertTrue("Target file should exist after migration", targetFile.exists());

        // Verify: source file should be deleted
        Assert.assertFalse("Source file should be deleted after migration", sourceFile.exists());

        // Verify: content matches
        String targetContent = new String(Files.readAllBytes(targetFile.toPath()), StandardCharsets.UTF_8);
        Assert.assertEquals("Content should match", content, targetContent);
    }

    @Test
    public void testIdempotentReRun() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));

        // Create source and target files (both exist - simulates prior migration)
        File custSourceDir = new File(sourceDirectory, "cust-1");
        custSourceDir.mkdirs();
        File sourceFile = new File(custSourceDir, "already-migrated.apk");
        Files.write(sourceFile.toPath(), "content".getBytes(StandardCharsets.UTF_8));

        File custTargetDir = new File(targetDirectory, "cust-1");
        custTargetDir.mkdirs();
        File targetFile = new File(custTargetDir, "already-migrated.apk");
        Files.write(targetFile.toPath(), "content".getBytes(StandardCharsets.UTF_8));

        Application app = new Application();
        app.setId(1);
        when(applicationMapper.getAllApplications(1)).thenReturn(Arrays.asList(app));

        ApplicationVersion version = new ApplicationVersion();
        version.setId(1);
        version.setApplicationId(1);
        version.setUrl("https://mdm.example.com/files/cust-1/already-migrated.apk");
        when(applicationMapper.getApplicationVersions(1)).thenReturn(Arrays.asList(version));

        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());

        FileMigrateTask migrateTask = new FileMigrateTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                hashService, sourceDirectory, targetDirectory);

        migrateTask.run();

        // Both files should still exist (source wasn't touched because target already exists)
        Assert.assertTrue("Source file should still exist (idempotent skip)", sourceFile.exists());
        Assert.assertTrue("Target file should still exist", targetFile.exists());
    }

    @Test
    public void testMissingSourceFile() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));

        // Don't create any source file

        Application app = new Application();
        app.setId(1);
        when(applicationMapper.getAllApplications(1)).thenReturn(Arrays.asList(app));

        ApplicationVersion version = new ApplicationVersion();
        version.setId(1);
        version.setApplicationId(1);
        version.setUrl("https://mdm.example.com/files/cust-1/nonexistent.apk");
        when(applicationMapper.getApplicationVersions(1)).thenReturn(Arrays.asList(version));

        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());

        FileMigrateTask migrateTask = new FileMigrateTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                hashService, sourceDirectory, targetDirectory);

        // Should not throw
        migrateTask.run();

        // Target file should not exist
        File targetFile = new File(new File(targetDirectory, "cust-1"), "nonexistent.apk");
        Assert.assertFalse("Target should not exist when source is missing", targetFile.exists());
    }

    @Test
    public void testHandlesEmptyDatabase() {
        when(customerMapper.findAll()).thenReturn(Collections.emptyList());

        FileMigrateTask migrateTask = new FileMigrateTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                hashService, sourceDirectory, targetDirectory);

        // Should not throw
        migrateTask.run();

        verify(customerMapper).findAll();
        verifyNoInteractions(applicationMapper);
    }

    @Test
    public void testMigratesConfigFiles() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));

        // Create source config file
        File custSourceDir = new File(sourceDirectory, "cust-1");
        custSourceDir.mkdirs();
        File sourceConfig = new File(custSourceDir, "config.xml");
        String configContent = "<config>data</config>";
        Files.write(sourceConfig.toPath(), configContent.getBytes(StandardCharsets.UTF_8));

        when(applicationMapper.getAllApplications(1)).thenReturn(Collections.emptyList());

        UploadedFile configFile = new UploadedFile();
        configFile.setId(1);
        configFile.setCustomerId(1);
        configFile.setFilePath("config.xml");
        configFile.setExternal(false);
        when(uploadedFileMapper.getAll(1)).thenReturn(Arrays.asList(configFile));

        FileMigrateTask migrateTask = new FileMigrateTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                hashService, sourceDirectory, targetDirectory);

        migrateTask.run();

        // Verify target config file exists
        File targetConfig = new File(new File(targetDirectory, "cust-1"), "config.xml");
        Assert.assertTrue("Target config file should exist", targetConfig.exists());
        Assert.assertFalse("Source config file should be deleted", sourceConfig.exists());

        String targetContent = new String(Files.readAllBytes(targetConfig.toPath()), StandardCharsets.UTF_8);
        Assert.assertEquals("Config content should match", configContent, targetContent);
    }

    @Test
    public void testSkipsExternalFiles() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));
        when(applicationMapper.getAllApplications(1)).thenReturn(Collections.emptyList());

        UploadedFile externalFile = new UploadedFile();
        externalFile.setId(1);
        externalFile.setCustomerId(1);
        externalFile.setFilePath("external.xml");
        externalFile.setExternal(true);
        externalFile.setExternalUrl("https://external.server.com/file.xml");
        when(uploadedFileMapper.getAll(1)).thenReturn(Arrays.asList(externalFile));

        FileMigrateTask migrateTask = new FileMigrateTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                hashService, sourceDirectory, targetDirectory);

        migrateTask.run();

        // No files should be created
        File targetDir = new File(targetDirectory, "cust-1");
        Assert.assertFalse("Target dir should not exist for external-only files",
                targetDir.exists() && targetDir.listFiles().length > 0);
    }

    @Test
    public void testNullUrlSkipped() throws IOException {
        Customer customer = createCustomer(1, "cust-1");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(customer));

        Application app = new Application();
        app.setId(1);
        when(applicationMapper.getAllApplications(1)).thenReturn(Arrays.asList(app));

        ApplicationVersion version = new ApplicationVersion();
        version.setId(1);
        version.setApplicationId(1);
        version.setUrl(null); // null URL
        when(applicationMapper.getApplicationVersions(1)).thenReturn(Arrays.asList(version));

        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());

        FileMigrateTask migrateTask = new FileMigrateTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                hashService, sourceDirectory, targetDirectory);

        // Should not throw
        migrateTask.run();
    }

    @Test
    public void testMultipleCustomers() throws IOException {
        Customer cust1 = createCustomer(1, "cust-1");
        Customer cust2 = createCustomer(2, "cust-2");
        when(customerMapper.findAll()).thenReturn(Arrays.asList(cust1, cust2));

        // Create source files for both customers
        for (String custDir : new String[]{"cust-1", "cust-2"}) {
            File custSourceDir = new File(sourceDirectory, custDir);
            custSourceDir.mkdirs();
            File sourceFile = new File(custSourceDir, "app.apk");
            Files.write(sourceFile.toPath(), ("content-" + custDir).getBytes(StandardCharsets.UTF_8));
        }

        Application app1 = new Application();
        app1.setId(1);
        Application app2 = new Application();
        app2.setId(2);

        when(applicationMapper.getAllApplications(1)).thenReturn(Arrays.asList(app1));
        when(applicationMapper.getAllApplications(2)).thenReturn(Arrays.asList(app2));

        ApplicationVersion v1 = new ApplicationVersion();
        v1.setId(1);
        v1.setApplicationId(1);
        v1.setUrl("https://mdm.example.com/files/cust-1/app.apk");
        when(applicationMapper.getApplicationVersions(1)).thenReturn(Arrays.asList(v1));

        ApplicationVersion v2 = new ApplicationVersion();
        v2.setId(2);
        v2.setApplicationId(2);
        v2.setUrl("https://mdm.example.com/files/cust-2/app.apk");
        when(applicationMapper.getApplicationVersions(2)).thenReturn(Arrays.asList(v2));

        when(uploadedFileMapper.getAll(1)).thenReturn(Collections.emptyList());
        when(uploadedFileMapper.getAll(2)).thenReturn(Collections.emptyList());

        FileMigrateTask migrateTask = new FileMigrateTask(
                customerMapper, applicationMapper, uploadedFileMapper,
                hashService, sourceDirectory, targetDirectory);

        migrateTask.run();

        // Both target files should exist
        File target1 = new File(new File(targetDirectory, "cust-1"), "app.apk");
        File target2 = new File(new File(targetDirectory, "cust-2"), "app.apk");
        Assert.assertTrue("Customer 1 file should be migrated", target1.exists());
        Assert.assertTrue("Customer 2 file should be migrated", target2.exists());
    }

    // ─── Helper ──────────────────────────────────────────────

    private Customer createCustomer(int id, String filesDir) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setFilesDir(filesDir);
        return customer;
    }
}
