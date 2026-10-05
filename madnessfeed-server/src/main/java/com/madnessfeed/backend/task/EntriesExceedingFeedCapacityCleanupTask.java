package com.madnessfeed.backend.task;

import com.madnessfeed.MadnessFeedConfiguration;
import com.madnessfeed.backend.service.db.DatabaseCleaningService;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Singleton
public class EntriesExceedingFeedCapacityCleanupTask extends ScheduledTask {

    private final MadnessFeedConfiguration config;
    private final DatabaseCleaningService cleaner;

    @Override
    public void run() {
        int maxFeedCapacity = config.database().cleanup().maxFeedCapacity();
        if (maxFeedCapacity > 0) {
            cleaner.cleanEntriesForFeedsExceedingCapacity(maxFeedCapacity);
        }
    }

    @Override
    public long getInitialDelay() {
        return 10;
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
