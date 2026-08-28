package network.vonix.serverutilities.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import network.vonix.serverutilities.platform.PlatformEvents;

import java.nio.file.Path;

/** Native Forge event delivery for lifecycle, commands, joins, ticks, and death persistence. */
public final class VsuPlatformEvents implements PlatformEvents {
    private Callbacks callbacks;
    private boolean registered;

    @Override
    public void register(Callbacks c) {
        if (registered) return;
        callbacks = c;
        registered = true;
        MinecraftForge.EVENT_BUS.register(this);
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) callbacks.serverTick().accept(server);
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) callbacks.playerJoin().accept(player);
    }

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) callbacks.playerQuit().accept(player);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        callbacks.livingDeath().accept(event.getEntityLiving(), event.getSource());
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
        network.vonix.serverutilities.inventory.forge.AccessoryHelperImpl.openAccessoryMenu(target, viewer);
    }
}
