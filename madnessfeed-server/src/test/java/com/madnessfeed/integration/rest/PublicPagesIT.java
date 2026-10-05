package com.madnessfeed.integration.rest;

import com.madnessfeed.TestConstants;
import com.madnessfeed.backend.dao.FeedCategoryDAO;
import com.madnessfeed.backend.dao.UnitOfWork;
import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.service.PublicPageService;
import com.madnessfeed.backend.service.UserService;
import com.madnessfeed.frontend.model.PublicCategory;
import com.madnessfeed.frontend.model.PublicPageSettings;
import com.madnessfeed.frontend.model.request.IDRequest;
import com.madnessfeed.integration.BaseIT;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import jakarta.inject.Inject;

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Multiple public pages per user, see {@link PublicIT} for what is shown on a public page. */
@QuarkusTest
class PublicPagesIT extends BaseIT {

    @Inject UnitOfWork unitOfWork;
    @Inject UserDAO userDAO;
    @Inject FeedCategoryDAO feedCategoryDAO;
    @Inject PublicPageService publicPageService;
    @Inject UserService userService;

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
    void noPublicPagesByDefault() {
        Assertions.assertEquals(List.of(), getPublicPages());
    }

    @Test
    void pagesHaveTheirOwnAddressAndCategories() {
        String tech = createCategory("tech");
        String news = createCategory("news");
        Long subscriptionId = subscribeAndWaitForEntries(getFeedUrl(), tech);

        Long techPageId = savePublicPage(null, " Tech ", true, List.of(Long.valueOf(tech)));
        Long newsPageId = savePublicPage(null, "", true, List.of(Long.valueOf(news)));

        List<PublicPageSettings> pages = getPublicPages();
        Assertions.assertEquals(
                List.of(techPageId, newsPageId),
                pages.stream().map(PublicPageSettings::getId).toList());
        Assertions.assertEquals("Tech", pages.get(0).getName());
        Assertions.assertNull(pages.get(1).getName());
        String techToken = pages.get(0).getToken();
        String newsToken = pages.get(1).getToken();
        Assertions.assertNotEquals(techToken, newsToken);

        PublicCategory techRoot = getPublicTree(techToken);
        Assertions.assertEquals("Tech", techRoot.getPageName());
        Assertions.assertEquals(
                List.of("tech"),
                techRoot.getChildren().stream().map(PublicCategory::getName).toList());

        PublicCategory newsRoot = getPublicTree(newsToken);
        Assertions.assertNull(newsRoot.getPageName());
        Assertions.assertEquals(
                List.of("news"),
                newsRoot.getChildren().stream().map(PublicCategory::getName).toList());

        // the feed is only visible on the page showing its category
        anonymous()
                .get("rest/public/{token}/entries?type=feed&id={id}", techToken, subscriptionId)
                .then()
                .statusCode(HttpStatus.SC_OK);
        anonymous()
                .get("rest/public/{token}/entries?type=feed&id={id}", newsToken, subscriptionId)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
        anonymous()
                .get("rest/public/{token}/favicon/{id}", newsToken, subscriptionId)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void disablingOnePageKeepsTheOthers() {
        Long first = savePublicPage(null, "first", true, List.of());
        savePublicPage(null, "second", true, List.of());

        savePublicPage(first, "first", false, List.of());

        List<PublicPageSettings> pages = getPublicPages();
        anonymous()
                .get("rest/public/{token}/tree", pages.get(0).getToken())
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
        anonymous()
                .get("rest/public/{token}/tree", pages.get(1).getToken())
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    void regenerateTokenOfOnePage() {
        Long first = savePublicPage(null, "first", true, List.of());
        savePublicPage(null, "second", true, List.of());
        List<PublicPageSettings> before = getPublicPages();

        IDRequest req = new IDRequest();
        req.setId(first);
        post("rest/user/publicPages/regenerateToken", req).statusCode(HttpStatus.SC_OK);

        List<PublicPageSettings> after = getPublicPages();
        Assertions.assertNotEquals(before.get(0).getToken(), after.get(0).getToken());
        Assertions.assertEquals(before.get(1).getToken(), after.get(1).getToken());
        anonymous()
                .get("rest/public/{token}/tree", before.get(0).getToken())
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
        anonymous()
                .get("rest/public/{token}/tree", after.get(0).getToken())
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    void deletePage() {
        Long first = savePublicPage(null, "first", true, List.of());
        Long second = savePublicPage(null, "second", true, List.of());
        String firstToken = getPublicPages().getFirst().getToken();

        IDRequest req = new IDRequest();
        req.setId(first);
        post("rest/user/publicPages/delete", req).statusCode(HttpStatus.SC_OK);

        Assertions.assertEquals(
                List.of(second), getPublicPages().stream().map(PublicPageSettings::getId).toList());
        anonymous()
                .get("rest/public/{token}/tree", firstToken)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);

        post("rest/user/publicPages/delete", req).statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void unknownPage() {
        PublicPageSettings settings = new PublicPageSettings();
        settings.setId(123456789L);
        post("rest/user/publicPages/save", settings).statusCode(HttpStatus.SC_NOT_FOUND);

        IDRequest req = new IDRequest();
        req.setId(123456789L);
        post("rest/user/publicPages/regenerateToken", req).statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void deletedCategoriesAreRemovedFromPages() {
        String kept = createCategory("kept");
        String deleted = createCategory("deleted");
        savePublicPage(null, "page", true, List.of(Long.valueOf(kept), Long.valueOf(deleted)));

        IDRequest req = new IDRequest();
        req.setId(Long.valueOf(deleted));
        post("rest/category/delete", req).statusCode(HttpStatus.SC_OK);

        Assertions.assertEquals(
                List.of(Long.valueOf(kept)), getPublicPages().getFirst().getCategoryIds());
    }

    @Test
    void unknownCategoriesAreIgnored() {
        String category = createCategory("category");
        savePublicPage(null, "page", true, List.of(Long.valueOf(category), 123456789L));
        Assertions.assertEquals(
                List.of(Long.valueOf(category)), getPublicPages().getFirst().getCategoryIds());
    }

    @Test
    void deletingTheUserDeletesItsPages() {
        String category = createCategory("category");
        savePublicPage(null, "page", true, List.of(Long.valueOf(category)));
        String token = getPublicPages().getFirst().getToken();

        unitOfWork.run(
                () -> userService.unregister(userDAO.findByName(TestConstants.ADMIN_USERNAME)));

        Assertions.assertNull(
                unitOfWork.call(() -> userDAO.findByName(TestConstants.ADMIN_USERNAME)));
        anonymous()
                .get("rest/public/{token}/tree", token)
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void legacyEndpointsActOnTheFirstPage() {
        Long first = savePublicPage(null, "first", false, List.of());
        savePublicPage(null, "second", false, List.of());

        PublicPageSettings settings = new PublicPageSettings();
        settings.setEnabled(true);
        post("rest/user/publicPage", settings).statusCode(HttpStatus.SC_OK);

        PublicPageSettings legacy =
                RestAssured.given()
                        .get("rest/user/publicPage")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(PublicPageSettings.class);
        Assertions.assertEquals(first, legacy.getId());
        Assertions.assertTrue(legacy.isEnabled());

        List<PublicPageSettings> pages = getPublicPages();
        // the legacy endpoint doesn't know about names, the name is kept
        Assertions.assertEquals("first", pages.get(0).getName());
        Assertions.assertTrue(pages.get(0).isEnabled());
        Assertions.assertFalse(pages.get(1).isEnabled());
    }

    @Test
    void legacyPublicPageIsMigrated() {
        String shown = createCategory("shown");
        createCategory("hidden");
        String legacyToken = PublicPageService.generateToken();

        // settings of the single public page stored before multiple pages were supported
        unitOfWork.run(
                () -> {
                    User user = userDAO.findByName(TestConstants.ADMIN_USERNAME);
                    user.setPublicPageEnabled(true);
                    user.setPublicPageUncategorized(true);
                    user.setPublicPageToken(legacyToken);
                    feedCategoryDAO.findAll(user).stream()
                            .filter(c -> c.getId().equals(Long.valueOf(shown)))
                            .forEach(c -> c.setPublicCategory(true));
                });

        unitOfWork.run(publicPageService::migrateLegacyPublicPages);

        List<PublicPageSettings> pages = getPublicPages();
        Assertions.assertEquals(1, pages.size());
        PublicPageSettings page = pages.getFirst();
        Assertions.assertEquals(legacyToken, page.getToken());
        Assertions.assertTrue(page.isEnabled());
        Assertions.assertTrue(page.isShowUncategorized());
        Assertions.assertEquals(List.of(Long.valueOf(shown)), page.getCategoryIds());

        // the existing address keeps working
        Assertions.assertEquals(
                List.of("shown"),
                getPublicTree(legacyToken).getChildren().stream()
                        .map(PublicCategory::getName)
                        .toList());

        // the legacy settings are cleared so that the migration only happens once
        unitOfWork.run(
                () -> {
                    User user = userDAO.findByName(TestConstants.ADMIN_USERNAME);
                    Assertions.assertNull(user.getPublicPageToken());
                    Assertions.assertFalse(user.isPublicPageEnabled());
                    Assertions.assertTrue(
                            feedCategoryDAO.findAll(user).stream()
                                    .noneMatch(c -> c.isPublicCategory()));
                });
        unitOfWork.run(publicPageService::migrateLegacyPublicPages);
        Assertions.assertEquals(1, getPublicPages().size());
    }

    private Long savePublicPage(Long id, String name, boolean enabled, List<Long> categoryIds) {
        PublicPageSettings settings = new PublicPageSettings();
        settings.setId(id);
        settings.setName(name);
        settings.setEnabled(enabled);
        settings.setCategoryIds(categoryIds);
        return post("rest/user/publicPages/save", settings)
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(Long.class);
    }

    private List<PublicPageSettings> getPublicPages() {
        return RestAssured.given()
                .get("rest/user/publicPages")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(new TypeRef<>() {});
    }

    private PublicCategory getPublicTree(String token) {
        return anonymous()
                .get("rest/public/{token}/tree", token)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract()
                .as(PublicCategory.class);
    }

    private io.restassured.response.ValidatableResponse post(String path, Object body) {
        return RestAssured.given().body(body).contentType(ContentType.JSON).post(path).then();
    }

    private RequestSpecification anonymous() {
        return RestAssured.given().auth().none();
    }
}
