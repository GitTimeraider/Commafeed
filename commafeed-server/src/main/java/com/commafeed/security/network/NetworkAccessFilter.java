package com.commafeed.security.network;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Refuses requests from clients outside the allowed networks, except for what's needed to display
 * the public pages: the single page application itself (the public pages are rendered by it), its
 * static files, the server infos and the public page API.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class NetworkAccessFilter {

    // run before everything else, including authentication
    private static final int PRIORITY = 1000;

    private static final Set<String> PUBLIC_PATHS =
            Set.of(
                    "/",
                    "/index.html",
                    "/favicon.ico",
                    "/favicon.svg",
                    "/manifest.json",
                    "/robots.txt",
                    "/rest/server/get");
    private static final Pattern PUBLIC_PATH_PATTERN =
            Pattern.compile("^/(assets/[^/]+|app-icon-\\d+\\.png|rest/public/.+)$");

    private final NetworkAccessService networkAccessService;

    void registerFilter(@Observes Filters filters) {
        filters.register(this::filter, PRIORITY);
    }

    private void filter(RoutingContext context) {
        if (!networkAccessService.isEnabled() || isPublicPath(context.normalizedPath())) {
            context.next();
            return;
        }

        String clientAddress =
                context.request().remoteAddress() == null
                        ? null
                        : context.request().remoteAddress().hostAddress();
        if (!networkAccessService.isRestricted(clientAddress)) {
            context.next();
            return;
        }

        log.debug(
                "refused {} {} from {}",
                context.request().method(),
                context.normalizedPath(),
                clientAddress);
        context.response()
                .setStatusCode(403)
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"message\":\"access from your network is not allowed\"}");
    }

    static boolean isPublicPath(String path) {
        return path != null
                && !path.contains("..")
                && (PUBLIC_PATHS.contains(path) || PUBLIC_PATH_PATTERN.matcher(path).matches());
    }
}
