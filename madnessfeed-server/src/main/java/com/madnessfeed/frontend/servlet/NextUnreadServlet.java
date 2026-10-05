package com.madnessfeed.frontend.servlet;

import com.madnessfeed.backend.dao.FeedCategoryDAO;
import com.madnessfeed.backend.dao.FeedEntryStatusDAO;
import com.madnessfeed.backend.dao.FeedSubscriptionDAO;
import com.madnessfeed.backend.model.FeedCategory;
import com.madnessfeed.backend.model.FeedEntryStatus;
import com.madnessfeed.backend.model.FeedSubscription;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.model.UserSettings.ReadingOrder;
import com.madnessfeed.backend.service.FeedEntryService;
import com.madnessfeed.frontend.resource.CategoryREST;
import com.madnessfeed.security.AuthenticationContext;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.net.URI;
import java.util.List;

@Path("/next")
@RequiredArgsConstructor
@Singleton
public class NextUnreadServlet {

    private final FeedSubscriptionDAO feedSubscriptionDAO;
    private final FeedEntryStatusDAO feedEntryStatusDAO;
    private final FeedCategoryDAO feedCategoryDAO;
    private final FeedEntryService feedEntryService;
    private final AuthenticationContext authenticationContext;
    private final UriInfo uri;

    @GET
    @Transactional
    @Operation(hidden = true)
    public Response get(
            @QueryParam("category") String categoryId,
            @QueryParam("order") @DefaultValue("desc") ReadingOrder order) {
        User user = authenticationContext.getCurrentUser();
        if (user == null) {
            return Response.temporaryRedirect(uri.getBaseUri()).build();
        }

        FeedEntryStatus s = null;
        if (StringUtils.isBlank(categoryId) || CategoryREST.ALL.equals(categoryId)) {
            List<FeedSubscription> subs = feedSubscriptionDAO.findAll(user);
            List<FeedEntryStatus> statuses =
                    feedEntryStatusDAO.findBySubscriptions(
                            user, subs, true, null, null, 0, 1, order, true, null, null, null);
            s = statuses.stream().findFirst().orElse(null);
        } else {
            FeedCategory category = feedCategoryDAO.findById(user, Long.valueOf(categoryId));
            if (category != null) {
                List<FeedCategory> children =
                        feedCategoryDAO.findAllChildrenCategories(user, category);
                List<FeedSubscription> subscriptions =
                        feedSubscriptionDAO.findByCategories(user, children);
                List<FeedEntryStatus> statuses =
                        feedEntryStatusDAO.findBySubscriptions(
                                user,
                                subscriptions,
                                true,
                                null,
                                null,
                                0,
                                1,
                                order,
                                true,
                                null,
                                null,
                                null);
                s = statuses.stream().findFirst().orElse(null);
            }
        }
        if (s != null) {
            feedEntryService.markEntry(user, s.getEntry().getId(), true);
        }

        String url = s == null ? uri.getBaseUri().toString() : s.getEntry().getUrl();
        return Response.temporaryRedirect(URI.create(url)).build();
    }
}
