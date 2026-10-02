package com.commafeed.backend.mfa;

import com.commafeed.backend.mfa.WebAuthn.RegisteredCredential;
import com.commafeed.backend.mfa.WebAuthn.WebAuthnException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Set;

class WebAuthnTest {

    private static final String RP_ID = "rss.example.com";
    private static final String ORIGIN = "https://rss.example.com";
    private static final String CHALLENGE = "Y2hhbGxlbmdl";

    private TestAuthenticator authenticator;
    private RegisteredCredential credential;

    @BeforeEach
    void setup() throws WebAuthnException {
        authenticator = new TestAuthenticator();
        credential = register(ORIGIN, RP_ID, CHALLENGE);
    }

    @Test
    void registration() {
        Assertions.assertEquals(authenticator.credentialId(), credential.credentialId());
        Assertions.assertEquals(WebAuthn.ALG_ES256, credential.algorithm());
        Assertions.assertEquals(RP_ID, credential.rpId());
        Assertions.assertEquals(0, credential.signCount());
    }

    @Test
    void registrationChecks() {
        TestAuthenticator other = new TestAuthenticator();
        // wrong challenge
        Assertions.assertThrows(
                WebAuthnException.class,
                () ->
                        WebAuthn.verifyRegistration(
                                decode(
                                        TestAuthenticator.clientData(
                                                "webauthn.create", "other", ORIGIN)),
                                decode(other.attestationObject(RP_ID)),
                                CHALLENGE));
        // wrong type
        Assertions.assertThrows(
                WebAuthnException.class,
                () ->
                        WebAuthn.verifyRegistration(
                                decode(
                                        TestAuthenticator.clientData(
                                                "webauthn.get", CHALLENGE, ORIGIN)),
                                decode(other.attestationObject(RP_ID)),
                                CHALLENGE));
        // authenticator data for another relying party
        Assertions.assertThrows(
                WebAuthnException.class,
                () ->
                        WebAuthn.verifyRegistration(
                                decode(
                                        TestAuthenticator.clientData(
                                                "webauthn.create", CHALLENGE, ORIGIN)),
                                decode(other.attestationObject("evil.com")),
                                CHALLENGE));
        // plain http
        Assertions.assertThrows(
                WebAuthnException.class,
                () -> register("http://rss.example.com", RP_ID, CHALLENGE));
        // garbage
        Assertions.assertThrows(
                WebAuthnException.class,
                () ->
                        WebAuthn.verifyRegistration(
                                decode(
                                        TestAuthenticator.clientData(
                                                "webauthn.create", CHALLENGE, ORIGIN)),
                                new byte[] {1, 2, 3},
                                CHALLENGE));
    }

    @Test
    void localhostIsAllowedWithoutHttps() throws WebAuthnException {
        Assertions.assertEquals(
                "localhost", register("http://localhost:8082", "localhost", CHALLENGE).rpId());
    }

    @Test
    void assertion() throws Exception {
        long signCount =
                verify(
                        authenticator.assertionJson(RP_ID, "c2Vjb25k", ORIGIN),
                        "c2Vjb25k",
                        credential);
        Assertions.assertEquals(1, signCount);
    }

    @Test
    void assertionChecks() throws Exception {
        // wrong challenge
        String json = authenticator.assertionJson(RP_ID, "c2Vjb25k", ORIGIN);
        Assertions.assertThrows(WebAuthnException.class, () -> verify(json, "other", credential));

        // wrong origin
        String json2 = authenticator.assertionJson(RP_ID, "c2Vjb25k", "https://evil.com");
        Assertions.assertThrows(
                WebAuthnException.class, () -> verify(json2, "c2Vjb25k", credential));

        // signed by another key
        TestAuthenticator other = new TestAuthenticator();
        String json3 = other.assertionJson(RP_ID, "c2Vjb25k", ORIGIN);
        Assertions.assertThrows(
                WebAuthnException.class, () -> verify(json3, "c2Vjb25k", credential));

        // signature counter must increase
        String json4 = authenticator.assertionJson(RP_ID, "c2Vjb25k", ORIGIN);
        RegisteredCredential used =
                new RegisteredCredential(
                        credential.credentialId(),
                        credential.publicKey(),
                        credential.algorithm(),
                        1000,
                        credential.rpId());
        Assertions.assertThrows(WebAuthnException.class, () -> verify(json4, "c2Vjb25k", used));
    }

    @Test
    void crossOrigin() throws Exception {
        Set<String> allowed = Set.of(WebAuthn.normalizeOrigin("https://dashboard.example.com/"));

        // embedded in an iframe of a trusted page
        String json =
                authenticator.assertionJson(
                        RP_ID,
                        TestAuthenticator.clientData(
                                "webauthn.get",
                                "c2Vjb25k",
                                ORIGIN,
                                true,
                                "https://Dashboard.example.com"));
        Assertions.assertEquals(1, verify(json, "c2Vjb25k", credential, allowed));

        // no trusted pages configured
        String json2 =
                authenticator.assertionJson(
                        RP_ID,
                        TestAuthenticator.clientData(
                                "webauthn.get",
                                "c2Vjb25k",
                                ORIGIN,
                                true,
                                "https://dashboard.example.com"));
        Assertions.assertThrows(
                WebAuthnException.class, () -> verify(json2, "c2Vjb25k", credential, Set.of()));

        // embedded in an iframe of an untrusted page
        String json3 =
                authenticator.assertionJson(
                        RP_ID,
                        TestAuthenticator.clientData(
                                "webauthn.get", "c2Vjb25k", ORIGIN, true, "https://evil.com"));
        Assertions.assertThrows(
                WebAuthnException.class, () -> verify(json3, "c2Vjb25k", credential, allowed));

        // the browser did not send the origin of the top level page
        String json4 =
                authenticator.assertionJson(
                        RP_ID,
                        TestAuthenticator.clientData(
                                "webauthn.get", "c2Vjb25k", ORIGIN, true, null));
        Assertions.assertThrows(
                WebAuthnException.class, () -> verify(json4, "c2Vjb25k", credential, allowed));
    }

    @Test
    void crossOriginRegistration() throws WebAuthnException {
        String clientData =
                TestAuthenticator.clientData(
                        "webauthn.create",
                        CHALLENGE,
                        ORIGIN,
                        true,
                        "https://dashboard.example.com");
        TestAuthenticator other = new TestAuthenticator();
        byte[] attestation = decode(other.attestationObject(RP_ID));

        Assertions.assertThrows(
                WebAuthnException.class,
                () -> WebAuthn.verifyRegistration(decode(clientData), attestation, CHALLENGE));
        RegisteredCredential registered =
                WebAuthn.verifyRegistration(
                        decode(clientData),
                        attestation,
                        CHALLENGE,
                        Set.of("https://dashboard.example.com"));
        Assertions.assertEquals(RP_ID, registered.rpId());
    }

    @Test
    void normalizeOrigin() {
        Assertions.assertEquals(
                "https://dashboard.example.com",
                WebAuthn.normalizeOrigin(" HTTPS://Dashboard.Example.com:443/ "));
        Assertions.assertEquals(
                "http://192.168.1.10:8080", WebAuthn.normalizeOrigin("http://192.168.1.10:8080"));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> WebAuthn.normalizeOrigin("https://dashboard.example.com/organizr"));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> WebAuthn.normalizeOrigin("dashboard"));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> WebAuthn.normalizeOrigin("ftp://example.com"));
    }

    private RegisteredCredential register(String origin, String rpId, String challenge)
            throws WebAuthnException {
        return WebAuthn.verifyRegistration(
                decode(TestAuthenticator.clientData("webauthn.create", challenge, origin)),
                decode(authenticator.attestationObject(rpId)),
                challenge);
    }

    private static long verify(String json, String challenge, RegisteredCredential credential)
            throws Exception {
        return verify(json, challenge, credential, Set.of());
    }

    private static long verify(
            String json,
            String challenge,
            RegisteredCredential credential,
            Set<String> allowedTopOrigins)
            throws Exception {
        JsonNode node = new ObjectMapper().readTree(json);
        return WebAuthn.verifyAssertion(
                credential,
                decode(node.get("clientDataJSON").asText()),
                decode(node.get("authenticatorData").asText()),
                decode(node.get("signature").asText()),
                challenge,
                allowedTopOrigins);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
