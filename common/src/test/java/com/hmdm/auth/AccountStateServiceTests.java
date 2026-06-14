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

import com.hmdm.persistence.domain.PendingSignup;
import com.hmdm.persistence.domain.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * <p>Regression tests for {@link AccountStateService}, the single source of truth for the token/account-state
 * semantics of the sign-up, login, password-reset and profile-update flows.</p>
 *
 * <p>The tests drive the service with a fixed, controllable clock so that expiry boundaries are exercised
 * deterministically and without any database. They cover the three scenarios that historically diverged between
 * entry points: <b>sign-up</b> (a newly created account must end up active), <b>password reset</b> (one-time-use and
 * expiry of the recovery token), and <b>re-login after a reset</b> (a fresh auth token is issued so the login and
 * profile-update flows never disagree).</p>
 *
 * @author Headwind MDM
 */
public class AccountStateServiceTests {

    /** A fixed "current time" the tests start from (arbitrary, but stable). */
    private static final long BASE_TIME = 1_700_000_000_000L;
    private static final long SIGNUP_TTL = 24L * 60L * 60L * 1000L;
    private static final long RECOVERY_TTL = 60L * 60L * 1000L;

    /** Mutable clock backing the service; tests advance {@code now[0]} to simulate the passage of time. */
    private long[] now;
    private AccountStateService service;

    @Before
    public void setUp() {
        now = new long[]{BASE_TIME};
        service = new AccountStateService(() -> now[0], SIGNUP_TTL, RECOVERY_TTL);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Sign-up / account activation
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void initializeNewUser_producesActiveAccountByDefault() {
        User user = newUser();

        service.initializeNewUser(user, false);

        assertNotNullOrEmpty("a new account must always receive an auth token", user.getAuthToken());
        assertFalse("a default new account must not require a password reset", user.isPasswordReset());
        assertNull("a default new account must have no outstanding reset token", user.getPasswordResetToken());
    }

    @Test
    public void initializeNewUser_withForcedReset_setsValidSingleUseToken() {
        User user = newUser();

        service.initializeNewUser(user, true);

        assertNotNullOrEmpty("the account still gets an auth token", user.getAuthToken());
        assertTrue("the account must be flagged as requiring a reset", user.isPasswordReset());
        assertNotNullOrEmpty("a recovery token must be issued", user.getPasswordResetToken());
        assertFalse("a freshly issued recovery token must not already be expired",
                service.isRecoveryTokenExpired(user.getPasswordResetToken()));
    }

    @Test
    public void initializeNewUser_preservesAnExistingAuthToken() {
        User user = newUser();
        user.setAuthToken("EXISTING-TOKEN");

        service.initializeNewUser(user, false);

        assertEquals("an existing auth token must not be regenerated", "EXISTING-TOKEN", user.getAuthToken());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Sign-up token validity (token presence + expiry)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void isSignupValid_acceptsAFreshSignup() {
        assertTrue(service.isSignupValid(signup(BASE_TIME)));
    }

    @Test
    public void isSignupValid_acceptsExactlyAtTtlAndRejectsBeyondIt() {
        PendingSignup signup = signup(BASE_TIME);

        now[0] = BASE_TIME + SIGNUP_TTL;            // exactly at the TTL boundary -> still valid
        assertTrue("a sign-up exactly at the TTL boundary must still be valid", service.isSignupValid(signup));

        now[0] = BASE_TIME + SIGNUP_TTL + 1;        // one millisecond past -> expired
        assertFalse("a sign-up past the TTL must be rejected", service.isSignupValid(signup));
    }

    @Test
    public void isSignupValid_failsClosedForMissingData() {
        assertFalse("null sign-up is invalid", service.isSignupValid(null));

        PendingSignup noToken = signup(BASE_TIME);
        noToken.setToken(null);
        assertFalse("sign-up without a token is invalid", service.isSignupValid(noToken));

        PendingSignup noTime = signup(BASE_TIME);
        noTime.setSignupTime(null);
        assertFalse("sign-up without a timestamp is invalid", service.isSignupValid(noTime));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Recovery (password-reset) token expiry
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void recoveryToken_expiresExactlyAfterItsTtl() {
        String token = service.issueRecoveryToken();

        now[0] = BASE_TIME + RECOVERY_TTL;          // exactly at the TTL boundary -> still valid
        assertFalse("a recovery token at the TTL boundary must still be valid", service.isRecoveryTokenExpired(token));

        now[0] = BASE_TIME + RECOVERY_TTL + 1;      // one millisecond past -> expired
        assertTrue("a recovery token past the TTL must be expired", service.isRecoveryTokenExpired(token));
    }

    @Test
    public void recoveryToken_malformedOrMissingIsTreatedAsExpired() {
        assertTrue("null token must be treated as expired", service.isRecoveryTokenExpired(null));
        assertTrue("token without an embedded time must be treated as expired (fail closed)",
                service.isRecoveryTokenExpired("legacyTokenWithoutTime"));
        assertTrue("token with an empty time part must be treated as expired",
                service.isRecoveryTokenExpired("token-"));
        assertTrue("token with an unparseable time part must be treated as expired",
                service.isRecoveryTokenExpired("token-not_base36"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Password reset completion: one-time use + re-login synchronisation
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void completePasswordReset_activatesAccountAndIssuesFreshAuthToken() {
        User user = newUser();
        service.initializeNewUser(user, true);              // a forced-reset account
        String authTokenBefore = user.getAuthToken();
        assertNotNullOrEmpty("precondition: reset token issued", user.getPasswordResetToken());

        service.completePasswordReset(user, "NEW-HASH");

        assertEquals("the new password hash must be stored", "NEW-HASH", user.getPassword());
        assertEquals("the new password hash must also be staged for the persistence update",
                "NEW-HASH", user.getNewPassword());
        assertNull("the reset token must be consumed (one-time use)", user.getPasswordResetToken());
        assertFalse("the account must be active (no longer requiring a reset)", user.isPasswordReset());
        assertNotNullOrEmpty("a fresh auth token must be present", user.getAuthToken());
        assertNotEquals("a fresh auth token must be issued so login and profile-update stay in sync",
                authTokenBefore, user.getAuthToken());
    }

    @Test
    public void completePasswordReset_consumesTheTokenSoItCannotBeReplayed() {
        User user = newUser();
        service.requirePasswordReset(user);
        String token = user.getPasswordResetToken();
        assertFalse("precondition: the token is valid before use", service.isRecoveryTokenExpired(token));

        service.completePasswordReset(user, "NEW-HASH");

        // The user no longer carries the token, so a replayed reset lookup by token would resolve to nobody.
        assertNull("the consumed token must be cleared from the account", user.getPasswordResetToken());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Begin reset request on an existing, active user
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void requirePasswordReset_doesNotTouchPasswordOrAuthToken() {
        User user = newUser();
        service.initializeNewUser(user, false);
        String authToken = user.getAuthToken();
        String password = user.getPassword();

        service.requirePasswordReset(user);

        assertTrue("the reset flag must be raised", user.isPasswordReset());
        assertNotNullOrEmpty("a recovery token must be issued", user.getPasswordResetToken());
        assertEquals("requesting a reset must not change the auth token", authToken, user.getAuthToken());
        assertEquals("requesting a reset must not change the password", password, user.getPassword());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Lazy auth-token back-fill used by the login flow
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void ensureAuthToken_backfillsOnlyWhenMissing() {
        User user = newUser();
        assertNull("precondition: no auth token", user.getAuthToken());

        service.ensureAuthToken(user);
        String issued = user.getAuthToken();
        assertNotNullOrEmpty("a token must be back-filled when missing", issued);

        service.ensureAuthToken(user);
        assertEquals("an existing token must not be regenerated", issued, user.getAuthToken());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private static User newUser() {
        User user = new User();
        user.setLogin("alice");
        user.setEmail("alice@example.com");
        user.setName("Alice");
        user.setPassword("OLD-HASH");
        return user;
    }

    private static PendingSignup signup(long signupTime) {
        PendingSignup signup = new PendingSignup();
        signup.setEmail("signup@example.com");
        signup.setLanguage("en");
        signup.setToken("SIGNUP-TOKEN");
        signup.setSignupTime(signupTime);
        return signup;
    }

    private static void assertNotNullOrEmpty(String message, String value) {
        assertNotNull(message, value);
        assertFalse(message, value.isEmpty());
    }
}
