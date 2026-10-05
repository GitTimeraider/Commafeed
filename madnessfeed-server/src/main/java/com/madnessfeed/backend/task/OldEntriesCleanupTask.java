package com.madnessfeed.backend.task;

import com.madnessfeed.MadnessFeedConfiguration;
import com.madnessfeed.backend.service.db.DatabaseCleaningService;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Singleton
public class OldEntriesCleanupTask extends ScheduledTask {

    private final MadnessFeedConfiguration config;
    private final DatabaseCleaningService cleaner;

    @Override
    public void run() {
        Duration entriesMaxAge = config.database().cleanup().entriesMaxAge();
        if (!entriesMaxAge.isZero()) {
            Instant threshold = Instant.now().minus(entriesMaxAge);
            cleaner.cleanEntriesOlderThan(threshold);
        }
    }

    @Override
    public long getInitialDelay() {
        return 5;
    }

    @Override
    public long getPeriod() {
        return 60;
    }

    @Override
    public TimeUnit getTimeUnit() {
        return TimeUnit.MINUTES;
    }
}
