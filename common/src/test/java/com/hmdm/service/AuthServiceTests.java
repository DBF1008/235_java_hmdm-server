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

import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.UserDAO;
import com.hmdm.persistence.domain.User;
import com.hmdm.util.CryptoUtil;
import com.hmdm.util.PasswordUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Regression tests for {@link AuthService} covering auth state transitions for signup,
 * login, password reset, and password change flows.</p>
 *
 * <p>Uses lightweight recording stubs for UnsecureDAO and UserDAO to verify that the
 * correct state mutations are persisted without requiring a real database.</p>
 */
public class AuthServiceTests {

    // =============================================================================================================
    // Recording stubs that capture DAO calls
    // =============================================================================================================

    /** Records calls to setUserNewPasswordUnsecure() */
    static class StubUnsecureDAO extends UnsecureDAO {
        final List<User> setPasswordCalls = new ArrayList<>();
        final List<User> initiateResetCalls = new ArrayList<>();

        StubUnsecureDAO() {
            super(null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null,
                    "", 0, "");
        }
        @Override
        public void setUserNewPasswordUnsecure(User user) {
            setPasswordCalls.add(snapshot(user));
        }

        @Override
        public void initiatePasswordResetUnsecure(User user) {
            initiateResetCalls.add(snapshot(user));
        }

        @Override
        public User findByLoginOrEmail(String login) {
            return null; // not used in these tests
        }

        @Override
        public User findByPasswordResetToken(String token) {
            return null; // not used in these tests
        }
    }

    /** Records calls to updatePassword() */
    static class StubUserDAO extends UserDAO {
        final List<User> updatePasswordCalls = new ArrayList<>();

        StubUserDAO() {
            super(null, 0);
        }

        @Override
        public void updatePassword(User user) {
            updatePasswordCalls.add(snapshot(user));
        }
    }

    /** Creates a snapshot of a User's auth-relevant fields for assertion */
    private static User snapshot(User user) {
        User copy = new User();
        copy.setId(user.getId());
        copy.setLogin(user.getLogin());
        copy.setNewPassword(user.getNewPassword());
        copy.setPassword(user.getPassword());
        copy.setAuthToken(user.getAuthToken());
        copy.setPasswordReset(user.isPasswordReset());
        copy.setPasswordResetToken(user.getPasswordResetToken());
        return copy;
    }

    // =============================================================================================================
    // Test fixtures
    // =============================================================================================================

    private StubUnsecureDAO unsecureDAO;
    private StubUserDAO userDAO;
    private AuthService authService;

    @Before
    public void setUp() {
        unsecureDAO = new StubUnsecureDAO();
        userDAO = new StubUserDAO();
        authService = new AuthService(unsecureDAO, userDAO);
    }

    private User makeUser(int id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    // =============================================================================================================
    // completePassword tests (signup, admin-initiated reset)
    // =============================================================================================================

    @Test
    public void testCompletePassword_setsHashAndClearsResetState() {
        User user = makeUser(1, "admin");
        String passwordMD5 = CryptoUtil.getMD5String("newPassword");

        authService.completePassword(user, passwordMD5);

        // Verify DAO was called exactly once
        Assert.assertEquals(1, unsecureDAO.setPasswordCalls.size());
        User saved = unsecureDAO.setPasswordCalls.get(0);

        // The new password hash should be set (SHA1(MD5+salt))
        String expectedHash = PasswordUtil.getHashFromMd5(passwordMD5);
        Assert.assertEquals("Password hash should be set in newPassword", expectedHash, saved.getNewPassword());

        // passwordReset flag should be cleared
        Assert.assertFalse("passwordReset flag should be false", saved.isPasswordReset());

        // passwordResetToken should be cleared
        Assert.assertNull("passwordResetToken should be null", saved.getPasswordResetToken());

        // authToken should be regenerated (non-null, non-empty)
        Assert.assertNotNull("authToken should be regenerated", saved.getAuthToken());
        Assert.assertFalse("authToken should not be empty", saved.getAuthToken().isEmpty());
    }

    @Test
    public void testCompletePassword_generatesNewAuthToken() {
        User user = makeUser(1, "admin");
        user.setAuthToken("old-token-should-be-replaced");
        String passwordMD5 = CryptoUtil.getMD5String("password");

        authService.completePassword(user, passwordMD5);

        User saved = unsecureDAO.setPasswordCalls.get(0);
        Assert.assertNotEquals("authToken should be different from old one", "old-token-should-be-replaced", saved.getAuthToken());
    }

    // =============================================================================================================
    // initiatePasswordReset tests
    // =============================================================================================================

    @Test
    public void testInitiatePasswordReset_generatesTokenAndSetsFlag() {
        User user = makeUser(1, "user1");

        String token = authService.initiatePasswordReset(user);

        // Token should be returned
        Assert.assertNotNull("Token should be generated", token);
        Assert.assertFalse("Token should not be empty", token.isEmpty());

        // Token should be set on the user
        Assert.assertEquals("Token should be set on user", token, user.getPasswordResetToken());

        // DAO should have been called
        Assert.assertEquals(1, unsecureDAO.initiateResetCalls.size());
    }

    @Test
    public void testInitiatePasswordReset_generatesUniqueTokens() {
        User user1 = makeUser(1, "user1");
        User user2 = makeUser(2, "user2");

        String token1 = authService.initiatePasswordReset(user1);
        String token2 = authService.initiatePasswordReset(user2);

        Assert.assertNotEquals("Tokens for different users should be unique", token1, token2);
    }

    // =============================================================================================================
    // completePasswordReset tests
    // =============================================================================================================

    @Test
    public void testCompletePasswordReset_validToken_succeeds() {
        User user = makeUser(1, "user1");
        String resetToken = "valid-reset-token-12345";
        user.setPasswordResetToken(resetToken);
        String newPasswordMD5 = CryptoUtil.getMD5String("newPassword");

        boolean result = authService.completePasswordReset(user, newPasswordMD5, resetToken);

        Assert.assertTrue("Reset should succeed with valid token", result);
        // Verify password was committed (DAO called)
        Assert.assertEquals(1, unsecureDAO.setPasswordCalls.size());
        User saved = unsecureDAO.setPasswordCalls.get(0);
        Assert.assertNull("passwordResetToken should be cleared after use", saved.getPasswordResetToken());
        Assert.assertFalse("passwordReset flag should be cleared", saved.isPasswordReset());
    }

    @Test
    public void testCompletePasswordReset_invalidToken_fails() {
        User user = makeUser(1, "user1");
        user.setPasswordResetToken("real-token");
        String newPasswordMD5 = CryptoUtil.getMD5String("newPassword");

        boolean result = authService.completePasswordReset(user, newPasswordMD5, "wrong-token");

        Assert.assertFalse("Reset should fail with wrong token", result);
        // Verify password was NOT committed
        Assert.assertEquals("DAO should not be called on invalid token", 0, unsecureDAO.setPasswordCalls.size());
    }

    @Test
    public void testCompletePasswordReset_nullStoredToken_fails() {
        User user = makeUser(1, "user1");
        user.setPasswordResetToken(null);
        String newPasswordMD5 = CryptoUtil.getMD5String("newPassword");

        boolean result = authService.completePasswordReset(user, newPasswordMD5, "some-token");

        Assert.assertFalse("Reset should fail when stored token is null", result);
        Assert.assertEquals(0, unsecureDAO.setPasswordCalls.size());
    }

    @Test
    public void testCompletePasswordReset_tokenIsOneTimeUse() {
        User user = makeUser(1, "user1");
        String resetToken = "one-time-token";
        user.setPasswordResetToken(resetToken);
        String newPasswordMD5 = CryptoUtil.getMD5String("password");

        // First use should succeed
        boolean result1 = authService.completePasswordReset(user, newPasswordMD5, resetToken);
        Assert.assertTrue("First use should succeed", result1);

        // After first use, the user's stored token is now null (cleared by completePassword)
        // So a second attempt with the same token should fail
        boolean result2 = authService.completePasswordReset(user, newPasswordMD5, resetToken);
        Assert.assertFalse("Second use of same token should fail (one-time use)", result2);
    }

    // =============================================================================================================
    // changePassword tests (authenticated user)
    // =============================================================================================================

    @Test
    public void testChangePassword_correctOldPassword_succeeds() {
        User user = makeUser(1, "user1");
        String oldRaw = "oldPassword123";
        String newRaw = "newPassword456";
        // Set up the user with the hashed old password
        user.setPassword(PasswordUtil.getHashFromRaw(oldRaw));
        String oldMD5 = CryptoUtil.getMD5String(oldRaw);
        String newMD5 = CryptoUtil.getMD5String(newRaw);

        boolean result = authService.changePassword(user, oldMD5, newMD5);

        Assert.assertTrue("Change should succeed with correct old password", result);
        Assert.assertEquals(1, userDAO.updatePasswordCalls.size());
        User saved = userDAO.updatePasswordCalls.get(0);
        Assert.assertFalse("passwordReset flag should be cleared", saved.isPasswordReset());
        Assert.assertNull("passwordResetToken should be cleared", saved.getPasswordResetToken());
        Assert.assertNotNull("authToken should be regenerated", saved.getAuthToken());
    }

    @Test
    public void testChangePassword_wrongOldPassword_fails() {
        User user = makeUser(1, "user1");
        user.setPassword(PasswordUtil.getHashFromRaw("correctOldPassword"));
        String wrongOldMD5 = CryptoUtil.getMD5String("wrongOldPassword");
        String newMD5 = CryptoUtil.getMD5String("newPassword");

        boolean result = authService.changePassword(user, wrongOldMD5, newMD5);

        Assert.assertFalse("Change should fail with wrong old password", result);
        Assert.assertEquals("DAO should not be called on wrong old password", 0, userDAO.updatePasswordCalls.size());
    }

    @Test
    public void testChangePassword_invalidatesAllExistingSessions() {
        User user = makeUser(1, "user1");
        String oldAuthToken = "existing-session-token";
        user.setAuthToken(oldAuthToken);
        user.setPassword(PasswordUtil.getHashFromRaw("oldPass"));
        String oldMD5 = CryptoUtil.getMD5String("oldPass");
        String newMD5 = CryptoUtil.getMD5String("newPass");

        authService.changePassword(user, oldMD5, newMD5);

        User saved = userDAO.updatePasswordCalls.get(0);
        Assert.assertNotEquals("authToken must change to invalidate existing JWTs",
                oldAuthToken, saved.getAuthToken());
    }

    // =============================================================================================================
    // ensureAuthToken tests (login flow)
    // =============================================================================================================

    @Test
    public void testEnsureAuthToken_whenMissing_generatesAndPersists() {
        User user = makeUser(1, "user1");
        user.setAuthToken(null);
        user.setPassword(PasswordUtil.getHashFromRaw("password"));

        authService.ensureAuthToken(user);

        Assert.assertNotNull("authToken should be generated", user.getAuthToken());
        Assert.assertFalse("authToken should not be empty", user.getAuthToken().isEmpty());
        Assert.assertEquals(1, unsecureDAO.setPasswordCalls.size());
    }

    @Test
    public void testEnsureAuthToken_whenEmpty_generatesAndPersists() {
        User user = makeUser(1, "user1");
        user.setAuthToken("");
        user.setPassword(PasswordUtil.getHashFromRaw("password"));

        authService.ensureAuthToken(user);

        Assert.assertNotNull("authToken should be generated", user.getAuthToken());
        Assert.assertFalse("authToken should not be empty", user.getAuthToken().isEmpty());
    }

    @Test
    public void testEnsureAuthToken_whenPresent_doesNotRegenerate() {
        User user = makeUser(1, "user1");
        String existingToken = "existing-valid-token";
        user.setAuthToken(existingToken);
        user.setPassword(PasswordUtil.getHashFromRaw("password"));

        authService.ensureAuthToken(user);

        Assert.assertEquals("Existing authToken should be preserved", existingToken, user.getAuthToken());
        Assert.assertEquals("DAO should not be called when token exists", 0, unsecureDAO.setPasswordCalls.size());
    }

    // =============================================================================================================
    // Cross-flow integration tests
    // =============================================================================================================

    @Test
    public void testFlow_signupThenLogin() {
        // Simulate signup: completePassword is called during signup
        User user = makeUser(1, "newadmin");
        String signupPasswordMD5 = CryptoUtil.getMD5String("signupPass");

        authService.completePassword(user, signupPasswordMD5);
        String authTokenAfterSignup = user.getAuthToken();
        Assert.assertNotNull("User should have authToken after signup", authTokenAfterSignup);
        Assert.assertFalse("passwordReset should be false after signup", user.isPasswordReset());

        // Simulate login: ensureAuthToken should preserve the existing token
        authService.ensureAuthToken(user);
        Assert.assertEquals("authToken should be preserved on login", authTokenAfterSignup, user.getAuthToken());
    }

    @Test
    public void testFlow_passwordResetThenRelogin() {
        // Setup: user exists with a password
        User user = makeUser(1, "user1");
        String originalPassword = "originalPass";
        user.setPassword(PasswordUtil.getHashFromRaw(originalPassword));
        user.setAuthToken("original-auth-token");

        // Step 1: Initiate password reset
        String resetToken = authService.initiatePasswordReset(user);
        Assert.assertNotNull("Reset token should be generated", resetToken);

        // Step 2: Complete password reset
        String newPasswordMD5 = CryptoUtil.getMD5String("newSecurePass");
        boolean resetSuccess = authService.completePasswordReset(user, newPasswordMD5, resetToken);
        Assert.assertTrue("Password reset should succeed", resetSuccess);

        String authTokenAfterReset = user.getAuthToken();
        Assert.assertNotEquals("authToken should change after reset", "original-auth-token", authTokenAfterReset);

        // Step 3: Verify new password works (simulating login)
        String loginMD5 = CryptoUtil.getMD5String("newSecurePass");
        // The user's password was updated via DAO; in real flow, a fresh DB fetch would give the new hash
        // For this test, we verify the hash was set correctly
        User saved = unsecureDAO.setPasswordCalls.get(unsecureDAO.setPasswordCalls.size() - 1);
        String expectedHash = PasswordUtil.getHashFromMd5(newPasswordMD5);
        Assert.assertEquals("Stored password should be the new password hash", expectedHash, saved.getNewPassword());

        // Step 4: Login with new password — ensureAuthToken should preserve the post-reset token
        authService.ensureAuthToken(user);
        Assert.assertEquals("authToken should be preserved on re-login",
                authTokenAfterReset, user.getAuthToken());
    }

    @Test
    public void testFlow_passwordChangeInvalidatesOldSessions() {
        // Setup: user is logged in with an existing session
        User user = makeUser(1, "user1");
        String oldPassword = "oldPassword";
        user.setPassword(PasswordUtil.getHashFromRaw(oldPassword));
        user.setAuthToken("session-token-before-change");

        // Change password
        String oldMD5 = CryptoUtil.getMD5String(oldPassword);
        String newMD5 = CryptoUtil.getMD5String("brandNewPassword");
        boolean changed = authService.changePassword(user, oldMD5, newMD5);
        Assert.assertTrue("Password change should succeed", changed);

        // The authToken should have changed
        String newAuthToken = user.getAuthToken();
        Assert.assertNotEquals("authToken must change after password change",
                "session-token-before-change", newAuthToken);

        // Old JWT (which embeds the old authToken) would now be rejected by JWTFilter
        // because the DB authToken no longer matches the JWT's embedded token
    }

    @Test
    public void testFlow_adminResetThenLogin() {
        // Admin-initiated password reset: completePassword is called
        User user = makeUser(1, "user1");
        user.setPasswordReset(true);
        user.setAuthToken("old-session-token");
        String adminSetPasswordMD5 = CryptoUtil.getMD5String("adminResetPass");

        authService.completePassword(user, adminSetPasswordMD5);

        // After admin reset, passwordReset should be false and user can log in normally
        Assert.assertFalse("passwordReset should be cleared after completePassword", user.isPasswordReset());
        Assert.assertNotEquals("authToken should be regenerated", "old-session-token", user.getAuthToken());

        // User logs in with the new password
        authService.ensureAuthToken(user);
        // Token should be preserved since it was already set
        Assert.assertNotNull("authToken should exist for login", user.getAuthToken());
    }
}
