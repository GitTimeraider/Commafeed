package com.commafeed.backend.service;

import com.commafeed.backend.dao.FeedCategoryDAO;
import com.commafeed.backend.dao.FeedSubscriptionDAO;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.model.FeedCategory;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.User;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

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
 * Computes what is visible on the public, read-only page of a user.
 *
 * <p>Each category is individually marked as public. Only feeds that are directly in a public
 * category (or uncategorized feeds, if enabled) are visible. Private categories are never exposed,
 * a public category whose parent is private is attached to its closest public ancestor instead.
 */
@Singleton
@RequiredArgsConstructor
public class PublicPageService {

    private final UserDAO userDAO;
    private final FeedCategoryDAO feedCategoryDAO;
    private final FeedSubscriptionDAO feedSubscriptionDAO;

    /**
     * @return the user owning the given public page token, only if that user is enabled and has
     *     enabled its public page
     */
    public Optional<User> findPublicPageUser(String token) {
        if (StringUtils.isBlank(token)) {
            return Optional.empty();
        }
        return Optional.ofNullable(userDAO.findByPublicPageToken(token))
                .filter(u -> !u.isDisabled())
                .filter(User::isPublicPageEnabled);
    }

    /**
     * @return a new random, hard to guess token used in the address of a public page
     */
    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    public PublicContent getPublicContent(User user) {
        List<FeedCategory> allCategories = feedCategoryDAO.findAll(user);
        Map<Long, FeedCategory> categoriesById =
                allCategories.stream()
                        .collect(Collectors.toMap(FeedCategory::getId, Function.identity()));

        List<FeedCategory> publicCategories =
                allCategories.stream().filter(FeedCategory::isPublicCategory).toList();
        Set<Long> publicCategoryIds =
                publicCategories.stream().map(FeedCategory::getId).collect(Collectors.toSet());

        List<FeedSubscription> publicSubscriptions =
                feedSubscriptionDAO.findAll(user).stream()
                        .filter(
                                s ->
                                        s.getCategory() == null
                                                ? user.isPublicPageUncategorized()
                                                : publicCategoryIds.contains(
                                                        s.getCategory().getId()))
                        .toList();

        return new PublicContent(categoriesById, publicCategories, publicSubscriptions);
    }

    public record PublicContent(
            Map<Long, FeedCategory> categoriesById,
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
                if (parent.isPublicCategory()) {
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
