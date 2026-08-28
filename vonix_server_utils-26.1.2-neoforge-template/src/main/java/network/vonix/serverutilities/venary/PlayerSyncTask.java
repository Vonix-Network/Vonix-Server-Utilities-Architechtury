package network.vonix.serverutilities.venary;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Periodically pushes a per-player stats snapshot to Venary
 * ({@code POST /minecraft/players/sync}).
 *
 * <p>Driven by {@link TickEvent.Server#SERVER_POST}. A tick counter is used
 * (rather than wall-clock) so a paused / lagging server doesn't burst-fire
 * sync requests once it catches up.
 *
 * <p>Gated by {@link VenaryConfig#isStatsSyncEnabled()}. When disabled the
 * whole task is a single int-increment-and-bail per tick — effectively free.
 *
 * <p>PORT-NOTE: Architectury {@code TickEvent.Server.SERVER_POST} exists on
 * all four target MC versions with the same signature. No port changes needed.
 */
public final class PlayerSyncTask {

    /** 20 ticks per second, 60 seconds per minute. */
    private static final int TICKS_PER_MINUTE = 20 * 60;

    /** Per-player session-start tick counter (resets on join). */
    private static final ConcurrentMap<UUID, Long> sessionStartTick = new ConcurrentHashMap<>();

    private static int tickCounter = 0;
    private static long lastFlushServerTicks = -1L;

    private PlayerSyncTask() {}

    public static void register() {}

    /** Called from EventHandler player-join. */
    public static void onPlayerJoin(ServerPlayer player) {
        sessionStartTick.put(player.getUUID(), (long) player.level().getServer().getTickCount());
    }

    /** Called from EventHandler player-leave. */
    public static void onPlayerLeave(UUID uuid) {
        sessionStartTick.remove(uuid);
    }

    public static void onServerTick(MinecraftServer server) {
        VenaryClient client = VenaryClient.get();
        if (client == null) return;
        VenaryConfig cfg = client.getConfig();
        if (!cfg.isEnabled() || !cfg.isStatsSyncEnabled()) {
            // Reset so the next enable doesn't immediately fire.
            tickCounter = 0;
            return;
        }

        tickCounter++;
        int interval = cfg.getStatsSyncIntervalMinutes() * TICKS_PER_MINUTE;
        if (tickCounter < interval) return;
        tickCounter = 0;

        long nowTicks = server.getTickCount();
        long lastFlush = lastFlushServerTicks;
        lastFlushServerTicks = nowTicks;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID uuid = p.getUUID();
            String username = p.getName().getString();

            long sessionStart = sessionStartTick.getOrDefault(uuid, nowTicks);
            long sessionTicks = Math.max(0L, nowTicks - sessionStart);
            long deltaTicks   = (lastFlush < 0) ? sessionTicks : Math.max(0L, nowTicks - Math.max(lastFlush, sessionStart));

            Map<String, Long> stats = new HashMap<>();
            stats.put("playtime_ticks",        sessionTicks);
            stats.put("playtime_ticks_delta",  deltaTicks);
            // TODO(operator-review): add health/xp/deaths once we agree on the schema with Venary.

            try {
                client.syncPlayer(uuid, username, stats).exceptionally(t -> {
                    // VenaryClient is already fail-open; this is paranoia.
                    return null;
                });
            } catch (Exception e) {
                VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] syncPlayer threw for {}: {}", username, e.getMessage());
            }
        }
    }

    public static void clear() {
        sessionStartTick.clear();
        tickCounter = 0;
        lastFlushServerTicks = -1L;
    }
}
