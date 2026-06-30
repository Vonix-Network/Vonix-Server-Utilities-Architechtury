package network.vonix.serverutilities.fabric.moderation;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.donation_ranks.LuckPermsBridge;
import network.vonix.serverutilities.moderation.MuteState;
import network.vonix.serverutilities.moderation.Punishment;
import network.vonix.serverutilities.moderation.PunishmentRepository;
import network.vonix.serverutilities.moderation.PunishmentService;

import java.util.Optional;
import java.util.UUID;

/**
 * Fabric 1.18.2 enforcement for the moderation subsystem.
 *
 * <p><b>API drift vs 1.21.1 Fabric reference</b>:
 * <ul>
 *   <li><b>{@code fabric-message-api-v1} does NOT exist on Fabric API 0.77.0+1.18.2</b>.
 *       {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE} / {@code ALLOW_COMMAND_MESSAGE}
 *       are unavailable. We fall back to a Mixin into
 *       {@code ServerGamePacketListenerImpl.handleChat} —
 *       see {@code mixin/ServerGamePacketListenerImplMixin} in this template.</li>
 *   <li>{@code Component.literal} -&gt; {@code new TextComponent}</li>
 *   <li>{@code player.sendSystemMessage} -&gt; {@code player.sendMessage(c, Util.NIL_UUID)}</li>
 * </ul>
 *
 * <p>Ban enforcement still uses {@code ServerPlayConnectionEvents.JOIN}, which
 * IS present in fabric-networking-api-v1 for 1.18.2.
 *
 * <p>Chat-style commands (/me /msg /tell /r /broadcast) are intercepted by the
 * SAME Mixin's secondary hook into command parsing (since 1.18.2 doesn't ship
 * an {@code ALLOW_COMMAND_MESSAGE} event). The Mixin checks {@link MuteState}
 * before the vanilla broadcast call and cancels at that point.
 */
public final class FabricModerationListener {

    private FabricModerationListener() {}

    public static void register() {
        // ── Ban check on join ────────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player == null) return;
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
                    try { handler.disconnect(msg); }
                    catch (Throwable t) {
                        VonixServerUtilities.LOGGER.warn("[VonixSU/mod] login-ban disconnect failed: {}", t.getMessage());
                    }
                }
            } catch (Throwable t) {
                VonixServerUtilities.LOGGER.warn("[VonixSU/mod] login ban check failed: {}", t.getMessage());
            }
        });

        // Chat mute is wired via Mixin (ServerGamePacketListenerImplMixin) —
        // ServerMessageEvents is unavailable on Fabric 1.18.2.

        VonixServerUtilities.LOGGER.info("[VonixSU/mod] Fabric moderation listener registered (chat mute via Mixin).");
    }

    /**
     * Called by the Mixin from {@code ServerGamePacketListenerImpl.handleChat}
     * (and any chat-style command dispatch) to check + notify when the player
     * is muted. Returns true when the event should be cancelled.
     */
    public static boolean checkAndNotifyMuted(ServerPlayer player) {
        if (player == null) return false;
        if (!MuteState.isMuted(player.getUUID())) return false;
        notifyMuted(player);
        return true;
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
