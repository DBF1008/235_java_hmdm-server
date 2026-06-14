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

package com.hmdm.security;

import com.hmdm.persistence.AuthTestSupport;
import com.hmdm.persistence.AuthTestSupport.TestRecord;
import com.hmdm.persistence.domain.User;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * <p>Regression tests for {@link SecurityContext#canAccess}, the single source of truth for tenant/role
 * access decisions. Covers multiple customers and multiple roles so the read, write and linked-data paths
 * (which all delegate here) cannot drift apart again.</p>
 */
public class SecurityContextAccessTest {

    private static final int HOME = 100;
    private static final int OTHER = 200;

    @After
    public void tearDown() {
        AuthTestSupport.clear();
    }

    @Test
    public void ordinaryAdminCanAccessOwnCustomerRecord() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        Assert.assertTrue(SecurityContext.get().canAccess(AuthTestSupport.record(1, HOME)));
    }

    @Test
    public void ordinaryAdminCannotAccessOtherCustomerRecord() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        Assert.assertFalse(SecurityContext.get().canAccess(AuthTestSupport.record(1, OTHER)));
    }

    @Test
    public void ordinaryAdminCanAccessCommonRecordOfAnotherCustomer() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        Assert.assertTrue(SecurityContext.get().canAccess(AuthTestSupport.commonRecord(1, OTHER)));
    }

    @Test
    public void superAdminCanAccessOtherCustomerRecord() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, true));
        Assert.assertTrue(SecurityContext.get().canAccess(AuthTestSupport.record(1, OTHER)));
    }

    @Test
    public void superAdminCanAccessOwnCustomerRecord() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, true));
        Assert.assertTrue(SecurityContext.get().canAccess(AuthTestSupport.record(1, HOME)));
    }

    @Test
    public void anonymousCannotAccessAnyRecord() {
        AuthTestSupport.asAnonymous();
        Assert.assertFalse("anonymous must not read ordinary records",
                SecurityContext.get().canAccess(AuthTestSupport.record(1, HOME)));
        Assert.assertFalse("anonymous must not read even common records",
                SecurityContext.get().canAccess(AuthTestSupport.commonRecord(1, HOME)));
    }

    @Test
    public void nullRecordIsDenied() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, true));
        Assert.assertFalse(SecurityContext.get().canAccess(null));
    }

    @Test
    public void userWithoutRoleCanAccessOwnCustomerRecord() {
        // A null role must not break the customer-scope check (no NPE) and own-customer access still works.
        AuthTestSupport.asUser(AuthTestSupport.userWithoutRole(HOME));
        Assert.assertTrue(SecurityContext.get().canAccess(AuthTestSupport.record(1, HOME)));
    }

    @Test
    public void userWithoutRoleCannotAccessOtherCustomerRecord() {
        AuthTestSupport.asUser(AuthTestSupport.userWithoutRole(HOME));
        Assert.assertFalse(SecurityContext.get().canAccess(AuthTestSupport.record(1, OTHER)));
    }

    @Test
    public void isSuperAdminReflectsRoleAndIsNullSafe() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, true));
        Assert.assertTrue(SecurityContext.get().isSuperAdmin());

        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        Assert.assertFalse(SecurityContext.get().isSuperAdmin());

        AuthTestSupport.asUser(AuthTestSupport.userWithoutRole(HOME));
        Assert.assertFalse("null role must not throw and must not be treated as super admin",
                SecurityContext.get().isSuperAdmin());

        AuthTestSupport.asAnonymous();
        Assert.assertFalse(SecurityContext.get().isSuperAdmin());
    }

    @Test
    public void userIsSuperAdminIsNullSafe() {
        User noRole = AuthTestSupport.userWithoutRole(HOME);
        Assert.assertFalse(noRole.isSuperAdmin());
    }
}
