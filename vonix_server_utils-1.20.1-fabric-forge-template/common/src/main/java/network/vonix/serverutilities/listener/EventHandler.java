package network.vonix.serverutilities.listener;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.admin.AdminManager;
import network.vonix.serverutilities.command.CrateCommands;
import network.vonix.serverutilities.command.ModCommands;
import network.vonix.serverutilities.command.UtilityCommands;
import network.vonix.serverutilities.command.WorldCommands;
import network.vonix.serverutilities.config.ModConfig;
import network.vonix.serverutilities.crates.CratePlaytimeTask;
import network.vonix.serverutilities.donation_ranks.RankGroupSyncer;
import network.vonix.serverutilities.donation_ranks.RankSyncTask;
import network.vonix.serverutilities.features.FeatureRegistry;
import network.vonix.serverutilities.features.ServerConfigClient;
import network.vonix.serverutilities.moderation.ModerationBootstrap;
import network.vonix.serverutilities.teleport.TeleportManager;
import network.vonix.serverutilities.venary.LinkCommands;
import network.vonix.serverutilities.venary.PlayerSyncTask;
import network.vonix.serverutilities.venary.VenaryClient;

/** Registers shared VSU lifecycle, commands, and persistence hooks. */
public final class EventHandler {
    private EventHandler() {}

    public static void init() {
        ModerationBootstrap.init();
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            ModCommands.register(dispatcher);
            CrateCommands.register(dispatcher);
            UtilityCommands.register(dispatcher);
            WorldCommands.register(dispatcher);
            LinkCommands.register(dispatcher);
            VonixServerUtilities.LOGGER.info("[VSU] All commands registered.");
        });

        LifecycleEvent.SERVER_STARTING.register(server -> {
            ModConfig.INSTANCE.load(server.getServerDirectory().toPath().resolve("config"));
            VonixServerUtilities.getInstance().getDatabase().init(server);
            VonixServerUtilities.dbAsync(() -> {
                try {
                    TeleportManager.getInstance().hydrateFromDb();
                    UtilityCommands.hydrateFromDb();
                    var manager = network.vonix.serverutilities.crates.CrateRepository.getInstance();
                    manager.ensureSchema(VonixServerUtilities.getInstance().getDatabase().getConnection());
                    manager.createCrate("playtime", "playtime");
                    manager.createCrate("event", "event");
                    int recovered = manager.recoverPendingClaims();
                    if (recovered > 0) VonixServerUtilities.LOGGER.warn("[VSU] Refunded {} pending crate claims after startup.", recovered);
                } catch (Exception exception) {
                    VonixServerUtilities.LOGGER.error("[VSU] Crate/key startup initialisation failed", exception);
                }
            });
            VonixServerUtilities.dbAsync(() -> network.vonix.serverutilities.kits.KitManager.getInstance().loadFromJson(server));
            VenaryClient.init(ModConfig.INSTANCE.getVenaryConfig());
            PlayerSyncTask.register();
            CratePlaytimeTask.register();
            FeatureRegistry.getInstance();
            ServerConfigClient.startPolling();
        });

        LifecycleEvent.SERVER_STARTED.register(server -> {
            try { RankGroupSyncer.syncAll(); }
            catch (Throwable throwable) { VonixServerUtilities.LOGGER.warn("[VSU] RankGroupSyncer threw: {}", throwable.getMessage()); }
        });

        LifecycleEvent.SERVER_STOPPED.register(server -> {
            TeleportManager.getInstance().clear();
            AdminManager.getInstance().clear();
            PlayerSyncTask.clear();
            CratePlaytimeTask.clear();
            LinkCommands.clearCooldowns();
            VenaryClient venary = VenaryClient.get();
            if (venary != null) venary.shutdown();
            VonixServerUtilities.getInstance().shutdown();
            VonixServerUtilities.getInstance().getDatabase().close();
        });

        PlayerEvent.PLAYER_JOIN.register(player -> {
            UtilityCommands.onPlayerJoin(player);
            PlayerSyncTask.onPlayerJoin(player);
            RankSyncTask.onJoin(player);
        });
        PlayerEvent.PLAYER_QUIT.register(player -> {
            UtilityCommands.onPlayerLeave(player.getUUID());
            TeleportManager.getInstance().clearPlayer(player.getUUID());
            PlayerSyncTask.onPlayerLeave(player.getUUID());
            LinkCommands.onPlayerLeave(player.getUUID());
        });
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) TeleportManager.getInstance().saveDeathLocation(player);
            return EventResult.pass();
        });
    }
}
