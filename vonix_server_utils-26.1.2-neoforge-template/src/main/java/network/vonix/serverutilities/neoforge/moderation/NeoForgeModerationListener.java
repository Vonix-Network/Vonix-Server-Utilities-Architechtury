package network.vonix.serverutilities.neoforge.moderation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.moderation.MuteState;
import network.vonix.serverutilities.moderation.Punishment;
import network.vonix.serverutilities.moderation.PunishmentRepository;
import network.vonix.serverutilities.moderation.PunishmentService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * NeoForge-side enforcement for the moderation subsystem.
 *
 * Ban  -> {@link PlayerEvent.PlayerLoggedInEvent}: look up active ban
 *         in DB (blocking; runs on netty thread post-login which is fine for
 *         SQLite — the existing connection is already used cross-thread by
 *         the DB executor and we're not on the tick thread here), disconnect
 *         immediately with the formatted ban message.
 *
 * Mute -> {@link ServerChatEvent}: cancel the chat event and tellraw the
 *         player a "you are muted" message including expiry.
 *
 * Chat-style commands (/me /msg /tell /r /broadcast) ->
 *         {@link CommandEvent}: parse the root literal, if it matches one of
 *         the chat-style commands and the player is muted, cancel and tellraw.
 */
public final class NeoForgeModerationListener {

    private static final Set<String> CHAT_COMMANDS =
            Set.of("me", "msg", "tell", "w", "r", "reply", "broadcast", "bc", "gc");

    private NeoForgeModerationListener() {}

    public static void register(Object unusedBus) {
        // PlayerEvent.PlayerLoggedInEvent, ServerChatEvent, and CommandEvent are all
        // forge "game" bus events — fire on NeoForge.EVENT_BUS, not the mod bus.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(NeoForgeModerationListener.class);
        VonixServerUtilities.LOGGER.info("[VonixSU/mod] NeoForge moderation listener registered.");
    }

    // ── Ban check on login ───────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        try {
            Optional<Punishment> ban = PunishmentRepository.findActive(uuid, Punishment.Type.BAN);
            if (ban.isPresent()) {
                // Bypass: vsu.bypass.ban (LP-gated, only when LP is present).
                // The active row stays in the DB; removing the bypass node re-enforces.
                if (network.vonix.serverutilities.donation_ranks.LuckPermsBridge.isPresent()
                        && network.vonix.serverutilities.donation_ranks.LuckPermsBridge.hasPermission(uuid, "vsu.bypass.ban")) {
                    VonixServerUtilities.LOGGER.info(
                            "[VonixSU/mod] login-ban bypassed via vsu.bypass.ban for {} ({})",
                            player.nameAndId().name(), uuid);
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

    // ── Chat mute ─────────────────────────────────────────────────────────────

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
        ServerPlayer player = src.getPlayer();
        if (player == null) return;
        if (!MuteState.isMuted(player.getUUID())) return;

        // The parsed input string — first whitespace-separated token is the root literal.
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
        MinecraftServer server = player.level().getServer();
        VonixServerUtilities.dbAsync(() -> {
            Optional<Punishment> mute = PunishmentService.activeMute(uuid);
            Component msg = mute.map(PunishmentService::muteChatRejectionMessage)
                    .orElse(Component.literal("§c[VSU] You are muted."));
            server.execute(() -> player.sendSystemMessage(msg));
        });
    }
}
