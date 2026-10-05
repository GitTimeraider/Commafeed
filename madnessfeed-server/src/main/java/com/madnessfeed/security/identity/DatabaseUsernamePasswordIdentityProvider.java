package com.madnessfeed.security.identity;

import com.madnessfeed.backend.dao.UnitOfWork;
import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.model.UserRole.Role;
import com.madnessfeed.backend.service.MfaService;
import com.madnessfeed.backend.service.UserService;
import com.madnessfeed.frontend.exception.MadnessFeedApplicationException;
import com.madnessfeed.frontend.exception.MadnessFeedExceptionType;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Singleton
public class DatabaseUsernamePasswordIdentityProvider
        implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

    public static final String FORM_LOGIN_ACTION = "j_security_check";
    public static final String FORM_MFA_TOTP = "j_mfa_totp";
    public static final String FORM_MFA_PASSKEY = "j_mfa_passkey";
    public static final String FORM_MFA_RESET_CODE = "j_mfa_reset_code";

    private final UnitOfWork unitOfWork;
    private final UserService userService;
    private final UserDAO userDAO;
    private final MfaService mfaService;

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
            UsernamePasswordAuthenticationRequest request, AuthenticationRequestContext context) {
        return context.runBlocking(
                () -> {
                    Optional<User> user =
                            unitOfWork.call(
                                    () ->
                                            userService.login(
                                                    request.getUsername(),
                                                    new String(
                                                            request.getPassword().getPassword())));
                    if (user.isEmpty()) {
                        throw new MadnessFeedApplicationException(
                                MadnessFeedExceptionType.WRONG_USERNAME_OR_PASSWORD);
                    }

                    verifySecondFactor(request, user.get());

                    Set<Role> roles = unitOfWork.call(() -> userService.getRoles(user.get()));
                    return QuarkusSecurityIdentity.builder()
                            .setPrincipal(new QuarkusPrincipal(String.valueOf(user.get().getId())))
                            .addRoles(roles.stream().map(Enum::name).collect(Collectors.toSet()))
                            .build();
                });
    }

    /**
     * users with two-factor authentication enabled can only log in with the login form, which sends
     * the second factor in addition to the user name and password. Logging in with HTTP basic
     * authentication (user name and password only) is refused for these users.
     */
    private void verifySecondFactor(UsernamePasswordAuthenticationRequest request, User user) {
        boolean mfaEnabled = unitOfWork.call(() -> mfaService.isMfaEnabled(user));
        if (!mfaEnabled) {
            return;
        }

        RoutingContext context = HttpSecurityUtils.getRoutingContextAttribute(request);
        boolean formLogin =
                context != null && context.normalizedPath().endsWith("/" + FORM_LOGIN_ACTION);
        if (!formLogin) {
            throw new AuthenticationFailedException(
                    "two-factor authentication is enabled for this user, log in with the login form");
        }

        MultiMap form = context.request().formAttributes();
        MfaService.LoginResult result =
                unitOfWork.call(
                        () ->
                                mfaService.verifyLogin(
                                        userDAO.findById(user.getId()),
                                        form.get(FORM_MFA_TOTP),
                                        form.get(FORM_MFA_PASSKEY),
                                        form.get(FORM_MFA_RESET_CODE)));
        switch (result) {
            case SUCCESS -> {
                // second factor verified
            }
            case MISSING ->
                    throw new MadnessFeedApplicationException(
                            MadnessFeedExceptionType.MFA_REQUIRED);
            case INVALID ->
                    throw new MadnessFeedApplicationException(
                            MadnessFeedExceptionType.MFA_INVALID_CODE);
            case TOO_MANY_ATTEMPTS ->
                    throw new MadnessFeedApplicationException(
                            MadnessFeedExceptionType.MFA_TOO_MANY_ATTEMPTS);
            default -> throw new IllegalStateException("unexpected result " + result);
        }
    }
}
