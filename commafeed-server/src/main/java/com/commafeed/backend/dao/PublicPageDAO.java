package com.commafeed.backend.dao;

import com.commafeed.backend.model.PublicPage;
import com.commafeed.backend.model.QPublicPage;
import com.commafeed.backend.model.User;

import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;

import java.util.List;

@Singleton
public class PublicPageDAO extends GenericDAO<PublicPage> {

    private static final QPublicPage PAGE = QPublicPage.publicPage;

    public PublicPageDAO(EntityManager entityManager) {
        super(entityManager, PublicPage.class);
    }

    public List<PublicPage> findAll(User user) {
        return query().selectFrom(PAGE).where(PAGE.user.eq(user)).orderBy(PAGE.id.asc()).fetch();
    }

    public PublicPage findById(User user, Long id) {
        return query().selectFrom(PAGE).where(PAGE.user.eq(user), PAGE.id.eq(id)).fetchOne();
    }

    public PublicPage findByToken(String token) {
        return query().selectFrom(PAGE).where(PAGE.token.eq(token)).fetchOne();
    }
}
