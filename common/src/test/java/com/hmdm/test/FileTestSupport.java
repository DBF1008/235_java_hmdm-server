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

package com.hmdm.test;

import com.hmdm.persistence.domain.Customer;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * <p>Base class for file-related unit tests. Provides common utilities and a temporary folder.</p>
 */
public abstract class FileTestSupport {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * <p>Creates a temporary file with the given name and content.</p>
     *
     * @param name the file name.
     * @param content the file content.
     * @return the absolute path of the created file.
     * @throws IOException if file creation fails.
     */
    protected String createTempFile(String name, String content) throws IOException {
        File f = tempFolder.newFile(name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return f.getAbsolutePath();
    }

    /**
     * <p>Creates a temporary file with the given name and binary content.</p>
     *
     * @param name the file name.
     * @param content the file content as byte array.
     * @return the absolute path of the created file.
     * @throws IOException if file creation fails.
     */
    protected String createTempFile(String name, byte[] content) throws IOException {
        File f = tempFolder.newFile(name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content);
        }
        return f.getAbsolutePath();
    }

    /**
     * <p>Creates a mock Customer with the specified files directory name.</p>
     *
     * @param customerId the customer ID.
     * @param filesDir the files directory name (UUID-like).
     * @return a Customer instance.
     */
    protected Customer createCustomer(int customerId, String filesDir) {
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setFilesDir(filesDir);
        return customer;
    }

    /**
     * <p>Creates a customer directory under the temp folder and returns a Customer referencing it.</p>
     *
     * @param customerId the customer ID.
     * @return a Customer with filesDir pointing to a real temp directory.
     * @throws IOException if directory creation fails.
     */
    protected Customer createCustomerWithDir(int customerId) throws IOException {
        String dirName = "customer-" + customerId + "-dir";
        tempFolder.newFolder(dirName);
        return createCustomer(customerId, dirName);
    }

    /**
     * <p>Gets the base files directory (temp folder root).</p>
     *
     * @return the absolute path to the temp folder.
     */
    protected String getFilesDirectory() {
        return tempFolder.getRoot().getAbsolutePath();
    }
}
