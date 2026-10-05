package com.madnessfeed.backend.task;

import com.madnessfeed.MadnessFeedConfiguration;
import com.madnessfeed.backend.service.db.DatabaseCleaningService;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Singleton
public class OldStatusesCleanupTask extends ScheduledTask {

    private final MadnessFeedConfiguration config;
    private final DatabaseCleaningService cleaner;

    @Override
    public void run() {
        Instant threshold = config.database().cleanup().statusesInstantThreshold();
        if (threshold != null) {
            cleaner.cleanStatusesOlderThan(threshold);
        }
    }

    @Override
    public long getInitialDelay() {
        return 15;
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
