package com.madnessfeed.integration.rest;

import com.madnessfeed.ExceptionMappers.MadnessFeedApplicationError;
import com.madnessfeed.TestConstants;
import com.madnessfeed.backend.mfa.TestAuthenticator;
import com.madnessfeed.backend.mfa.Totp;
import com.madnessfeed.backend.service.MfaService;
import com.madnessfeed.frontend.exception.MadnessFeedExceptionType;
import com.madnessfeed.frontend.model.MfaLoginOptions;
import com.madnessfeed.frontend.model.MfaStatus;
import com.madnessfeed.frontend.model.PasskeyRegistrationOptions;
import com.madnessfeed.frontend.model.TotpSetupResponse;
import com.madnessfeed.frontend.model.request.MfaLoginRequest;
import com.madnessfeed.frontend.model.request.MfaPasswordRequest;
import com.madnessfeed.frontend.model.request.PasskeyDeleteRequest;
import com.madnessfeed.frontend.model.request.PasskeyRegistrationRequest;
import com.madnessfeed.frontend.model.request.TotpEnableRequest;
import com.madnessfeed.integration.BaseIT;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import jakarta.inject.Inject;

import org.apache.hc.core5.http.HttpStatus;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@QuarkusTest
class MfaIT extends BaseIT {

    private static final String ORIGIN = "http://localhost:8085";
    private static final PatternFormatter MESSAGE_FORMATTER = new PatternFormatter("%s");

    @Inject MfaService mfaService;

    private final List<String> logMessages = new ArrayList<>();
    private final Handler logHandler =
            new Handler() {
                @Override
                public void publish(LogRecord logRecord) {
                    logMessages.add(MESSAGE_FORMATTER.format(logRecord));
                }

                @Override
                public void flush() {
                    // nothing to do
                }

                @Override
                public void close() {
                    // nothing to do
                }
            };

    private Response session;

    @BeforeEach
    void setup() {
        initialSetup(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD);
        // the database is reset between tests and user ids are reused
        mfaService.clearTemporaryState();
        Logger.getLogger(MfaService.class.getName()).addHandler(logHandler);
        session = formLogin(Map.of());
        session.then().statusCode(HttpStatus.SC_OK);
    }

    @AfterEach
    void cleanup() {
        Logger.getLogger(MfaService.class.getName()).removeHandler(logHandler);
        RestAssured.reset();
    }

    @Test
    void loginWithoutMfa() {
        formLogin(Map.of()).then().statusCode(HttpStatus.SC_OK);
        basicAuth().get("rest/user/profile").then().statusCode(HttpStatus.SC_OK);

        MfaLoginOptions options = getLoginOptions();
        Assertions.assertFalse(options.isTotp());
        Assertions.assertFalse(options.isPasskey());
    }

    @Test
    void totp() {
        String secret = enableTotp();
        Assertions.assertTrue(getStatus().isTotpEnabled());

        // basic auth is refused, the second factor can't be provided
        basicAuth().get("rest/user/profile").then().statusCode(HttpStatus.SC_UNAUTHORIZED);

        // the password alone is not enough
        assertLoginError(formLogin(Map.of()), MadnessFeedExceptionType.MFA_REQUIRED);

        // wrong password is still reported as such
        Response wrongPassword =
                RestAssured.given()
                        .formParams(
                                "j_username",
                                TestConstants.ADMIN_USERNAME,
                                "j_password",
                                "wrong",
                                "j_mfa_totp",
                                "123456")
                        .post("j_security_check");
        assertLoginError(wrongPassword, MadnessFeedExceptionType.WRONG_USERNAME_OR_PASSWORD);

        MfaLoginOptions options = getLoginOptions();
        Assertions.assertTrue(options.isTotp());
        Assertions.assertFalse(options.isPasskey());

        // invalid code
        String invalidCode = Totp.generateCode(secret, Totp.currentStep(Instant.now()) - 10);
        assertLoginError(
                formLogin(Map.of("j_mfa_totp", invalidCode)),
                MadnessFeedExceptionType.MFA_INVALID_CODE);

        // the code used to enable the authenticator app can't be used again, use the next one
        String code = Totp.generateCode(secret, Totp.currentStep(Instant.now()) + 1);
        Response login = formLogin(Map.of("j_mfa_totp", code));
        login.then().statusCode(HttpStatus.SC_OK);
        RestAssured.given()
                .cookies(login.getCookies())
                .get("rest/user/profile")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // a code can only be used once
        assertLoginError(
                formLogin(Map.of("j_mfa_totp", code)), MadnessFeedExceptionType.MFA_INVALID_CODE);

        // disabling requires the password
        MfaPasswordRequest disable = new MfaPasswordRequest();
        disable.setPassword("wrong");
        withSession(login)
                .body(disable)
                .post("rest/mfa/totp/disable")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
        disable.setPassword(TestConstants.ADMIN_PASSWORD);
        withSession(login)
                .body(disable)
                .post("rest/mfa/totp/disable")
                .then()
                .statusCode(HttpStatus.SC_OK);

        formLogin(Map.of()).then().statusCode(HttpStatus.SC_OK);
    }

    @Test
    void tooManyAttempts() {
        enableTotp();
        for (int i = 0; i < MfaService.MAX_FAILED_ATTEMPTS; i++) {
            assertLoginError(
                    formLogin(Map.of("j_mfa_totp", "000000")),
                    MadnessFeedExceptionType.MFA_INVALID_CODE);
        }
        assertLoginError(
                formLogin(Map.of("j_mfa_totp", "000000")),
                MadnessFeedExceptionType.MFA_TOO_MANY_ATTEMPTS);
    }

    @Test
    void passkey() {
        TestAuthenticator authenticator = new TestAuthenticator();

        PasskeyRegistrationOptions registrationOptions =
                withSession(session)
                        .post("rest/mfa/passkey/registrationOptions")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(PasskeyRegistrationOptions.class);
        Assertions.assertTrue(registrationOptions.getAlgorithms().contains(-7));

        PasskeyRegistrationRequest registration = new PasskeyRegistrationRequest();
        registration.setName("my key");
        registration.setClientDataJSON(
                TestAuthenticator.clientData(
                        "webauthn.create", registrationOptions.getChallenge(), ORIGIN));
        registration.setAttestationObject(authenticator.attestationObject("localhost"));
        withSession(session)
                .body(registration)
                .post("rest/mfa/passkey/register")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // the challenge can't be reused
        withSession(session)
                .body(registration)
                .post("rest/mfa/passkey/register")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        MfaStatus status = getStatus();
        Assertions.assertEquals(1, status.getPasskeys().size());
        Assertions.assertEquals("my key", status.getPasskeys().getFirst().getName());

        assertLoginError(formLogin(Map.of()), MadnessFeedExceptionType.MFA_REQUIRED);

        MfaLoginOptions options = getLoginOptions();
        Assertions.assertFalse(options.isTotp());
        Assertions.assertTrue(options.isPasskey());
        Assertions.assertEquals(
                List.of(authenticator.credentialId()), options.getPasskeyCredentialIds());

        // assertion for another origin
        String evil =
                authenticator.assertionJson(
                        "localhost", options.getPasskeyChallenge(), "https://evil.com");
        assertLoginError(
                formLogin(Map.of("j_mfa_passkey", evil)),
                MadnessFeedExceptionType.MFA_INVALID_CODE);

        // valid assertion
        String challenge = getLoginOptions().getPasskeyChallenge();
        String assertion = authenticator.assertionJson("localhost", challenge, ORIGIN);
        Response login = formLogin(Map.of("j_mfa_passkey", assertion));
        login.then().statusCode(HttpStatus.SC_OK);
        Assertions.assertNotNull(getStatus().getPasskeys().getFirst().getLastUsed());

        // the challenge can't be reused
        assertLoginError(
                formLogin(Map.of("j_mfa_passkey", assertion)),
                MadnessFeedExceptionType.MFA_INVALID_CODE);

        // delete the passkey
        PasskeyDeleteRequest delete = new PasskeyDeleteRequest();
        delete.setId(status.getPasskeys().getFirst().getId());
        delete.setPassword(TestConstants.ADMIN_PASSWORD);
        withSession(login)
                .body(delete)
                .post("rest/mfa/passkey/delete")
                .then()
                .statusCode(HttpStatus.SC_OK);
        formLogin(Map.of()).then().statusCode(HttpStatus.SC_OK);
    }

    @Test
    void resetCode() {
        enableTotp();

        MfaLoginRequest request = new MfaLoginRequest();
        request.setName(TestConstants.ADMIN_USERNAME);
        request.setPassword("wrong");
        RestAssured.given()
                .body(request)
                .contentType(ContentType.JSON)
                .post("rest/mfa/login/resetRequest")
                .then()
                .statusCode(HttpStatus.SC_UNAUTHORIZED);
        Assertions.assertNull(findResetCode());

        request.setPassword(TestConstants.ADMIN_PASSWORD);
        RestAssured.given()
                .body(request)
                .contentType(ContentType.JSON)
                .post("rest/mfa/login/resetRequest")
                .then()
                .statusCode(HttpStatus.SC_OK);
        String resetCode = findResetCode();
        Assertions.assertNotNull(resetCode);

        assertLoginError(
                formLogin(Map.of("j_mfa_reset_code", "WRONG-CODE0")),
                MadnessFeedExceptionType.MFA_INVALID_CODE);

        // the code disables two-factor authentication and logs the user in
        formLogin(Map.of("j_mfa_reset_code", resetCode.toLowerCase()))
                .then()
                .statusCode(HttpStatus.SC_OK);
        Assertions.assertFalse(getStatus().isTotpEnabled());
        formLogin(Map.of()).then().statusCode(HttpStatus.SC_OK);

        // the code can only be used once
        enableTotp();
        assertLoginError(
                formLogin(Map.of("j_mfa_reset_code", resetCode)),
                MadnessFeedExceptionType.MFA_INVALID_CODE);
    }

    private String enableTotp() {
        TotpSetupResponse setup =
                withSession(session)
                        .post("rest/mfa/totp/setup")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(TotpSetupResponse.class);
        Assertions.assertTrue(
                setup.getUri()
                        .startsWith(
                                "otpauth://totp/MadnessFeed:admin?secret=" + setup.getSecret()));

        TotpEnableRequest enable = new TotpEnableRequest();
        enable.setCode("000000");
        withSession(session)
                .body(enable)
                .post("rest/mfa/totp/enable")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        enable.setCode(Totp.generateCode(setup.getSecret(), Totp.currentStep(Instant.now())));
        withSession(session)
                .body(enable)
                .post("rest/mfa/totp/enable")
                .then()
                .statusCode(HttpStatus.SC_OK);
        return setup.getSecret();
    }

    private MfaStatus getStatus() {
        // the session cookie obtained before two-factor authentication was enabled stays valid
        return withSession(session)
                .get("rest/mfa/status")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(MfaStatus.class);
    }

    private MfaLoginOptions getLoginOptions() {
        MfaLoginRequest request = new MfaLoginRequest();
        request.setName(TestConstants.ADMIN_USERNAME);
        request.setPassword(TestConstants.ADMIN_PASSWORD);
        return RestAssured.given()
                .body(request)
                .contentType(ContentType.JSON)
                .post("rest/mfa/login/options")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(MfaLoginOptions.class);
    }

    private Response formLogin(Map<String, String> extraParams) {
        RequestSpecification spec =
                RestAssured.given()
                        .formParams(
                                "j_username",
                                TestConstants.ADMIN_USERNAME,
                                "j_password",
                                TestConstants.ADMIN_PASSWORD);
        extraParams.forEach(spec::formParam);
        return spec.post("j_security_check");
    }

    private RequestSpecification basicAuth() {
        return RestAssured.given()
                .auth()
                .preemptive()
                .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD);
    }

    private static RequestSpecification withSession(Response login) {
        return RestAssured.given().cookies(login.getCookies()).contentType(ContentType.JSON);
    }

    private static void assertLoginError(Response response, MadnessFeedExceptionType type) {
        MadnessFeedApplicationError error =
                response.then()
                        .statusCode(HttpStatus.SC_UNAUTHORIZED)
                        .extract()
                        .as(MadnessFeedApplicationError.class);
        Assertions.assertEquals(type, error.type());
    }

    private String findResetCode() {
        Pattern pattern = Pattern.compile("Reset code: ([A-Z0-9]{5}-[A-Z0-9]{5})");
        for (String message : logMessages) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
