package dev.bgame.lanplus.invites;

import dev.bgame.lanplus.api.HostAccessMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostAccessControlTest {

    private final UUID host = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void reset() {
        HostAccessControl.clear();
    }

    @Test
    void inactiveControlAllowsAnyone() {
        assertTrue(HostAccessControl.isAllowed(stranger));
        assertFalse(HostAccessControl.isActive());
        assertEquals(HostAccessMode.EVERYONE, HostAccessControl.mode());
        assertEquals(HostAccessControl.DEFAULT_MAX_PLAYERS, HostAccessControl.maxPlayers());
    }

    @Test
    void restrictedModeAllowsOnlyHostAndConfiguredPlayers() {
        HostAccessControl.set(HostAccessMode.FRIENDS, host, Set.of(friend), 4);

        assertTrue(HostAccessControl.isAllowed(host));
        assertTrue(HostAccessControl.isAllowed(friend));
        assertFalse(HostAccessControl.isAllowed(stranger));
        assertFalse(HostAccessControl.isAllowed(null));
        assertEquals(4, HostAccessControl.maxPlayers());
    }

    @Test
    void invitedModeAcceptsInvitationsAfterHostingStarts() {
        HostAccessControl.set(HostAccessMode.INVITED, host, Set.of(), 8);

        assertFalse(HostAccessControl.isAllowed(friend));
        HostAccessControl.invite(friend);
        assertTrue(HostAccessControl.isAllowed(friend));
    }

    @Test
    void everyoneModeDoesNotRetainUnnecessaryInvitations() {
        HostAccessControl.set(HostAccessMode.EVERYONE, host, Set.of(), 8);

        HostAccessControl.invite(friend);

        assertTrue(HostAccessControl.isAllowed(friend));
        assertTrue(HostAccessControl.allowedSnapshot().isEmpty());
    }

    @Test
    void allowedSnapshotCannotMutateAccessRules() {
        HostAccessControl.set(HostAccessMode.INVITED, host, Set.of(friend), 8);
        Set<UUID> snapshot = HostAccessControl.allowedSnapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(stranger));
        assertFalse(HostAccessControl.isAllowed(stranger));
    }

    @Test
    void gatedReflectsOfflineHostingSetting() {
        assertTrue(HostAccessControl.gated(true));
        assertFalse(HostAccessControl.gated(false));
    }
}