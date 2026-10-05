package com.madnessfeed.security;

import com.madnessfeed.backend.dao.UserDAO;
import com.madnessfeed.backend.model.User;

import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Singleton
public class AuthenticationContext {

    private final SecurityIdentity securityIdentity;
    private final UserDAO userDAO;

    public User getCurrentUser() {
        if (securityIdentity.isAnonymous()) {
            return null;
        }

        String userId = securityIdentity.getPrincipal().getName();
        if (userId == null) {
            return null;
        }

        return userDAO.findById(Long.valueOf(userId));
    }
}
