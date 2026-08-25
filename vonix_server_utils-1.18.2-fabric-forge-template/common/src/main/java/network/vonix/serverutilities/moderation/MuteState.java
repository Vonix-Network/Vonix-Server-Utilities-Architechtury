package network.vonix.serverutilities.moderation;

import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.donation_ranks.LuckPermsBridge;

import java.util.HashSet;
import java.util.Map;
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
 * Reads are lock-free and fast — the chat-event handler calls
 * {@link #isMuted} every chat message. Cache replacement is serialized with
 * service mutations so startup hydration cannot erase a newly-issued mute.
 *
 * Bypass: {@code vsu.bypass.mute} (via LuckPerms when present). Players with
 * the bypass node are reported as not-muted even if they have an active row
 * in the DB. Intentional: the bypass overrides the cache, not the row, so
 * removing the node re-enforces the existing mute without an admin action.
 */
public final class MuteState {

    private static final String BYPASS_NODE = "vsu.bypass.mute";

    private static final Set<UUID> MUTED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> PENDING_PERSISTENCE = new ConcurrentHashMap<>();
    private static final Set<UUID> PERSISTED_ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Object MUTATION_LOCK = new Object();
    private static volatile boolean HYDRATION_COMPLETE = false;

    private MuteState() {}

    public static boolean isMuted(UUID uuid) {
        if (uuid == null) return false;
        // Until the first successful DB snapshot, an empty cache is ambiguous.
        // Fail closed so a hydration outage cannot silently unmute players.
        if (!HYDRATION_COMPLETE) return true;
        if (!MUTED.contains(uuid)) return false;
        // Bypass check: LP-gated. If LP is absent, no bypass possible — mute stands.
        if (LuckPermsBridge.isPresent() && LuckPermsBridge.hasPermission(uuid, BYPASS_NODE)) {
            return false;
        }
        return true;
    }

    public static void add(UUID uuid) {
        if (uuid != null) synchronized (MUTATION_LOCK) { MUTED.add(uuid); }
    }

    /** Apply a mute before its asynchronous persistence task runs. */
    public static void addPending(UUID uuid) {
        if (uuid == null) return;
        synchronized (MUTATION_LOCK) {
            MUTED.add(uuid);
            PENDING_PERSISTENCE.merge(uuid, 1, Integer::sum);
        }
    }

    /** Mark one optimistic mute as durably persisted. */
    public static void markPersisted(UUID uuid) {
        if (uuid == null) return;
        synchronized (MUTATION_LOCK) {
            decrementPending(uuid);
            PERSISTED_ACTIVE.add(uuid);
            MUTED.add(uuid);
        }
    }

    /** Reconcile enforcement after a persisted row is revoked or expires. */
    public static void reconcilePersisted(UUID uuid, boolean hasActiveRow) {
        if (uuid == null) return;
        synchronized (MUTATION_LOCK) {
            if (hasActiveRow) {
                PERSISTED_ACTIVE.add(uuid);
                MUTED.add(uuid);
            } else {
                PERSISTED_ACTIVE.remove(uuid);
                if (!PENDING_PERSISTENCE.containsKey(uuid)) MUTED.remove(uuid);
            }
        }
    }

    /** Roll back one failed optimistic insert without removing other mutes. */
    public static void clearPending(UUID uuid) {
        if (uuid == null) return;
        synchronized (MUTATION_LOCK) {
            decrementPending(uuid);
            if (!PENDING_PERSISTENCE.containsKey(uuid) && !PERSISTED_ACTIVE.contains(uuid)) {
                MUTED.remove(uuid);
            }
        }
    }

    private static void decrementPending(UUID uuid) {
        PENDING_PERSISTENCE.computeIfPresent(uuid, (ignored, count) -> count > 1 ? count - 1 : null);
    }

    public static void remove(UUID uuid) {
        if (uuid != null) synchronized (MUTATION_LOCK) {
            MUTED.remove(uuid);
            PENDING_PERSISTENCE.remove(uuid);
            PERSISTED_ACTIVE.remove(uuid);
        }
    }

    public static Set<UUID> snapshot() {
        synchronized (MUTATION_LOCK) { return new HashSet<>(MUTED); }
    }

    public static void clear() {
        synchronized (MUTATION_LOCK) {
            MUTED.clear();
            PENDING_PERSISTENCE.clear();
            PERSISTED_ACTIVE.clear();
            HYDRATION_COMPLETE = false;
        }
    }

    static void markHydrationComplete() {
        synchronized (MUTATION_LOCK) { HYDRATION_COMPLETE = true; }
    }

    /**
     * Rehydrate the cache from the DB. Must be called from the DB executor.
     * Safe to call multiple times. The DB snapshot is merged under the same
     * lock used by service mutations, preserving mutes issued while the query
     * was in flight.
     */
    public static void hydrateFromDb() {
        try {
            synchronized (MUTATION_LOCK) {
                // Keep the DB snapshot and cache reconciliation in the same
                // critical section. Otherwise a persistence completion between
                // the query and merge can be erased by this hydration pass.
                Set<UUID> next = new HashSet<>(PunishmentRepository.activeMuteUuids());
                PERSISTED_ACTIVE.retainAll(next);
                PERSISTED_ACTIVE.addAll(next);
                next.addAll(PENDING_PERSISTENCE.keySet());
                MUTED.retainAll(next);
                MUTED.addAll(next);
                HYDRATION_COMPLETE = true;
                VonixServerUtilities.LOGGER.info("[VonixSU/mod] MuteState hydrated with {} active mutes", next.size());
            }
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] MuteState hydrate failed", e);
        }
    }
}
