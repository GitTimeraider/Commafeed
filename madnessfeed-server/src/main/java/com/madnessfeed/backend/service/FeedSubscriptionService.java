package com.madnessfeed.backend.service;

import com.madnessfeed.MadnessFeedConfiguration;
import com.madnessfeed.backend.Urls;
import com.madnessfeed.backend.dao.FeedEntryStatusDAO;
import com.madnessfeed.backend.dao.FeedSubscriptionDAO;
import com.madnessfeed.backend.feed.FeedRefreshEngine;
import com.madnessfeed.backend.feed.FeedUtils;
import com.madnessfeed.backend.model.Feed;
import com.madnessfeed.backend.model.FeedCategory;
import com.madnessfeed.backend.model.FeedSubscription;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.frontend.model.UnreadCount;

import jakarta.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class FeedSubscriptionService {

    private final FeedEntryStatusDAO feedEntryStatusDAO;
    private final FeedSubscriptionDAO feedSubscriptionDAO;
    private final FeedService feedService;
    private final FeedRefreshEngine feedRefreshEngine;
    private final MadnessFeedConfiguration config;

    public FeedSubscriptionService(
            FeedEntryStatusDAO feedEntryStatusDAO,
            FeedSubscriptionDAO feedSubscriptionDAO,
            FeedService feedService,
            FeedRefreshEngine feedRefreshEngine,
            MadnessFeedConfiguration config) {
        this.feedEntryStatusDAO = feedEntryStatusDAO;
        this.feedSubscriptionDAO = feedSubscriptionDAO;
        this.feedService = feedService;
        this.feedRefreshEngine = feedRefreshEngine;
        this.config = config;

        // automatically refresh new feeds after they are subscribed to
        // we need to use this hook because the feed needs to have been persisted before being
        // processed
        // by the feed engine
        feedSubscriptionDAO.onPostCommitInsert(
                sub -> {
                    Feed feed = sub.getFeed();
                    if (feed.getDisabledUntil() == null
                            || feed.getDisabledUntil().isBefore(Instant.now())) {
                        feedRefreshEngine.refreshImmediately(feed);
                    }
                });
    }

    public long subscribe(
            User user, String url, String title, FeedCategory category, int position) {
        Integer maxFeedsPerUser = config.database().cleanup().maxFeedsPerUser();
        if (maxFeedsPerUser > 0 && feedSubscriptionDAO.count(user) >= maxFeedsPerUser) {
            String message =
                    String.format(
                            "You cannot subscribe to more feeds on this MadnessFeed instance (max %s feeds per user)",
                            maxFeedsPerUser);
            throw new FeedSubscriptionException(message);
        }

        Feed feed = feedService.findOrCreate(url);

        // upgrade feed to https if it was using http
        if (Urls.isHttp(feed.getUrl()) && Urls.isHttps(url)) {
            feed.setUrl(url);
        }

        FeedSubscription sub = feedSubscriptionDAO.findByFeed(user, feed);
        if (sub == null) {
            sub = new FeedSubscription();
            sub.setFeed(feed);
            sub.setUser(user);
        }
        sub.setCategory(category);
        sub.setPosition(position);
        sub.setTitle(FeedUtils.truncate(title, 128));
        return feedSubscriptionDAO.merge(sub).getId();
    }

    public boolean unsubscribe(User user, Long subId) {
        FeedSubscription sub = feedSubscriptionDAO.findById(user, subId);
        if (sub != null) {
            feedSubscriptionDAO.delete(sub);
            return true;
        } else {
            return false;
        }
    }

    public void refreshAll(User user) throws ForceFeedRefreshTooSoonException {
        Instant lastForceRefresh = user.getLastForceRefresh();
        if (lastForceRefresh != null
                && lastForceRefresh
                        .plus(config.feedRefresh().forceRefreshCooldownDuration())
                        .isAfter(Instant.now())) {
            throw new ForceFeedRefreshTooSoonException();
        }

        List<FeedSubscription> subs = feedSubscriptionDAO.findAll(user);
        for (FeedSubscription sub : subs) {
            Feed feed = sub.getFeed();
            feedRefreshEngine.refreshImmediately(feed);
        }

        user.setLastForceRefresh(Instant.now());
    }

    public Map<Long, UnreadCount> getUnreadCount(User user) {
        return feedSubscriptionDAO.findAll(user).stream()
                .collect(
                        Collectors.toMap(
                                FeedSubscription::getId, feedEntryStatusDAO::getUnreadCount));
    }

    @SuppressWarnings("serial")
    public static class FeedSubscriptionException extends RuntimeException {
        private FeedSubscriptionException(String msg) {
            super(msg);
        }
    }

    @SuppressWarnings("serial")
    public static class ForceFeedRefreshTooSoonException extends Exception {
        private ForceFeedRefreshTooSoonException() {
            super();
        }
    }
}
