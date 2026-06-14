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

import com.hmdm.persistence.domain.CustomerData;
import com.hmdm.persistence.domain.User;

import java.util.Optional;

/**
 * <p>A security context set for the current request processing thread.</p>
 *
 * @author isv
 */
public class SecurityContext {

    /**
     * <p>A thread-local context.</p>
     */
    private static ThreadLocal<SecurityContext> ctx = new ThreadLocal<>();

    /**
     * <p>A reference to current user associated with request being processed.</p>
     */
    private final User currentUser;

    /**
     * <p>A reference to current user associated with request being processed.</p>
     */
    private final Integer customerId;

    /**
     * <p>Constructs new <code>SecurityContext</code> instance.</p>
     */
    private SecurityContext(User currentUser) {
        this.currentUser = currentUser;
        this.customerId = currentUser.getCustomerId();
    }

    /**
     * <p>Constructs new <code>SecurityContext</code> instance.</p>
     */
    private SecurityContext(Integer customerId) {
        this.currentUser = null;
        this.customerId = customerId;
    }

    /**
     * <p>Initializes the context at the start of processing the incoming request.</p>
     *
     * @param currentUser a reference to current user associated with request being processed.
     */
    public static void init(User currentUser) {
        ctx.set(new SecurityContext(currentUser));
    }

    /**
     * <p>Initializes the context at the start of processing the incoming request.</p>
     *
     * @param customerId a reference to current user associated with request being processed.
     */
    public static void init(Integer customerId) {
        ctx.set(new SecurityContext(customerId));
    }

    public static SecurityContext get() {
        return ctx.get();
    }

    /**
     * <p>Releases the context after finishing processing the incoming request.</p>
     */
    public static void release() {
        ctx.remove();
    }

    /**
     * <p>Gets the current user associated with processed request.</p>
     *
     * @return an optional reference to current user.
     */
    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(this.currentUser);
    }

    /**
     * <p>Gets the user name for logging purposes</p>
     */
    public String getCurrentUserName() {
        return getCurrentUser()
                .map(u -> u.getLogin())
                .orElse("null");
    }

    /**
     * <p>Gets the current customer ID associated with processed request.</p>
     *
     * @return an optional reference to current customer ID.
     */
    public Optional<Integer> getCurrentCustomerId() {
        return Optional.ofNullable(this.customerId);
    }

    /**
     * <p>Checks if current user is granted a Super Admin role.</p>
     *
     * @return <code>true</code> if current user is granted SUPER ADMIN role; <code>false</code> otherwise.
     */
    public boolean isSuperAdmin() {
        return getCurrentUser().map(User::isSuperAdmin).orElse(false);
    }

    /**
     * <p>Checks whether the current request is allowed to access the specified customer-owned record.</p>
     *
     * <p>This is the single source of truth for tenant/role access decisions, shared by the read, write and
     * linked-data paths so they all enforce identical semantics. Access is granted when the current user is a
     * super admin, or the record is common (shared across customers), or the record belongs to the current
     * user's customer. Anonymous requests (no current user) and {@code null} records are always denied.</p>
     *
     * @param record a customer-owned record to check access for.
     * @return <code>true</code> if the current user may access the record; <code>false</code> otherwise.
     */
    public boolean canAccess(CustomerData record) {
        if (record == null) {
            return false;
        }
        return getCurrentUser()
                .map(u -> u.isSuperAdmin() || record.isCommon() || u.getCustomerId() == record.getCustomerId())
                .orElse(false);
    }

    /**
     * <p>Checks if current user is granted specified permission.</p>
     *
     * @param permission a permission to check for granting.
     * @return <code>true</code> if current user is granted a specified permission; <code>false</code> otherwise.
     */
    public boolean hasPermission(String permission) {
        return isSuperAdmin() ||
                getCurrentUser()
                        .map(u -> u.getUserRole()
                                .getPermissions()
                                .stream()
                                .anyMatch(p -> p.getName().equalsIgnoreCase(permission))
                        ).orElse(false);
    }
}
