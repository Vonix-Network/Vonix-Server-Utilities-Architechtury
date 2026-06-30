package network.vonix.serverutilities.moderation;

import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.donation_ranks.LuckPermsBridge;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of active mute UUIDs.
 *
 * Populated from the DB on server start ({@link #hydrateFromDb}) and kept in
 * sync by the service layer ({@link #add}/{@link #remove}) and the expiry
 * sweeper.
 *
 * Reads must be lock-free and fast — the chat-event handler calls
 * {@link #isMuted} every chat message.
 *
 * Bypass: {@code vsu.bypass.mute} (via LuckPerms when present). Players with
 * the bypass node are reported as not-muted even if they have an active row
 * in the DB. Intentional: the bypass overrides the cache, not the row, so
 * removing the node re-enforces the existing mute without an admin action.
 */
public final class MuteState {

    private static final String BYPASS_NODE = "vsu.bypass.mute";

    private static final Set<UUID> MUTED = ConcurrentHashMap.newKeySet();

    private MuteState() {}

    public static boolean isMuted(UUID uuid) {
        if (uuid == null) return false;
        if (!MUTED.contains(uuid)) return false;
        // Bypass check: LP-gated. If LP is absent, no bypass possible — mute stands.
        if (LuckPermsBridge.isPresent() && LuckPermsBridge.hasPermission(uuid, BYPASS_NODE)) {
            return false;
        }
        return true;
    }

    public static void add(UUID uuid) {
        if (uuid != null) MUTED.add(uuid);
    }

    public static void remove(UUID uuid) {
        if (uuid != null) MUTED.remove(uuid);
    }

    public static Set<UUID> snapshot() {
        return new HashSet<>(MUTED);
    }

    public static void clear() {
        MUTED.clear();
    }

    /**
     * Rehydrate the cache from the DB. Must be called from the DB executor.
     * Safe to call multiple times — fully replaces the current set.
     */
    public static void hydrateFromDb() {
        try {
            Set<UUID> next = new HashSet<>(PunishmentRepository.activeMuteUuids());
            MUTED.clear();
            MUTED.addAll(next);
            VonixServerUtilities.LOGGER.info("[VonixSU/mod] MuteState hydrated with {} active mutes", next.size());
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] MuteState hydrate failed", e);
        }
    }
}
