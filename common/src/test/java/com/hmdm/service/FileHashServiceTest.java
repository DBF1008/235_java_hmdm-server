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

import com.hmdm.test.FileTestSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * <p>Unit tests for {@link FileHashService}.</p>
 */
public class FileHashServiceTest extends FileTestSupport {

    private FileHashService hashService;

    @Before
    public void setUp() {
        hashService = new FileHashService();
    }

    @Test
    public void testComputeSha256_knownInput() throws IOException {
        // SHA-256 of "hello world" is well-known
        String path = createTempFile("known.txt", "hello world");
        String hash = hashService.computeSha256(new File(path));

        Assert.assertEquals("SHA-256 should match known value for 'hello world'",
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                hash);
    }

    @Test
    public void testComputeSha256_emptyFile() throws IOException {
        // SHA-256 of empty input is well-known
        String path = createTempFile("empty.txt", "");
        String hash = hashService.computeSha256(new File(path));

        Assert.assertEquals("SHA-256 should match known value for empty file",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                hash);
    }

    @Test
    public void testComputeSha256_consistentResults() throws IOException {
        String path = createTempFile("consistent.txt", "same content");
        String hash1 = hashService.computeSha256(new File(path));
        String hash2 = hashService.computeSha256(new File(path));

        Assert.assertEquals("Same file should produce same hash", hash1, hash2);
    }

    @Test
    public void testComputeSha256_differentContentDifferentHash() throws IOException {
        String path1 = createTempFile("file1.txt", "content A");
        String path2 = createTempFile("file2.txt", "content B");

        String hash1 = hashService.computeSha256(new File(path1));
        String hash2 = hashService.computeSha256(new File(path2));

        Assert.assertNotEquals("Different content should produce different hashes", hash1, hash2);
    }

    @Test
    public void testComputeSha256_largeFile() throws IOException {
        // Create a file larger than the buffer size (8KB)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("Line ").append(i).append(": This is test data for large file hashing.\n");
        }
        String content = sb.toString();
        Assert.assertTrue("Content should be larger than 8KB",
                content.getBytes(StandardCharsets.UTF_8).length > 8192);

        String path = createTempFile("large.txt", content);
        String hash = hashService.computeSha256(new File(path));

        Assert.assertNotNull("Hash should not be null", hash);
        Assert.assertEquals("Hash should be 64 hex chars", 64, hash.length());
        // Verify consistency
        String hash2 = hashService.computeSha256(new File(path));
        Assert.assertEquals("Large file hash should be consistent", hash, hash2);
    }

    @Test
    public void testComputeSha256_hashIsLowercase() throws IOException {
        String path = createTempFile("lowercase.txt", "test");
        String hash = hashService.computeSha256(new File(path));

        Assert.assertEquals("Hash should be lowercase hex", hash, hash.toLowerCase());
    }

    @Test(expected = IOException.class)
    public void testComputeSha256_throwsForMissingFile() throws IOException {
        hashService.computeSha256(new File("/non/existent/file.txt"));
    }

    // ─── verify() tests ──────────────────────────────────────

    @Test
    public void testVerify_matchingHash() throws IOException {
        String path = createTempFile("verify-match.txt", "hello world");
        boolean result = hashService.verify(new File(path),
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");

        Assert.assertTrue("Verify should return true for matching hash", result);
    }

    @Test
    public void testVerify_mismatchedHash() throws IOException {
        String path = createTempFile("verify-mismatch.txt", "hello world");
        boolean result = hashService.verify(new File(path),
                "0000000000000000000000000000000000000000000000000000000000000000");

        Assert.assertFalse("Verify should return false for mismatched hash", result);
    }

    @Test
    public void testVerify_caseInsensitive() throws IOException {
        String path = createTempFile("verify-case.txt", "hello world");
        boolean result = hashService.verify(new File(path),
                "B94D27B9934D3E08A52E52D7DA7DABFAC484EFE37A5380EE9088F7ACE2EFCDE9");

        Assert.assertTrue("Verify should be case-insensitive", result);
    }

    @Test
    public void testVerify_nullHashReturnsFalse() throws IOException {
        String path = createTempFile("verify-null.txt", "content");
        boolean result = hashService.verify(new File(path), null);

        Assert.assertFalse("Verify with null hash should return false", result);
    }

    @Test
    public void testVerify_emptyHashReturnsFalse() throws IOException {
        String path = createTempFile("verify-empty-hash.txt", "content");
        boolean result = hashService.verify(new File(path), "");

        Assert.assertFalse("Verify with empty hash should return false", result);
    }
}
