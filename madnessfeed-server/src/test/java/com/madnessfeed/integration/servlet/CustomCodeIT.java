package com.madnessfeed.integration.servlet;

import com.madnessfeed.TestConstants;
import com.madnessfeed.frontend.model.Settings;
import com.madnessfeed.integration.BaseIT;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.apache.hc.core5.http.HttpStatus;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CustomCodeIT extends BaseIT {

    @BeforeEach
    void setup() {
        initialSetup(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD);
        RestAssured.authentication =
                RestAssured.preemptive()
                        .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD);
    }

    @AfterEach
    void cleanup() {
        RestAssured.reset();
    }

    @Test
    void test() {
        // get settings
        Settings settings =
                RestAssured.given()
                        .get("rest/user/settings")
                        .then()
                        .statusCode(200)
                        .extract()
                        .as(Settings.class);

        // update settings
        settings.setCustomJs("custom-js");
        settings.setCustomCss("custom-css");
        RestAssured.given()
                .body(settings)
                .contentType(ContentType.JSON)
                .post("rest/user/settings")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // check custom code servlets
        RestAssured.given()
                .get("custom_js.js")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.is("custom-js"));
        RestAssured.given()
                .get("custom_css.css")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body(CoreMatchers.is("custom-css"));
    }

    @Test
    void unsetCustomCodeReturnsEmptyBody() {
        Settings settings =
                RestAssured.given()
                        .get("rest/user/settings")
                        .then()
                        .statusCode(200)
                        .extract()
                        .as(Settings.class);

        // save settings without any custom code
        settings.setCustomJs(null);
        settings.setCustomCss(null);
        RestAssured.given()
                .body(settings)
                .contentType(ContentType.JSON)
                .post("rest/user/settings")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // servlets must return 200 with the declared content type, not 204
        RestAssured.given()
                .get("custom_js.js")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(CoreMatchers.containsString("application/javascript"))
                .body(CoreMatchers.is(""));
        RestAssured.given()
                .get("custom_css.css")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(CoreMatchers.containsString("text/css"))
                .body(CoreMatchers.is(""));
    }
}
