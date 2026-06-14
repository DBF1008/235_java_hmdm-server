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

import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.domain.UserRolePermission;
import com.hmdm.persistence.mapper.UserRoleMapper;
import com.hmdm.security.SecurityContext;
import com.hmdm.security.SecurityException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>Regression tests for {@link UserRoleDAO} — verifies that:
 * <ul>
 *   <li>{@code checkAccess()} throws {@link SecurityException} (not IllegalArgumentException)</li>
 *   <li>{@code findAll()} excludes the orgAdmin role</li>
 *   <li>{@code delete()} refuses to delete the orgAdmin role</li>
 *   <li>Access control works correctly in both single-customer and multi-tenant modes</li>
 * </ul>
 * </p>
 */
public class UserRoleDAOTest {

    private static final int ORG_ADMIN_ROLE_ID = 2;

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    // ---- Stub implementations ----

    static class StubUserRoleMapper implements UserRoleMapper {
        List<UserRole> rolesToReturn = new ArrayList<>();
        int deleteCallCount = 0;
        int lastDeletedId = -1;

        @Override
        public List<UserRolePermission> getPermissionsList() { return Collections.emptyList(); }

        @Override
        public List<UserRole> findAll() { return new ArrayList<>(rolesToReturn); }

        @Override
        public UserRole findByName(String name) { return null; }

        @Override
        public UserRole findById(Integer id) { return null; }

        @Override
        public void insert(UserRole userRole) {}

        @Override
        public void update(UserRole userRole) {}

        @Override
        public void delete(Integer roleId) {
            deleteCallCount++;
            lastDeletedId = roleId;
        }

        @Override
        public void deletePermissions(Integer id) {}

        @Override
        public void insertPermissions(Integer roleId, List<Integer> permissions) {}
    }

    static class StubUnsecureDAO extends UnsecureDAO {
        private final boolean singleCustomer;

        StubUnsecureDAO(boolean singleCustomer) {
            super(null, null, null, null, null, null,
                  null, null, null, null, null, null,
                  null, null, "/tmp", ORG_ADMIN_ROLE_ID, "");
            this.singleCustomer = singleCustomer;
        }

        @Override
        public boolean isSingleCustomer() {
            return singleCustomer;
        }
    }

    static class StubUserDAO extends UserDAO {
        StubUserDAO() {
            super(null, ORG_ADMIN_ROLE_ID);
        }

        @Override
        public boolean isOrgAdmin(User user) {
            return user.getUserRole().getId() == ORG_ADMIN_ROLE_ID;
        }
    }

    // ---- Testable UserRoleDAO subclass ----

    static class TestableUserRoleDAO extends UserRoleDAO {
        TestableUserRoleDAO(UnsecureDAO unsecureDAO, UserDAO userDAO, UserRoleMapper mapper, int orgAdminRoleId) {
            super(unsecureDAO, userDAO, mapper, orgAdminRoleId);
        }

        // Make checkAccess visible for testing
        @Override
        public void checkAccess() {
            super.checkAccess();
        }
    }

    // ---- Helpers ----

    private static User createUser(int id, int customerId, int roleId, boolean superAdmin) {
        UserRole role = new UserRole();
        role.setId(roleId);
        role.setSuperAdmin(superAdmin);
        role.setPermissions(Collections.emptyList());

        User user = new User();
        user.setId(id);
        user.setCustomerId(customerId);
        user.setUserRole(role);
        user.setLogin("user" + id);
        return user;
    }

    private static UserRole createRole(int id, String name) {
        UserRole role = new UserRole();
        role.setId(id);
        role.setName(name);
        return role;
    }

    // ---- tests: checkAccess throws SecurityException ----

    @Test
    public void testCheckAccess_throwsSecurityException_notIllegalArgumentException() {
        // Setup: normal user (not superAdmin, not orgAdmin) in multi-tenant mode
        StubUnsecureDAO unsecureDAO = new StubUnsecureDAO(false); // multi-tenant
        StubUserDAO userDAO = new StubUserDAO();
        StubUserRoleMapper mapper = new StubUserRoleMapper();
        TestableUserRoleDAO dao = new TestableUserRoleDAO(unsecureDAO, userDAO, mapper, ORG_ADMIN_ROLE_ID);

        User normalUser = createUser(1, 10, 3, false); // role id 3 = User, not superAdmin
        SecurityContext.init(normalUser);

        try {
            dao.checkAccess();
            Assert.fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            Assert.assertEquals("error code should be 403", 403, e.getErrorCode());
        } catch (IllegalArgumentException e) {
            Assert.fail("Should throw SecurityException, NOT IllegalArgumentException");
        }
    }

    // ---- tests: hasAccess in different modes ----

    @Test
    public void testHasAccess_superAdmin_alwaysTrue() {
        // Single-customer mode
        StubUnsecureDAO unsecureDAO = new StubUnsecureDAO(true);
        StubUserDAO userDAO = new StubUserDAO();
        StubUserRoleMapper mapper = new StubUserRoleMapper();
        TestableUserRoleDAO dao = new TestableUserRoleDAO(unsecureDAO, userDAO, mapper, ORG_ADMIN_ROLE_ID);

        User superUser = createUser(1, 1, 1, true);
        SecurityContext.init(superUser);

        Assert.assertTrue("superAdmin should have access in single-customer mode", dao.hasAccess());
        SecurityContext.release();

        // Multi-tenant mode
        StubUnsecureDAO unsecureDAO2 = new StubUnsecureDAO(false);
        TestableUserRoleDAO dao2 = new TestableUserRoleDAO(unsecureDAO2, userDAO, mapper, ORG_ADMIN_ROLE_ID);
        SecurityContext.init(superUser);

        Assert.assertTrue("superAdmin should have access in multi-tenant mode", dao2.hasAccess());
    }

    @Test
    public void testHasAccess_orgAdmin_singleCustomer_true() {
        StubUnsecureDAO unsecureDAO = new StubUnsecureDAO(true); // single-customer
        StubUserDAO userDAO = new StubUserDAO();
        StubUserRoleMapper mapper = new StubUserRoleMapper();
        TestableUserRoleDAO dao = new TestableUserRoleDAO(unsecureDAO, userDAO, mapper, ORG_ADMIN_ROLE_ID);

        User orgAdmin = createUser(2, 1, ORG_ADMIN_ROLE_ID, false);
        SecurityContext.init(orgAdmin);

        Assert.assertTrue("orgAdmin should have access in single-customer mode", dao.hasAccess());
    }

    @Test
    public void testHasAccess_orgAdmin_multiTenant_false() {
        StubUnsecureDAO unsecureDAO = new StubUnsecureDAO(false); // multi-tenant
        StubUserDAO userDAO = new StubUserDAO();
        StubUserRoleMapper mapper = new StubUserRoleMapper();
        TestableUserRoleDAO dao = new TestableUserRoleDAO(unsecureDAO, userDAO, mapper, ORG_ADMIN_ROLE_ID);

        User orgAdmin = createUser(2, 10, ORG_ADMIN_ROLE_ID, false);
        SecurityContext.init(orgAdmin);

        Assert.assertFalse("orgAdmin should NOT have access in multi-tenant mode", dao.hasAccess());
    }

    @Test
    public void testHasAccess_normalUser_alwaysFalse() {
        StubUnsecureDAO unsecureDAO = new StubUnsecureDAO(true);
        StubUserDAO userDAO = new StubUserDAO();
        StubUserRoleMapper mapper = new StubUserRoleMapper();
        TestableUserRoleDAO dao = new TestableUserRoleDAO(unsecureDAO, userDAO, mapper, ORG_ADMIN_ROLE_ID);

        User normalUser = createUser(3, 10, 3, false); // role id 3 = User
        SecurityContext.init(normalUser);

        Assert.assertFalse("normal user should NOT have access", dao.hasAccess());
    }

    // ---- tests: findAll excludes orgAdmin role ----

    @Test
    public void testFindAll_excludesOrgAdminRole() {
        StubUnsecureDAO unsecureDAO = new StubUnsecureDAO(true);
        StubUserDAO userDAO = new StubUserDAO();
        StubUserRoleMapper mapper = new StubUserRoleMapper();

        mapper.rolesToReturn.add(createRole(1, "Super-Admin"));
        mapper.rolesToReturn.add(createRole(ORG_ADMIN_ROLE_ID, "Admin"));
        mapper.rolesToReturn.add(createRole(3, "User"));
        mapper.rolesToReturn.add(createRole(100, "Observer"));

        TestableUserRoleDAO dao = new TestableUserRoleDAO(unsecureDAO, userDAO, mapper, ORG_ADMIN_ROLE_ID);

        User superUser = createUser(1, 1, 1, true);
        SecurityContext.init(superUser);

        List<UserRole> roles = dao.findAll();

        Assert.assertEquals("should have 3 roles (orgAdmin excluded)", 3, roles.size());
        for (UserRole role : roles) {
            Assert.assertNotEquals("orgAdmin role should be excluded",
                    ORG_ADMIN_ROLE_ID, role.getId().intValue());
        }
    }

    // ---- tests: delete refuses orgAdmin role ----

    @Test
    public void testDelete_refusesOrgAdminRole() {
        StubUnsecureDAO unsecureDAO = new StubUnsecureDAO(true);
        StubUserDAO userDAO = new StubUserDAO();
        StubUserRoleMapper mapper = new StubUserRoleMapper();
        TestableUserRoleDAO dao = new TestableUserRoleDAO(unsecureDAO, userDAO, mapper, ORG_ADMIN_ROLE_ID);

        User superUser = createUser(1, 1, 1, true);
        SecurityContext.init(superUser);

        try {
            dao.delete(ORG_ADMIN_ROLE_ID);
            Assert.fail("Should throw exception when trying to delete orgAdmin role");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue("should mention admin role",
                    e.getMessage().toLowerCase().contains("admin"));
        }

        Assert.assertEquals("delete should NOT be called on mapper", 0, mapper.deleteCallCount);
    }

    @Test
    public void testDelete_allowsNonOrgAdminRole() {
        StubUnsecureDAO unsecureDAO = new StubUnsecureDAO(true);
        StubUserDAO userDAO = new StubUserDAO();
        StubUserRoleMapper mapper = new StubUserRoleMapper();
        TestableUserRoleDAO dao = new TestableUserRoleDAO(unsecureDAO, userDAO, mapper, ORG_ADMIN_ROLE_ID);

        User superUser = createUser(1, 1, 1, true);
        SecurityContext.init(superUser);

        dao.delete(3); // Delete User role (id=3, not orgAdmin)

        Assert.assertEquals("delete should be called once", 1, mapper.deleteCallCount);
        Assert.assertEquals("should delete correct role", 3, mapper.lastDeletedId);
    }
}
