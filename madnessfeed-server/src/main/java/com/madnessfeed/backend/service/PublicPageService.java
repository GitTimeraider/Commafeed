package com.madnessfeed.backend.service;

import com.madnessfeed.backend.dao.FeedCategoryDAO;
import com.madnessfeed.backend.dao.FeedSubscriptionDAO;
import com.madnessfeed.backend.dao.PublicPageDAO;
import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.model.FeedCategory;
import com.madnessfeed.backend.model.FeedSubscription;
import com.madnessfeed.backend.model.PublicPage;
import com.madnessfeed.backend.model.User;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages the public, read-only pages of users and computes what is visible on them.
 *
 * <p>A user can have several public pages, each with its own address and selection of categories.
 * Only feeds that are directly in a selected category (or uncategorized feeds, if enabled) are
 * visible. Categories that aren't selected are never exposed, a selected category whose parent
 * isn't selected is attached to its closest selected ancestor instead.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class PublicPageService {

    private final UserDAO userDAO;
    private final PublicPageDAO publicPageDAO;
    private final FeedCategoryDAO feedCategoryDAO;
    private final FeedSubscriptionDAO feedSubscriptionDAO;

    /**
     * @return the public page with the given token, only if it is enabled and its user is enabled
     */
    public Optional<PublicPage> findPublicPage(String token) {
        if (StringUtils.isBlank(token)) {
            return Optional.empty();
        }
        return Optional.ofNullable(publicPageDAO.findByToken(token))
                .filter(PublicPage::isEnabled)
                .filter(p -> !p.getUser().isDisabled());
    }

    /**
     * @return a new random, hard to guess token used in the address of a public page
     */
    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    /** Removes a category that is about to be deleted from the public pages of its user. */
    public void removeCategory(User user, FeedCategory category) {
        for (PublicPage page : publicPageDAO.findAll(user)) {
            page.getCategoryIds().remove(category.getId());
        }
    }

    /**
     * Moves the settings of the single public page users had before multiple pages were supported
     * to a {@link PublicPage}, keeping its token so that existing addresses keep working.
     */
    public void migrateLegacyPublicPages() {
        for (User user : userDAO.findWithLegacyPublicPage()) {
            List<FeedCategory> categories = feedCategoryDAO.findAll(user);

            PublicPage page = new PublicPage();
            page.setUser(user);
            page.setEnabled(user.isPublicPageEnabled());
            page.setShowUncategorized(user.isPublicPageUncategorized());
            page.setToken(
                    user.getPublicPageToken() == null
                            ? generateToken()
                            : user.getPublicPageToken());
            page.setCategoryIds(
                    categories.stream()
                            .filter(FeedCategory::isPublicCategory)
                            .map(FeedCategory::getId)
                            .collect(Collectors.toCollection(HashSet::new)));

            user.setPublicPageEnabled(false);
            user.setPublicPageUncategorized(false);
            user.setPublicPageToken(null);
            categories.forEach(c -> c.setPublicCategory(false));

            publicPageDAO.persist(page);
            log.info("moved the public page of user {} to the public pages table", user.getName());
        }
    }

    public PublicContent getPublicContent(PublicPage page) {
        User user = page.getUser();
        List<FeedCategory> allCategories = feedCategoryDAO.findAll(user);
        Map<Long, FeedCategory> categoriesById =
                allCategories.stream()
                        .collect(Collectors.toMap(FeedCategory::getId, Function.identity()));

        Set<Long> selectedIds = page.getCategoryIds();
        List<FeedCategory> publicCategories =
                allCategories.stream().filter(c -> selectedIds.contains(c.getId())).toList();
        Set<Long> publicCategoryIds =
                publicCategories.stream().map(FeedCategory::getId).collect(Collectors.toSet());

        List<FeedSubscription> publicSubscriptions =
                feedSubscriptionDAO.findAll(user).stream()
                        .filter(
                                s ->
                                        s.getCategory() == null
                                                ? page.isShowUncategorized()
                                                : publicCategoryIds.contains(
                                                        s.getCategory().getId()))
                        .toList();

        return new PublicContent(
                categoriesById, publicCategoryIds, publicCategories, publicSubscriptions);
    }

    public record PublicContent(
            Map<Long, FeedCategory> categoriesById,
            Set<Long> publicCategoryIds,
            List<FeedCategory> publicCategories,
            List<FeedSubscription> publicSubscriptions) {

        public Optional<FeedCategory> findPublicCategory(Long id) {
            return publicCategories.stream().filter(c -> Objects.equals(c.getId(), id)).findFirst();
        }

        public Optional<FeedSubscription> findPublicSubscription(Long id) {
            return publicSubscriptions.stream()
                    .filter(s -> Objects.equals(s.getId(), id))
                    .findFirst();
        }

        /**
         * @return the id of the closest public ancestor of the category, or null if it has none
         */
        public Long getClosestPublicAncestorId(FeedCategory category) {
            Set<Long> visited = new HashSet<>();
            Long parentId = getParentId(category);
            while (parentId != null && visited.add(parentId)) {
                FeedCategory parent = categoriesById.get(parentId);
                if (parent == null) {
                    return null;
                }
                if (publicCategoryIds.contains(parentId)) {
                    return parentId;
                }
                parentId = getParentId(parent);
            }
            return null;
        }

        /**
         * @return the public subscriptions in the given category or in any of its subcategories
         */
        public List<FeedSubscription> getPublicSubscriptions(FeedCategory category) {
            return publicSubscriptions.stream()
                    .filter(
                            s ->
                                    s.getCategory() != null
                                            && isSameOrDescendant(
                                                    s.getCategory().getId(), category.getId()))
                    .toList();
        }

        private boolean isSameOrDescendant(Long categoryId, Long ancestorId) {
            Set<Long> visited = new HashSet<>();
            Long current = categoryId;
            while (current != null && visited.add(current)) {
                if (Objects.equals(current, ancestorId)) {
                    return true;
                }
                FeedCategory category = categoriesById.get(current);
                current = category == null ? null : getParentId(category);
            }
            return false;
        }

        private static Long getParentId(FeedCategory category) {
            return category.getParent() == null ? null : category.getParent().getId();
        }
    }
}
