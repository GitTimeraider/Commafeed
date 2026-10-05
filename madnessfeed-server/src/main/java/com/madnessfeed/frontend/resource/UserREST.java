package com.madnessfeed.frontend.resource;

import com.google.common.base.Preconditions;
import com.madnessfeed.MadnessFeedConfiguration;
import com.madnessfeed.MadnessFeedConstants;
import com.madnessfeed.backend.Digests;
import com.madnessfeed.backend.Urls;
import com.madnessfeed.backend.dao.FeedCategoryDAO;
import com.madnessfeed.backend.dao.PublicPageDAO;
import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.dao.UserRoleDAO;
import com.madnessfeed.backend.dao.UserSettingsDAO;
import com.madnessfeed.backend.model.Feed;
import com.madnessfeed.backend.model.FeedCategory;
import com.madnessfeed.backend.model.FeedEntry;
import com.madnessfeed.backend.model.FeedEntryContent;
import com.madnessfeed.backend.model.FeedSubscription;
import com.madnessfeed.backend.model.PublicPage;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.model.UserRole;
import com.madnessfeed.backend.model.UserRole.Role;
import com.madnessfeed.backend.model.UserSettings;
import com.madnessfeed.backend.model.UserSettings.IconDisplayMode;
import com.madnessfeed.backend.model.UserSettings.PushNotificationUserSettings;
import com.madnessfeed.backend.model.UserSettings.ReadingMode;
import com.madnessfeed.backend.model.UserSettings.ReadingOrder;
import com.madnessfeed.backend.model.UserSettings.ScrollMode;
import com.madnessfeed.backend.service.MailService;
import com.madnessfeed.backend.service.PasswordEncryptionService;
import com.madnessfeed.backend.service.PublicPageService;
import com.madnessfeed.backend.service.PushNotificationService;
import com.madnessfeed.backend.service.UserService;
import com.madnessfeed.backend.service.db.DatabaseStartupService;
import com.madnessfeed.frontend.model.PublicPageSettings;
import com.madnessfeed.frontend.model.Settings;
import com.madnessfeed.frontend.model.Settings.PushNotificationSettings;
import com.madnessfeed.frontend.model.UserModel;
import com.madnessfeed.frontend.model.request.IDRequest;
import com.madnessfeed.frontend.model.request.InitialSetupRequest;
import com.madnessfeed.frontend.model.request.PasswordResetConfirmationRequest;
import com.madnessfeed.frontend.model.request.PasswordResetRequest;
import com.madnessfeed.frontend.model.request.ProfileModificationRequest;
import com.madnessfeed.frontend.model.request.RegistrationRequest;
import com.madnessfeed.security.AuthenticationContext;
import com.madnessfeed.security.Roles;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Path("/rest/user")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequiredArgsConstructor
@Singleton
@Tag(name = "Users")
public class UserREST {

    private final AuthenticationContext authenticationContext;
    private final UserDAO userDAO;
    private final FeedCategoryDAO feedCategoryDAO;
    private final PublicPageDAO publicPageDAO;
    private final UserRoleDAO userRoleDAO;
    private final UserSettingsDAO userSettingsDAO;
    private final UserService userService;
    private final PasswordEncryptionService encryptionService;
    private final DatabaseStartupService databaseStartupService;
    private final MailService mailService;
    private final MadnessFeedConfiguration config;
    private final UriInfo uri;
    private final PushNotificationService pushNotificationService;

    @Path("/settings")
    @GET
    @Transactional
    @Operation(summary = "Retrieve user settings", description = "Retrieve user settings")
    public Settings getUserSettings() {
        Settings s = new Settings();

        User user = authenticationContext.getCurrentUser();
        UserSettings settings = userSettingsDAO.findByUser(user);
        if (settings != null) {
            s.setReadingMode(settings.getReadingMode());
            s.setReadingOrder(settings.getReadingOrder());
            s.setShowRead(settings.isShowRead());

            s.getSharingSettings().setEmail(settings.isEmail());
            s.getSharingSettings().setGmail(settings.isGmail());
            s.getSharingSettings().setFacebook(settings.isFacebook());
            s.getSharingSettings().setTwitter(settings.isTwitter());
            s.getSharingSettings().setTumblr(settings.isTumblr());
            s.getSharingSettings().setInstapaper(settings.isInstapaper());
            s.getSharingSettings().setBuffer(settings.isBuffer());

            s.setScrollMarks(settings.isScrollMarks());
            s.setCustomCss(settings.getCustomCss());
            s.setCustomJs(settings.getCustomJs());
            s.setLanguage(settings.getLanguage());
            s.setScrollSpeed(settings.getScrollSpeed());
            s.setScrollMode(settings.getScrollMode());
            s.setEntriesToKeepOnTopWhenScrolling(settings.getEntriesToKeepOnTopWhenScrolling());
            s.setStarIconDisplayMode(settings.getStarIconDisplayMode());
            s.setExternalLinkIconDisplayMode(settings.getExternalLinkIconDisplayMode());
            s.setMarkAllAsReadConfirmation(settings.isMarkAllAsReadConfirmation());
            s.setMarkAllAsReadNavigateToNextUnread(settings.isMarkAllAsReadNavigateToNextUnread());
            s.setCustomContextMenu(settings.isCustomContextMenu());
            s.setMobileFooter(settings.isMobileFooter());
            s.setUnreadCountTitle(settings.isUnreadCountTitle());
            s.setUnreadCountFavicon(settings.isUnreadCountFavicon());
            s.setDisablePullToRefresh(settings.isDisablePullToRefresh());
            s.setPrimaryColor(settings.getPrimaryColor());

            if (settings.getPushNotifications() != null) {
                s.getPushNotificationSettings().setType(settings.getPushNotifications().getType());
                s.getPushNotificationSettings()
                        .setServerUrl(settings.getPushNotifications().getServerUrl());
                s.getPushNotificationSettings()
                        .setUserId(settings.getPushNotifications().getUserId());
                s.getPushNotificationSettings()
                        .setUserSecret(settings.getPushNotifications().getUserSecret());
                s.getPushNotificationSettings()
                        .setTopic(settings.getPushNotifications().getTopic());
            }
        } else {
            s.setReadingMode(ReadingMode.UNREAD);
            s.setReadingOrder(ReadingOrder.DESC);
            s.setShowRead(true);

            s.getSharingSettings().setEmail(true);
            s.getSharingSettings().setGmail(true);
            s.getSharingSettings().setFacebook(true);
            s.getSharingSettings().setTwitter(true);
            s.getSharingSettings().setTumblr(true);
            s.getSharingSettings().setInstapaper(true);
            s.getSharingSettings().setBuffer(true);

            s.setScrollMarks(true);
            s.setScrollSpeed(400);
            s.setScrollMode(ScrollMode.IF_NEEDED);
            s.setEntriesToKeepOnTopWhenScrolling(1);
            s.setStarIconDisplayMode(IconDisplayMode.ON_DESKTOP);
            s.setExternalLinkIconDisplayMode(IconDisplayMode.ON_DESKTOP);
            s.setMarkAllAsReadConfirmation(true);
            s.setMarkAllAsReadNavigateToNextUnread(false);
            s.setCustomContextMenu(true);
            s.setMobileFooter(false);
            s.setUnreadCountTitle(false);
            s.setUnreadCountFavicon(true);
            s.setDisablePullToRefresh(false);
        }
        return s;
    }

    @Path("/settings")
    @POST
    @Transactional
    @Operation(summary = "Save user settings", description = "Save user settings")
    public Response saveUserSettings(@Parameter(required = true) Settings settings) {
        Preconditions.checkNotNull(settings);

        User user = authenticationContext.getCurrentUser();
        UserSettings s = userSettingsDAO.findByUser(user);
        if (s == null) {
            s = new UserSettings();
            s.setUser(user);
        }
        s.setReadingMode(settings.getReadingMode());
        s.setReadingOrder(settings.getReadingOrder());
        s.setShowRead(settings.isShowRead());
        s.setScrollMarks(settings.isScrollMarks());
        s.setCustomCss(settings.getCustomCss());
        s.setCustomJs(
                MadnessFeedConstants.USERNAME_DEMO.equals(user.getName())
                        ? ""
                        : settings.getCustomJs());
        s.setLanguage(settings.getLanguage());
        s.setScrollSpeed(settings.getScrollSpeed());
        s.setScrollMode(settings.getScrollMode());
        s.setEntriesToKeepOnTopWhenScrolling(settings.getEntriesToKeepOnTopWhenScrolling());
        s.setStarIconDisplayMode(settings.getStarIconDisplayMode());
        s.setExternalLinkIconDisplayMode(settings.getExternalLinkIconDisplayMode());
        s.setMarkAllAsReadConfirmation(settings.isMarkAllAsReadConfirmation());
        s.setMarkAllAsReadNavigateToNextUnread(settings.isMarkAllAsReadNavigateToNextUnread());
        s.setCustomContextMenu(settings.isCustomContextMenu());
        s.setMobileFooter(settings.isMobileFooter());
        s.setUnreadCountTitle(settings.isUnreadCountTitle());
        s.setUnreadCountFavicon(settings.isUnreadCountFavicon());
        s.setDisablePullToRefresh(settings.isDisablePullToRefresh());
        s.setPrimaryColor(settings.getPrimaryColor());

        PushNotificationUserSettings ps = new PushNotificationUserSettings();
        ps.setType(settings.getPushNotificationSettings().getType());
        ps.setServerUrl(settings.getPushNotificationSettings().getServerUrl());
        ps.setUserId(settings.getPushNotificationSettings().getUserId());
        ps.setUserSecret(settings.getPushNotificationSettings().getUserSecret());
        ps.setTopic(settings.getPushNotificationSettings().getTopic());
        s.setPushNotifications(ps);

        s.setEmail(settings.getSharingSettings().isEmail());
        s.setGmail(settings.getSharingSettings().isGmail());
        s.setFacebook(settings.getSharingSettings().isFacebook());
        s.setTwitter(settings.getSharingSettings().isTwitter());
        s.setTumblr(settings.getSharingSettings().isTumblr());
        s.setInstapaper(settings.getSharingSettings().isInstapaper());
        s.setBuffer(settings.getSharingSettings().isBuffer());

        userSettingsDAO.merge(s);
        return Response.ok().build();
    }

    @Path("/pushNotificationTest")
    @POST
    @Transactional
    @Operation(summary = "Send a test push notification")
    public Response sendTestPushNotification(
            @Parameter(required = true) PushNotificationSettings settings) {
        FeedSubscription sub = new FeedSubscription();
        sub.setTitle("MadnessFeed Test Feed");
        sub.setFeed(new Feed());

        FeedEntryContent content = new FeedEntryContent();
        content.setTitle("Test Entry");

        FeedEntry entry = new FeedEntry();
        entry.setContent(content);

        PushNotificationUserSettings pushSettings = new PushNotificationUserSettings();
        pushSettings.setType(settings.getType());
        pushSettings.setServerUrl(settings.getServerUrl());
        pushSettings.setUserId(settings.getUserId());
        pushSettings.setUserSecret(settings.getUserSecret());
        pushSettings.setTopic(settings.getTopic());

        try {
            pushNotificationService.notify(pushSettings, sub, entry);
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getCause().getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        return Response.ok().build();
    }

    @Path("/publicPages")
    @GET
    @Transactional
    @Operation(
            summary = "Retrieve public pages",
            description = "Retrieve the settings of all public, read-only pages of the user")
    public List<PublicPageSettings> getPublicPages() {
        User user = authenticationContext.getCurrentUser();
        return publicPageDAO.findAll(user).stream().map(UserREST::toPublicPageSettings).toList();
    }

    @Path("/publicPages/save")
    @POST
    @Transactional
    @Operation(
            summary = "Create or update a public page",
            description =
                    "Create a public page when no id is given, update the public page with that id otherwise. Returns the id of the page.")
    public Response savePublicPage(@Parameter(required = true) PublicPageSettings settings) {
        Preconditions.checkNotNull(settings);

        User user = authenticationContext.getCurrentUser();
        PublicPage page;
        if (settings.getId() == null) {
            page = newPublicPage(user);
        } else {
            page = publicPageDAO.findById(user, settings.getId());
            if (page == null) {
                return Response.status(Status.NOT_FOUND).build();
            }
        }
        applyPublicPageSettings(user, page, settings);
        return Response.ok(page.getId()).build();
    }

    @Path("/publicPages/delete")
    @POST
    @Transactional
    @Operation(
            summary = "Delete a public page",
            description = "The address of the page stops working")
    public Response deletePublicPage(@Parameter(required = true) IDRequest req) {
        Preconditions.checkNotNull(req);
        Preconditions.checkNotNull(req.getId());

        User user = authenticationContext.getCurrentUser();
        PublicPage page = publicPageDAO.findById(user, req.getId());
        if (page == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        publicPageDAO.delete(page);
        return Response.ok().build();
    }

    @Path("/publicPages/regenerateToken")
    @POST
    @Transactional
    @Operation(
            summary = "Generate a new address for a public page",
            description =
                    "Generate a new secret token for the address of the public page. The previous address stops working.")
    public Response regeneratePublicPageToken(@Parameter(required = true) IDRequest req) {
        Preconditions.checkNotNull(req);
        Preconditions.checkNotNull(req.getId());

        User user = authenticationContext.getCurrentUser();
        PublicPage page = publicPageDAO.findById(user, req.getId());
        if (page == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        page.setToken(PublicPageService.generateToken());
        return Response.ok().build();
    }

    // the endpoints below predate multiple public pages, they act on the first public page of the
    // user and are kept for compatibility with existing API clients

    @Path("/publicPage")
    @GET
    @Transactional
    @Operation(
            summary = "Retrieve the settings of the first public page",
            description =
                    "Retrieve the settings of the first public, read-only page of the user. Use /user/publicPages to manage all public pages.")
    public PublicPageSettings getPublicPageSettings() {
        User user = authenticationContext.getCurrentUser();
        return findFirstPublicPage(user)
                .map(UserREST::toPublicPageSettings)
                .orElseGet(PublicPageSettings::new);
    }

    @Path("/publicPage")
    @POST
    @Transactional
    @Operation(
            summary = "Save the settings of the first public page",
            description =
                    "Save the settings of the first public, read-only page of the user, creating it if needed. Use /user/publicPages to manage all public pages.")
    public Response savePublicPageSettings(
            @Parameter(required = true) PublicPageSettings settings) {
        Preconditions.checkNotNull(settings);

        User user = authenticationContext.getCurrentUser();
        PublicPage page = findFirstPublicPage(user).orElseGet(() -> newPublicPage(user));
        // the name isn't part of the settings of this endpoint, keep the current one
        settings.setName(page.getName());
        applyPublicPageSettings(user, page, settings);
        return Response.ok().build();
    }

    @Path("/publicPage/regenerateToken")
    @POST
    // no request body
    @Consumes(MediaType.WILDCARD)
    @Transactional
    @Operation(
            summary = "Generate a new address for the first public page",
            description =
                    "Generate a new secret token for the address of the first public page. The previous address stops working.")
    public Response regenerateFirstPublicPageToken() {
        User user = authenticationContext.getCurrentUser();
        PublicPage page = findFirstPublicPage(user).orElseGet(() -> newPublicPage(user));
        page.setToken(PublicPageService.generateToken());
        return Response.ok().build();
    }

    private Optional<PublicPage> findFirstPublicPage(User user) {
        return publicPageDAO.findAll(user).stream().findFirst();
    }

    private PublicPage newPublicPage(User user) {
        PublicPage page = new PublicPage();
        page.setUser(user);
        page.setToken(PublicPageService.generateToken());
        publicPageDAO.persist(page);
        return page;
    }

    private void applyPublicPageSettings(User user, PublicPage page, PublicPageSettings settings) {
        page.setName(StringUtils.truncate(StringUtils.trimToNull(settings.getName()), 128));
        page.setEnabled(settings.isEnabled());
        page.setShowUncategorized(settings.isShowUncategorized());

        // only keep categories of the user
        Set<Long> requestedIds =
                settings.getCategoryIds() == null
                        ? Set.of()
                        : Set.copyOf(settings.getCategoryIds());
        page.getCategoryIds().clear();
        feedCategoryDAO.findAll(user).stream()
                .map(FeedCategory::getId)
                .filter(requestedIds::contains)
                .forEach(page.getCategoryIds()::add);
    }

    private static PublicPageSettings toPublicPageSettings(PublicPage page) {
        PublicPageSettings settings = new PublicPageSettings();
        settings.setId(page.getId());
        settings.setName(page.getName());
        settings.setEnabled(page.isEnabled());
        settings.setShowUncategorized(page.isShowUncategorized());
        settings.setToken(page.getToken());
        settings.setCategoryIds(page.getCategoryIds().stream().sorted().toList());
        return settings;
    }

    @Path("/profile")
    @GET
    @Transactional
    @Operation(summary = "Retrieve user's profile")
    public UserModel getUserProfile() {
        User user = authenticationContext.getCurrentUser();

        UserModel userModel = new UserModel();
        userModel.setId(user.getId());
        userModel.setName(user.getName());
        userModel.setEmail(user.getEmail());
        userModel.setEnabled(!user.isDisabled());
        userModel.setApiKey(user.getApiKey());
        userModel.setLastForceRefresh(user.getLastForceRefresh());
        for (UserRole role : userRoleDAO.findAll(user)) {
            if (role.getRole() == Role.ADMIN) {
                userModel.setAdmin(true);
            }
        }
        return userModel;
    }

    @Path("/profile")
    @POST
    @Transactional
    @Operation(summary = "Save user's profile")
    public Response saveUserProfile(
            @Valid @Parameter(required = true) ProfileModificationRequest request) {
        User user = authenticationContext.getCurrentUser();
        if (MadnessFeedConstants.USERNAME_DEMO.equals(user.getName())) {
            return Response.status(Status.FORBIDDEN)
                    .entity("the profile of the demo account cannot be modified")
                    .build();
        }

        Optional<User> login = userService.login(user.getName(), request.getCurrentPassword());
        if (login.isEmpty()) {
            throw new BadRequestException("invalid password");
        }

        String email = StringUtils.trimToNull(request.getEmail());
        if (StringUtils.isNotBlank(email)) {
            User u = userDAO.findByEmail(email);
            if (u != null && !user.getId().equals(u.getId())) {
                throw new BadRequestException("email already taken");
            }
            user.setEmail(email);
        }

        if (StringUtils.isNotBlank(request.getNewPassword())) {
            byte[] password =
                    encryptionService.getEncryptedPassword(
                            request.getNewPassword(), user.getSalt());
            user.setPassword(password);
            user.setApiKey(userService.generateApiKey(user));
        }

        if (request.isNewApiKey()) {
            user.setApiKey(userService.generateApiKey(user));
        }

        userDAO.merge(user);
        return Response.ok().build();
    }

    @Path("/register")
    @PermitAll
    @POST
    @Transactional
    @Operation(summary = "Register a new account")
    public Response registerUser(@Valid @Parameter(required = true) RegistrationRequest req) {
        try {
            userService.register(
                    req.getName(),
                    req.getPassword(),
                    req.getEmail(),
                    Collections.singletonList(Role.USER));
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Path("/initialSetup")
    @PermitAll
    @POST
    @Transactional
    @Operation(
            summary = "Create the initial admin account",
            description = "This endpoint is only available when no users exist in the database")
    public Response initialSetup(@Valid @Parameter(required = true) InitialSetupRequest req) {
        boolean initialSetupRequired = databaseStartupService.isInitialSetupRequired();
        if (!initialSetupRequired) {
            return Response.status(Status.BAD_REQUEST)
                    .entity("Initial setup has already been completed")
                    .build();
        }

        userService.register(
                req.getName(),
                req.getPassword(),
                req.getEmail(),
                List.of(Role.ADMIN, Role.USER),
                true);

        if (config.users().createDemoAccount()) {
            User demo = userDAO.findByName(MadnessFeedConstants.USERNAME_DEMO);
            if (demo == null) {
                userService.createDemoUser();
            }
        }

        return Response.ok().build();
    }

    @Path("/passwordReset")
    @PermitAll
    @POST
    @Transactional
    @Operation(summary = "send a password reset email")
    public Response sendPasswordReset(@Valid @Parameter(required = true) PasswordResetRequest req) {
        if (!config.passwordRecoveryEnabled()) {
            throw new IllegalArgumentException(
                    "Password recovery is not enabled on this MadnessFeed instance");
        }

        User user = userDAO.findByEmail(req.getEmail());
        if (user == null) {
            return Response.ok().build();
        }

        try {
            user.setRecoverPasswordToken(Digests.sha1Hex(UUID.randomUUID().toString()));
            user.setRecoverPasswordTokenDate(Instant.now());

            mailService.sendMail(user, "Password recovery", buildEmailContent(user));
            return Response.ok().build();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("could not send email")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }

    private String buildEmailContent(User user) throws URISyntaxException {
        String publicUrl =
                Urls.removeTrailingSlash(config.passwordRecoveryPublicBaseUrl().orElseThrow());
        return String.format(
                "You asked for password recovery for account '%s', <a href='%s'>follow this link</a> to change your password. Ignore this if you didn't request a password recovery.",
                user.getName(), callbackUrl(user, publicUrl));
    }

    private String callbackUrl(User user, String publicUrl) throws URISyntaxException {
        URIBuilder queryBuilder = new URIBuilder();
        queryBuilder.addParameter("email", user.getEmail());
        queryBuilder.addParameter("token", user.getRecoverPasswordToken());
        String queryString = queryBuilder.build().getRawQuery();
        return publicUrl + "/#/passwordReset?" + queryString;
    }

    @Path("/passwordResetCallback")
    @PermitAll
    @POST
    @Transactional
    @Operation(summary = "confirm password reset with new password")
    public Response passwordRecoveryCallback(
            @Valid @Parameter(required = true) PasswordResetConfirmationRequest req) {
        String email = req.getEmail();
        String token = req.getToken();
        String password = req.getPassword();

        Preconditions.checkNotNull(email);
        Preconditions.checkNotNull(token);
        Preconditions.checkNotNull(password);

        User user = userDAO.findByEmail(email);
        if (user == null
                || user.getRecoverPasswordToken() == null
                || !user.getRecoverPasswordToken().equals(token)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity("Email not found or invalid token.")
                    .build();
        }
        if (ChronoUnit.MINUTES.between(user.getRecoverPasswordTokenDate(), Instant.now()) >= 30) {
            return Response.status(Status.UNAUTHORIZED).entity("Token expired.").build();
        }

        byte[] encryptedPassword = encryptionService.getEncryptedPassword(password, user.getSalt());
        user.setPassword(encryptedPassword);
        if (StringUtils.isNotBlank(user.getApiKey())) {
            user.setApiKey(userService.generateApiKey(user));
        }
        user.setRecoverPasswordToken(null);
        user.setRecoverPasswordTokenDate(null);

        return Response.ok().build();
    }

    @Path("/profile/deleteAccount")
    @POST
    @Transactional
    @Operation(summary = "Delete the user account")
    public Response deleteUser() {
        User user = authenticationContext.getCurrentUser();
        if (MadnessFeedConstants.USERNAME_DEMO.equals(user.getName())) {
            return Response.status(Status.FORBIDDEN)
                    .entity("the demo account cannot be deleted")
                    .build();
        }

        Set<Role> roles = userRoleDAO.findRoles(user);
        if (roles.contains(Role.ADMIN) && userRoleDAO.countAdmins() == 1) {
            return Response.status(Status.FORBIDDEN)
                    .entity("The last admin account cannot be deleted")
                    .build();
        }

        userService.unregister(userDAO.findById(user.getId()));
        return Response.ok().build();
    }
}
