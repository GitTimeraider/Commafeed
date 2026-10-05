package com.madnessfeed;

import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.FallbackConfigSourceInterceptor;
import io.smallrye.config.Priorities;

import jakarta.annotation.Priority;

import java.io.Serial;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps settings written before the rename from CommaFeed to MadnessFeed working.
 *
 * <p>When a {@code madnessfeed.*} property is not set, its {@code commafeed.*} counterpart is used
 * instead. This covers every way of setting a property, including environment variables ({@code
 * COMMAFEED_ALLOWED_NETWORKS} for {@code MADNESSFEED_ALLOWED_NETWORKS}) and profile-specific
 * properties ({@code %dev.commafeed.*}). The new name always wins when both are set.
 */
@Priority(Priorities.LIBRARY + 600)
public class LegacyConfigFallbackInterceptor extends FallbackConfigSourceInterceptor {

    @Serial private static final long serialVersionUID = 1L;

    private static final Pattern NEW_PROPERTY = Pattern.compile("^(%[^.]+\\.)?madnessfeed\\.");
    private static final Pattern LEGACY_PROPERTY = Pattern.compile("^(%[^.]+\\.)?commafeed\\.");
    private static final Pattern LEGACY_ENV = Pattern.compile("^(_[A-Z0-9]+_)?COMMAFEED_");

    public LegacyConfigFallbackInterceptor() {
        super(LegacyConfigFallbackInterceptor::toLegacyName);
    }

    static String toLegacyName(String name) {
        return NEW_PROPERTY.matcher(name).replaceFirst("$1commafeed.");
    }

    static String toNewName(String name) {
        String renamed = LEGACY_PROPERTY.matcher(name).replaceFirst("$1madnessfeed.");
        return LEGACY_ENV.matcher(renamed).replaceFirst("$1MADNESSFEED_");
    }

    @Override
    public Iterator<String> iterateNames(ConfigSourceInterceptorContext context) {
        // also advertise legacy names under their new name, so that lookups that enumerate property
        // names first (maps, optional groups) find them too
        Set<String> names = new LinkedHashSet<>();
        Iterator<String> it = context.iterateNames();
        while (it.hasNext()) {
            String name = it.next();
            names.add(name);
            names.add(toNewName(name));
        }
        return names.iterator();
    }
}
