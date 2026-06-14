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
import com.hmdm.persistence.domain.User;
import com.hmdm.rest.json.PasswordResetCompleteForm;
import com.hmdm.rest.json.PasswordResetForm;
import com.hmdm.rest.json.Response;
import com.hmdm.service.AuthService;
import com.hmdm.service.EmailService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * <p>REST resource for password reset flow.</p>
 *
 * <p>Two-step process:</p>
 * <ol>
 *   <li><b>POST /public/passwordReset</b>: Submit email to receive a password reset email with a token link</li>
 *   <li><b>POST /public/passwordReset/complete</b>: Submit the token along with the new password to
 *       reset the password and invalidate all existing sessions</li>
 * </ol>
 *
 * <p>Auth state transitions are delegated to {@link AuthService} to ensure:</p>
 * <ul>
 *   <li>The passwordResetToken is always cleared after use (one-time token)</li>
 *   <li>The passwordReset flag is cleared when the new password is committed</li>
 *   <li>The authToken is regenerated, invalidating all existing JWTs for the user</li>
 * </ul>
 */
@Singleton
@Path("/public")
@Api(tags = {"Password Reset"})
public class PasswordResetResource {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetResource.class);

    private final UnsecureDAO unsecureDAO;
    private final EmailService emailService;
    private final AuthService authService;

    @Inject
    public PasswordResetResource(UnsecureDAO unsecureDAO,
                                 EmailService emailService,
                                 AuthService authService) {
        this.unsecureDAO = unsecureDAO;
        this.emailService = emailService;
        this.authService = authService;
    }

    /**
     * <p>Swagger constructor.</p>
     */
    public PasswordResetResource() {
    }

    // =============================================================================================================
    // Step 1: Request password reset email
    // =============================================================================================================

    @POST
    @Path("/passwordReset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Initiate password reset",
            notes = "Generates a password reset token and sends a recovery email with a reset link")
    public Response requestPasswordReset(PasswordResetForm form) {
        try {
            if (form.getEmail() == null || form.getEmail().trim().isEmpty()) {
                return Response.ERROR("error.email.required");
            }

            if (!emailService.isConfigured()) {
                log.warn("Email service is not configured; cannot process password reset request");
                return Response.ERROR("error.email.notconfigured");
            }

            String email = form.getEmail().trim();
            String language = form.getLanguage() != null ? form.getLanguage() : "en";

            // Look up user by email (or login, for backward compatibility)
            User user = unsecureDAO.findByLoginOrEmail(email);
            if (user == null) {
                // Don't reveal whether the email exists or not — return OK either way
                log.info("Password reset requested for unknown email/login: {}", email);
                return Response.OK();
            }

            // Generate reset token and persist it via AuthService
            String token = authService.initiatePasswordReset(user);

            // Send recovery email
            String subject = emailService.getRecoveryEmailSubj(language);
            String body = emailService.getRecoveryEmailBody(language, token);
            emailService.sendEmail(user.getEmail(), subject, body);

            log.info("Password reset email sent to: {}", user.getEmail());
            return Response.OK();

        } catch (Exception e) {
            log.error("Error processing password reset request", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =============================================================================================================
    // Step 2: Complete password reset with token validation
    // =============================================================================================================

    @POST
    @Path("/passwordReset/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Complete password reset",
            notes = "Validates the password reset token and sets the new password. " +
                    "All existing sessions for this user are invalidated.")
    public Response completePasswordReset(PasswordResetCompleteForm form) {
        try {
            if (form.getToken() == null || form.getToken().trim().isEmpty()) {
                return Response.ERROR("error.token.required");
            }
            if (form.getPassword() == null || form.getPassword().trim().isEmpty()) {
                return Response.ERROR("error.password.required");
            }

            // Look up user by password reset token
            User user = unsecureDAO.findByPasswordResetToken(form.getToken());
            if (user == null) {
                log.warn("Invalid password reset token: {}", form.getToken());
                return Response.ERROR("error.token.invalid");
            }

            // Complete the password reset via AuthService:
            // - Validates the token
            // - Hashes the new password
            // - Clears passwordReset flag
            // - Clears passwordResetToken (one-time use)
            // - Regenerates authToken (invalidates all existing JWTs)
            boolean success = authService.completePasswordReset(user, form.getPassword(), form.getToken());
            if (!success) {
                return Response.ERROR("error.token.invalid");
            }

            log.info("Password reset completed for user: {}", user.getLogin());
            return Response.OK();

        } catch (Exception e) {
            log.error("Error completing password reset", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
