package network.vonix.serverutilities.moderation;

import network.vonix.serverutilities.VonixServerUtilities;

/**
 * Wires the moderation subsystem into the Architectury event bus.
 *
 * Call exactly once from the common entry point (see {@link
 * network.vonix.serverutilities.listener.EventHandler}). Idempotent.
 *
 * Responsibilities:
 *   - register the 11 moderation commands on CommandRegistrationEvent
 *   - hydrate {@link MuteState} from DB on SERVER_STARTED
 *   - start {@link ExpirySweeper} on SERVER_STARTED
 *   - stop sweeper and clear cache on SERVER_STOPPING
 */
public final class ModerationBootstrap {

    private static boolean wired = false;

    private ModerationBootstrap() {}

    public static synchronized void init() {
        if (wired) return;
        wired = true;

    }
    public static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) { ModerationCommands.register(dispatcher); VonixServerUtilities.LOGGER.info("[VonixSU/mod] Moderation commands registered."); }
    public static void serverStarted(net.minecraft.server.MinecraftServer server) { VonixServerUtilities.dbAsync(MuteState::hydrateFromDb); ExpirySweeper.start(server); }
    public static void serverStopping(net.minecraft.server.MinecraftServer server) { ExpirySweeper.stop(); MuteState.clear(); }
}
