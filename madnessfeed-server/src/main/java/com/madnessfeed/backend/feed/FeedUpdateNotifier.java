package com.madnessfeed.backend.feed;

import com.madnessfeed.MadnessFeedConfiguration;
import com.madnessfeed.backend.dao.UnitOfWork;
import com.madnessfeed.backend.dao.UserSettingsDAO;
import com.madnessfeed.backend.model.FeedEntry;
import com.madnessfeed.backend.model.FeedSubscription;
import com.madnessfeed.backend.model.UserSettings;
import com.madnessfeed.backend.service.PushNotificationService;
import com.madnessfeed.frontend.ws.WebSocketMessageBuilder;
import com.madnessfeed.frontend.ws.WebSocketSessions;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class FeedUpdateNotifier {

    private final MadnessFeedConfiguration config;
    private final UnitOfWork unitOfWork;
    private final UserSettingsDAO userSettingsDAO;
    private final WebSocketSessions webSocketSessions;
    private final PushNotificationService pushNotificationService;

    public void notifyOverWebsocket(FeedSubscription sub, List<FeedEntry> entries) {
        if (!entries.isEmpty()) {
            webSocketSessions.sendMessage(
                    sub.getUser(), WebSocketMessageBuilder.newFeedEntries(sub, entries.size()));
        }
    }

    public void sendPushNotifications(FeedSubscription sub, List<FeedEntry> entries) {
        if (!config.pushNotifications().enabled()
                || !sub.isPushNotificationsEnabled()
                || entries.isEmpty()) {
            return;
        }

        UserSettings settings = unitOfWork.call(() -> userSettingsDAO.findByUser(sub.getUser()));
        if (settings != null
                && settings.getPushNotifications() != null
                && settings.getPushNotifications().getType() != null) {
            for (FeedEntry entry : entries) {
                pushNotificationService.notify(settings.getPushNotifications(), sub, entry);
            }
        }
    }
}
