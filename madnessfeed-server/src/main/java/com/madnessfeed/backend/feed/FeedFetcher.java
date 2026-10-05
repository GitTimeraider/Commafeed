package com.madnessfeed.backend.feed;

import com.madnessfeed.backend.Digests;
import com.madnessfeed.backend.HttpGetter;
import com.madnessfeed.backend.HttpGetter.HttpRequest;
import com.madnessfeed.backend.HttpGetter.HttpResult;
import com.madnessfeed.backend.HttpGetter.NotModifiedException;
import com.madnessfeed.backend.HttpGetter.SchemeNotAllowedException;
import com.madnessfeed.backend.HttpGetter.TooManyRequestsException;
import com.madnessfeed.backend.feed.parser.FeedParser;
import com.madnessfeed.backend.feed.parser.FeedParser.FeedParsingException;
import com.madnessfeed.backend.feed.parser.FeedParserResult;
import com.madnessfeed.backend.urlprovider.FeedURLProvider;

import io.quarkus.arc.All;

import jakarta.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Fetches a feed then parses it */
@Slf4j
@Singleton
public class FeedFetcher {

    private final FeedParser parser;
    private final HttpGetter getter;
    private final List<FeedURLProvider> urlProviders;

    public FeedFetcher(
            FeedParser parser, HttpGetter getter, @All List<FeedURLProvider> urlProviders) {
        this.parser = parser;
        this.getter = getter;
        this.urlProviders = urlProviders;
    }

    public FeedFetcherResult fetch(
            String feedUrl,
            boolean extractFeedUrlFromHtml,
            String lastModified,
            String eTag,
            Instant lastPublishedDate,
            String lastContentHash)
            throws FeedParsingException,
                    IOException,
                    NotModifiedException,
                    TooManyRequestsException,
                    SchemeNotAllowedException,
                    NoFeedFoundException {
        log.debug("Fetching feed {}", feedUrl);

        HttpResult result =
                getter.get(
                        HttpRequest.builder(feedUrl).lastModified(lastModified).eTag(eTag).build());
        byte[] content = result.content();

        FeedParserResult parserResult;
        try {
            parserResult = parser.parse(result.urlAfterRedirect(), content);
        } catch (FeedParsingException e) {
            if (extractFeedUrlFromHtml) {
                String extractedUrl =
                        extractFeedUrl(
                                urlProviders,
                                feedUrl,
                                new String(result.content(), StandardCharsets.UTF_8));
                if (StringUtils.isNotBlank(extractedUrl)) {
                    feedUrl = extractedUrl;

                    result =
                            getter.get(
                                    HttpRequest.builder(extractedUrl)
                                            .lastModified(lastModified)
                                            .eTag(eTag)
                                            .build());
                    content = result.content();
                    parserResult = parser.parse(result.urlAfterRedirect(), content);
                } else {
                    throw new NoFeedFoundException(e);
                }
            } else {
                throw e;
            }
        }

        if (content == null) {
            throw new IOException("Feed content is empty.");
        }

        boolean lastModifiedHeaderValueChanged =
                !Strings.CS.equals(lastModified, result.lastModifiedSince());
        boolean etagHeaderValueChanged = !Strings.CS.equals(eTag, result.eTag());

        String hash = Digests.sha1Hex(content);
        if (lastContentHash != null && lastContentHash.equals(hash)) {
            log.debug("content hash not modified: {}", feedUrl);
            throw new NotModifiedException(
                    "content hash not modified",
                    lastModifiedHeaderValueChanged ? result.lastModifiedSince() : null,
                    etagHeaderValueChanged ? result.eTag() : null);
        }

        if (lastPublishedDate != null
                && lastPublishedDate.equals(parserResult.lastPublishedDate())) {
            log.debug("publishedDate not modified: {}", feedUrl);
            throw new NotModifiedException(
                    "publishedDate not modified",
                    lastModifiedHeaderValueChanged ? result.lastModifiedSince() : null,
                    etagHeaderValueChanged ? result.eTag() : null);
        }

        return new FeedFetcherResult(
                parserResult,
                result.urlAfterRedirect(),
                result.lastModifiedSince(),
                result.eTag(),
                hash,
                result.validFor());
    }

    private static String extractFeedUrl(
            List<FeedURLProvider> urlProviders, String url, String urlContent) {
        return urlProviders.stream()
                .flatMap(provider -> provider.get(url, urlContent).stream())
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    public record FeedFetcherResult(
            FeedParserResult feed,
            String urlAfterRedirect,
            String lastModifiedHeader,
            String lastETagHeader,
            String contentHash,
            Duration validFor) {}

    public static class NoFeedFoundException extends Exception {
        private static final long serialVersionUID = 1L;

        public NoFeedFoundException(Throwable cause) {
            super("This URL does not point to an RSS feed or a website with an RSS feed.", cause);
        }
    }
}
