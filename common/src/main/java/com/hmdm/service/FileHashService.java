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

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <p>A service for computing and verifying SHA-256 file hashes.
 * Uses streaming reads to handle large files without excessive memory usage.</p>
 */
@Singleton
public class FileHashService {

    private static final Logger log = LoggerFactory.getLogger(FileHashService.class);

    private static final int BUFFER_SIZE = 8192;
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    /**
     * <p>Computes the SHA-256 hash of the specified file.</p>
     *
     * @param file the file to hash.
     * @return the hex-encoded SHA-256 hash string (lowercase).
     * @throws IOException if the file cannot be read.
     */
    public String computeSha256(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist: " + (file == null ? "null" : file.getAbsolutePath()));
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in any JVM
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * <p>Verifies whether the specified file's SHA-256 hash matches the expected value.</p>
     *
     * @param file the file to verify.
     * @param expectedHash the expected hex-encoded SHA-256 hash.
     * @return {@code true} if the file's hash matches; {@code false} otherwise.
     * @throws IOException if the file cannot be read.
     */
    public boolean verify(File file, String expectedHash) throws IOException {
        if (expectedHash == null || expectedHash.isEmpty()) {
            log.warn("Cannot verify file {} - expected hash is null or empty", file);
            return false;
        }
        String actualHash = computeSha256(file);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * <p>Converts a byte array to a lowercase hex string.</p>
     *
     * @param bytes the byte array.
     * @return the hex-encoded string.
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
