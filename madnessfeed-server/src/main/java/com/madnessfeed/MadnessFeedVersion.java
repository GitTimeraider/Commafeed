package com.madnessfeed;

import jakarta.inject.Singleton;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Singleton
@Getter
public class MadnessFeedVersion {

    private final String version;
    private final String gitCommit;

    public MadnessFeedVersion() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = getClass().getResourceAsStream("/git.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        }

        this.version = properties.getProperty("git.build.version", "unknown");
        this.gitCommit = properties.getProperty("git.commit.id.abbrev", "unknown");
    }
}
