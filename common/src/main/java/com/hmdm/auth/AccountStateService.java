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

package com.hmdm.auth;

import com.google.inject.Inject;
import com.hmdm.persistence.domain.PendingSignup;
import com.hmdm.persistence.domain.User;
import com.hmdm.util.PasswordUtil;

import javax.inject.Singleton;
import java.util.function.LongSupplier;

/**
 * <p>The single source of truth for the token and account-state semantics shared by the login, sign-up,
 * password-reset and user-profile-update flows.</p>
 *
 * <p>Historically every one of those entry points assembled the {@code authToken}, {@code passwordReset} flag and
 * {@code passwordResetToken} of a {@link User} by hand, which let them drift apart: a freshly created account could be
 * left in a state that the login flow still treats as "not activated", and a completed password reset could leave a
 * reusable reset token behind or a session/auth token that no longer matched what the profile-update flow expected.
 * Routing every transition through this service guarantees that all flows agree on:</p>
 *
 * <ul>
 *     <li><b>Token generation</b> &mdash; auth tokens and recovery (sign-up / password-reset) tokens are produced in
 *     exactly one place ({@link #issueAuthToken()} / {@link #issueRecoveryToken()}).</li>
 *     <li><b>Expiry</b> &mdash; sign-up tokens expire relative to {@link PendingSignup#getSignupTime()} and recovery
 *     tokens carry their issue time inside the token itself (so expiry needs no extra database column), both bounded by
 *     a single, configurable TTL.</li>
 *     <li><b>One-time use</b> &mdash; {@link #completePasswordReset(User, String)} clears the reset token, so a token
 *     can never be replayed once a password has been changed with it.</li>
 *     <li><b>Activation state</b> &mdash; a user is "active" exactly when it has an auth token, no pending reset flag
 *     and no outstanding reset token; {@link #initializeNewUser(User, boolean)} and
 *     {@link #completePasswordReset(User, String)} are the only ways to reach that state.</li>
 * </ul>
 *
 * <p>The class holds no mutable state of its own and performs no persistence; it only mutates the supplied domain
 * objects. The clock is injected so that expiry behaviour is fully deterministic under test.</p>
 *
 * @author Headwind MDM
 */
@Singleton
public class AccountStateService {

    /**
     * <p>Default lifetime of a sign-up confirmation token (24 hours).</p>
     */
    public static final long DEFAULT_SIGNUP_TOKEN_TTL_MILLIS = 24L * 60L * 60L * 1000L;

    /**
     * <p>Default lifetime of a password-reset token (1 hour).</p>
     */
    public static final long DEFAULT_RECOVERY_TOKEN_TTL_MILLIS = 60L * 60L * 1000L;

    /**
     * <p>Separator between the random part of a recovery token and its (base-36 encoded) issue time. It is deliberately
     * a character that {@link PasswordUtil#generateToken()} never emits and that is safe inside a URL path segment,
     * because recovery tokens are embedded into e-mailed links (for example {@code /#/passwordReset/<token>}).</p>
     */
    private static final char RECOVERY_TIME_SEPARATOR = '-';

    private static final int TOKEN_TIME_RADIX = 36;

    private final LongSupplier clock;
    private final long signupTokenTtlMillis;
    private final long recoveryTokenTtlMillis;

    /**
     * <p>Production constructor used by the dependency-injection container. Uses the wall clock and the default TTLs.</p>
     */
    @Inject
    public AccountStateService() {
        this(System::currentTimeMillis, DEFAULT_SIGNUP_TOKEN_TTL_MILLIS, DEFAULT_RECOVERY_TOKEN_TTL_MILLIS);
    }

    /**
     * <p>Constructs a service with an explicit clock and explicit TTLs. Intended for tests, which need a controllable
     * clock to exercise expiry boundaries deterministically.</p>
     *
     * @param clock supplier of the current time in milliseconds since the epoch.
     * @param signupTokenTtlMillis lifetime of a sign-up token, in milliseconds.
     * @param recoveryTokenTtlMillis lifetime of a password-reset token, in milliseconds.
     */
    public AccountStateService(LongSupplier clock, long signupTokenTtlMillis, long recoveryTokenTtlMillis) {
        this.clock = clock;
        this.signupTokenTtlMillis = signupTokenTtlMillis;
        this.recoveryTokenTtlMillis = recoveryTokenTtlMillis;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Token generation
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * <p>Issues a fresh authentication (session) token.</p>
     *
     * @return a newly generated auth token.
     */
    public String issueAuthToken() {
        return PasswordUtil.generateToken();
    }

    /**
     * <p>Issues a fresh recovery token (used for password resets). The token embeds its own issue time so that its
     * freshness can be validated later without any extra persisted state.</p>
     *
     * @return a newly generated recovery token.
     */
    public String issueRecoveryToken() {
        return PasswordUtil.generateToken() + RECOVERY_TIME_SEPARATOR + Long.toString(clock.getAsLong(), TOKEN_TIME_RADIX);
    }

    /**
     * <p>Extracts the issue time embedded into a recovery token by {@link #issueRecoveryToken()}.</p>
     *
     * @param token the recovery token to inspect.
     * @return the issue time in milliseconds since the epoch, or {@code -1} if the token is {@code null} or does not
     *         carry a parseable issue time (for example a legacy token issued before this scheme existed).
     */
    static long recoveryTokenIssueTime(String token) {
        if (token == null) {
            return -1L;
        }
        int sep = token.lastIndexOf(RECOVERY_TIME_SEPARATOR);
        if (sep < 0 || sep == token.length() - 1) {
            return -1L;
        }
        try {
            return Long.parseLong(token.substring(sep + 1), TOKEN_TIME_RADIX);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * <p>Tests whether a recovery token is expired. Fails closed: a {@code null} token, or a token without a parseable
     * issue time, is always considered expired.</p>
     *
     * @param token the recovery token to validate.
     * @return {@code true} if the token must no longer be accepted.
     */
    public boolean isRecoveryTokenExpired(String token) {
        long issuedAt = recoveryTokenIssueTime(token);
        if (issuedAt < 0L) {
            return true;
        }
        return clock.getAsLong() - issuedAt > recoveryTokenTtlMillis;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Account state transitions
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * <p>Brings a brand-new user account into a consistent initial state. After this call the user always has an auth
     * token, and is either immediately active or explicitly placed into the "must reset password" state &mdash; never an
     * accidental mix of the two. This is the only transition that should be used when creating an account (self sign-up
     * as well as admin-driven creation), which keeps those paths from diverging.</p>
     *
     * @param user the freshly populated (but not yet persisted) user account.
     * @param forcePasswordReset whether the user must set a new password before the account becomes usable.
     */
    public void initializeNewUser(User user, boolean forcePasswordReset) {
        ensureAuthToken(user);
        if (forcePasswordReset) {
            user.setPasswordReset(true);
            user.setPasswordResetToken(issueRecoveryToken());
        } else {
            user.setPasswordReset(false);
            user.setPasswordResetToken(null);
        }
    }

    /**
     * <p>Starts a password-reset request for an existing, otherwise active user: it raises the reset flag and issues a
     * fresh, single-use recovery token. It deliberately does not touch the password or the auth token, so requesting a
     * reset never changes the user's current credentials or logs them out elsewhere.</p>
     *
     * @param user the user requesting a password reset.
     */
    public void requirePasswordReset(User user) {
        user.setPasswordReset(true);
        user.setPasswordResetToken(issueRecoveryToken());
    }

    /**
     * <p>Ensures the user owns an auth token, generating one only if it is missing. Used by the login flow to lazily
     * back-fill tokens for legacy accounts created before auth tokens existed, without disturbing accounts that already
     * have one.</p>
     *
     * @param user the user to back-fill an auth token for.
     */
    public void ensureAuthToken(User user) {
        if (user.getAuthToken() == null || user.getAuthToken().isEmpty()) {
            user.setAuthToken(issueAuthToken());
        }
    }

    /**
     * <p>Validates a pending sign-up record: the token must be present and the record must not be older than the
     * sign-up token TTL. Fails closed for missing records, tokens or timestamps.</p>
     *
     * @param signup the pending sign-up record looked up by its token.
     * @return {@code true} if the sign-up may still be completed.
     */
    public boolean isSignupValid(PendingSignup signup) {
        if (signup == null || signup.getToken() == null || signup.getToken().isEmpty()) {
            return false;
        }
        Long signupTime = signup.getSignupTime();
        if (signupTime == null) {
            return false;
        }
        return clock.getAsLong() - signupTime <= signupTokenTtlMillis;
    }

    /**
     * <p>Completes a password reset. This is the single canonical "the user has a new password" transition, and it
     * always leaves the account in the same, fully consistent state regardless of which flow invoked it:</p>
     *
     * <ul>
     *     <li>the new password hash is stored (in both {@code password} and {@code newPassword} so the persistence
     *     layer's update statement picks it up);</li>
     *     <li>the reset token is cleared, making it one-time-use;</li>
     *     <li>the reset flag is cleared, so the account is considered active;</li>
     *     <li>a fresh auth token is issued, so the login flow and the profile-update flow can never disagree about the
     *     account's current session token after a reset.</li>
     * </ul>
     *
     * @param user the user whose password has been reset.
     * @param newPasswordHash the already-hashed new password to store.
     */
    public void completePasswordReset(User user, String newPasswordHash) {
        user.setPassword(newPasswordHash);
        user.setNewPassword(newPasswordHash);
        user.setPasswordResetToken(null);
        user.setPasswordReset(false);
        user.setAuthToken(issueAuthToken());
    }
}
