package network.vonix.serverutilities.features;

import com.google.gson.JsonObject;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.venary.VenaryClient;
import network.vonix.serverutilities.donation_ranks.RankGroupSyncer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls {@code GET /minecraft/server-config} on a fixed cadence and applies
 * any change to {@link FeatureRegistry}, then refreshes the command tree for
 * every online player so disabled commands disappear (and re-enabled ones
 * reappear) without a relog.
 *
 * <p>Fail-open: on network errors we keep the last-known config. We never
 * crash the tick thread; all HTTP runs on the VenaryClient executor.
 *
 * <p>PORT-NOTE: Architectury {@link TickEvent.Server#SERVER_POST} exists
 * unchanged on 1.18.2 / 1.19.2 / 1.20.1 / 1.21.1.
 */
public final class ServerConfigClient {

    /** 20 ticks per second. */
    private static final int TICKS_PER_SECOND = 20;
    /** Poll cadence (seconds). 60s matches the README. */
    private static final int POLL_INTERVAL_SECONDS = 60;
    /** First-poll delay so we don't fire while the server is still booting. */
    private static final int INITIAL_DELAY_TICKS = TICKS_PER_SECOND * 5;

    private static int tickCounter = 0;
    private static final AtomicBoolean inFlight = new AtomicBoolean(false);
    private static volatile boolean registered = false;
    /** Set by /vonixsu feature reload to force the next tick to fetch. */
    private static volatile boolean forceFetch = false;

    private ServerConfigClient() {}

    public static void startPolling() {
        if (registered) return;
        registered = true;
        TickEvent.SERVER_POST.register(ServerConfigClient::onTick);
        VonixServerUtilities.LOGGER.info("[VonixSU] Feature-flag poller registered (every {}s).", POLL_INTERVAL_SECONDS);
    }

    /** Forces the next tick to issue a fetch, regardless of interval. */
    public static void requestImmediateFetch() {
        forceFetch = true;
    }

    private static void onTick(MinecraftServer server) {
        VenaryClient client = VenaryClient.get();
        if (client == null) return;
        if (!client.getConfig().isEnabled()) return;

        tickCounter++;
        int interval = POLL_INTERVAL_SECONDS * TICKS_PER_SECOND;
        boolean tickFire  = tickCounter >= interval;
        boolean firstFire = tickCounter == INITIAL_DELAY_TICKS && !FeatureRegistry.getInstance().isHydratedFromBackend();
        if (!forceFetch && !tickFire && !firstFire) return;
        if (tickFire) tickCounter = 0;
        forceFetch = false;

        if (!inFlight.compareAndSet(false, true)) return;
        client.getServerConfig().handle((resp, err) -> {
            try {
                if (resp == null) return null;
                int previousVersion = FeatureRegistry.getInstance().getConfigVersion();
                int incomingVersion = resp.has("config_version") ? resp.get("config_version").getAsInt() : previousVersion;
                if (previousVersion == incomingVersion && FeatureRegistry.getInstance().isHydratedFromBackend()) {
                    return null; // no-op, already up to date
                }
                FeatureRegistry.Delta delta = FeatureRegistry.getInstance().update(resp);
                logDelta(delta, previousVersion, incomingVersion);
                // First successful hydration (or any donation-rank change) must
                // re-run the LP group syncer — SERVER_STARTED fires before the first
                // /server-config response, so syncAll() there sees an empty rank list.
                try { RankGroupSyncer.syncAll(); }
                catch (Throwable t) {
                    VonixServerUtilities.LOGGER.warn("[VonixSU] post-hydration RankGroupSyncer threw: {}", t.getMessage());
                }
                // Bounce back to the main thread to resend command trees.
                server.execute(() -> resendCommandTrees(server));
            } catch (Exception e) {
                VonixServerUtilities.LOGGER.debug("[VonixSU] /server-config apply failed: {}", e.getMessage());
            } finally {
                inFlight.set(false);
            }
            return null;
        });
    }

    private static void logDelta(FeatureRegistry.Delta delta, int prev, int now) {
        if (delta == null || delta.isEmpty()) {
            VonixServerUtilities.LOGGER.info("[VonixSU] Feature config refreshed (v{} → v{}).", prev, now);
            return;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : delta.changes.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
            first = false;
        }
        VonixServerUtilities.LOGGER.info("[VonixSU] Feature flags updated ({}).", sb);
    }

    private static void resendCommandTrees(MinecraftServer server) {
        // PORT-NOTE: server.getCommands().sendCommands(player) exists on
        // 1.18.2 / 1.19.2 / 1.20.1 / 1.21.1 with this exact signature.
        try {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(p);
            }
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.debug("[VonixSU] Failed to resend command trees: {}", e.getMessage());
        }
    }

    public static void reset() {
        tickCounter = 0;
        inFlight.set(false);
        forceFetch = false;
    }
}
