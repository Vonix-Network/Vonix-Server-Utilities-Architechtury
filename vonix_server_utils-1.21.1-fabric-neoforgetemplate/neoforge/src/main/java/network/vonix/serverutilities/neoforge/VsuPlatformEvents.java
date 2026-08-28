package network.vonix.serverutilities.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
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
    @Override
    public void register(Callbacks c) {
        NeoForge.EVENT_BUS.register(new Adapter(c));
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String platformDisplay() {
        return "NeoForge 1.21.1";
    }

    @Override public boolean easyNpcInstalled() { return false; }
    @Override public void registerEasyNpcInteraction() {}
    @Override public void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        AccessoryHelperImpl.openAccessoryMenu(target, viewer);
    }

    private static final class Adapter {
        private final Callbacks c;
        Adapter(Callbacks c) { this.c = c; }

        @SubscribeEvent
        public void commands(RegisterCommandsEvent event) {
            c.commands().accept(event.getDispatcher());
        }

        @SubscribeEvent
        public void starting(ServerStartingEvent event) {
            c.serverStarting().accept(event.getServer());
        }

        @SubscribeEvent
        public void started(ServerStartedEvent event) {
            c.serverStarted().accept(event.getServer());
        }

        @SubscribeEvent
        public void stopping(ServerStoppingEvent event) {
            c.serverStopping().accept(event.getServer());
        }

        @SubscribeEvent
        public void stopped(ServerStoppedEvent event) {
            c.serverStopped().accept(event.getServer());
        }

        @SubscribeEvent
        public void tick(ServerTickEvent.Post event) {
            c.serverTick().accept(event.getServer());
        }

        @SubscribeEvent
        public void join(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) c.playerJoin().accept(player);
        }

        @SubscribeEvent
        public void quit(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) c.playerQuit().accept(player);
        }

        @SubscribeEvent
        public void death(LivingDeathEvent event) {
            c.livingDeath().accept(event.getEntity(), event.getSource());
        }
    }
}
