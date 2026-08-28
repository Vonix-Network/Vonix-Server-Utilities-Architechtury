package network.vonix.serverutilities.fabric;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.platform.PlatformEvents;

import java.nio.file.Path;

public final class VsuPlatformEvents implements PlatformEvents {
    @Override
    public void register(Callbacks c) {
        CommandRegistrationEvent.EVENT.register((dispatcher, ignoredAccess, ignoredEnvironment) -> c.commands().accept(dispatcher));
        LifecycleEvent.SERVER_STARTING.register(server -> c.serverStarting().accept(server));
        LifecycleEvent.SERVER_STARTED.register(server -> c.serverStarted().accept(server));
        LifecycleEvent.SERVER_STOPPING.register(server -> c.serverStopping().accept(server));
        LifecycleEvent.SERVER_STOPPED.register(server -> c.serverStopped().accept(server));
        TickEvent.SERVER_POST.register(server -> c.serverTick().accept(server));
        PlayerEvent.PLAYER_JOIN.register(player -> c.playerJoin().accept(player));
        PlayerEvent.PLAYER_QUIT.register(player -> c.playerQuit().accept(player));
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            c.livingDeath().accept(entity, source);
            return EventResult.pass();
        });
    }

    @Override
    public Path configDirectory() {
        return dev.architectury.platform.Platform.getConfigFolder();
    }

    @Override
    public boolean easyNpcInstalled() {
        return false;
    }

    @Override
    public void registerEasyNpcInteraction() {
    }

    @Override
    public void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        network.vonix.serverutilities.inventory.fabric.AccessoryHelperImpl.openAccessoryMenu(target, viewer);
    }
}
