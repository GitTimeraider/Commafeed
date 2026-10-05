package com.madnessfeed.backend.dao;

import com.madnessfeed.backend.model.QFeedEntry;
import com.madnessfeed.backend.model.QFeedEntryStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Predicates {

    private static final QFeedEntryStatus STATUS = QFeedEntryStatus.feedEntryStatus;

    public static BooleanExpression isNotStarred(QFeedEntry entry) {
        return JPAExpressions.selectOne()
                .from(STATUS)
                .where(STATUS.entry.eq(entry).and(STATUS.starred.isTrue()))
                .notExists();
    }
}
