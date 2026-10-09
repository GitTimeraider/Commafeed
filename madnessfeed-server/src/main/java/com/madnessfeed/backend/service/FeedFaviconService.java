package com.madnessfeed.backend.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.Resources;
import com.madnessfeed.backend.favicon.Favicon;
import com.madnessfeed.backend.favicon.FaviconFetcher;
import com.madnessfeed.backend.model.Feed;

import io.quarkus.arc.All;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Singleton
@Slf4j
public class FeedFaviconService {

    private static final Set<MediaType> ICON_MIMETYPE_BLACKLIST =
            Set.of(MediaType.APPLICATION_XML_TYPE, MediaType.TEXT_HTML_TYPE);
    private static final long MIN_ICON_LENGTH = 100;
    private static final long MAX_ICON_LENGTH = 100000;

    // fetching an icon can take several outgoing requests (and their timeouts), keep the result
    // so that pages showing many icons (e.g. public pages, often embedded in an iframe of another
    // website that waits for them before it's done loading) don't wait on remote websites
    private static final Duration CACHE_EXPIRATION = Duration.ofHours(12);
    private static final long CACHE_MAXIMUM_WEIGHT = 5 * 1024 * 1024;

    private final List<FaviconFetcher> faviconFetchers;
    private final Favicon defaultFavicon;
    private final Cache<CacheKey, Favicon> cache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(CACHE_EXPIRATION)
                    .maximumWeight(CACHE_MAXIMUM_WEIGHT)
                    .<CacheKey, Favicon>weigher((key, icon) -> icon.icon().length)
                    .build();

    public FeedFaviconService(@All List<FaviconFetcher> faviconFetchers) throws IOException {
        this.faviconFetchers = faviconFetchers;
        this.defaultFavicon =
                new Favicon(
                        Resources.toByteArray(
                                Objects.requireNonNull(
                                        getClass().getResource("/images/default_favicon.gif"))),
                        "image/gif");
    }

    public Favicon fetchFavicon(Feed feed) {
        // the key contains every url the fetchers use so that a change in the feed fetches the icon
        // again
        CacheKey key = new CacheKey(feed.getUrl(), feed.getLink(), feed.getIconUrl());
        try {
            // concurrent requests for the same icon wait for a single fetch
            return cache.get(key, () -> doFetchFavicon(feed));
        } catch (ExecutionException e) {
            log.debug("Failed to fetch favicon for feed {}", feed.getUrl(), e);
            return defaultFavicon;
        }
    }

    private Favicon doFetchFavicon(Feed feed) {
        for (FaviconFetcher faviconFetcher : faviconFetchers) {
            Favicon icon = faviconFetcher.fetch(feed);
            if (isFaviconValid(icon)) {
                return icon;
            }
        }
        return defaultFavicon;
    }

    private static boolean isFaviconValid(Favicon favicon) {
        if (favicon == null || ArrayUtils.isEmpty(favicon.icon())) {
            return false;
        }

        long length = favicon.icon().length;
        if (length < MIN_ICON_LENGTH) {
            log.debug("Length {} below MIN_ICON_LENGTH {}", length, MIN_ICON_LENGTH);
            return false;
        }

        if (length > MAX_ICON_LENGTH) {
            log.debug("Length {} greater than MAX_ICON_LENGTH {}", length, MAX_ICON_LENGTH);
            return false;
        }

        if (ICON_MIMETYPE_BLACKLIST.stream().anyMatch(bl -> bl.isCompatible(favicon.mediaType()))) {
            log.debug("Content-Type {} is blacklisted", favicon.mediaType());
            return false;
        }

        return true;
    }

    private record CacheKey(String url, String link, String iconUrl) {}
}
