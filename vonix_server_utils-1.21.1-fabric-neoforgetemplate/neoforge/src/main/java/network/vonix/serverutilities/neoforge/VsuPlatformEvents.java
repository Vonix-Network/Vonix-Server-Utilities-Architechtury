package network.vonix.serverutilities.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import network.vonix.serverutilities.inventory.neoforge.AccessoryHelperImpl;
import network.vonix.serverutilities.platform.PlatformEvents;

import java.nio.file.Path;

public final class VsuPlatformEvents implements PlatformEvents {
    private Callbacks callbacks;

    @Override
    public void register(Callbacks callbacks) {
        this.callbacks = callbacks;
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        callbacks.commands().accept(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        callbacks.serverStarting().accept(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        callbacks.serverStarted().accept(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        callbacks.serverStopping().accept(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        callbacks.serverStopped().accept(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        callbacks.serverTick().accept(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            callbacks.playerJoin().accept(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            callbacks.playerQuit().accept(player);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        callbacks.livingDeath().accept(event.getEntity(), event.getSource());
    }

    @Override
    public Path configDirectory() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String platformDisplay() {
        return "NeoForge";
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
