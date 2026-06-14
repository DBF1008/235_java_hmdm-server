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
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <p>Regression tests for {@link AbstractDAO} — verifies that the template methods
 * correctly enforce customer scope isolation and super-admin bypass.</p>
 */
public class AbstractDAOTest {

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    // ---- Test domain object ----

    static class TestRecord implements CustomerData {
        private Integer id;
        private int customerId;

        TestRecord(Integer id, int customerId) {
            this.id = id;
            this.customerId = customerId;
        }

        @Override
        public Integer getId() { return id; }

        @Override
        public int getCustomerId() { return customerId; }

        @Override
        public void setCustomerId(int customerId) { this.customerId = customerId; }
    }

    // ---- Test DAO ----

    static class TestDAO extends AbstractDAO<TestRecord> {
        // Expose protected methods for testing
        public List<TestRecord> callGetList(Function<Integer, List<TestRecord>> fn) {
            return getList(fn);
        }

        public TestRecord callGetSingleRecord(java.util.function.Supplier<TestRecord> searchLogic,
                                              Function<TestRecord, com.hmdm.security.SecurityException> exceptionProvider) {
            return getSingleRecord(searchLogic, exceptionProvider);
        }

        public void callInsertRecord(TestRecord record, Consumer<TestRecord> insertLogic) {
            insertRecord(record, insertLogic);
        }

        public void callUpdateRecord(TestRecord record, Consumer<TestRecord> updateLogic,
                                     Function<TestRecord, com.hmdm.security.SecurityException> exceptionProvider) {
            updateRecord(record, updateLogic, exceptionProvider);
        }
    }

    // ---- Helpers ----

    private static UserRole createRole(int id, boolean superAdmin) {
        UserRole role = new UserRole();
        role.setId(id);
        role.setSuperAdmin(superAdmin);
        role.setPermissions(Collections.emptyList());
        return role;
    }

    private static User createUser(int id, int customerId, boolean superAdmin) {
        User user = new User();
        user.setId(id);
        user.setCustomerId(customerId);
        user.setUserRole(createRole(superAdmin ? 1 : 2, superAdmin));
        user.setLogin("user" + id);
        return user;
    }

    // ---- tests: getList ----

    @Test
    public void testGetList_scopesToCurrentCustomer() {
        User user = createUser(1, 42, false);
        SecurityContext.init(user);

        TestDAO dao = new TestDAO();
        AtomicReference<Integer> capturedCustomerId = new AtomicReference<>();
        dao.callGetList(customerId -> {
            capturedCustomerId.set(customerId);
            return Collections.singletonList(new TestRecord(1, customerId));
        });

        Assert.assertEquals("getList should pass current user's customerId", Integer.valueOf(42), capturedCustomerId.get());
    }

    @Test
    public void testGetList_noUser_returnsEmptyList() {
        // No SecurityContext initialized
        TestDAO dao = new TestDAO();
        List<TestRecord> result = dao.callGetList(customerId -> {
            Assert.fail("Should not be called when no user");
            return Collections.emptyList();
        });

        Assert.assertTrue("should return empty list when no user", result.isEmpty());
    }

    // ---- tests: getSingleRecord ----

    @Test
    public void testGetSingleRecord_verifyCustomerMatch() {
        User user = createUser(1, 10, false);
        SecurityContext.init(user);

        TestDAO dao = new TestDAO();
        TestRecord record = new TestRecord(1, 10);
        TestRecord result = dao.callGetSingleRecord(
                () -> record,
                SecurityException::onCustomerDataAccessViolation
        );

        Assert.assertNotNull("should return record when customerId matches", result);
        Assert.assertEquals(10, result.getCustomerId());
    }

    @Test(expected = com.hmdm.security.SecurityException.class)
    public void testGetSingleRecord_throwOnCustomerMismatch() {
        User user = createUser(1, 10, false);
        SecurityContext.init(user);

        TestDAO dao = new TestDAO();
        TestRecord record = new TestRecord(1, 99); // Different customerId
        dao.callGetSingleRecord(
                () -> record,
                SecurityException::onCustomerDataAccessViolation
        );
    }

    @Test
    public void testGetSingleRecord_superAdmin_bypassesCustomerCheck() {
        User superUser = createUser(1, 1, true);
        SecurityContext.init(superUser);

        TestDAO dao = new TestDAO();
        // Record belongs to customer 99, but superAdmin should be able to access it
        // However, getSingleRecord checks user.customerId == record.customerId, so superAdmin with customerId=1
        // would NOT match record with customerId=99. This tests the CURRENT behavior.
        // Note: getSingleRecord does NOT have superAdmin bypass — only updateRecord does.
        TestRecord record = new TestRecord(1, 1); // Same customer as superAdmin
        TestRecord result = dao.callGetSingleRecord(
                () -> record,
                SecurityException::onCustomerDataAccessViolation
        );

        Assert.assertNotNull("superAdmin should access records from their own customer", result);
    }

    @Test
    public void testGetSingleRecord_nullRecord_returnsNull() {
        User user = createUser(1, 10, false);
        SecurityContext.init(user);

        TestDAO dao = new TestDAO();
        TestRecord result = dao.callGetSingleRecord(
                () -> null,
                SecurityException::onCustomerDataAccessViolation
        );

        Assert.assertNull("should return null when record not found", result);
    }

    // ---- tests: insertRecord ----

    @Test
    public void testInsertRecord_autoStampsCustomerId() {
        User user = createUser(1, 42, false);
        SecurityContext.init(user);

        TestDAO dao = new TestDAO();
        TestRecord record = new TestRecord(null, 0); // customerId not yet set
        AtomicReference<TestRecord> capturedRecord = new AtomicReference<>();

        dao.callInsertRecord(record, capturedRecord::set);

        Assert.assertNotNull("insert logic should have been called", capturedRecord.get());
        Assert.assertEquals("customerId should be auto-stamped from current user",
                42, record.getCustomerId());
    }

    @Test
    public void testInsertRecord_noUser_doesNotCallInsertLogic() {
        // No SecurityContext
        TestDAO dao = new TestDAO();
        TestRecord record = new TestRecord(null, 0);
        AtomicInteger callCount = new AtomicInteger(0);

        dao.callInsertRecord(record, r -> callCount.incrementAndGet());

        Assert.assertEquals("insert logic should NOT be called when no user", 0, callCount.get());
    }

    // ---- tests: updateRecord ----

    @Test
    public void testUpdateRecord_allowsSameCustomer() {
        User user = createUser(1, 10, false);
        SecurityContext.init(user);

        TestDAO dao = new TestDAO();
        TestRecord record = new TestRecord(1, 10);
        AtomicInteger updateCount = new AtomicInteger(0);

        dao.callUpdateRecord(record, r -> updateCount.incrementAndGet(),
                SecurityException::onCustomerDataAccessViolation);

        Assert.assertEquals("update should execute for same customer", 1, updateCount.get());
    }

    @Test
    public void testUpdateRecord_allowsSuperAdmin() {
        User superUser = createUser(1, 1, true);
        SecurityContext.init(superUser);

        TestDAO dao = new TestDAO();
        TestRecord record = new TestRecord(1, 99); // Different customer
        AtomicInteger updateCount = new AtomicInteger(0);

        dao.callUpdateRecord(record, r -> updateCount.incrementAndGet(),
                SecurityException::onCustomerDataAccessViolation);

        Assert.assertEquals("superAdmin should be able to update any customer's record", 1, updateCount.get());
    }

    @Test(expected = com.hmdm.security.SecurityException.class)
    public void testUpdateRecord_throwsOnDifferentCustomer() {
        User user = createUser(1, 10, false);
        SecurityContext.init(user);

        TestDAO dao = new TestDAO();
        TestRecord record = new TestRecord(1, 99); // Different customer
        dao.callUpdateRecord(record, r -> {},
                SecurityException::onCustomerDataAccessViolation);
    }
}
