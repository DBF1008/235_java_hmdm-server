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

package com.hmdm.persistence;

import com.hmdm.persistence.domain.CustomerData;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.domain.UserRolePermission;
import com.hmdm.security.SecurityContext;

import java.util.Collections;

/**
 * <p>Shared fixtures for the customer-scope / role-authorization regression tests. Provides factory methods
 * for building users in various roles, lightweight {@link CustomerData} records owned by a given customer,
 * and helpers for driving the request-scoped {@link SecurityContext}.</p>
 */
public final class AuthTestSupport {

    /** A role id that is treated as the super-admin role in fixtures. */
    public static final int SUPER_ADMIN_ROLE_ID = 1;
    /** A role id that is treated as an ordinary organization-admin role in fixtures. */
    public static final int ORG_ADMIN_ROLE_ID = 100;

    private AuthTestSupport() {
    }

    /**
     * <p>Builds a user bound to the given customer, with a role flagged (or not) as super admin. The role is
     * granted a single non-super permission so the permission list is exercised without granting cross-tenant
     * access by itself.</p>
     */
    public static User user(int customerId, boolean superAdmin) {
        UserRolePermission permission = new UserRolePermission();
        permission.setId(10);
        permission.setName("devices");

        UserRole role = new UserRole();
        role.setId(superAdmin ? SUPER_ADMIN_ROLE_ID : ORG_ADMIN_ROLE_ID);
        role.setName(superAdmin ? "Super Admin" : "Org Admin");
        role.setSuperAdmin(superAdmin);
        role.setPermissions(Collections.singletonList(permission));

        User user = new User();
        user.setId(customerId * 1000 + (superAdmin ? 1 : 2));
        user.setLogin((superAdmin ? "superadmin" : "admin") + "@c" + customerId);
        user.setCustomerId(customerId);
        user.setUserRole(role);
        return user;
    }

    /**
     * <p>Builds a user whose role reference is {@code null}. Used to exercise the null-safety of
     * {@link User#isSuperAdmin()} and {@link SecurityContext#canAccess(CustomerData)}.</p>
     */
    public static User userWithoutRole(int customerId) {
        User user = new User();
        user.setId(customerId * 1000 + 9);
        user.setLogin("norole@c" + customerId);
        user.setCustomerId(customerId);
        user.setUserRole(null);
        return user;
    }

    /** A customer-scoped record (not shared across customers). */
    public static TestRecord record(int id, int customerId) {
        return new TestRecord(id, customerId, false);
    }

    /** A common record, shared/visible across customers. */
    public static TestRecord commonRecord(int id, int customerId) {
        return new TestRecord(id, customerId, true);
    }

    /** Establishes a security context for the given authenticated user. */
    public static void asUser(User user) {
        SecurityContext.release();
        SecurityContext.init(user);
    }

    /** Establishes a security context with no authenticated user (anonymous request). */
    public static void asAnonymous() {
        SecurityContext.release();
        SecurityContext.init((Integer) null);
    }

    /** Clears the security context; call from test tear-down to avoid thread-local leakage. */
    public static void clear() {
        SecurityContext.release();
    }

    /**
     * <p>Minimal {@link CustomerData} implementation for exercising the security helpers without depending on
     * a fully-populated production domain object.</p>
     */
    public static final class TestRecord implements CustomerData {
        private final Integer id;
        private int customerId;
        private final boolean common;

        public TestRecord(int id, int customerId, boolean common) {
            this.id = id;
            this.customerId = customerId;
            this.common = common;
        }

        @Override
        public Integer getId() {
            return id;
        }

        @Override
        public int getCustomerId() {
            return customerId;
        }

        @Override
        public void setCustomerId(int customerId) {
            this.customerId = customerId;
        }

        @Override
        public boolean isCommon() {
            return common;
        }
    }
}
