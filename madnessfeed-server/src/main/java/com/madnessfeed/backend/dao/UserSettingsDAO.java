package com.madnessfeed.backend.dao;

import com.madnessfeed.backend.model.QUserSettings;
import com.madnessfeed.backend.model.User;
import com.madnessfeed.backend.model.UserSettings;

import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;

@Singleton
public class UserSettingsDAO extends GenericDAO<UserSettings> {

    private static final QUserSettings SETTINGS = QUserSettings.userSettings;

    public UserSettingsDAO(EntityManager entityManager) {
        super(entityManager, UserSettings.class);
    }

    public UserSettings findByUser(User user) {
        return query().selectFrom(SETTINGS).where(SETTINGS.user.eq(user)).fetchFirst();
    }
}
