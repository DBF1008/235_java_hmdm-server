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

import com.hmdm.persistence.AuthTestSupport.TestRecord;
import com.hmdm.security.SecurityException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * <p>Regression tests for the customer-scope / role guards in {@link AbstractDAO}: the read guard
 * ({@code getSingleRecord}), the write guard ({@code updateRecord}/{@code updateById}), list scoping
 * ({@code getList}) and customer stamping on insert ({@code insertRecord}). Exercised through a concrete
 * subclass across multiple customers and roles.</p>
 */
public class AbstractDaoSecurityTest {

    private static final int HOME = 100;
    private static final int OTHER = 200;

    private final TestDAO dao = new TestDAO();

    @After
    public void tearDown() {
        AuthTestSupport.clear();
    }

    // ---- read guard: getSingleRecord --------------------------------------------------------------------

    @Test
    public void readReturnsRecordForOwnCustomer() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        TestRecord rec = AuthTestSupport.record(1, HOME);
        Assert.assertSame(rec, dao.read(rec));
    }

    @Test
    public void readDeniesOtherCustomerForOrdinaryAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        try {
            dao.read(AuthTestSupport.record(1, OTHER));
            Assert.fail("expected SecurityException for cross-customer read");
        } catch (SecurityException expected) {
            // expected
        }
    }

    @Test
    public void readAllowsOtherCustomerForSuperAdmin() {
        // Unified semantics: a super admin can now READ another customer's record (previously this path
        // denied it while the write path allowed it).
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, true));
        TestRecord rec = AuthTestSupport.record(1, OTHER);
        Assert.assertSame(rec, dao.read(rec));
    }

    @Test
    public void readAllowsCommonRecordForOrdinaryAdmin() {
        // Unified semantics: a common record is readable via getSingleRecord (previously only linked paths
        // honored isCommon).
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        TestRecord rec = AuthTestSupport.commonRecord(1, OTHER);
        Assert.assertSame(rec, dao.read(rec));
    }

    @Test
    public void readDeniesForAnonymous() {
        AuthTestSupport.asAnonymous();
        try {
            dao.read(AuthTestSupport.record(1, HOME));
            Assert.fail("expected SecurityException for anonymous read");
        } catch (SecurityException expected) {
            // expected
        }
    }

    @Test
    public void readReturnsNullWhenNotFound() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        Assert.assertNull(dao.read(null));
    }

    // ---- write guard: updateRecord / updateById ---------------------------------------------------------

    @Test
    public void writeRunsForOwnCustomer() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        dao.write(AuthTestSupport.record(1, HOME), r -> ran[0] = true);
        Assert.assertTrue(ran[0]);
    }

    @Test
    public void writeDeniesOtherCustomerForOrdinaryAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        try {
            dao.write(AuthTestSupport.record(1, OTHER), r -> ran[0] = true);
            Assert.fail("expected SecurityException for cross-customer write");
        } catch (SecurityException expected) {
            // expected
        }
        Assert.assertFalse("update logic must not run when access is denied", ran[0]);
    }

    @Test
    public void writeAllowsOtherCustomerForSuperAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, true));
        boolean[] ran = {false};
        dao.write(AuthTestSupport.record(1, OTHER), r -> ran[0] = true);
        Assert.assertTrue(ran[0]);
    }

    @Test
    public void writeAllowsCommonRecordForOrdinaryAdmin() {
        // Unified semantics: the write guard now honors isCommon, matching the linked-data path.
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        dao.write(AuthTestSupport.commonRecord(1, OTHER), r -> ran[0] = true);
        Assert.assertTrue(ran[0]);
    }

    @Test
    public void updateByIdDeniesOtherCustomerForOrdinaryAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        try {
            dao.writeById(7, AuthTestSupport.record(7, OTHER), r -> ran[0] = true);
            Assert.fail("expected SecurityException for cross-customer updateById");
        } catch (SecurityException expected) {
            // expected
        }
        Assert.assertFalse(ran[0]);
    }

    @Test
    public void updateByIdIsNoOpWhenRecordNotFound() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        dao.writeById(7, null, r -> ran[0] = true);
        Assert.assertFalse("missing record must not run update logic and must not throw", ran[0]);
    }

    // ---- insert stamping ---------------------------------------------------------------------------------

    @Test
    public void insertStampsCurrentCustomerId() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        TestRecord rec = AuthTestSupport.record(5, OTHER); // belongs to OTHER before insert
        boolean[] ran = {false};
        dao.insert(rec, r -> ran[0] = true);
        Assert.assertTrue(ran[0]);
        Assert.assertEquals("insert must bind the record to the current user's customer", HOME, rec.getCustomerId());
    }

    // ---- list scoping ----------------------------------------------------------------------------------

    @Test
    public void listReturnsDataForAuthenticatedUser() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        List<TestRecord> data = Arrays.asList(AuthTestSupport.record(1, HOME), AuthTestSupport.record(2, HOME));
        Assert.assertEquals(2, dao.list(data).size());
    }

    @Test
    public void listIsEmptyForAnonymous() {
        AuthTestSupport.asAnonymous();
        List<TestRecord> data = Arrays.asList(AuthTestSupport.record(1, HOME), AuthTestSupport.record(2, HOME));
        Assert.assertTrue(dao.list(data).isEmpty());
    }

    /**
     * <p>A concrete {@link AbstractDAO} that exposes the protected security helpers for testing.</p>
     */
    private static final class TestDAO extends AbstractDAO<TestRecord> {
        TestRecord read(TestRecord found) {
            return getSingleRecord(() -> found, SecurityException::onCustomerDataAccessViolation);
        }

        void write(TestRecord record, Consumer<TestRecord> logic) {
            updateRecord(record, logic, SecurityException::onCustomerDataAccessViolation);
        }

        void writeById(Integer id, TestRecord found, Consumer<TestRecord> logic) {
            updateById(id, anyId -> found, logic, SecurityException::onCustomerDataAccessViolation);
        }

        List<TestRecord> list(List<TestRecord> data) {
            return getList(customerId -> data);
        }

        void insert(TestRecord record, Consumer<TestRecord> logic) {
            insertRecord(record, logic);
        }
    }
}
