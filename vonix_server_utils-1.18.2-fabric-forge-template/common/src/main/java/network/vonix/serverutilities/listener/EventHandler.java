package network.vonix.serverutilities.listener;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.admin.AdminManager;
import network.vonix.serverutilities.command.ModCommands;
import network.vonix.serverutilities.command.UtilityCommands;
import network.vonix.serverutilities.command.WorldCommands;
import network.vonix.serverutilities.config.ModConfig;
import network.vonix.serverutilities.donation_ranks.RankGroupSyncer;
import network.vonix.serverutilities.donation_ranks.RankSyncTask;
import network.vonix.serverutilities.features.FeatureRegistry;
import network.vonix.serverutilities.features.ServerConfigClient;
import network.vonix.serverutilities.teleport.TeleportManager;
import network.vonix.serverutilities.venary.LinkCommands;
import network.vonix.serverutilities.venary.PlayerSyncTask;
import network.vonix.serverutilities.venary.VenaryClient;

/**
 * Registers Architectury lifecycle, command, and entity events.
 * All event registrations happen at mod init time.
 */
public final class EventHandler {

    public static void init() {

        // ── Commands ──────────────────────────────────────────────────────────
        CommandRegistrationEvent.EVENT.register((dispatcher, selection) -> {
            ModCommands.register(dispatcher);
            UtilityCommands.register(dispatcher);
            WorldCommands.register(dispatcher);
            LinkCommands.register(dispatcher);
            VonixServerUtilities.LOGGER.info("[VonixSU] All commands registered.");
        });

        // ── Server starting — load config & open DB ───────────────────────────
        LifecycleEvent.SERVER_STARTING.register(server -> {
            ModConfig.INSTANCE.load(server.getServerDirectory().toPath().resolve("config"));
            // Pass server so the DB can locate the VonixCore DB for migration
            VonixServerUtilities.getInstance().getDatabase().init(server);
            // Rehydrate persisted caches on the DB executor so we don't block the main thread.
            VonixServerUtilities.dbAsync(() -> {
                TeleportManager.getInstance().hydrateFromDb();
                UtilityCommands.hydrateFromDb();
            });
            // Load (or seed default) kit definitions from kits.json.
            VonixServerUtilities.dbAsync(() ->
                    network.vonix.serverutilities.kits.KitManager.getInstance()
                            .loadFromJson(server));
            // Bring up the Venary HTTP layer using the freshly-loaded config.
            // Even when disabled this is cheap; the client itself short-circuits.
            VenaryClient.init(ModConfig.INSTANCE.getVenaryConfig());
            PlayerSyncTask.register();
            // Feature-flag registry: first call runs the first-run heuristic
            // against the (now-open) SQLite DB, then the poller takes over.
            FeatureRegistry.getInstance();
            ServerConfigClient.startPolling();
        });

        // ── Server started — LP service is now registered, sync donation groups ──
        LifecycleEvent.SERVER_STARTED.register(server -> {
            // RankGroupSyncer is a no-op if LuckPerms is absent or
            // donation_ranks is empty — safe to call unconditionally.
            try { RankGroupSyncer.syncAll(); }
            catch (Throwable t) {
                VonixServerUtilities.LOGGER.warn("[VonixSU] RankGroupSyncer threw: {}", t.getMessage());
            }
        });

        // ── Server stopped — flush pending tasks then close DB ────────────────
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            TeleportManager.getInstance().clear();
            AdminManager.getInstance().clear();
            PlayerSyncTask.clear();
            LinkCommands.clearCooldowns();
            VenaryClient venary = VenaryClient.get();
            if (venary != null) venary.shutdown();
            // Shutdown executor first so queued DB writes finish before connection closes
            VonixServerUtilities.getInstance().shutdown();
            VonixServerUtilities.getInstance().getDatabase().close();
        });

        // ── Player join ───────────────────────────────────────────────────────
        PlayerEvent.PLAYER_JOIN.register(player -> {
            UtilityCommands.onPlayerJoin(player);
            PlayerSyncTask.onPlayerJoin(player);
            RankSyncTask.onJoin(player);
        });

        // ── Player quit — clean up per-player in-memory state ─────────────────
        PlayerEvent.PLAYER_QUIT.register(player -> {
            UtilityCommands.onPlayerLeave(player.getUUID());
            TeleportManager.getInstance().clearPlayer(player.getUUID());
            PlayerSyncTask.onPlayerLeave(player.getUUID());
            LinkCommands.onPlayerLeave(player.getUUID());
        });

        // ── Death location tracking ───────────────────────────────────────────
        // Saves the death position ONLY to the death-location store.
        // Does NOT call saveLastLocation() so /back history is never contaminated
        // by deaths — the two histories remain fully independent.
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                TeleportManager.getInstance().saveDeathLocation(player);
                VonixServerUtilities.LOGGER.debug("[VonixSU] Death location saved for {}",
                        player.getName().getString());
            }
            return EventResult.pass();
        });
    }
}
