package dev.bgame.lanplus.announcements;

import dev.bgame.lanplus.api.Announcement;

import java.util.List;

public interface AnnouncementsService {

    List<Announcement> announcements();

    int unseenCount();

    void refresh();

    void markSeen(List<Integer> ids);

    void connect();

    void addListener(AnnouncementsListener listener);

    void removeListener(AnnouncementsListener listener);

    interface AnnouncementsListener {
        void onAnnouncementsChanged(List<Announcement> announcements);

        default void onNewAnnouncement(Announcement announcement) {
        }
    }
}