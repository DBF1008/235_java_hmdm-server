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
 * <p>Regression tests for the customer-scope / role guards in {@link AbstractLinkedDAO}: reading linked data
 * ({@code getLinkedList}) and updating linked data ({@code updateLinkedData}). Verifies the unified access
 * rule (super admin and common records allowed) and that common records are read in the scope of the current
 * user's customer while ordinary records are read in the scope of the record's own customer.</p>
 */
public class AbstractLinkedDaoSecurityTest {

    private static final int HOME = 100;
    private static final int OTHER = 200;
    private static final int FAR = 999;

    private final TestLinkedDAO dao = new TestLinkedDAO();

    @After
    public void tearDown() {
        AuthTestSupport.clear();
    }

    // ---- read linked data: getLinkedList ----------------------------------------------------------------

    @Test
    public void linkedListReturnsDataAndScopesToRecordCustomerForOwnCustomer() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        List<TestRecord> linked = Arrays.asList(AuthTestSupport.record(11, HOME));
        List<TestRecord> result = dao.linkedList(1, AuthTestSupport.record(1, HOME), linked);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(HOME, dao.lastScopeCustomerId.intValue());
    }

    @Test
    public void linkedListDeniesOtherCustomerForOrdinaryAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        try {
            dao.linkedList(1, AuthTestSupport.record(1, OTHER), Arrays.asList(AuthTestSupport.record(11, OTHER)));
            Assert.fail("expected SecurityException for cross-customer linked read");
        } catch (SecurityException expected) {
            // expected
        }
    }

    @Test
    public void linkedListAllowsOtherCustomerForSuperAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, true));
        List<TestRecord> linked = Arrays.asList(AuthTestSupport.record(11, OTHER));
        List<TestRecord> result = dao.linkedList(1, AuthTestSupport.record(1, OTHER), linked);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("super admin reads linked data in the record's own customer scope",
                OTHER, dao.lastScopeCustomerId.intValue());
    }

    @Test
    public void linkedListForCommonRecordScopesToCurrentUserCustomer() {
        // For a common record the linked data must be read in the CURRENT user's customer scope, not the
        // record's customer scope.
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        List<TestRecord> linked = Arrays.asList(AuthTestSupport.record(11, HOME));
        List<TestRecord> result = dao.linkedList(1, AuthTestSupport.commonRecord(1, FAR), linked);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(HOME, dao.lastScopeCustomerId.intValue());
    }

    @Test
    public void linkedListThrowsWhenRecordNotFound() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        try {
            dao.linkedList(1, null, Arrays.asList(AuthTestSupport.record(11, HOME)));
            Assert.fail("expected SecurityException when the parent record is not found");
        } catch (SecurityException expected) {
            // expected
        }
    }

    @Test
    public void linkedListDeniesForAnonymous() {
        AuthTestSupport.asAnonymous();
        try {
            dao.linkedList(1, AuthTestSupport.record(1, HOME), Arrays.asList(AuthTestSupport.record(11, HOME)));
            Assert.fail("expected SecurityException for anonymous linked read");
        } catch (SecurityException expected) {
            // expected
        }
    }

    // ---- update linked data: updateLinkedData -----------------------------------------------------------

    @Test
    public void updateLinkedRunsForOwnCustomer() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        dao.updateLinked(1, AuthTestSupport.record(1, HOME), r -> ran[0] = true);
        Assert.assertTrue(ran[0]);
    }

    @Test
    public void updateLinkedDeniesOtherCustomerForOrdinaryAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        try {
            dao.updateLinked(1, AuthTestSupport.record(1, OTHER), r -> ran[0] = true);
            Assert.fail("expected SecurityException for cross-customer linked update");
        } catch (SecurityException expected) {
            // expected
        }
        Assert.assertFalse(ran[0]);
    }

    @Test
    public void updateLinkedAllowsOtherCustomerForSuperAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, true));
        boolean[] ran = {false};
        dao.updateLinked(1, AuthTestSupport.record(1, OTHER), r -> ran[0] = true);
        Assert.assertTrue(ran[0]);
    }

    @Test
    public void updateLinkedAllowsCommonRecordForOrdinaryAdmin() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        dao.updateLinked(1, AuthTestSupport.commonRecord(1, FAR), r -> ran[0] = true);
        Assert.assertTrue(ran[0]);
    }

    @Test
    public void updateLinkedThrowsWhenRecordNotFound() {
        AuthTestSupport.asUser(AuthTestSupport.user(HOME, false));
        boolean[] ran = {false};
        try {
            dao.updateLinked(1, null, r -> ran[0] = true);
            Assert.fail("expected SecurityException when the parent record is not found");
        } catch (SecurityException expected) {
            // expected
        }
        Assert.assertFalse(ran[0]);
    }

    /**
     * <p>A concrete {@link AbstractLinkedDAO} that exposes the protected security helpers for testing and
     * records the customer id the linked-data retrieval was scoped to.</p>
     */
    private static final class TestLinkedDAO extends AbstractLinkedDAO<TestRecord, TestRecord> {
        Integer lastScopeCustomerId;

        List<TestRecord> linkedList(int recordId, TestRecord found, List<TestRecord> linked) {
            return getLinkedList(recordId,
                    anyId -> found,
                    customerId -> {
                        this.lastScopeCustomerId = customerId;
                        return linked;
                    },
                    id -> SecurityException.onCustomerDataAccessViolation(id, "test"));
        }

        void updateLinked(int recordId, TestRecord found, Consumer<TestRecord> logic) {
            updateLinkedData(recordId,
                    anyId -> found,
                    logic,
                    id -> SecurityException.onCustomerDataAccessViolation(id, "test"));
        }
    }
}
