/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.authentication.authenticators.broker;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.authenticators.broker.util.ExistingUserInfo;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class IdpConfirmLinkAuthenticator extends AbstractIdpAuthenticator {

    private static final Logger logger = Logger.getLogger(IdpReviewProfileAuthenticator.class);

    @Override
    protected void authenticateImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();

        logger.debugf("1 serializedCtx '%s' ", serializedCtx.getContextData());
        logger.debugf("1 serializedCtx attri '%s' ", serializedCtx.getAttributes());
        logger.debugf("1 brokerContext '%s' ", brokerContext.getContextData());
        logger.debugf("1 brokerContext attri '%s' ", brokerContext.getAttributes());

        String existingUserInfo = authSession.getAuthNote(EXISTING_USER_INFO);
        if (existingUserInfo == null) {
            ServicesLogger.LOGGER.noDuplicationDetected();
            context.attempted();
            return;
        }

        ExistingUserInfo duplicationInfo = ExistingUserInfo.deserialize(existingUserInfo);
        Response challenge = context.form()
                .setStatus(Response.Status.OK)
                .setAttribute(LoginFormsProvider.IDENTITY_PROVIDER_BROKER_CONTEXT, brokerContext)
                .setError(Messages.FEDERATED_IDENTITY_CONFIRM_LINK_MESSAGE, duplicationInfo.getDuplicateAttributeName(), duplicationInfo.getDuplicateAttributeValue())
                .createIdpLinkConfirmLinkPage();
        context.challenge(challenge);
    }

    @Override
    protected void actionImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        logger.debugf("2 serializedCtx '%s' ", serializedCtx.getContextData());
        logger.debugf("2 serializedCtx attri '%s' ", serializedCtx.getAttributes());
        logger.debugf("2 brokerContext '%s' ", brokerContext.getContextData());
        logger.debugf("2 brokerContext attri '%s' ", brokerContext.getAttributes());

        String action = formData.getFirst("submitAction");
        if (action != null && action.equals("updateProfile")) {
            context.resetFlow(() -> {
                AuthenticationSessionModel authSession = context.getAuthenticationSession();

                serializedCtx.saveToAuthenticationSession(authSession, BROKERED_CONTEXT_NOTE);
                authSession.setAuthNote(ENFORCE_UPDATE_PROFILE, "true");
            });
        } else if (action != null && action.equals("linkAccount")) {
            context.success();
        } else {
            throw new AuthenticationFlowException("Unknown action: " + action,
                    AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return false;
    }
}
