package com.madnessfeed.security;

import io.quarkus.vertx.http.runtime.FormAuthConfig.CookieSameSite;
import io.quarkus.vertx.http.runtime.VertxHttpConfig;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.NewCookie;

import java.time.Instant;
import java.util.Date;

@Singleton
public class CookieService {

    private final String cookieName;
    private final CookieSameSite cookieSameSite;

    public CookieService(VertxHttpConfig config) {
        this.cookieName = config.auth().form().cookieName();
        this.cookieSameSite = config.auth().form().cookieSameSite();
    }

    public NewCookie buildLogoutCookie() {
        // use the same SameSite attribute as the login cookie, otherwise browsers refuse to remove
        // it when MadnessFeed is shown in an iframe of another site. Browsers only accept
        // SameSite=None on secure cookies.
        return new NewCookie.Builder(cookieName)
                .maxAge(0)
                .expiry(Date.from(Instant.EPOCH))
                .path("/")
                .sameSite(NewCookie.SameSite.valueOf(cookieSameSite.name()))
                .secure(cookieSameSite == CookieSameSite.NONE)
                .build();
    }
}
