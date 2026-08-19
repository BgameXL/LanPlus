package dev.bgame.lanplus.announcements;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.Announcement;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.network.LanPlusNetwork;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public final class DefaultAnnouncementsService
        implements AnnouncementsService, LanPlusNetwork.BackendEventListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final LanPlusNetwork network;
    private final Supplier<PlayerIdentity> identity;
    private final List<AnnouncementsListener> listeners = new CopyOnWriteArrayList<>();
    private volatile List<Announcement> cache = List.of();
    private volatile int unseen;

    public DefaultAnnouncementsService(LanPlusNetwork network, Supplier<PlayerIdentity> identity) {
        this.network = network;
        this.identity = identity;
    }

    @Override
    public List<Announcement> announcements() {
        return cache;
    }

    @Override
    public int unseenCount() {
        return unseen;
    }

    @Override
    public void refresh() {
        if (localUuid() == null) {
            return;
        }
        network.getAnnouncements().thenAccept(list -> {
            cache = list;
            notifyChanged();
        });
        network.getUnseenAnnouncements().thenAccept(list -> {
            unseen = list.size();
            notifyChanged();
        });
    }

    @Override
    public void markSeen(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        network.markAnnouncementsSeen(ids);
        unseen = 0;
        notifyChanged();
    }

    @Override
    public void connect() {
        UUID uuid = localUuid();
        if (uuid == null) {
            return;
        }
        refresh();
        network.connectEvents(uuid, this);
    }

    @Override
    public void onConnected() {
        refresh();
    }

    @Override
    public void onAnnouncement(Announcement announcement) {
        if (announcement == null) {
            return;
        }
        List<Announcement> next = new ArrayList<>();
        next.add(announcement);
        for (Announcement a : cache) {
            if (a.id() != announcement.id()) {
                next.add(a);
            }
        }
        cache = List.copyOf(next);
        unseen++;
        notifyChanged();
        for (AnnouncementsListener listener : listeners) {
            try {
                listener.onNewAnnouncement(announcement);
            } catch (RuntimeException e) {
                LOGGER.warn("announcements listener error", e);
            }
        }
    }

    @Override
    public void onAnnouncementDeleted(int id) {
        List<Announcement> next = new ArrayList<>();
        for (Announcement a : cache) {
            if (a.id() != id) {
                next.add(a);
            }
        }
        if (next.size() == cache.size()) {
            return;
        }
        cache = List.copyOf(next);
        unseen = Math.min(unseen, cache.size());
        notifyChanged();
    }

    @Override
    public void addListener(AnnouncementsListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(AnnouncementsListener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        List<Announcement> snapshot = cache;
        for (AnnouncementsListener listener : listeners) {
            try {
                listener.onAnnouncementsChanged(snapshot);
            } catch (RuntimeException e) {
                LOGGER.warn("announcements error", e);
            }
        }
    }

    private UUID localUuid() {
        PlayerIdentity id = identity.get();
        return id == null ? null : id.uuid();
    }
}