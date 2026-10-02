package com.commafeed.integration;

import com.commafeed.TestConstants;
import com.commafeed.frontend.model.ServerInfo;
import com.commafeed.frontend.model.request.MfaLoginRequest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
@TestProfile(NetworkAccessIT.AllowedNetworksProfile.class)
class NetworkAccessIT extends BaseIT {

    private static final String ALLOWED_IP = "192.168.1.20";
    private static final String EXTERNAL_IP = "203.0.113.5";

    @BeforeEach
    void setup() {
        initialSetup(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD);
    }

    @AfterEach
    void cleanup() {
        RestAssured.reset();
    }

    @Test
    void externalClientsCanOnlyOpenPublicPages() {
        // the single page application is served, it renders the public pages
        from(EXTERNAL_IP).get("/").then().statusCode(HttpStatus.SC_OK);

        ServerInfo infos =
                from(EXTERNAL_IP)
                        .get("rest/server/get")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(ServerInfo.class);
        Assertions.assertTrue(infos.isAccessRestricted());
        Assertions.assertNull(infos.getVersion());

        // public page api is reachable (404: unknown token)
        from(EXTERNAL_IP)
                .get("rest/public/unknown/tree")
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);

        // everything else is refused
        from(EXTERNAL_IP)
                .formParams(
                        "j_username",
                        TestConstants.ADMIN_USERNAME,
                        "j_password",
                        TestConstants.ADMIN_PASSWORD)
                .post("j_security_check")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
        from(EXTERNAL_IP)
                .auth()
                .preemptive()
                .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD)
                .get("rest/user/profile")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
        MfaLoginRequest login = new MfaLoginRequest();
        login.setName(TestConstants.ADMIN_USERNAME);
        login.setPassword(TestConstants.ADMIN_PASSWORD);
        from(EXTERNAL_IP)
                .body(login)
                .contentType(ContentType.JSON)
                .post("rest/mfa/login/options")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
        from(EXTERNAL_IP).get("rest/fever/user/1").then().statusCode(HttpStatus.SC_FORBIDDEN);
        from(EXTERNAL_IP)
                .get("rest/googlereader/reader/api/0/user-info")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
        from(EXTERNAL_IP).get("ws").then().statusCode(HttpStatus.SC_FORBIDDEN);
        from(EXTERNAL_IP).get("openapi").then().statusCode(HttpStatus.SC_FORBIDDEN);
        from(EXTERNAL_IP).get("custom_css.css").then().statusCode(HttpStatus.SC_FORBIDDEN);
        from("2001:db8::1").get("rest/user/profile").then().statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void allowedClients() {
        ServerInfo infos =
                from(ALLOWED_IP)
                        .get("rest/server/get")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(ServerInfo.class);
        Assertions.assertFalse(infos.isAccessRestricted());
        Assertions.assertNotNull(infos.getVersion());

        from(ALLOWED_IP)
                .formParams(
                        "j_username",
                        TestConstants.ADMIN_USERNAME,
                        "j_password",
                        TestConstants.ADMIN_PASSWORD)
                .post("j_security_check")
                .then()
                .statusCode(HttpStatus.SC_OK);
        from("fd12::5")
                .auth()
                .preemptive()
                .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD)
                .get("rest/user/profile")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // localhost (no forwarded address) is always allowed
        RestAssured.given()
                .auth()
                .preemptive()
                .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD)
                .get("rest/user/profile")
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    private static RequestSpecification from(String clientAddress) {
        return RestAssured.given().header("X-Forwarded-For", clientAddress);
    }

    public static class AllowedNetworksProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // simulates a reverse proxy running on localhost and forwarding the client address
            return Map.of(
                    "commafeed.allowed-networks", "192.168.1.0/24, fd00::/8",
                    "quarkus.http.proxy.proxy-address-forwarding", "true",
                    "quarkus.http.proxy.allow-x-forwarded", "true",
                    "quarkus.http.proxy.trusted-proxies", "127.0.0.1,0:0:0:0:0:0:0:1");
        }
    }
}
