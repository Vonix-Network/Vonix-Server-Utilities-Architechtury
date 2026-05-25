package network.vonix.serverutilities.teleport;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 */
public final class TeleportManager {
    private static final TeleportManager INSTANCE = new TeleportManager();
    public static TeleportManager getInstance() { return INSTANCE; }

    /** TPA requests keyed by the TARGET player's UUID (the one who accepts/denies). */
    private final Map<UUID, TpaRequest> tpaRequests   = new ConcurrentHashMap<>();
    /** Last teleport origin per player â€” updated only by teleportPlayer(). */
    private final Map<UUID, Location>   lastLocations  = new ConcurrentHashMap<>();
    /** Last death location per player â€” updated only by the death event. */
    private final Map<UUID, Location>   deathLocations = new ConcurrentHashMap<>();

    // â”€â”€ Location snapshots â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Record player's current position as their last teleport origin. */
    public void saveLastLocation(ServerPlayer player) {
        lastLocations.put(player.getUUID(), snapshot(player));
    }

    /** Record player's current position as their last death location. */
    public void saveDeathLocation(ServerPlayer player) {
        deathLocations.put(player.getUUID(), snapshot(player));
    }

    private static Location snapshot(ServerPlayer p) {
        return new Location(
                p.level.dimension().location().toString(),
                p.getX(), p.getY(), p.getZ(),
                p.getYRot(), p.getXRot(),
                System.currentTimeMillis());
    }

    public Location getLastLocation(UUID uuid)  { return lastLocations.get(uuid); }
    public Location getDeathLocation(UUID uuid) { return deathLocations.get(uuid); }

    // â”€â”€ Teleport â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Teleport {@code player} to the given position. Saves the player's
     * current location as the /back target BEFORE moving them.
     */
    public void teleportPlayer(ServerPlayer player, ServerLevel level,
                               double x, double y, double z, float yaw, float pitch) {
        saveLastLocation(player);
        player.teleportTo(level, x, y, z, yaw, pitch);
    }

    // â”€â”€ TPA â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
            teleportPlayer(target, (ServerLevel) requester.level,
                    requester.getX(), requester.getY(), requester.getZ(),
                    requester.getYRot(), requester.getXRot());
            requester.sendMessage(new net.minecraft.network.chat.TextComponent(
                    "Â§a[VSU] " + target.getName().getString() + " teleported to you."), net.minecraft.Util.NIL_UUID);
        } else {
            // Requester asked to go to target
            teleportPlayer(requester, (ServerLevel) target.level,
                    target.getX(), target.getY(), target.getZ(),
                    target.getYRot(), target.getXRot());
            requester.sendMessage(new net.minecraft.network.chat.TextComponent(
                    "Â§a[VSU] " + target.getName().getString() + " accepted your teleport request."), net.minecraft.Util.NIL_UUID);
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
            requester.sendMessage(new net.minecraft.network.chat.TextComponent(
                    "Â§c[VSU] " + target.getName().getString() + " denied your teleport request."), net.minecraft.Util.NIL_UUID);
        }
        return true;
    }

    // â”€â”€ Lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Clear all in-memory state. Call on server shutdown. */
    public void clear() {
        tpaRequests.clear();
        lastLocations.clear();
        deathLocations.clear();
    }

    /** Clear in-memory state for a single player. Call on player disconnect. */
    public void clearPlayer(UUID uuid) {
        tpaRequests.remove(uuid);
        lastLocations.remove(uuid);
        deathLocations.remove(uuid);
    }

    // â”€â”€ Records â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

