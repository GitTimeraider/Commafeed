package com.madnessfeed.backend.service;

import com.google.common.base.Preconditions;
import com.madnessfeed.MadnessFeedConfiguration;
import com.madnessfeed.MadnessFeedConstants;
import com.madnessfeed.backend.Digests;
import com.madnessfeed.backend.dao.FeedCategoryDAO;
import com.madnessfeed.backend.dao.FeedSubscriptionDAO;
import com.madnessfeed.backend.dao.PublicPageDAO;
import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.dao.UserPasskeyDAO;
import com.madnessfeed.backend.dao.UserRoleDAO;
import com.madnessfeed.backend.dao.UserSettingsDAO;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.model.UserRole;
import com.madnessfeed.backend.model.UserRole.Role;
import com.madnessfeed.backend.service.internal.PostLoginActivities;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Singleton
public class UserService {

    private final FeedCategoryDAO feedCategoryDAO;
    private final FeedSubscriptionDAO feedSubscriptionDAO;
    private final UserDAO userDAO;
    private final UserRoleDAO userRoleDAO;
    private final UserPasskeyDAO userPasskeyDAO;
    private final PublicPageDAO publicPageDAO;
    private final UserSettingsDAO userSettingsDAO;

    private final PasswordEncryptionService encryptionService;
    private final MadnessFeedConfiguration config;

    private final PostLoginActivities postLoginActivities;

    /** try to log in with given credentials */
    public Optional<User> login(String nameOrEmail, String password) {
        if (nameOrEmail == null || password == null) {
            return Optional.empty();
        }

        User user = userDAO.findByName(nameOrEmail);
        if (user == null) {
            user = userDAO.findByEmail(nameOrEmail);
        }
        if (user != null && !user.isDisabled()) {
            boolean authenticated =
                    encryptionService.authenticate(password, user.getPassword(), user.getSalt());
            if (authenticated) {
                performPostLoginActivities(user);
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    /** try to log in with given api key */
    public Optional<User> login(String apiKey) {
        if (apiKey == null) {
            return Optional.empty();
        }

        User user = userDAO.findByApiKey(apiKey);
        if (user != null && !user.isDisabled()) {
            performPostLoginActivities(user);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** try to log in with given fever api key */
    public Optional<User> login(long userId, String feverApiKey) {
        if (feverApiKey == null) {
            return Optional.empty();
        }

        User user = userDAO.findById(userId);
        if (user == null || user.isDisabled() || user.getApiKey() == null) {
            return Optional.empty();
        }

        String computedFeverApiKey = Digests.md5Hex(user.getName() + ":" + user.getApiKey());
        if (!computedFeverApiKey.equalsIgnoreCase(feverApiKey)) {
            return Optional.empty();
        }

        performPostLoginActivities(user);
        return Optional.of(user);
    }

    /** should triggers after successful login */
    public void performPostLoginActivities(User user) {
        postLoginActivities.executeFor(user);
    }

    public User register(String name, String password, String email, Collection<Role> roles) {
        return register(name, password, email, roles, false);
    }

    public User register(
            String name,
            String password,
            String email,
            Collection<Role> roles,
            boolean forceRegistration) {

        if (!forceRegistration) {
            Preconditions.checkState(
                    config.users().allowRegistrations(),
                    "Registrations are closed on this MadnessFeed instance");
        }

        Preconditions.checkArgument(userDAO.findByName(name) == null, "Name already taken");
        if (StringUtils.isNotBlank(email)) {
            Preconditions.checkArgument(userDAO.findByEmail(email) == null, "Email already taken");
        }

        User user = new User();
        byte[] salt = encryptionService.generateSalt();
        user.setName(name);
        user.setEmail(email);
        user.setCreated(Instant.now());
        user.setSalt(salt);
        user.setPassword(encryptionService.getEncryptedPassword(password, salt));
        userDAO.persist(user);
        for (Role role : roles) {
            userRoleDAO.persist(new UserRole(user, role));
        }
        return user;
    }

    public void createDemoUser() {
        register(
                MadnessFeedConstants.USERNAME_DEMO,
                "demo",
                "demo@madnessfeed.com",
                Collections.singletonList(Role.USER),
                true);
    }

    public void unregister(User user) {
        userSettingsDAO.delete(userSettingsDAO.findByUser(user));
        userRoleDAO.delete(userRoleDAO.findAll(user));
        userPasskeyDAO.delete(userPasskeyDAO.findAll(user));
        publicPageDAO.delete(publicPageDAO.findAll(user));
        feedSubscriptionDAO.delete(feedSubscriptionDAO.findAll(user));
        feedCategoryDAO.delete(feedCategoryDAO.findAll(user));
        userDAO.delete(user);
    }

    public String generateApiKey(User user) {
        byte[] key =
                encryptionService.getEncryptedPassword(
                        UUID.randomUUID().toString(), user.getSalt());
        return Digests.sha1Hex(key);
    }

    public Set<Role> getRoles(User user) {
        return userRoleDAO.findRoles(user);
    }
}
