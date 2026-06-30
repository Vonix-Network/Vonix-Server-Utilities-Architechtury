package network.vonix.serverutilities.fabric.moderation;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.moderation.MuteState;
import network.vonix.serverutilities.moderation.Punishment;
import network.vonix.serverutilities.moderation.PunishmentRepository;
import network.vonix.serverutilities.moderation.PunishmentService;

import java.util.Optional;
import java.util.UUID;

/**
 * Fabric-side enforcement for the moderation subsystem.
 *
 * Ban  -> {@link ServerPlayConnectionEvents#JOIN}: blocking DB lookup
 *         (handler runs on netty thread post-join, safe for SQLite); disconnect
 *         immediately with the formatted ban message.
 *
 * Mute -> {@link ServerMessageEvents#ALLOW_CHAT_MESSAGE}: reject (return false)
 *         and tellraw the player.
 *
 * Chat-style command intercept (/me /msg /tell /r /broadcast):
 *         registered separately by AdminCommands wrapper. We avoid Fabric's
 *         CommandRegistrationCallback for this — it would either require
 *         redefining the commands or wrapping them, both of which collide
 *         with subagent A's territory. Instead we lean on the
 *         {@link ServerMessageEvents#ALLOW_CHAT_MESSAGE} hook AND a
 *         {@link ServerMessageEvents#ALLOW_COMMAND_MESSAGE} hook (which
 *         catches /me and /say/-style commands routed through the server
 *         message system on Fabric 1.21).
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
                    // Bypass: vsu.bypass.ban (LP-gated). Active row stays in DB.
                    if (network.vonix.serverutilities.donation_ranks.LuckPermsBridge.isPresent()
                            && network.vonix.serverutilities.donation_ranks.LuckPermsBridge.hasPermission(uuid, "vsu.bypass.ban")) {
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

        // ── Mute check on chat ───────────────────────────────────────────────
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (sender == null) return true;
            if (!MuteState.isMuted(sender.getUUID())) return true;
            notifyMuted(sender);
            return false;
        });

        // ── Mute check on /me-style command messages ─────────────────────────
        // ALLOW_COMMAND_MESSAGE fires for /me, /msg, /tell, /say, /teammsg
        // (anything routed through the OutgoingChatMessage system) — the
        // lowest-friction hook that does NOT require redefining commands.
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
            ServerPlayer p;
            try { p = source.getPlayer(); }
            catch (Throwable t) { return true; }
            if (p == null) return true;
            if (!MuteState.isMuted(p.getUUID())) return true;
            notifyMuted(p);
            return false;
        });

        // Touch CommandRegistrationCallback class to assert classpath presence
        // (also documents that this hook exists if we ever need to swap to it).
        @SuppressWarnings("unused")
        Class<?> c = CommandRegistrationCallback.class;

        VonixServerUtilities.LOGGER.info("[VonixSU/mod] Fabric moderation listener registered.");
    }

    private static void notifyMuted(ServerPlayer player) {
        UUID uuid = player.getUUID();
        MinecraftServer server = player.server;
        VonixServerUtilities.dbAsync(() -> {
            Optional<Punishment> mute = PunishmentService.activeMute(uuid);
            Component msg = mute.map(PunishmentService::muteChatRejectionMessage)
                    .orElse(Component.literal("§c[VSU] You are muted."));
            server.execute(() -> player.sendSystemMessage(msg));
        });
    }
}
