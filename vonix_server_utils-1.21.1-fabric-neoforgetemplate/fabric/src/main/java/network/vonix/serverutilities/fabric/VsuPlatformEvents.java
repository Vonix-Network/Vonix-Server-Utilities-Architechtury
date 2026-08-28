package network.vonix.serverutilities.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.inventory.fabric.AccessoryHelperImpl;
import network.vonix.serverutilities.platform.PlatformEvents;

import java.nio.file.Path;

public final class VsuPlatformEvents implements PlatformEvents {
    @Override
    public void register(Callbacks c) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> c.commands().accept(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(c.serverStarting()::accept);
        ServerLifecycleEvents.SERVER_STARTED.register(c.serverStarted()::accept);
        ServerLifecycleEvents.SERVER_STOPPING.register(c.serverStopping()::accept);
        ServerLifecycleEvents.SERVER_STOPPED.register(c.serverStopped()::accept);
        ServerTickEvents.END_SERVER_TICK.register(c.serverTick()::accept);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player != null) c.playerJoin().accept(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) c.playerQuit().accept(handler.player);
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            c.livingDeath().accept(entity, source);
            return true;
        });
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String platformDisplay() {
        return "Fabric 1.21.1";
    }

    @Override public boolean easyNpcInstalled() { return false; }
    @Override public void registerEasyNpcInteraction() {}
    @Override public void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        AccessoryHelperImpl.openAccessoryMenu(target, viewer);
    }
}
