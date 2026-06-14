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

import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.domain.UserRolePermission;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * <p>Regression tests for {@link SecurityContext} — verifies customer scope isolation,
 * role detection, and permission checking across multi-customer and multi-role scenarios.</p>
 */
public class SecurityContextTest {

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    // ---- helpers ----

    private static UserRole createRole(int id, boolean superAdmin, String... permissionNames) {
        UserRole role = new UserRole();
        role.setId(id);
        role.setSuperAdmin(superAdmin);
        if (permissionNames.length > 0) {
            java.util.List<UserRolePermission> perms = new java.util.ArrayList<>();
            for (String name : permissionNames) {
                UserRolePermission p = new UserRolePermission();
                p.setName(name);
                perms.add(p);
            }
            role.setPermissions(perms);
        } else {
            role.setPermissions(Collections.emptyList());
        }
        return role;
    }

    private static User createUser(int id, int customerId, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setCustomerId(customerId);
        user.setUserRole(role);
        user.setLogin("user" + id);
        return user;
    }

    // ---- tests: init / release ----

    @Test
    public void testInit_withUser_setsCustomerIdFromUser() {
        UserRole role = createRole(2, false);
        User user = createUser(1, 42, role);

        SecurityContext.init(user);

        Assert.assertTrue("currentUser should be present", SecurityContext.get().getCurrentUser().isPresent());
        Assert.assertEquals("customerId should match user's customerId",
                Optional.of(42), SecurityContext.get().getCurrentCustomerId());
    }

    @Test
    public void testInit_withCustomerId_setsCustomerIdOnly() {
        SecurityContext.init(99);

        Assert.assertFalse("currentUser should NOT be present", SecurityContext.get().getCurrentUser().isPresent());
        Assert.assertEquals("customerId should be set",
                Optional.of(99), SecurityContext.get().getCurrentCustomerId());
    }

    @Test
    public void testRelease_clearsContext() {
        SecurityContext.init(42);
        Assert.assertNotNull("context should exist", SecurityContext.get());

        SecurityContext.release();

        Assert.assertNull("context should be null after release", SecurityContext.get());
    }

    // ---- tests: isSuperAdmin ----

    @Test
    public void testIsSuperAdmin_withSuperAdminUser_returnsTrue() {
        UserRole superRole = createRole(1, true);
        User superUser = createUser(1, 1, superRole);

        SecurityContext.init(superUser);

        Assert.assertTrue("superAdmin should be true", SecurityContext.get().isSuperAdmin());
    }

    @Test
    public void testIsSuperAdmin_withNormalUser_returnsFalse() {
        UserRole normalRole = createRole(2, false);
        User normalUser = createUser(2, 10, normalRole);

        SecurityContext.init(normalUser);

        Assert.assertFalse("superAdmin should be false for normal user", SecurityContext.get().isSuperAdmin());
    }

    @Test
    public void testIsSuperAdmin_withCustomerIdOnly_returnsFalse() {
        SecurityContext.init(5);

        Assert.assertFalse("superAdmin should be false when no user present", SecurityContext.get().isSuperAdmin());
    }

    // ---- tests: hasPermission ----

    @Test
    public void testHasPermission_superAdmin_alwaysTrue() {
        UserRole superRole = createRole(1, true);
        User superUser = createUser(1, 1, superRole);

        SecurityContext.init(superUser);

        Assert.assertTrue("superAdmin should have any permission",
                SecurityContext.get().hasPermission("any_random_permission"));
    }

    @Test
    public void testHasPermission_matchingPermission_returnsTrue() {
        UserRole role = createRole(3, false, "plugin_messaging_send", "plugin_messaging_delete");
        User user = createUser(3, 10, role);

        SecurityContext.init(user);

        Assert.assertTrue("should have messaging_send permission",
                SecurityContext.get().hasPermission("plugin_messaging_send"));
        Assert.assertTrue("should have messaging_delete permission",
                SecurityContext.get().hasPermission("plugin_messaging_delete"));
    }

    @Test
    public void testHasPermission_nonMatchingPermission_returnsFalse() {
        UserRole role = createRole(3, false, "plugin_messaging_send");
        User user = createUser(3, 10, role);

        SecurityContext.init(user);

        Assert.assertFalse("should NOT have audit permission",
                SecurityContext.get().hasPermission("plugin_audit_access"));
    }

    @Test
    public void testHasPermission_caseInsensitive() {
        UserRole role = createRole(3, false, "Plugin_Messaging_Send");
        User user = createUser(3, 10, role);

        SecurityContext.init(user);

        Assert.assertTrue("permission matching should be case-insensitive",
                SecurityContext.get().hasPermission("plugin_messaging_send"));
        Assert.assertTrue("permission matching should be case-insensitive (upper)",
                SecurityContext.get().hasPermission("PLUGIN_MESSAGING_SEND"));
    }

    // ---- tests: multi-customer isolation ----

    @Test
    public void testMultiCustomer_isolation() {
        UserRole role = createRole(2, false);

        // Simulate customer A
        User userA = createUser(1, 100, role);
        SecurityContext.init(userA);
        Assert.assertEquals("customer A should see customerId 100",
                Optional.of(100), SecurityContext.get().getCurrentCustomerId());
        SecurityContext.release();

        // Simulate customer B
        User userB = createUser(2, 200, role);
        SecurityContext.init(userB);
        Assert.assertEquals("customer B should see customerId 200",
                Optional.of(200), SecurityContext.get().getCurrentCustomerId());
        Assert.assertNotEquals("customer B should NOT see customer A's id",
                Optional.of(100), SecurityContext.get().getCurrentCustomerId());
    }

    @Test
    public void testGetCurrentUserName_returnsLoginWhenPresent() {
        UserRole role = createRole(2, false);
        User user = createUser(1, 10, role);
        user.setLogin("admin@acme.com");

        SecurityContext.init(user);

        Assert.assertEquals("should return user login", "admin@acme.com", SecurityContext.get().getCurrentUserName());
    }

    @Test
    public void testGetCurrentUserName_returnsNullWhenNoUser() {
        SecurityContext.init(10);

        Assert.assertEquals("should return 'null' string when no user", "null", SecurityContext.get().getCurrentUserName());
    }
}
