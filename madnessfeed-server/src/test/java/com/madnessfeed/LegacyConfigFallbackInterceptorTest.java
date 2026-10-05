package com.madnessfeed;

import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class LegacyConfigFallbackInterceptorTest {

    private static SmallRyeConfig config(Map<String, String> env, Map<String, String> properties) {
        return new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .addDiscoveredInterceptors()
                .withProfile("prod")
                .withSources(new EnvConfigSource(env, 300))
                .withSources(new PropertiesConfigSource(properties, "test", 250))
                .build();
    }

    private static String value(SmallRyeConfig config, String name) {
        return config.getOptionalValue(name, String.class).orElse(null);
    }

    @Test
    void legacyEnvironmentVariable() {
        SmallRyeConfig config =
                config(Map.of("COMMAFEED_ALLOWED_NETWORKS", "10.0.0.0/8"), Map.of());
        Assertions.assertEquals("10.0.0.0/8", value(config, "madnessfeed.allowed-networks"));
    }

    @Test
    void legacyProperty() {
        SmallRyeConfig config =
                config(Map.of(), Map.of("commafeed.users.allow-registrations", "true"));
        Assertions.assertEquals("true", value(config, "madnessfeed.users.allow-registrations"));
    }

    @Test
    void legacyProfileProperty() {
        SmallRyeConfig config =
                config(Map.of(), Map.of("%prod.commafeed.feed-refresh.interval", "10m"));
        Assertions.assertEquals("10m", value(config, "madnessfeed.feed-refresh.interval"));
    }

    @Test
    void newNameWins() {
        SmallRyeConfig config =
                config(
                        Map.of(
                                "COMMAFEED_IMAGE_PROXY_ENABLED", "true",
                                "MADNESSFEED_IMAGE_PROXY_ENABLED", "false"),
                        Map.of());
        Assertions.assertEquals("false", value(config, "madnessfeed.image-proxy-enabled"));
    }

    @Test
    void otherPropertiesUntouched() {
        SmallRyeConfig config = config(Map.of(), Map.of("quarkus.http.port", "1234"));
        Assertions.assertEquals("1234", value(config, "quarkus.http.port"));
        Assertions.assertNull(value(config, "madnessfeed.unknown"));
    }

    @Test
    void namesAreMapped() {
        Assertions.assertEquals(
                "commafeed.allowed-networks",
                LegacyConfigFallbackInterceptor.toLegacyName("madnessfeed.allowed-networks"));
        Assertions.assertEquals(
                "%dev.commafeed.x",
                LegacyConfigFallbackInterceptor.toLegacyName("%dev.madnessfeed.x"));
        Assertions.assertEquals(
                "madnessfeed.x", LegacyConfigFallbackInterceptor.toNewName("commafeed.x"));
        Assertions.assertEquals(
                "MADNESSFEED_ALLOWED_NETWORKS",
                LegacyConfigFallbackInterceptor.toNewName("COMMAFEED_ALLOWED_NETWORKS"));
        Assertions.assertEquals(
                "_DEV_MADNESSFEED_X",
                LegacyConfigFallbackInterceptor.toNewName("_DEV_COMMAFEED_X"));
    }
}
