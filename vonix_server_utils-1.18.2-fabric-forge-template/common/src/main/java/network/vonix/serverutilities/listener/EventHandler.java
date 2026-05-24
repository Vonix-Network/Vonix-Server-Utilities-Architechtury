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
import network.vonix.serverutilities.teleport.TeleportManager;

/**
 * Registers Architectury lifecycle, command, and entity events.
 * All event registrations happen at mod init time.
 */
public final class EventHandler {

    public static void init() {

        // â”€â”€ Commands â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        CommandRegistrationEvent.EVENT.register((dispatcher, dedicated) -> {
            ModCommands.register(dispatcher);
            UtilityCommands.register(dispatcher);
            WorldCommands.register(dispatcher);
            VonixServerUtilities.LOGGER.info("[VonixSU] All commands registered.");
        });

        // â”€â”€ Server starting â€” load config & open DB â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        LifecycleEvent.SERVER_STARTING.register(server -> {
            ModConfig.INSTANCE.load(server.getServerDirectory().resolve("config"));
            // Pass server so the DB can locate the VonixCore DB for migration
            VonixServerUtilities.getInstance().getDatabase().init(server);
        });

        // â”€â”€ Server stopped â€” flush pending tasks then close DB â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            TeleportManager.getInstance().clear();
            AdminManager.getInstance().clear();
            // Shutdown executor first so queued DB writes finish before connection closes
            VonixServerUtilities.getInstance().shutdown();
            VonixServerUtilities.getInstance().getDatabase().close();
        });

        // â”€â”€ Player join â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        PlayerEvent.PLAYER_JOIN.register(player -> {
            UtilityCommands.onPlayerJoin(player.getUUID());
        });

        // â”€â”€ Player quit â€” clean up per-player in-memory state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        PlayerEvent.PLAYER_QUIT.register(player -> {
            UtilityCommands.onPlayerLeave(player.getUUID());
            TeleportManager.getInstance().clearPlayer(player.getUUID());
        });

        // â”€â”€ Death location tracking â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Saves the death position ONLY to the death-location store.
        // Does NOT call saveLastLocation() so /back history is never contaminated
        // by deaths â€” the two histories remain fully independent.
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

