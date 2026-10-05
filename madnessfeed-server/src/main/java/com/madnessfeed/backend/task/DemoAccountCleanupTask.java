package com.madnessfeed.backend.task;

import com.madnessfeed.MadnessFeedConfiguration;
import com.madnessfeed.MadnessFeedConstants;
import com.madnessfeed.backend.dao.UnitOfWork;
import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.service.UserService;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Singleton
@Slf4j
public class DemoAccountCleanupTask extends ScheduledTask {

    private final MadnessFeedConfiguration config;
    private final UnitOfWork unitOfWork;
    private final UserDAO userDAO;
    private final UserService userService;

    @Override
    protected void run() {
        if (!config.users().createDemoAccount()) {
            return;
        }

        log.info("recreating demo user account");
        unitOfWork.run(
                () -> {
                    User demoUser = userDAO.findByName(MadnessFeedConstants.USERNAME_DEMO);
                    if (demoUser == null) {
                        return;
                    }

                    userService.unregister(demoUser);
                    userService.createDemoUser();
                });
    }

    @Override
    protected long getInitialDelay() {
        return 1;
    }

    @Override
    protected long getPeriod() {
        return getTimeUnit().convert(24, TimeUnit.HOURS);
    }

    @Override
    protected TimeUnit getTimeUnit() {
        return TimeUnit.MINUTES;
    }
}
