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

package com.hmdm.plugins.messaging.persistence;

import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.plugins.messaging.persistence.domain.Message;
import com.hmdm.plugins.messaging.persistence.mapper.MessageMapper;
import com.hmdm.plugins.messaging.rest.json.MessageFilter;
import com.hmdm.security.SecurityContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>Regression tests for {@link MessagingDAO} — verifies that customer scope is
 * correctly propagated to the mapper layer for all CRUD operations.</p>
 */
public class MessagingDAOTest {

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    // ---- Stub MessageMapper ----

    static class StubMessageMapper implements MessageMapper {
        int lastDeleteId = -1;
        int lastDeleteCustomerId = -1;
        int deleteCallCount = 0;

        int lastUpdateId = -1;
        int lastUpdateStatus = -1;
        int lastUpdateCustomerId = -1;
        int updateCallCount = 0;

        int lastInsertCustomerId = -1;
        int insertCallCount = 0;

        MessageFilter lastFindAllFilter = null;
        int findAllCallCount = 0;

        MessageFilter lastCountAllFilter = null;

        int purgeCallCount = 0;
        long purgeTs = -1;
        int purgeCustomerId = -1;

        @Override
        public int insertMessage(Message msg) {
            insertCallCount++;
            lastInsertCustomerId = msg.getCustomerId();
            msg.setId(insertCallCount); // simulate generated ID
            return 1;
        }

        @Override
        public int updateMessageStatus(int id, int status, int customerId) {
            updateCallCount++;
            lastUpdateId = id;
            lastUpdateStatus = status;
            lastUpdateCustomerId = customerId;
            return 1;
        }

        @Override
        public void deleteMessage(int id, int customerId) {
            deleteCallCount++;
            lastDeleteId = id;
            lastDeleteCustomerId = customerId;
        }

        @Override
        public void purgeOldMessages(long ts, int customerId) {
            purgeCallCount++;
            purgeTs = ts;
            purgeCustomerId = customerId;
        }

        @Override
        public List<Message> findAllMessages(MessageFilter filter) {
            findAllCallCount++;
            lastFindAllFilter = filter;
            return Collections.emptyList();
        }

        @Override
        public long countAll(MessageFilter filter) {
            lastCountAllFilter = filter;
            return 0;
        }
    }

    // ---- Helpers ----

    private static User createUser(int id, int customerId) {
        UserRole role = new UserRole();
        role.setId(2);
        role.setSuperAdmin(false);
        role.setPermissions(Collections.emptyList());

        User user = new User();
        user.setId(id);
        user.setCustomerId(customerId);
        user.setUserRole(role);
        user.setLogin("user" + id);
        return user;
    }

    // ---- tests: deleteMessage ----

    @Test
    public void testDeleteMessage_passesCustomerIdToMapper() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        User user = createUser(1, 42);
        SecurityContext.init(user);

        dao.deleteMessage(123);

        Assert.assertEquals("deleteMessage should be called once", 1, stub.deleteCallCount);
        Assert.assertEquals("should pass correct message id", 123, stub.lastDeleteId);
        Assert.assertEquals("should pass current user's customerId", 42, stub.lastDeleteCustomerId);
    }

    @Test
    public void testDeleteMessage_noUser_noOp() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        // No SecurityContext initialized
        dao.deleteMessage(123);

        Assert.assertEquals("deleteMessage should NOT be called when no user", 0, stub.deleteCallCount);
    }

    @Test
    public void testDeleteMessage_crossTenantPrevented() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        // Customer A tries to delete
        User userA = createUser(1, 100);
        SecurityContext.init(userA);
        dao.deleteMessage(500);

        // The mapper receives customerId=100, so the SQL WHERE clause
        // "AND customerId = 100" would prevent deleting messages belonging to customer 200
        Assert.assertEquals("customerId should be scoped to user A", 100, stub.lastDeleteCustomerId);
    }

    // ---- tests: updateMessageStatus ----

    @Test
    public void testUpdateMessageStatus_passesCustomerId() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        dao.updateMessageStatus(55, 1, 42);

        Assert.assertEquals("updateMessageStatus should be called once", 1, stub.updateCallCount);
        Assert.assertEquals("should pass correct message id", 55, stub.lastUpdateId);
        Assert.assertEquals("should pass correct status", 1, stub.lastUpdateStatus);
        Assert.assertEquals("should pass customerId", 42, stub.lastUpdateCustomerId);
    }

    @Test
    public void testUpdateMessageStatus_differentCustomers_isolated() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        // Customer A updates status
        dao.updateMessageStatus(10, 1, 100);
        Assert.assertEquals(100, stub.lastUpdateCustomerId);

        // Customer B updates same message id — but with different customerId
        // The SQL will NOT match if the message belongs to customer 100
        dao.updateMessageStatus(10, 1, 200);
        Assert.assertEquals("customerId should be 200 for second call", 200, stub.lastUpdateCustomerId);
        Assert.assertEquals("updateMessageStatus should be called twice", 2, stub.updateCallCount);
    }

    // ---- tests: findAll ----

    @Test
    public void testFindAll_scopesToCustomer() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        User user = createUser(1, 42);
        SecurityContext.init(user);

        MessageFilter filter = new MessageFilter();
        dao.findAll(filter);

        Assert.assertEquals("findAll should be called once", 1, stub.findAllCallCount);
        Assert.assertNotNull("filter should be captured", stub.lastFindAllFilter);
        Assert.assertEquals("filter customerId should be set from current user",
                42, stub.lastFindAllFilter.getCustomerId());
    }

    // ---- tests: insertMessage ----

    @Test
    public void testInsertMessage_stampsCustomerId() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        User user = createUser(1, 55);
        SecurityContext.init(user);

        Message msg = new Message();
        msg.setMessage("Hello");
        msg.setDeviceId(1);
        dao.insertMessage(msg);

        Assert.assertEquals("insert should be called once", 1, stub.insertCallCount);
        Assert.assertEquals("customerId should be stamped from current user", 55, stub.lastInsertCustomerId);
    }

    @Test
    public void testInsertMessage_noUser_noOp() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        // No SecurityContext
        Message msg = new Message();
        dao.insertMessage(msg);

        Assert.assertEquals("insert should NOT be called when no user", 0, stub.insertCallCount);
    }

    // ---- tests: purgeOldMessages ----

    @Test
    public void testPurgeOldMessages_scopedToCustomer() {
        StubMessageMapper stub = new StubMessageMapper();
        MessagingDAO dao = new MessagingDAO(stub);

        User user = createUser(1, 77);
        SecurityContext.init(user);

        dao.purgeOldMessages(30);

        Assert.assertEquals("purge should be called once", 1, stub.purgeCallCount);
        Assert.assertEquals("customerId should be scoped", 77, stub.purgeCustomerId);
    }
}
