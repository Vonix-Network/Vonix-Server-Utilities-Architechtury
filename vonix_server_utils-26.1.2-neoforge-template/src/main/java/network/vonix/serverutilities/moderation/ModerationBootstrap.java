package network.vonix.serverutilities.moderation;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import network.vonix.serverutilities.VonixServerUtilities;

/** Wires the moderation subsystem into NeoForge's game event bus. */
public final class ModerationBootstrap {
    private static boolean wired = false;
    private ModerationBootstrap() {}

    public static synchronized void init() {
        if (wired) return;
        wired = true;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ModerationBootstrap.class);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        ModerationCommands.register(event.getDispatcher());
        VonixServerUtilities.LOGGER.info("[VonixSU/mod] Moderation commands registered.");
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        VonixServerUtilities.dbAsync(MuteState::hydrateFromDb);
        ExpirySweeper.start(event.getServer());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        ExpirySweeper.stop();
        MuteState.clear();
    }
}
