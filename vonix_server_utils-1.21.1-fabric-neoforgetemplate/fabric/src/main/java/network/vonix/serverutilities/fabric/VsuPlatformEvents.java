package network.vonix.serverutilities.fabric;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.inventory.fabric.AccessoryHelperImpl;
import network.vonix.serverutilities.platform.PlatformEvents;

import java.nio.file.Path;

public final class VsuPlatformEvents implements PlatformEvents {
    @Override public void register(Callbacks c) {
        CommandRegistrationEvent.EVENT.register((d, r, s) -> c.commands().accept(d));
        LifecycleEvent.SERVER_STARTING.register(c.serverStarting());
        LifecycleEvent.SERVER_STARTED.register(c.serverStarted());
        LifecycleEvent.SERVER_STOPPING.register(c.serverStopping());
        LifecycleEvent.SERVER_STOPPED.register(c.serverStopped());
        TickEvent.SERVER_POST.register(c.serverTick());
        PlayerEvent.PLAYER_JOIN.register(c.playerJoin());
        PlayerEvent.PLAYER_QUIT.register(c.playerQuit());
        EntityEvent.LIVING_DEATH.register((e, s) -> { c.livingDeath().accept(e, s); return EventResult.pass(); });
    }
    @Override public Path configDirectory() { return dev.architectury.platform.Platform.getConfigFolder(); }
    @Override public boolean easyNpcInstalled() { return false; }
    @Override public void registerEasyNpcInteraction() {}
    @Override public void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) { AccessoryHelperImpl.openAccessoryMenu(target, viewer); }
}
