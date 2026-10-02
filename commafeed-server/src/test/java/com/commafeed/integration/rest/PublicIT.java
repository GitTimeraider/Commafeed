package com.commafeed.integration.rest;

import com.commafeed.TestConstants;
import com.commafeed.frontend.model.Entries;
import com.commafeed.frontend.model.PublicCategory;
import com.commafeed.frontend.model.PublicPageSettings;
import com.commafeed.frontend.model.request.AddCategoryRequest;
import com.commafeed.integration.BaseIT;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class PublicIT extends BaseIT {

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
    void publicPageIsDisabledByDefault() {
        PublicPageSettings settings = getPublicPageSettings();
        Assertions.assertFalse(settings.isEnabled());
        Assertions.assertFalse(settings.isShowUncategorized());
        Assertions.assertEquals(List.of(), settings.getCategoryIds());

        anonymous()
                .get("rest/public/{user}/tree", TestConstants.ADMIN_USERNAME)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
        anonymous()
                .get("rest/public/{user}/entries", TestConstants.ADMIN_USERNAME)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void unknownUser() {
        anonymous()
                .get("rest/public/{user}/tree", "unknown-user")
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void settingsRequireAuthentication() {
        anonymous().get("rest/user/publicPage").then().statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    void onlyPublicCategoriesAreShown() {
        // public-a (public)
        // └── private-d (private)
        // private-b (private)
        // └── public-c (public) -> contains the feed
        String publicA = createCategory("public-a");
        createCategory("private-d", publicA);
        String privateB = createCategory("private-b");
        String publicC = createCategory("public-c", privateB);
        Long subscriptionId = subscribeAndWaitForEntries(getFeedUrl(), publicC);

        PublicPageSettings settings = new PublicPageSettings();
        settings.setEnabled(true);
        settings.setCategoryIds(List.of(Long.valueOf(publicA), Long.valueOf(publicC)));
        savePublicPageSettings(settings);

        PublicPageSettings savedSettings = getPublicPageSettings();
        Assertions.assertTrue(savedSettings.isEnabled());
        Assertions.assertEquals(
                List.of(Long.valueOf(publicA), Long.valueOf(publicC)),
                savedSettings.getCategoryIds().stream().sorted().toList());

        PublicCategory root = getPublicTree();
        Assertions.assertEquals(
                List.of("public-a", "public-c"),
                root.getChildren().stream().map(PublicCategory::getName).sorted().toList());

        PublicCategory a = findChild(root, publicA);
        Assertions.assertTrue(a.getChildren().isEmpty());
        Assertions.assertTrue(a.getFeeds().isEmpty());

        // public-c is attached to the root because its parent is private
        PublicCategory c = findChild(root, publicC);
        Assertions.assertEquals(1, c.getFeeds().size());
        Assertions.assertEquals(subscriptionId, c.getFeeds().getFirst().getId());
        Assertions.assertEquals(
                "rest/public/admin/favicon/" + subscriptionId,
                c.getFeeds().getFirst().getIconUrl());

        Assertions.assertEquals(2, getPublicEntries("category", "all").getEntries().size());
        Assertions.assertEquals(2, getPublicEntries("category", publicC).getEntries().size());
        Assertions.assertEquals(0, getPublicEntries("category", publicA).getEntries().size());
        Assertions.assertEquals(
                2, getPublicEntries("feed", String.valueOf(subscriptionId)).getEntries().size());

        Entries entries = getPublicEntries("feed", String.valueOf(subscriptionId));
        Assertions.assertTrue(entries.getEntries().stream().allMatch(e -> e.getTags().isEmpty()));

        // private categories are not accessible
        anonymous()
                .get(
                        "rest/public/{user}/entries?type=category&id={id}",
                        TestConstants.ADMIN_USERNAME,
                        privateB)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void feedsOfPrivateCategoriesAreNotAccessible() {
        String privateCategory = createCategory("private");
        Long subscriptionId = subscribeAndWaitForEntries(getFeedUrl(), privateCategory);

        PublicPageSettings settings = new PublicPageSettings();
        settings.setEnabled(true);
        savePublicPageSettings(settings);

        Assertions.assertTrue(getPublicTree().getChildren().isEmpty());
        Assertions.assertTrue(getPublicTree().getFeeds().isEmpty());
        Assertions.assertTrue(getPublicEntries("category", "all").getEntries().isEmpty());
        anonymous()
                .get(
                        "rest/public/{user}/entries?type=feed&id={id}",
                        TestConstants.ADMIN_USERNAME,
                        subscriptionId)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
        anonymous()
                .get(
                        "rest/public/{user}/favicon/{id}",
                        TestConstants.ADMIN_USERNAME,
                        subscriptionId)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void uncategorizedFeeds() {
        Long subscriptionId = subscribeAndWaitForEntries(getFeedUrl());

        PublicPageSettings settings = new PublicPageSettings();
        settings.setEnabled(true);
        savePublicPageSettings(settings);
        Assertions.assertTrue(getPublicTree().getFeeds().isEmpty());

        settings.setShowUncategorized(true);
        savePublicPageSettings(settings);
        Assertions.assertEquals(subscriptionId, getPublicTree().getFeeds().getFirst().getId());
        Assertions.assertEquals(2, getPublicEntries("category", "all").getEntries().size());
    }

    private RequestSpecification anonymous() {
        return RestAssured.given().auth().none();
    }

    private String createCategory(String name, String parentId) {
        AddCategoryRequest req = new AddCategoryRequest();
        req.setName(name);
        req.setParentId(parentId);
        return RestAssured.given()
                .body(req)
                .contentType(ContentType.JSON)
                .post("rest/category/add")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(String.class);
    }

    private PublicPageSettings getPublicPageSettings() {
        return RestAssured.given()
                .get("rest/user/publicPage")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(PublicPageSettings.class);
    }

    private void savePublicPageSettings(PublicPageSettings settings) {
        RestAssured.given()
                .body(settings)
                .contentType(ContentType.JSON)
                .post("rest/user/publicPage")
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    private PublicCategory getPublicTree() {
        return anonymous()
                .get("rest/public/{user}/tree", TestConstants.ADMIN_USERNAME)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(PublicCategory.class);
    }

    private Entries getPublicEntries(String type, String id) {
        return anonymous()
                .get(
                        "rest/public/{user}/entries?type={type}&id={id}",
                        TestConstants.ADMIN_USERNAME,
                        type,
                        id)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(Entries.class);
    }

    private static PublicCategory findChild(PublicCategory parent, String id) {
        return parent.getChildren().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
