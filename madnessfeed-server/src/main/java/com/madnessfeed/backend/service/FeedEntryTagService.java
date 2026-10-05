package com.madnessfeed.backend.service;

import com.madnessfeed.backend.dao.FeedEntryDAO;
import com.madnessfeed.backend.dao.FeedEntryTagDAO;
import com.madnessfeed.backend.dao.FeedSubscriptionDAO;
import com.madnessfeed.backend.model.FeedEntry;
import com.madnessfeed.backend.model.FeedEntryTag;
import com.madnessfeed.backend.model.FeedSubscription;
import com.madnessfeed.backend.model.User;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Singleton
public class FeedEntryTagService {

    private final FeedEntryDAO feedEntryDAO;
    private final FeedEntryTagDAO feedEntryTagDAO;
    private final FeedSubscriptionDAO feedSubscriptionDAO;

    public void updateTags(User user, Long entryId, List<String> tagNames) {
        FeedEntry entry = feedEntryDAO.findById(entryId);
        if (entry == null) {
            return;
        }

        FeedSubscription sub = feedSubscriptionDAO.findByFeed(user, entry.getFeed());
        if (sub == null) {
            return;
        }

        List<FeedEntryTag> existingTags = feedEntryTagDAO.findByEntry(user, entry);
        Set<String> existingTagNames =
                existingTags.stream().map(FeedEntryTag::getName).collect(Collectors.toSet());

        List<FeedEntryTag> addList =
                tagNames.stream()
                        .filter(name -> !existingTagNames.contains(name))
                        .map(name -> new FeedEntryTag(user, entry, name))
                        .toList();
        List<FeedEntryTag> removeList =
                existingTags.stream().filter(tag -> !tagNames.contains(tag.getName())).toList();

        addList.forEach(feedEntryTagDAO::persist);
        feedEntryTagDAO.delete(removeList);
    }
}
