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
    private Callbacks callbacks;

    @Override
    public void register(Callbacks callbacks) {
        this.callbacks = callbacks;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                callbacks.commands().accept(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(callbacks.serverStarting()::accept);
        ServerLifecycleEvents.SERVER_STARTED.register(callbacks.serverStarted()::accept);
        ServerLifecycleEvents.SERVER_STOPPING.register(callbacks.serverStopping()::accept);
        ServerLifecycleEvents.SERVER_STOPPED.register(callbacks.serverStopped()::accept);
        ServerTickEvents.END_SERVER_TICK.register(callbacks.serverTick()::accept);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                callbacks.playerJoin().accept(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                callbacks.playerQuit().accept(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register(callbacks.livingDeath()::accept);
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String platformDisplay() {
        return "Fabric (" + FabricLoader.getInstance().getEnvironmentType().name() + ")";
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
        AccessoryHelperImpl.openAccessoryMenu(target, viewer);
    }
}
