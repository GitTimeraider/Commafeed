package com.madnessfeed.frontend.resource;

import com.madnessfeed.MadnessFeedConstants;
import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.mfa.WebAuthn.WebAuthnException;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.model.UserPasskey;
import com.madnessfeed.backend.service.MfaService;
import com.madnessfeed.backend.service.UserService;
import com.madnessfeed.frontend.exception.MadnessFeedApplicationException;
import com.madnessfeed.frontend.exception.MadnessFeedExceptionType;
import com.madnessfeed.frontend.model.MfaLoginOptions;
import com.madnessfeed.frontend.model.MfaStatus;
import com.madnessfeed.frontend.model.PasskeyInfo;
import com.madnessfeed.frontend.model.PasskeyRegistrationOptions;
import com.madnessfeed.frontend.model.TotpSetupResponse;
import com.madnessfeed.frontend.model.request.MfaLoginRequest;
import com.madnessfeed.frontend.model.request.MfaPasswordRequest;
import com.madnessfeed.frontend.model.request.PasskeyDeleteRequest;
import com.madnessfeed.frontend.model.request.PasskeyRegistrationRequest;
import com.madnessfeed.frontend.model.request.TotpEnableRequest;
import com.madnessfeed.security.AuthenticationContext;
import com.madnessfeed.security.Roles;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import lombok.RequiredArgsConstructor;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/rest/mfa")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Singleton
@Tag(name = "Two-factor authentication")
public class MfaREST {

    private final AuthenticationContext authenticationContext;
    private final UserService userService;
    private final UserDAO userDAO;
    private final MfaService mfaService;

    @GET
    @Path("/status")
    @Transactional
    @Operation(summary = "Get the two-factor authentication methods of the current user")
    public MfaStatus getStatus() {
        User user = authenticationContext.getCurrentUser();

        MfaStatus status = new MfaStatus();
        status.setTotpEnabled(user.getTotpSecret() != null);
        for (UserPasskey passkey : mfaService.getPasskeys(user)) {
            PasskeyInfo info = new PasskeyInfo();
            info.setId(passkey.getId());
            info.setName(passkey.getName());
            info.setCreated(passkey.getCreated());
            info.setLastUsed(passkey.getLastUsed());
            status.getPasskeys().add(info);
        }
        return status;
    }

    @POST
    @Path("/totp/setup")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @Operation(
            summary = "Start setting up an authenticator app",
            description =
                    "Generate a new secret. It is only used after being confirmed with a code.")
    public TotpSetupResponse startTotpSetup() {
        User user = getModifiableUser();
        MfaService.TotpSetup setup = mfaService.startTotpSetup(user);

        TotpSetupResponse response = new TotpSetupResponse();
        response.setSecret(setup.secret());
        response.setUri(setup.uri());
        return response;
    }

    @POST
    @Path("/totp/enable")
    @Transactional
    @Operation(summary = "Enable the authenticator app by confirming a code it generated")
    public Response enableTotp(@Valid @Parameter(required = true) TotpEnableRequest request) {
        User user = getModifiableUser();
        if (!mfaService.enableTotp(user, request.getCode())) {
            throw new BadRequestException("invalid code");
        }
        return Response.ok().build();
    }

    @POST
    @Path("/totp/disable")
    @Transactional
    @Operation(summary = "Disable the authenticator app")
    public Response disableTotp(@Valid @Parameter(required = true) MfaPasswordRequest request) {
        User user = getModifiableUser();
        verifyPassword(user, request.getPassword());
        mfaService.disableTotp(user);
        return Response.ok().build();
    }

    @POST
    @Path("/passkey/registrationOptions")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @Operation(
            summary =
                    "Get the options to register a new passkey with navigator.credentials.create()")
    public PasskeyRegistrationOptions getPasskeyRegistrationOptions() {
        User user = getModifiableUser();
        MfaService.PasskeyRegistrationOptions options = mfaService.startPasskeyRegistration(user);

        PasskeyRegistrationOptions response = new PasskeyRegistrationOptions();
        response.setChallenge(options.challenge());
        response.setRpName(options.rpName());
        response.setUserId(options.userId());
        response.setUserName(options.userName());
        response.setAlgorithms(options.algorithms());
        response.setExcludeCredentialIds(options.excludeCredentialIds());
        return response;
    }

    @POST
    @Path("/passkey/register")
    @Transactional
    @Operation(summary = "Register a new passkey")
    public Response registerPasskey(
            @Valid @Parameter(required = true) PasskeyRegistrationRequest request) {
        User user = getModifiableUser();
        try {
            mfaService.finishPasskeyRegistration(
                    user,
                    request.getName(),
                    request.getClientDataJSON(),
                    request.getAttestationObject());
        } catch (WebAuthnException e) {
            throw new BadRequestException("could not register the passkey: " + e.getMessage());
        }
        return Response.ok().build();
    }

    @POST
    @Path("/passkey/delete")
    @Transactional
    @Operation(summary = "Delete a passkey")
    public Response deletePasskey(@Valid @Parameter(required = true) PasskeyDeleteRequest request) {
        User user = getModifiableUser();
        verifyPassword(user, request.getPassword());
        if (!mfaService.deletePasskey(user, request.getId())) {
            throw new NotFoundException();
        }
        return Response.ok().build();
    }

    @POST
    @Path("/login/options")
    @PermitAll
    @Transactional
    @Operation(
            summary = "Get the second factors available to log in",
            description =
                    "Called by the login page after the server answered that two-factor authentication is required."
                            + " Requires the user name and password.")
    public MfaLoginOptions getLoginOptions(
            @Valid @Parameter(required = true) MfaLoginRequest request) {
        User user = login(request);
        MfaService.LoginOptions options = mfaService.getLoginOptions(user);

        MfaLoginOptions response = new MfaLoginOptions();
        response.setTotp(options.totp());
        response.setPasskey(options.passkey());
        response.setPasskeyChallenge(options.passkeyChallenge());
        response.setPasskeyCredentialIds(options.passkeyCredentialIds());
        return response;
    }

    @POST
    @Path("/login/resetRequest")
    @PermitAll
    @Transactional
    @Operation(
            summary = "Request a two-factor authentication reset code",
            description =
                    "Writes a single use code in the server logs. Entering it on the login page disables"
                            + " two-factor authentication for the user. Requires the user name and password.")
    public Response requestResetCode(@Valid @Parameter(required = true) MfaLoginRequest request) {
        User user = login(request);
        if (mfaService.isMfaEnabled(user)) {
            mfaService.requestResetCode(user);
        }
        return Response.ok().build();
    }

    private User login(MfaLoginRequest request) {
        return userService
                .login(request.getName(), request.getPassword())
                .orElseThrow(
                        () ->
                                new MadnessFeedApplicationException(
                                        MadnessFeedExceptionType.WRONG_USERNAME_OR_PASSWORD));
    }

    private User getModifiableUser() {
        User user = authenticationContext.getCurrentUser();
        if (MadnessFeedConstants.USERNAME_DEMO.equals(user.getName())) {
            throw new ForbiddenException(
                    "two-factor authentication can't be enabled for the demo account");
        }
        return userDAO.findById(user.getId());
    }

    private void verifyPassword(User user, String password) {
        if (userService.login(user.getName(), password).isEmpty()) {
            throw new BadRequestException("invalid password");
        }
    }
}
