package com.commafeed.backend.dao;

import com.commafeed.backend.model.QUserPasskey;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserPasskey;

import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;

import java.util.List;

@Singleton
public class UserPasskeyDAO extends GenericDAO<UserPasskey> {

    private static final QUserPasskey PASSKEY = QUserPasskey.userPasskey;

    public UserPasskeyDAO(EntityManager entityManager) {
        super(entityManager, UserPasskey.class);
    }

    public List<UserPasskey> findAll(User user) {
        return query().selectFrom(PASSKEY)
                .where(PASSKEY.user.eq(user))
                .orderBy(PASSKEY.id.asc())
                .fetch();
    }

    public long count(User user) {
        return query().select(PASSKEY.count())
                .from(PASSKEY)
                .where(PASSKEY.user.eq(user))
                .fetchOne();
    }
}
