package com.madnessfeed.backend.service.internal;

import com.madnessfeed.backend.dao.UnitOfWork;
import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.model.User;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RequiredArgsConstructor
@Singleton
public class PostLoginActivities {

    private final UserDAO userDAO;
    private final UnitOfWork unitOfWork;

    public void executeFor(User user) {
        // only update lastLogin every once in a while in order to avoid invalidating the cache
        // every
        // time someone logs in
        Instant now = Instant.now();
        Instant lastLogin = user.getLastLogin();
        if (lastLogin == null || ChronoUnit.MINUTES.between(lastLogin, now) >= 30) {
            user.setLastLogin(now);
            unitOfWork.run(() -> userDAO.merge(user));
        }
    }
}
