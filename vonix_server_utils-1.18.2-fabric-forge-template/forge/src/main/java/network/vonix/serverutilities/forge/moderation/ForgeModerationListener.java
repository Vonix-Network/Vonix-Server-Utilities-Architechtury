package network.vonix.serverutilities.forge.moderation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.donation_ranks.LuckPermsBridge;
import network.vonix.serverutilities.moderation.MuteState;
import network.vonix.serverutilities.moderation.Punishment;
import network.vonix.serverutilities.moderation.PunishmentRepository;
import network.vonix.serverutilities.moderation.PunishmentService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Forge 1.18.2 enforcement for the moderation subsystem.
 *
 * <p>API drift from the NeoForge 1.21.1 reference:
 * <ul>
 *   <li>{@code PlayerEvent.getEntity()} doesn't exist on 1.18.2 — use {@code getPlayer()} (Player)</li>
 *   <li>{@code Component.literal} -&gt; {@code new TextComponent}</li>
 *   <li>{@code player.sendSystemMessage} -&gt; {@code player.sendMessage(c, Util.NIL_UUID)}</li>
 *   <li>Event bus: {@code MinecraftForge.EVENT_BUS} (no NeoForge equivalent class)</li>
 *   <li>{@code CommandSourceStack.getPlayer()} doesn't exist on 1.18.2 — use {@code getEntity() instanceof ServerPlayer}</li>
 * </ul>
 */
public final class ForgeModerationListener {

    private static final Set<String> CHAT_COMMANDS =
            Set.of("me", "msg", "tell", "w", "r", "reply", "broadcast", "bc", "gc");

    private ForgeModerationListener() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(ForgeModerationListener.class);
        VonixServerUtilities.LOGGER.info("[VonixSU/mod] Forge moderation listener registered.");
    }

    // ── Ban check on login ───────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        try {
            Optional<Punishment> ban = PunishmentRepository.findActive(uuid, Punishment.Type.BAN);
            if (ban.isPresent()) {
                if (LuckPermsBridge.isPresent()
                        && LuckPermsBridge.hasPermission(uuid, "vsu.bypass.ban")) {
                    VonixServerUtilities.LOGGER.info(
                            "[VonixSU/mod] login-ban bypassed via vsu.bypass.ban for {} ({})",
                            player.getGameProfile().getName(), uuid);
                    return;
                }
                Punishment p = ban.get();
                Component msg = PunishmentService.buildBanReasonComponent(p.reason(), p.expiresAt());
                try { player.connection.disconnect(msg); }
                catch (Throwable t) {
                    VonixServerUtilities.LOGGER.warn("[VonixSU/mod] login-ban disconnect failed: {}", t.getMessage());
                }
            }
        } catch (Throwable t) {
            VonixServerUtilities.LOGGER.warn("[VonixSU/mod] login ban check failed: {}", t.getMessage());
        }
    }

    // ── Chat mute ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) return;
        if (!MuteState.isMuted(player.getUUID())) return;
        event.setCanceled(true);
        notifyMuted(player);
    }

    // ── Chat-style command intercept ─────────────────────────────────────────

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        CommandSourceStack src = event.getParseResults().getContext().getSource();
        Entity e = src.getEntity();
        if (!(e instanceof ServerPlayer player)) return;
        if (!MuteState.isMuted(player.getUUID())) return;

        String input = event.getParseResults().getReader().getString();
        if (input.startsWith("/")) input = input.substring(1);
        int sp = input.indexOf(' ');
        String root = (sp < 0 ? input : input.substring(0, sp)).toLowerCase();
        if (!CHAT_COMMANDS.contains(root)) return;

        event.setCanceled(true);
        notifyMuted(player);
    }

    private static void notifyMuted(ServerPlayer player) {
        UUID uuid = player.getUUID();
        MinecraftServer server = player.server;
        VonixServerUtilities.dbAsync(() -> {
            Optional<Punishment> mute = PunishmentService.activeMute(uuid);
            Component msg = mute.map(PunishmentService::muteChatRejectionMessage)
                    .orElse(new TextComponent("§c[VSU] You are muted."));
            server.execute(() -> player.sendMessage(msg, Util.NIL_UUID));
        });
    }
}
