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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.UserDAO;
import com.hmdm.persistence.domain.User;
import com.hmdm.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized service for managing authentication state transitions.
 *
 * <p>This service unifies the handling of auth tokens, password hashes, password reset tokens,
 * and the passwordReset flag across all entry points (login, signup, password reset, profile update).
 * By routing all state mutations through this service, we guarantee consistent semantics:</p>
 *
 * <ul>
 *   <li>Every password change/regeneration invalidates existing JWTs by regenerating the authToken</li>
 *   <li>The passwordReset flag is always cleared when a new password is committed</li>
 *   <li>passwordResetToken is always cleared after use (one-time token)</li>
 *   <li>All tokens are generated using SecureRandom for cryptographic safety</li>
 * </ul>
 *
 * @author refactored
 */
@Singleton
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UnsecureDAO unsecureDAO;
    private final UserDAO userDAO;

    @Inject
    public AuthService(UnsecureDAO unsecureDAO, UserDAO userDAO) {
        this.unsecureDAO = unsecureDAO;
        this.userDAO = userDAO;
    }

    // =============================================================================================================
    // Token generation
    // =============================================================================================================

    /**
     * Generates a cryptographically secure random token suitable for use as an authToken,
     * passwordResetToken, or signup verification token.
     *
     * @return a 20-character alphanumeric token generated using SecureRandom.
     */
    public String generateSecureToken() {
        return PasswordUtil.generateToken();
    }

    // =============================================================================================================
    // Password commitment (sets hash, clears reset flag, regenerates authToken)
    // =============================================================================================================

    /**
     * Commits a new password for a user and atomically resets all associated auth state.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Hashes the supplied MD5 password using the standard hash chain</li>
     *   <li>Clears the passwordReset flag (user no longer needs to change their password)</li>
     *   <li>Clears the passwordResetToken (one-time token consumed)</li>
     *   <li>Regenerates the authToken (invalidates all existing JWTs)</li>
     * </ol>
     *
     * <p>Use this method for signup completion, admin-initiated password resets, and any context
     * where no prior password exists or needs to be verified.</p>
     *
     * @param user        the user whose password is being set (must have id set)
     * @param passwordMD5 the MD5 hash of the raw password (as sent by the client)
     */
    public void completePassword(User user, String passwordMD5) {
        String passwordHash = PasswordUtil.getHashFromMd5(passwordMD5);
        user.setNewPassword(passwordHash);
        user.setPasswordReset(false);
        user.setPasswordResetToken(null);
        user.setAuthToken(generateSecureToken());
        unsecureDAO.setUserNewPasswordUnsecure(user);
        logger.info("Password committed and auth state reset for user: {}", user.getLogin());
    }

    // =============================================================================================================
    // Password reset initiation (generates token, sets flag)
    // =============================================================================================================

    /**
     * Initiates a password reset flow for the given user.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Generates a secure one-time password reset token</li>
     *   <li>Sets the passwordReset flag to true</li>
     *   <li>Persists the token and flag to the database</li>
     * </ol>
     *
     * <p>The caller should then send the token to the user via email using
     * {@link EmailService#getRecoveryEmailBody(String, String)}.</p>
     *
     * @param user the user requesting a password reset (must have id set)
     * @return the generated password reset token to be sent via email
     */
    public String initiatePasswordReset(User user) {
        String token = generateSecureToken();
        user.setPasswordResetToken(token);
        unsecureDAO.initiatePasswordResetUnsecure(user);
        logger.info("Password reset initiated for user: {}", user.getLogin());
        return token;
    }

    // =============================================================================================================
    // Password reset completion (validates token, commits new password, regenerates authToken)
    // =============================================================================================================

    /**
     * Completes a password reset by validating the token and committing the new password.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Verifies the provided token matches the stored passwordResetToken</li>
     *   <li>Commits the new password (hashes, clears reset flag and token, regenerates authToken)</li>
     * </ol>
     *
     * <p>On success, all existing JWTs for this user are invalidated because the authToken changes.</p>
     *
     * @param user            the user found by password reset token lookup (must have id and passwordResetToken set)
     * @param passwordMD5     the MD5 hash of the new raw password
     * @param providedToken   the token supplied by the user (from the reset URL)
     * @return true if the token was valid and the password was reset; false otherwise
     */
    public boolean completePasswordReset(User user, String passwordMD5, String providedToken) {
        if (user.getPasswordResetToken() == null ||
                !user.getPasswordResetToken().equals(providedToken)) {
            logger.warn("Invalid password reset token for user: {}", user.getLogin());
            return false;
        }

        completePassword(user, passwordMD5);
        logger.info("Password reset completed for user: {}", user.getLogin());
        return true;
    }

    // =============================================================================================================
    // Authenticated password change (verifies old password, commits new password)
    // =============================================================================================================

    /**
     * Changes the password for an authenticated user after verifying the old password.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Verifies the old password matches the stored hash</li>
     *   <li>Commits the new password (hashes, clears reset flag and token, regenerates authToken)</li>
     * </ol>
     *
     * <p>On success, all existing JWTs for this user are invalidated because the authToken changes.
     * The user must re-authenticate to obtain a new JWT.</p>
     *
     * @param user        the authenticated user (with current password hash loaded)
     * @param oldPasswordMD5 the MD5 hash of the current password (as sent by the client)
     * @param newPasswordMD5 the MD5 hash of the new password (as sent by the client)
     * @return true if the old password was correct and the new password was set; false otherwise
     */
    public boolean changePassword(User user, String oldPasswordMD5, String newPasswordMD5) {
        if (!PasswordUtil.passwordMatch(oldPasswordMD5, user.getPassword())) {
            logger.warn("Old password mismatch for user: {}", user.getLogin());
            return false;
        }

        String newPasswordHash = PasswordUtil.getHashFromMd5(newPasswordMD5);
        user.setNewPassword(newPasswordHash);
        user.setPasswordReset(false);
        user.setPasswordResetToken(null);
        user.setAuthToken(generateSecureToken());
        userDAO.updatePassword(user);
        logger.info("Password changed for authenticated user: {}", user.getLogin());
        return true;
    }

    // =============================================================================================================
    // Auth token management (for login flow)
    // =============================================================================================================

    /**
     * Ensures the user has a valid authToken, generating one if absent.
     *
     * <p>This is called during the login flow. If the user already has an authToken,
     * it is left unchanged (so existing sessions are not disrupted). If the authToken
     * is null or empty, a new one is generated and persisted.</p>
     *
     * @param user the user to ensure has an authToken
     */
    public void ensureAuthToken(User user) {
        if (user.getAuthToken() == null || user.getAuthToken().isEmpty()) {
            user.setAuthToken(generateSecureToken());
            // Persist the new authToken alongside the existing password hash
            user.setNewPassword(user.getPassword());
            user.setPasswordReset(false);
            user.setPasswordResetToken(null);
            unsecureDAO.setUserNewPasswordUnsecure(user);
            logger.info("Generated initial authToken for user: {}", user.getLogin());
        }
    }
}
