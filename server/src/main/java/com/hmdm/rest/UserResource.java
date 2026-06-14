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

package com.hmdm.rest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.UserDAO;
import com.hmdm.persistence.domain.User;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import com.hmdm.service.AuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * <p>REST resource for authenticated user operations: profile update and password change.</p>
 *
 * <p>All endpoints require authentication (JWT or session). Password changes are routed through
 * {@link AuthService} to ensure consistent auth state transitions:</p>
 * <ul>
 *   <li>Old password is verified before change</li>
 *   <li>New password is hashed using the standard chain</li>
 *   <li>passwordReset flag is cleared</li>
 *   <li>passwordResetToken is cleared (one-time token consumed)</li>
 *   <li>authToken is regenerated, invalidating all existing JWTs — the user must re-login</li>
 * </ul>
 */
@Singleton
@Path("/rest/private/users")
@Api(tags = {"User Management"})
public class UserResource {

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserDAO userDAO;
    private final UnsecureDAO unsecureDAO;
    private final AuthService authService;

    @Inject
    public UserResource(UserDAO userDAO,
                        UnsecureDAO unsecureDAO,
                        AuthService authService) {
        this.userDAO = userDAO;
        this.unsecureDAO = unsecureDAO;
        this.authService = authService;
    }

    /**
     * <p>Swagger constructor.</p>
     */
    public UserResource() {
    }

    // =============================================================================================================
    // Profile update
    // =============================================================================================================

    @PUT
    @Path("/profile")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update current user's profile",
            notes = "Updates the authenticated user's name, email, and other editable profile fields",
            authorizations = {@Authorization("Bearer")})
    public Response updateProfile(User updatedUser) {
        try {
            User currentUser = SecurityContext.get()
                    .getCurrentUser()
                    .orElse(null);

            if (currentUser == null) {
                return Response.ERROR("error.notauthenticated");
            }

            // Ensure the update targets the current user only (prevent IDOR)
            updatedUser.setId(currentUser.getId());
            updatedUser.setCustomerId(currentUser.getCustomerId());

            // Preserve fields that should not be changed via profile update
            updatedUser.setPassword(currentUser.getPassword());
            updatedUser.setAuthToken(currentUser.getAuthToken());
            updatedUser.setPasswordResetToken(currentUser.getPasswordResetToken());
            updatedUser.setUserRole(currentUser.getUserRole());

            userDAO.updateUserMainDetails(updatedUser, false);

            log.info("Profile updated for user: {}", currentUser.getLogin());
            return Response.OK();

        } catch (Exception e) {
            log.error("Error updating user profile", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =============================================================================================================
    // Password change (authenticated)
    // =============================================================================================================

    @PUT
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Change current user's password",
            notes = "Changes the authenticated user's password after verifying the old password. " +
                    "All existing sessions are invalidated — the user must re-login.",
            authorizations = {@Authorization("Bearer")})
    public Response changePassword(User passwordChange) {
        try {
            User currentUser = SecurityContext.get()
                    .getCurrentUser()
                    .orElse(null);

            if (currentUser == null) {
                return Response.ERROR("error.notauthenticated");
            }

            // Require both old and new password
            if (passwordChange.getOldPassword() == null || passwordChange.getOldPassword().trim().isEmpty()) {
                return Response.ERROR("error.oldpassword.required");
            }
            if (passwordChange.getNewPassword() == null || passwordChange.getNewPassword().trim().isEmpty()) {
                return Response.ERROR("error.newpassword.required");
            }

            // Fetch a fresh copy of the user to get the current password hash
            User freshUser = unsecureDAO.findByLoginOrEmail(currentUser.getLogin());
            if (freshUser == null) {
                return Response.ERROR("error.user.notfound");
            }

            // Delegate to AuthService which:
            // 1. Verifies the old password
            // 2. Hashes the new password
            // 3. Clears passwordReset flag and token
            // 4. Regenerates authToken (invalidates all existing JWTs)
            // 5. Persists via UserDAO.updatePassword()
            boolean success = authService.changePassword(
                    freshUser,
                    passwordChange.getOldPassword(),
                    passwordChange.getNewPassword()
            );

            if (!success) {
                return Response.ERROR("error.oldpassword.mismatch");
            }

            log.info("Password changed for user: {}", currentUser.getLogin());
            return Response.OK("password.changed");

        } catch (Exception e) {
            log.error("Error changing password", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
