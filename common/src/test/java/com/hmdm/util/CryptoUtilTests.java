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

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * <p>Regression tests for {@link CryptoUtil} covering hash functions and secure random generation.</p>
 */
public class CryptoUtilTests {

    // =============================================================================================================
    // MD5 tests
    // =============================================================================================================

    @Test
    public void testGetMD5String_knownValue() {
        // MD5 of empty string is well-known
        String md5 = CryptoUtil.getMD5String("");
        Assert.assertEquals("D41D8CD98F00B204E9800998ECF8427E", md5);
    }

    @Test
    public void testGetMD5String_consistency() {
        String md5a = CryptoUtil.getMD5String("hello");
        String md5b = CryptoUtil.getMD5String("hello");
        Assert.assertEquals("Same input should produce same MD5", md5a, md5b);
    }

    @Test
    public void testGetMD5String_length() {
        String md5 = CryptoUtil.getMD5String("test");
        Assert.assertEquals("MD5 should be 32 hex chars", 32, md5.length());
    }

    // =============================================================================================================
    // SHA1 tests
    // =============================================================================================================

    @Test
    public void testGetSHA1String_length() {
        String sha1 = CryptoUtil.getSHA1String("test");
        Assert.assertEquals("SHA1 should be 40 hex chars", 40, sha1.length());
    }

    @Test
    public void testGetSHA1String_consistency() {
        String sha1a = CryptoUtil.getSHA1String("hello");
        String sha1b = CryptoUtil.getSHA1String("hello");
        Assert.assertEquals("Same input should produce same SHA1", sha1a, sha1b);
    }

    // =============================================================================================================
    // randomHexString tests (verifies SecureRandom usage)
    // =============================================================================================================

    @Test
    public void testRandomHexString_length() {
        String hex = CryptoUtil.randomHexString(40);
        Assert.assertEquals("Hex string should have requested length", 40, hex.length());
    }

    @Test
    public void testRandomHexString_hexCharsOnly() {
        String hex = CryptoUtil.randomHexString(100);
        Assert.assertTrue("Hex string should contain only hex chars", hex.matches("[0-9a-f]+"));
    }

    @Test
    public void testRandomHexString_uniqueness() {
        Set<String> strings = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            strings.add(CryptoUtil.randomHexString(40));
        }
        Assert.assertEquals("1000 random hex strings should all be unique", 1000, strings.size());
    }

    @Test
    public void testRandomHexString_notPredictable() {
        String hex1 = CryptoUtil.randomHexString(40);
        String hex2 = CryptoUtil.randomHexString(40);
        Assert.assertNotEquals("Consecutive random hex strings should be different", hex1, hex2);
    }
}
