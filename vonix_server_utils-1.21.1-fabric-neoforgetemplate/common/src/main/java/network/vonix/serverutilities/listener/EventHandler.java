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

        // ── Commands ──────────────────────────────────────────────────────────
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            ModCommands.register(dispatcher);
            UtilityCommands.register(dispatcher);
            WorldCommands.register(dispatcher);
            VonixServerUtilities.LOGGER.info("[VonixSU] All commands registered.");
        });

        // ── Server starting — load config & open DB ───────────────────────────
        LifecycleEvent.SERVER_STARTING.register(server -> {
            ModConfig.INSTANCE.load(server.getServerDirectory().resolve("config"));
            // Pass server so the DB can locate the VonixCore DB for migration
            VonixServerUtilities.getInstance().getDatabase().init(server);
        });

        // ── Server stopped — flush pending tasks then close DB ────────────────
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            TeleportManager.getInstance().clear();
            AdminManager.getInstance().clear();
            // Shutdown executor first so queued DB writes finish before connection closes
            VonixServerUtilities.getInstance().shutdown();
            VonixServerUtilities.getInstance().getDatabase().close();
        });

        // ── Player join ───────────────────────────────────────────────────────
        PlayerEvent.PLAYER_JOIN.register(player -> {
            UtilityCommands.onPlayerJoin(player.getUUID());
        });

        // ── Player quit — clean up per-player in-memory state ─────────────────
        PlayerEvent.PLAYER_QUIT.register(player -> {
            UtilityCommands.onPlayerLeave(player.getUUID());
            TeleportManager.getInstance().clearPlayer(player.getUUID());
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
