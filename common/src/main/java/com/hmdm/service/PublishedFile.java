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

import java.io.File;

/**
 * <p>Represents the result of a successful file publish operation.
 * Contains the download URL, file hash, file name, and reference to the physical file.</p>
 */
public class PublishedFile {

    private final String url;
    private final String sha256Hash;
    private final String fileName;
    private final File physicalFile;

    /**
     * <p>Constructs a new {@code PublishedFile} instance.</p>
     *
     * @param url the download URL for the published file.
     * @param sha256Hash the SHA-256 hash of the file content.
     * @param fileName the final file name on disk.
     * @param physicalFile a reference to the physical file.
     */
    public PublishedFile(String url, String sha256Hash, String fileName, File physicalFile) {
        this.url = url;
        this.sha256Hash = sha256Hash;
        this.fileName = fileName;
        this.physicalFile = physicalFile;
    }

    /**
     * <p>Gets the download URL for this published file.</p>
     *
     * @return the download URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * <p>Gets the SHA-256 hash of the file content.</p>
     *
     * @return the hex-encoded SHA-256 hash.
     */
    public String getSha256Hash() {
        return sha256Hash;
    }

    /**
     * <p>Gets the final file name on disk.</p>
     *
     * @return the file name.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * <p>Gets a reference to the physical file on disk.</p>
     *
     * @return the physical file.
     */
    public File getPhysicalFile() {
        return physicalFile;
    }

    @Override
    public String toString() {
        return "PublishedFile{" +
                "url='" + url + '\'' +
                ", sha256Hash='" + sha256Hash + '\'' +
                ", fileName='" + fileName + '\'' +
                ", physicalFile=" + physicalFile +
                '}';
    }
}
