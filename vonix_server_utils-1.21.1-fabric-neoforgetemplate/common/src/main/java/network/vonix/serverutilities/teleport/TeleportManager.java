package network.vonix.serverutilities.teleport;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.config.ModConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages TPA requests and per-player location history.
 *
 * Back/death separation guarantee:
 *  - {@link #saveLastLocation} is called automatically inside {@link #teleportPlayer}
 *    and records only teleport-based movement. It is NEVER called on death.
 *  - {@link #saveDeathLocation} is called only by the death event listener and stores
 *    the death spot in a separate map. It does NOT touch lastLocations.
 *
 * This means /back always returns to the last *teleport* origin, while /backdeath
 * always returns to the last death spot, and neither can overwrite the other.
 *
 * Both stores are write-through cached: the in-memory map answers reads
 * synchronously while every mutation is also persisted to vsu_back_locations
 * on the DB executor thread. {@link #hydrateFromDb()} repopulates the cache at
 * server start so /back and /backdeath survive restarts.
 */
public final class TeleportManager {
    private static final TeleportManager INSTANCE = new TeleportManager();
    public static TeleportManager getInstance() { return INSTANCE; }

    private static final String KIND_TP    = "tp";
    private static final String KIND_DEATH = "death";

    /** TPA requests keyed by the TARGET player's UUID (the one who accepts/denies). */
    private final Map<UUID, TpaRequest> tpaRequests   = new ConcurrentHashMap<>();
    /** Last teleport origin per player — updated only by teleportPlayer(). */
    private final Map<UUID, Location>   lastLocations  = new ConcurrentHashMap<>();
    /** Last death location per player — updated only by the death event. */
    private final Map<UUID, Location>   deathLocations = new ConcurrentHashMap<>();

    // ── Location snapshots ────────────────────────────────────────────────────

    /** Record player's current position as their last teleport origin. */
    public void saveLastLocation(ServerPlayer player) {
        Location loc = snapshot(player);
        lastLocations.put(player.getUUID(), loc);
        persist(player.getUUID(), loc, KIND_TP);
    }

    /** Record player's current position as their last death location. */
    public void saveDeathLocation(ServerPlayer player) {
        Location loc = snapshot(player);
        deathLocations.put(player.getUUID(), loc);
        persist(player.getUUID(), loc, KIND_DEATH);
    }

    private static void persist(UUID uuid, Location loc, String kind) {
        VonixServerUtilities.dbAsync(() ->
                VonixServerUtilities.getInstance().getDatabase().setBackLocation(
                        uuid, loc.world(), loc.x(), loc.y(), loc.z(),
                        loc.yaw(), loc.pitch(), kind));
    }

    /**
     * Rehydrate the in-memory caches from vsu_back_locations.
     * Call once per server start, from the DB executor thread.
     */
    public void hydrateFromDb() {
        try {
            for (Object[] row : VonixServerUtilities.getInstance().getDatabase().getAllBackLocations()) {
                UUID uuid    = UUID.fromString((String) row[0]);
                String world = (String) row[1];
                double x = (Double) row[2], y = (Double) row[3], z = (Double) row[4];
                float yaw = (Float) row[5], pitch = (Float) row[6];
                String kind = (String) row[7];
                long ts     = (Long) row[8];
                Location loc = new Location(world, x, y, z, yaw, pitch, ts);
                if (KIND_DEATH.equals(kind)) deathLocations.put(uuid, loc);
                else                          lastLocations.put(uuid, loc);
            }
            VonixServerUtilities.LOGGER.info(
                    "[VonixSU] Hydrated {} /back + {} /backdeath locations from DB.",
                    lastLocations.size(), deathLocations.size());
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] hydrateFromDb failed", e);
        }
    }

    private static Location snapshot(ServerPlayer p) {
        return new Location(
                p.level().dimension().location().toString(),
                p.getX(), p.getY(), p.getZ(),
                p.getYRot(), p.getXRot(),
                System.currentTimeMillis());
    }

    public Location getLastLocation(UUID uuid)  { return lastLocations.get(uuid); }
    public Location getDeathLocation(UUID uuid) { return deathLocations.get(uuid); }

    // ── Teleport ──────────────────────────────────────────────────────────────

    /**
     * Teleport {@code player} to the given position. Saves the player's
     * current location as the /back target BEFORE moving them.
     */
    public void teleportPlayer(ServerPlayer player, ServerLevel level,
                               double x, double y, double z, float yaw, float pitch) {
        saveLastLocation(player);
        player.teleportTo(level, x, y, z, yaw, pitch);
    }

    // ── TPA ───────────────────────────────────────────────────────────────────

    /**
     * Register a new TPA request from {@code requester} to {@code target}.
     * Replaces any expired request; returns false if a live request already exists.
     */
    public boolean sendTpaRequest(ServerPlayer requester, ServerPlayer target, boolean tpaHere) {
        UUID targetId = target.getUUID();
        TpaRequest existing = tpaRequests.get(targetId);
        if (existing != null && !existing.isExpired()) return false;
        tpaRequests.put(targetId, new TpaRequest(
                requester.getUUID(), requester.getName().getString(),
                tpaHere, System.currentTimeMillis()));
        return true;
    }

    /**
     * Accept the pending request aimed at {@code target}.
     * Executes the teleport and notifies both parties. Returns false if no
     * valid request exists or the requester is offline.
     */
    public boolean acceptTpaRequest(ServerPlayer target, MinecraftServer server) {
        TpaRequest req = tpaRequests.remove(target.getUUID());
        if (req == null || req.isExpired()) return false;

        ServerPlayer requester = server.getPlayerList().getPlayer(req.requesterUuid());
        if (requester == null) return false;

        if (req.tpaHere()) {
            // Requester asked target to come to them
            teleportPlayer(target, (ServerLevel) requester.level(),
                    requester.getX(), requester.getY(), requester.getZ(),
                    requester.getYRot(), requester.getXRot());
            requester.sendSystemMessage(Component.literal(
                    "§a[VSU] " + target.getName().getString() + " teleported to you."));
        } else {
            // Requester asked to go to target
            teleportPlayer(requester, (ServerLevel) target.level(),
                    target.getX(), target.getY(), target.getZ(),
                    target.getYRot(), target.getXRot());
            requester.sendSystemMessage(Component.literal(
                    "§a[VSU] " + target.getName().getString() + " accepted your teleport request."));
        }
        return true;
    }

    /**
     * Deny and remove the pending request aimed at {@code target}.
     * Notifies the requester if still online.
     */
    public boolean denyTpaRequest(ServerPlayer target, MinecraftServer server) {
        TpaRequest req = tpaRequests.remove(target.getUUID());
        if (req == null) return false;
        ServerPlayer requester = server.getPlayerList().getPlayer(req.requesterUuid());
        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                    "§c[VSU] " + target.getName().getString() + " denied your teleport request."));
        }
        return true;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Clear all in-memory state. Call on server shutdown. */
    public void clear() {
        tpaRequests.clear();
        lastLocations.clear();
        deathLocations.clear();
    }

    /** Clear in-memory state for a single player. Call on player disconnect.
     *  NOTE: back/death locations stay in lastLocations/deathLocations because
     *  they are also persisted to vsu_back_locations and will be re-hydrated
     *  on the next join; we only drop transient TPA state here.
     */
    public void clearPlayer(UUID uuid) {
        tpaRequests.remove(uuid);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record TpaRequest(UUID requesterUuid, String requesterName,
                             boolean tpaHere, long timestamp) {
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ModConfig.INSTANCE.getTpaTimeoutMs();
        }
    }

    /** Snapshot of a player's position at a specific moment in time. */
    public record Location(String world, double x, double y, double z,
                           float yaw, float pitch, long timestamp) {}
}
