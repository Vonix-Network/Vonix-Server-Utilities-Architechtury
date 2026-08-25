package network.vonix.serverutilities.listener;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(EventHandler.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(CratePlaytimeTask.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(PlayerSyncTask.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ServerConfigClient.class);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        CrateCommands.register(event.getDispatcher());
        UtilityCommands.register(event.getDispatcher());
        WorldCommands.register(event.getDispatcher());
        LinkCommands.register(event.getDispatcher());
        VonixServerUtilities.LOGGER.info("[VSU] All commands registered.");
    }

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        var server = event.getServer();
        ModConfig.INSTANCE.load(server.getServerDirectory().resolve("config"));
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
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        try { RankGroupSyncer.syncAll(); }
        catch (Throwable throwable) { VonixServerUtilities.LOGGER.warn("[VSU] RankGroupSyncer threw: {}", throwable.getMessage()); }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        TeleportManager.getInstance().clear();
        AdminManager.getInstance().clear();
        PlayerSyncTask.clear();
        CratePlaytimeTask.clear();
        LinkCommands.clearCooldowns();
        VenaryClient venary = VenaryClient.get();
        if (venary != null) venary.shutdown();
        VonixServerUtilities.getInstance().shutdown();
        VonixServerUtilities.getInstance().getDatabase().close();
    }

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UtilityCommands.onPlayerJoin(player);
        PlayerSyncTask.onPlayerJoin(player);
        RankSyncTask.onJoin(player);
    }

    @SubscribeEvent
    public static void quit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UtilityCommands.onPlayerLeave(player.getUUID());
        TeleportManager.getInstance().clearPlayer(player.getUUID());
        PlayerSyncTask.onPlayerLeave(player.getUUID());
        LinkCommands.onPlayerLeave(player.getUUID());
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TeleportManager.getInstance().saveDeathLocation(player);
    }
}
