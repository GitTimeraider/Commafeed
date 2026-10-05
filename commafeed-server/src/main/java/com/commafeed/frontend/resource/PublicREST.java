package com.commafeed.frontend.resource;

import com.commafeed.backend.dao.FeedEntryStatusDAO;
import com.commafeed.backend.favicon.Favicon;
import com.commafeed.backend.model.Feed;
import com.commafeed.backend.model.FeedCategory;
import com.commafeed.backend.model.FeedEntryStatus;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.PublicPage;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserSettings.ReadingOrder;
import com.commafeed.backend.service.FeedFaviconService;
import com.commafeed.backend.service.PublicPageService;
import com.commafeed.backend.service.PublicPageService.PublicContent;
import com.commafeed.frontend.model.Entries;
import com.commafeed.frontend.model.Entry;
import com.commafeed.frontend.model.PublicCategory;
import com.commafeed.frontend.model.PublicSubscription;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.Cache;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Read-only, unauthenticated access to the categories a user chose to show on a public page. */
@Path("/rest/public/{token}")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Singleton
@Tag(name = "Public page")
public class PublicREST {

    public static final String ALL = "all";

    private static final int MAX_LIMIT = 100;

    private static final Comparator<FeedCategory> CATEGORY_COMPARATOR =
            Comparator.comparing(FeedCategory::getPosition).thenComparing(FeedCategory::getName);
    private static final Comparator<FeedSubscription> SUBSCRIPTION_COMPARATOR =
            Comparator.comparing(FeedSubscription::getPosition)
                    .thenComparing(FeedSubscription::getTitle);

    private final PublicPageService publicPageService;
    private final FeedEntryStatusDAO feedEntryStatusDAO;
    private final FeedFaviconService feedFaviconService;

    @GET
    @Path("/tree")
    @Transactional
    @Operation(
            summary = "Get public categories",
            description = "Get the name of a public page and the categories and feeds shown on it")
    @APIResponse(
            responseCode = "200",
            content = {
                @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = PublicCategory.class))
            })
    @APIResponse(responseCode = "404", description = "public page not found")
    public PublicCategory getTree(
            @Parameter(description = "public page token", required = true) @PathParam("token")
                    String token) {
        PublicPage page = findPage(token);
        PublicContent content = publicPageService.getPublicContent(page);

        PublicCategory root = buildCategory(page, null, content);
        root.setId(ALL);
        root.setName(ALL);
        root.setPageName(StringUtils.trimToNull(page.getName()));
        return root;
    }

    @GET
    @Path("/entries")
    @Transactional
    @Operation(
            summary = "Get public entries",
            description = "Get the entries of a public category or feed")
    @APIResponse(
            responseCode = "200",
            content = {
                @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Entries.class))
            })
    @APIResponse(responseCode = "404", description = "public page, category or feed not found")
    public Entries getEntries(
            @Parameter(description = "public page token", required = true) @PathParam("token")
                    String token,
            @Parameter(description = "'category' or 'feed'")
                    @QueryParam("type")
                    @DefaultValue("category")
                    String type,
            @Parameter(description = "id of the category or feed, 'all' for every public feed")
                    @QueryParam("id")
                    @DefaultValue(ALL)
                    String id,
            @Parameter(description = "offset for paging") @DefaultValue("0") @QueryParam("offset")
                    int offset,
            @Parameter(description = "limit for paging, default 20, maximum 100")
                    @DefaultValue("20")
                    @QueryParam("limit")
                    int limit) {
        PublicPage page = findPage(token);
        User user = page.getUser();
        PublicContent content = publicPageService.getPublicContent(page);

        offset = Math.max(0, offset);
        limit = Math.clamp(limit, 0, MAX_LIMIT);

        String name;
        List<FeedSubscription> subs;
        if ("feed".equals(type)) {
            FeedSubscription sub =
                    content.findPublicSubscription(parseId(id)).orElseThrow(NotFoundException::new);
            name = sub.getTitle();
            subs = List.of(sub);
        } else if (ALL.equals(id) || StringUtils.isBlank(id)) {
            name = ALL;
            subs = content.publicSubscriptions();
        } else {
            FeedCategory category =
                    content.findPublicCategory(parseId(id)).orElseThrow(NotFoundException::new);
            name = category.getName();
            subs = content.getPublicSubscriptions(category);
        }

        Entries entries = new Entries();
        entries.setName(name);
        entries.setOffset(offset);
        entries.setLimit(limit);
        entries.setIgnoredReadStatus(true);
        entries.setTimestamp(System.currentTimeMillis());

        if (!subs.isEmpty()) {
            List<FeedEntryStatus> statuses =
                    feedEntryStatusDAO.findBySubscriptions(
                            user,
                            subs,
                            false,
                            null,
                            null,
                            offset,
                            limit + 1,
                            ReadingOrder.DESC,
                            true,
                            null,
                            null,
                            null);
            for (FeedEntryStatus status : statuses) {
                entries.getEntries().add(buildPublicEntry(page, status));
            }
        }

        if (entries.getEntries().size() > limit) {
            entries.setHasMore(true);
            entries.getEntries().removeLast();
        }

        return entries;
    }

    @GET
    @Path("/favicon/{id}")
    @Cache(maxAge = 2592000)
    @Transactional
    @Operation(summary = "Fetch a public feed's icon")
    public Response getFavicon(
            @Parameter(description = "public page token", required = true) @PathParam("token")
                    String token,
            @Parameter(description = "subscription id", required = true) @PathParam("id") Long id) {
        FeedSubscription subscription =
                publicPageService
                        .getPublicContent(findPage(token))
                        .findPublicSubscription(id)
                        .orElseThrow(NotFoundException::new);

        Feed feed = subscription.getFeed();
        if (feed.getLastUpdated() == null) {
            return Response.status(Status.SERVICE_UNAVAILABLE)
                    .entity("Feed has not been fetched yet, please retry in a bit")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        Favicon icon = feedFaviconService.fetchFavicon(feed);
        return Response.ok(icon.icon(), icon.mediaType()).build();
    }

    private PublicPage findPage(String token) {
        return publicPageService.findPublicPage(token).orElseThrow(NotFoundException::new);
    }

    private static Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new NotFoundException();
        }
    }

    private PublicCategory buildCategory(
            PublicPage page, FeedCategory category, PublicContent content) {
        Long id = category == null ? null : category.getId();

        PublicCategory result = new PublicCategory();
        if (category != null) {
            result.setId(String.valueOf(category.getId()));
            result.setName(category.getName());
        }

        content.publicCategories().stream()
                .filter(c -> Objects.equals(content.getClosestPublicAncestorId(c), id))
                .sorted(CATEGORY_COMPARATOR)
                .map(c -> buildCategory(page, c, content))
                .forEach(result.getChildren()::add);

        content.publicSubscriptions().stream()
                .filter(
                        s ->
                                Objects.equals(
                                        s.getCategory() == null ? null : s.getCategory().getId(),
                                        id))
                .sorted(SUBSCRIPTION_COMPARATOR)
                .map(s -> buildSubscription(page, s))
                .forEach(result.getFeeds()::add);

        return result;
    }

    private PublicSubscription buildSubscription(PublicPage page, FeedSubscription subscription) {
        PublicSubscription sub = new PublicSubscription();
        sub.setId(subscription.getId());
        sub.setName(subscription.getTitle());
        sub.setFeedLink(subscription.getFeed().getLink());
        sub.setIconUrl(getFaviconUrl(page, subscription));
        return sub;
    }

    private Entry buildPublicEntry(PublicPage page, FeedEntryStatus status) {
        // images are not proxied because the image proxy requires authentication
        Entry entry = Entry.build(status, false);

        // do not leak the personal state of the owner of the page
        entry.setRead(false);
        entry.setStarred(false);
        entry.setMarkable(false);
        entry.setTags(List.of());
        entry.setIconUrl(getFaviconUrl(page, status.getSubscription()));
        return entry;
    }

    private static String getFaviconUrl(PublicPage page, FeedSubscription subscription) {
        // the token only contains hex characters, no encoding is needed
        return "rest/public/" + page.getToken() + "/favicon/" + subscription.getId();
    }
}
