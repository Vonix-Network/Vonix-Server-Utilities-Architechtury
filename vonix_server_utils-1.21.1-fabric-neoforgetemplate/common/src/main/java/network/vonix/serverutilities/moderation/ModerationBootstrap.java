package network.vonix.serverutilities.moderation;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import network.vonix.serverutilities.VonixServerUtilities;

/** Shared moderation lifecycle behavior; loader modules provide event delivery. */
public final class ModerationBootstrap {
    private static boolean wired;
    private ModerationBootstrap() {}

    public static synchronized void init() { wired = true; }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!wired) init();
        ModerationCommands.register(dispatcher);
        VonixServerUtilities.LOGGER.info("[VSU/mod] Moderation commands registered.");
    }

    public static void serverStarted(MinecraftServer server) {
        VonixServerUtilities.dbAsync(MuteState::hydrateFromDb);
        ExpirySweeper.start(server);
    }

    public static void serverStopping(MinecraftServer server) {
        ExpirySweeper.stop();
        MuteState.clear();
    }
}
