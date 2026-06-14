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
import com.hmdm.persistence.CustomerDAO;
import com.hmdm.persistence.PendingSignupDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.persistence.domain.PendingSignup;
import com.hmdm.rest.json.Response;
import com.hmdm.rest.json.SignupCompleteForm;
import com.hmdm.rest.json.SignupForm;
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
 * <p>REST resource for customer self-signup flow.</p>
 *
 * <p>Two-step process:</p>
 * <ol>
 *   <li><b>POST /public/signup</b>: Submit email to receive a verification email with a token</li>
 *   <li><b>POST /public/signup/complete</b>: Submit the token along with account details to
 *       create the customer account and admin user</li>
 * </ol>
 *
 * <p>Auth state transitions are delegated to {@link AuthService} to ensure consistent
 * token/password/reset-flag semantics across all entry points.</p>
 */
@Singleton
@Path("/public")
@Api(tags = {"Signup"})
public class SignupResource {

    private static final Logger log = LoggerFactory.getLogger(SignupResource.class);

    private final PendingSignupDAO pendingSignupDAO;
    private final UnsecureDAO unsecureDAO;
    private final EmailService emailService;
    private final AuthService authService;

    @Inject
    public SignupResource(PendingSignupDAO pendingSignupDAO,
                          UnsecureDAO unsecureDAO,
                          EmailService emailService,
                          AuthService authService) {
        this.pendingSignupDAO = pendingSignupDAO;
        this.unsecureDAO = unsecureDAO;
        this.emailService = emailService;
        this.authService = authService;
    }

    /**
     * <p>Swagger constructor.</p>
     */
    public SignupResource() {
    }

    // =============================================================================================================
    // Step 1: Request signup verification email
    // =============================================================================================================

    @POST
    @Path("/signup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Initiate customer self-signup",
            notes = "Creates a pending signup record and sends a verification email with a token link")
    public Response signup(SignupForm form) {
        try {
            if (form.getEmail() == null || form.getEmail().trim().isEmpty()) {
                return Response.ERROR("error.email.required");
            }

            if (!emailService.isConfigured()) {
                log.warn("Email service is not configured; cannot process signup request");
                return Response.ERROR("error.email.notconfigured");
            }

            String email = form.getEmail().trim().toLowerCase();
            String language = form.getLanguage() != null ? form.getLanguage() : "en";

            // Check for duplicate signup requests (UPSERT handles this gracefully)
            PendingSignup existing = pendingSignupDAO.getByEmail(email);

            // Generate a secure verification token
            String token = authService.generateSecureToken();

            PendingSignup pendingSignup = new PendingSignup();
            pendingSignup.setEmail(email);
            pendingSignup.setLanguage(language);
            pendingSignup.setToken(token);
            pendingSignup.setSignupTime(System.currentTimeMillis());

            // Remove existing entry first (to handle UPSERT semantics)
            if (existing != null) {
                pendingSignupDAO.remove(email);
            }
            pendingSignupDAO.insert(pendingSignup);

            // Send verification email
            String subject = emailService.getVerifyEmailSubj(language);
            String body = emailService.getVerifyEmailBody(language, token);
            emailService.sendEmail(email, subject, body);

            log.info("Signup verification email sent to: {}", email);
            return Response.OK();

        } catch (Exception e) {
            log.error("Error processing signup request", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =============================================================================================================
    // Step 2: Complete signup with token verification and account creation
    // =============================================================================================================

    @POST
    @Path("/signup/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Complete customer self-signup",
            notes = "Verifies the signup token and creates the customer account with an admin user")
    public Response completeSignup(SignupCompleteForm form) {
        try {
            // Validate required fields
            if (form.getToken() == null || form.getToken().trim().isEmpty()) {
                return Response.ERROR("error.token.required");
            }
            if (form.getName() == null || form.getName().trim().isEmpty()) {
                return Response.ERROR("error.name.required");
            }
            if (form.getPassword() == null || form.getPassword().trim().isEmpty()) {
                return Response.ERROR("error.password.required");
            }
            if (form.getFirstName() == null || form.getFirstName().trim().isEmpty()) {
                return Response.ERROR("error.firstname.required");
            }
            if (form.getLastName() == null || form.getLastName().trim().isEmpty()) {
                return Response.ERROR("error.lastname.required");
            }

            // Look up the pending signup by token
            PendingSignup pendingSignup = pendingSignupDAO.getByToken(form.getToken());
            if (pendingSignup == null) {
                log.warn("Invalid or expired signup token: {}", form.getToken());
                return Response.ERROR("error.token.invalid");
            }

            // Check if a customer with this name already exists
            Customer existingCustomer = unsecureDAO.getCustomerByNameUnsecure(form.getName().trim());
            if (existingCustomer != null) {
                return Response.DUPLICATE_ENTITY("error.duplicate.customer");
            }

            // Build the Customer object
            Customer customer = new Customer();
            customer.setName(form.getName().trim());
            customer.setEmail(pendingSignup.getEmail());
            customer.setFirstName(form.getFirstName().trim());
            customer.setLastName(form.getLastName().trim());
            customer.setLanguage(pendingSignup.getLanguage() != null ? pendingSignup.getLanguage() : "en");
            customer.setSignupStatus("unconfirmed");
            customer.setSignupToken(form.getToken());

            if (form.getDeviceConfigurationId() != null) {
                customer.setDeviceConfigurationId(form.getDeviceConfigurationId());
            }
            if (form.getConfigurationIds() != null) {
                customer.setConfigurationIds(form.getConfigurationIds());
            }

            // Create customer and admin user via UnsecureDAO
            // This creates: customer record, admin user (with password hash + authToken),
            // default devices, and copies settings/configs as requested
            unsecureDAO.signupCustomerUnsecure(customer, form.getPassword(), form.isCopyDesign());

            // Activate the customer account (set signupStatus = "active", clear signupToken)
            unsecureDAO.activateCustomerUnsecure(customer);

            // Clean up the pending signup record
            pendingSignupDAO.remove(pendingSignup.getEmail());

            // Send welcome email with account details
            try {
                String completeSubj = emailService.getSignupCompleteEmailSubj(customer.getLanguage());
                String completeBody = emailService.getSignupCompleteEmailBody(customer);
                emailService.sendEmail(customer.getEmail(), completeSubj, completeBody);
            } catch (Exception emailEx) {
                // Log but don't fail the signup if welcome email fails
                log.warn("Failed to send welcome email to {}: {}", customer.getEmail(), emailEx.getMessage());
            }

            log.info("Customer self-signup completed: {} (email: {})", customer.getName(), customer.getEmail());
            return Response.OK();

        } catch (Exception e) {
            log.error("Error completing signup", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
