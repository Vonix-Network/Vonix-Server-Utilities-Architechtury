package network.vonix.serverutilities.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Loader-neutral event and platform services supplied by each loader module. */
public interface PlatformEvents {
    void register(Callbacks callbacks);
    Path configDirectory();
    String platformDisplay();
    boolean easyNpcInstalled();
    void registerEasyNpcInteraction();
    void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer);

    record Callbacks(
            Consumer<CommandDispatcher<CommandSourceStack>> commands,
            Consumer<MinecraftServer> serverStarting,
            Consumer<MinecraftServer> serverStarted,
            Consumer<MinecraftServer> serverStopping,
            Consumer<MinecraftServer> serverStopped,
            Consumer<MinecraftServer> serverTick,
            Consumer<ServerPlayer> playerJoin,
            Consumer<ServerPlayer> playerQuit,
            BiConsumer<LivingEntity, DamageSource> livingDeath) {}

    final class Holder {
        private static PlatformEvents instance;
        private Holder() {}
        public static void install(PlatformEvents value) { instance = value; }
        public static PlatformEvents get() {
            if (instance == null) throw new IllegalStateException("VSU loader platform was not installed");
            return instance;
        }
    }
}
