package network.vonix.serverutilities.admin;

import net.minecraft.network.chat.Component;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

import java.util.*;

/**
 * In-memory admin state: fly, god mode, vanish, heal, and feed.
 * All state is cleared on server stop (session-scoped). No DB required.
 * All methods must be called from the main tick thread.
 */
public final class AdminManager {
    private static final AdminManager INSTANCE = new AdminManager();
    public static AdminManager getInstance() { return INSTANCE; }

    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Set<UUID> godModePlayers  = new HashSet<>();
    private final Set<UUID> flyingPlayers   = new HashSet<>();

    // â”€â”€ Vanish â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void toggleVanish(ServerPlayer player, MinecraftServer server) {
        UUID uuid = player.getUUID();
        if (vanishedPlayers.remove(uuid)) {
            // Reveal to all players
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (!other.equals(player)) {
                    other.connection.send(new ClientboundPlayerInfoPacket(
                            ClientboundPlayerInfoPacket.Action.ADD_PLAYER, player));
                }
            }
            player.sendSystemMessage(Component.literal("Â§a[VSU] You are now visible."));
        } else {
            vanishedPlayers.add(uuid);
            // Hide from non-operators
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (!other.equals(player) && !other.hasPermissions(2)) {
                    other.connection.send(new ClientboundPlayerInfoPacket(
                            ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, player));
                }
            }
            player.sendSystemMessage(Component.literal("Â§a[VSU] You are now vanished."));
        }
    }

    public boolean isVanished(UUID uuid) { return vanishedPlayers.contains(uuid); }

    // â”€â”€ God mode â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void toggleGodMode(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (godModePlayers.remove(uuid)) {
            player.setInvulnerable(false);
            player.sendSystemMessage(Component.literal("Â§c[VSU] God mode disabled."));
        } else {
            godModePlayers.add(uuid);
            player.setInvulnerable(true);
            player.sendSystemMessage(Component.literal("Â§a[VSU] God mode enabled."));
        }
    }

    public boolean isGodMode(UUID uuid) { return godModePlayers.contains(uuid); }

    // â”€â”€ Fly â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void toggleFly(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (flyingPlayers.remove(uuid)) {
            player.getAbilities().mayfly = player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.sendSystemMessage(Component.literal("Â§c[VSU] Fly mode disabled."));
        } else {
            flyingPlayers.add(uuid);
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            player.sendSystemMessage(Component.literal("Â§a[VSU] Fly mode enabled."));
        }
    }

    // â”€â”€ Heal & Feed â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public void healPlayer(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        player.sendSystemMessage(Component.literal("Â§a[VSU] You have been healed."));
    }

    public void feedPlayer(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);
        player.sendSystemMessage(Component.literal("Â§a[VSU] You have been fed."));
    }

    // â”€â”€ Lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Clear all session state. Call on server stop. */
    public void clear() {
        vanishedPlayers.clear();
        godModePlayers.clear();
        flyingPlayers.clear();
    }
}

