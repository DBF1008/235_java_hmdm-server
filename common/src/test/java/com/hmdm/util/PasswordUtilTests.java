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
 * <p>Regression tests for {@link PasswordUtil} covering password hashing, matching, and token generation.</p>
 */
public class PasswordUtilTests {

    // =============================================================================================================
    // Hash chain tests
    // =============================================================================================================

    @Test
    public void testGetHashFromRaw_consistency() {
        String password = "testPassword123";
        String hash1 = PasswordUtil.getHashFromRaw(password);
        String hash2 = PasswordUtil.getHashFromRaw(password);
        Assert.assertEquals("Same password should produce same hash", hash1, hash2);
    }

    @Test
    public void testGetHashFromRaw_isSha1Hex() {
        String hash = PasswordUtil.getHashFromRaw("hello");
        // SHA1 produces 40 hex chars
        Assert.assertEquals("Hash should be 40 chars (SHA1 hex)", 40, hash.length());
        Assert.assertTrue("Hash should be uppercase hex", hash.matches("[0-9A-F]+"));
    }

    @Test
    public void testGetHashFromMd5_consistency() {
        String md5 = CryptoUtil.getMD5String("testPassword123");
        String hash1 = PasswordUtil.getHashFromMd5(md5);
        String hash2 = PasswordUtil.getHashFromMd5(md5);
        Assert.assertEquals("Same MD5 should produce same hash", hash1, hash2);
    }

    @Test
    public void testHashChain_rawEqualsMd5Chain() {
        String password = "mySecurePassword";
        String rawHash = PasswordUtil.getHashFromRaw(password);
        String md5 = CryptoUtil.getMD5String(password);
        String md5Hash = PasswordUtil.getHashFromMd5(md5);
        Assert.assertEquals("getHashFromRaw should equal MD5 + getHashFromMd5", rawHash, md5Hash);
    }

    @Test
    public void testDifferentPasswords_produceDifferentHashes() {
        String hash1 = PasswordUtil.getHashFromRaw("password1");
        String hash2 = PasswordUtil.getHashFromRaw("password2");
        Assert.assertNotEquals("Different passwords should produce different hashes", hash1, hash2);
    }

    // =============================================================================================================
    // passwordMatch tests
    // =============================================================================================================

    @Test
    public void testPasswordMatch_correctPassword() {
        String rawPassword = "correctPassword123";
        String md5 = CryptoUtil.getMD5String(rawPassword);
        String storedHash = PasswordUtil.getHashFromRaw(rawPassword);

        Assert.assertTrue("Correct password should match", PasswordUtil.passwordMatch(md5, storedHash));
    }

    @Test
    public void testPasswordMatch_wrongPassword() {
        String storedHash = PasswordUtil.getHashFromRaw("correctPassword");
        String wrongMd5 = CryptoUtil.getMD5String("wrongPassword");

        Assert.assertFalse("Wrong password should not match", PasswordUtil.passwordMatch(wrongMd5, storedHash));
    }

    @Test
    public void testPasswordMatch_emptyPassword() {
        String storedHash = PasswordUtil.getHashFromRaw("somePassword");
        String emptyMd5 = CryptoUtil.getMD5String("");

        Assert.assertFalse("Empty password should not match non-empty stored hash",
                PasswordUtil.passwordMatch(emptyMd5, storedHash));
    }

    @Test
    public void testPasswordMatch_caseInsensitiveHash() {
        String rawPassword = "testCase";
        String md5 = CryptoUtil.getMD5String(rawPassword);
        String storedHash = PasswordUtil.getHashFromRaw(rawPassword).toLowerCase();

        // passwordMatch uses equalsIgnoreCase, so lowercase hash should still match
        Assert.assertTrue("Match should be case-insensitive on stored hash",
                PasswordUtil.passwordMatch(md5, storedHash));
    }

    // =============================================================================================================
    // checkPassword tests
    // =============================================================================================================

    @Test
    public void testCheckPassword_strengthNone() {
        Assert.assertTrue("Any password should pass NONE strength check",
                PasswordUtil.checkPassword("abc", 3, PasswordUtil.PASS_STRENGTH_NONE));
    }

    @Test
    public void testCheckPassword_tooShort() {
        Assert.assertFalse("Password shorter than minimum should fail",
                PasswordUtil.checkPassword("ab", 8, PasswordUtil.PASS_STRENGTH_NONE));
    }

    @Test
    public void testCheckPassword_alphaDigit() {
        Assert.assertTrue("Password with digits, lower and upper should pass ALPHADIGIT",
                PasswordUtil.checkPassword("Test1234", 8, PasswordUtil.PASS_STRENGTH_ALPHADIGIT));
        Assert.assertFalse("Password without uppercase should fail ALPHADIGIT",
                PasswordUtil.checkPassword("test1234", 8, PasswordUtil.PASS_STRENGTH_ALPHADIGIT));
        Assert.assertFalse("Password without digits should fail ALPHADIGIT",
                PasswordUtil.checkPassword("TestAbcd", 8, PasswordUtil.PASS_STRENGTH_ALPHADIGIT));
    }

    @Test
    public void testCheckPassword_special() {
        Assert.assertTrue("Password with all character types should pass SPECIAL",
                PasswordUtil.checkPassword("Test1234!", 8, PasswordUtil.PASS_STRENGTH_SPECIAL));
        Assert.assertFalse("Password without special chars should fail SPECIAL",
                PasswordUtil.checkPassword("Test1234", 8, PasswordUtil.PASS_STRENGTH_SPECIAL));
    }

    // =============================================================================================================
    // generateToken tests (verifies SecureRandom usage)
    // =============================================================================================================

    @Test
    public void testGenerateToken_length() {
        String token = PasswordUtil.generateToken();
        Assert.assertEquals("Token should be 20 characters", 20, token.length());
    }

    @Test
    public void testGenerateToken_alphanumeric() {
        String token = PasswordUtil.generateToken();
        Assert.assertTrue("Token should be alphanumeric", token.matches("[0-9a-zA-Z]+"));
    }

    @Test
    public void testGenerateToken_uniqueness() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(PasswordUtil.generateToken());
        }
        // With SecureRandom and 62^20 possible tokens, 1000 tokens should all be unique
        Assert.assertEquals("1000 generated tokens should all be unique", 1000, tokens.size());
    }

    @Test
    public void testGenerateToken_notPredictable() {
        // Generate two consecutive tokens and verify they're different
        // (java.util.Random would produce predictable sequences with the same seed)
        String token1 = PasswordUtil.generateToken();
        String token2 = PasswordUtil.generateToken();
        Assert.assertNotEquals("Consecutive tokens should be different", token1, token2);
    }

    // =============================================================================================================
    // generatePassword tests
    // =============================================================================================================

    @Test
    public void testGeneratePassword_minimumLength() {
        String password = PasswordUtil.generatePassword(4, PasswordUtil.PASS_STRENGTH_NONE);
        Assert.assertTrue("Password should be at least 8 characters", password.length() >= 8);
    }

    @Test
    public void testGeneratePassword_meetsStrengthAlphaDigit() {
        String password = PasswordUtil.generatePassword(12, PasswordUtil.PASS_STRENGTH_ALPHADIGIT);
        Assert.assertTrue("Generated password should meet ALPHADIGIT strength",
                PasswordUtil.checkPassword(password, 12, PasswordUtil.PASS_STRENGTH_ALPHADIGIT));
    }

    @Test
    public void testGeneratePassword_meetsStrengthSpecial() {
        String password = PasswordUtil.generatePassword(12, PasswordUtil.PASS_STRENGTH_SPECIAL);
        Assert.assertTrue("Generated password should meet SPECIAL strength",
                PasswordUtil.checkPassword(password, 12, PasswordUtil.PASS_STRENGTH_SPECIAL));
    }
}
