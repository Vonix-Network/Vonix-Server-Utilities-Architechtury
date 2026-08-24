package network.vonix.serverutilities.moderation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service layer the moderation commands call into.
 *
 * Owns:
 *   - online-player resolution (kick / tellraw / disconnect orchestration)
 *   - DB writes via {@link PunishmentRepository}
 *   - {@link MuteState} cache updates on mute/unmute
 *   - op-only broadcast on state changes
 *
 * Public methods are non-blocking from the caller's perspective: they bounce
 * DB work onto {@link VonixServerUtilities#dbAsync} and Minecraft side-effects
 * back to the main thread via {@code server.execute(...)}.
 */
public final class PunishmentService {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

    private PunishmentService() {}

    // ─────────────────────────────────────────────────────────────────────────
    // BAN / TEMPBAN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Issue a ban (permanent if {@code millisFromNow} is null).
     * If the target is online, tellraw the reason then disconnect 1s later.
     */
    public static void ban(MinecraftServer server,
                           UUID targetUuid, String targetName,
                           UUID issuerUuid, String issuerName,
                           String reason, Long millisFromNow) {
        Long expiresAt = millisFromNow == null ? null : System.currentTimeMillis() + millisFromNow;

        Punishment row = Punishment.forInsert(
                Punishment.Type.BAN, targetUuid, targetName,
                issuerUuid, issuerName, reason, expiresAt);

        VonixServerUtilities.dbAsync(() -> {
            PunishmentRepository.insert(row);
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(targetUuid);
                if (online != null) {
                    kickOrDisconnectForBan(server, online, reason, expiresAt);
                }
                broadcastToOps(server,
                        "§c[VSU] §e" + issuerName + " §7banned §e" + targetName + "§7: §f"
                                + (reason == null ? "(no reason)" : reason)
                                + (expiresAt == null ? " §8[permanent]" : " §8[until " + ISO.format(Instant.ofEpochMilli(expiresAt)) + "]"));
            });
        });
    }

    private static void kickOrDisconnectForBan(MinecraftServer server, ServerPlayer player,
                                                String reason, Long expiresAt) {
        // Step 1: tellraw so they see the reason on screen.
        Component banMsg = Component.literal(
                "§4§l[VONIX] §r§cYou have been banned.\n" +
                "§7Reason: §f" + (reason == null ? "(no reason)" : reason) + "\n" +
                "§7" + (expiresAt == null
                        ? "This ban is §cpermanent§7."
                        : "Expires: §f" + ISO.format(Instant.ofEpochMilli(expiresAt))));
        player.sendSystemMessage(banMsg);
        // Step 2: delay 1s then kick.
        CompletableFuture
                .delayedExecutor(1L, TimeUnit.SECONDS)
                .execute(() -> server.execute(() -> {
                    Component reasonComp = buildBanReasonComponent(reason, expiresAt);
                    try {
                        player.connection.disconnect(reasonComp);
                    } catch (Throwable t) {
                        VonixServerUtilities.LOGGER.warn("[VonixSU/mod] disconnect failed: {}", t.getMessage());
                    }
                }));
    }

    public static Component buildBanReasonComponent(String reason, Long expiresAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("§cBanned: §f").append(reason == null ? "(no reason)" : reason).append('\n');
        if (expiresAt == null) {
            sb.append("§7Permanent");
        } else {
            sb.append("§7Expires: §f").append(ISO.format(Instant.ofEpochMilli(expiresAt)));
        }
        return Component.literal(sb.toString());
    }

    public static void unban(MinecraftServer server, UUID target, String targetName, String revokedBy) {
        VonixServerUtilities.dbAsync(() -> {
            boolean ok = PunishmentRepository.revoke(target, Punishment.Type.BAN, revokedBy);
            server.execute(() -> broadcastToOps(server,
                    ok ? "§a[VSU] §e" + revokedBy + " §7unbanned §e" + targetName
                       : "§7[VSU] §e" + targetName + " §7was not banned."));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MUTE / TEMPMUTE
    // ─────────────────────────────────────────────────────────────────────────

    public static void mute(MinecraftServer server,
                            UUID targetUuid, String targetName,
                            UUID issuerUuid, String issuerName,
                            String reason, Long millisFromNow) {
        Long expiresAt = millisFromNow == null ? null : System.currentTimeMillis() + millisFromNow;

        Punishment row = Punishment.forInsert(
                Punishment.Type.MUTE, targetUuid, targetName,
                issuerUuid, issuerName, reason, expiresAt);

        // Apply enforcement before the asynchronous DB write so the mute is
        // effective immediately and cannot be lost during startup hydration.
        MuteState.addPending(targetUuid);
        VonixServerUtilities.dbAsync(() -> {
            long id = PunishmentRepository.insert(row);
            if (id < 0) {
                MuteState.clearPending(targetUuid);
            } else {
                MuteState.markPersisted(targetUuid);
            }
            server.execute(() -> {
                if (id < 0) {
                    broadcastToOps(server, "§c[VSU] Failed to persist mute for §e" + targetName + "§c; no mute was applied.");
                    return;
                }
                ServerPlayer online = server.getPlayerList().getPlayer(targetUuid);
                if (online != null) {
                    online.sendSystemMessage(Component.literal(
                            "§c[VSU] You have been muted.\n" +
                            "§7Reason: §f" + (reason == null ? "(no reason)" : reason) + "\n" +
                            (expiresAt == null
                                    ? "§7This mute is §cpermanent§7."
                                    : "§7Expires: §f" + ISO.format(Instant.ofEpochMilli(expiresAt)))));
                }
                broadcastToOps(server,
                        "§e[VSU] §e" + issuerName + " §7muted §e" + targetName + "§7: §f"
                                + (reason == null ? "(no reason)" : reason)
                                + (expiresAt == null ? " §8[permanent]" : " §8[" + DurationParser.format(millisFromNow) + "]"));
            });
        });
    }

    public static void unmute(MinecraftServer server, UUID target, String targetName, String revokedBy) {
        VonixServerUtilities.dbAsync(() -> {
            boolean ok = PunishmentRepository.revoke(target, Punishment.Type.MUTE, revokedBy);
            boolean verified = false;
            boolean activeAfter = true;
            if (ok) {
                try {
                    activeAfter = PunishmentRepository.hasActiveMute(target);
                    MuteState.reconcilePersisted(target, activeAfter);
                    verified = true;
                }
                catch (Exception e) { VonixServerUtilities.LOGGER.error("[VonixSU/mod] mute state reconciliation failed", e); }
            }
            final boolean stateVerified = verified;
            final boolean remainingMute = activeAfter;
            server.execute(() -> {
                if (ok) {
                    ServerPlayer online = server.getPlayerList().getPlayer(target);
                    if (online != null) {
                        String message = !stateVerified
                                ? "§e[VSU] Your mute revocation was recorded, but the active mute state could not be verified."
                                : remainingMute
                                        ? "§e[VSU] The latest mute was revoked, but another active mute remains."
                                        : "§a[VSU] You have been unmuted.";
                        online.sendSystemMessage(Component.literal(message));
                    }
                }
                broadcastToOps(server,
                        !ok ? "§7[VSU] §e" + targetName + " §7was not muted."
                           : !stateVerified ? "§e[VSU] §e" + revokedBy + " §7revoked the latest mute for §e" + targetName
                                + "§7, but the remaining active state could not be verified."
                           : remainingMute ? "§e[VSU] §e" + revokedBy + " §7revoked the latest mute for §e" + targetName
                                + "§7; another active mute remains."
                           : "§a[VSU] §e" + revokedBy + " §7unmuted §e" + targetName
                        );
            });
        });
    }

    /** Build the "you are muted" component shown when a muted player tries to chat. */
    public static Component muteChatRejectionMessage(Punishment mute) {
        StringBuilder sb = new StringBuilder("§c[VSU] You are muted");
        if (mute.expiresAt() != null) {
            long remaining = mute.expiresAt() - System.currentTimeMillis();
            if (remaining > 0) sb.append(" §7(").append(DurationParser.format(remaining)).append(" remaining)");
        } else {
            sb.append(" §7(permanent)");
        }
        if (mute.reason() != null) sb.append("§r\n§7Reason: §f").append(mute.reason());
        return Component.literal(sb.toString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KICK
    // ─────────────────────────────────────────────────────────────────────────

    public static void kick(MinecraftServer server,
                            UUID targetUuid, String targetName,
                            UUID issuerUuid, String issuerName,
                            String reason) {
        Punishment row = Punishment.forInsert(
                Punishment.Type.KICK, targetUuid, targetName,
                issuerUuid, issuerName, reason, null);

        VonixServerUtilities.dbAsync(() -> {
            PunishmentRepository.insert(row);
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(targetUuid);
                if (online != null) {
                    Component msg = Component.literal(
                            "§cKicked: §f" + (reason == null ? "(no reason)" : reason));
                    try { online.connection.disconnect(msg); }
                    catch (Throwable t) {
                        VonixServerUtilities.LOGGER.warn("[VonixSU/mod] kick disconnect failed: {}", t.getMessage());
                    }
                }
                broadcastToOps(server,
                        "§6[VSU] §e" + issuerName + " §7kicked §e" + targetName + "§7: §f"
                                + (reason == null ? "(no reason)" : reason));
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WARN
    // ─────────────────────────────────────────────────────────────────────────

    public static void warn(MinecraftServer server,
                            UUID targetUuid, String targetName,
                            UUID issuerUuid, String issuerName,
                            String reason) {
        Punishment row = Punishment.forInsert(
                Punishment.Type.WARN, targetUuid, targetName,
                issuerUuid, issuerName, reason, null);

        VonixServerUtilities.dbAsync(() -> {
            PunishmentRepository.insert(row);
            int active = PunishmentRepository.count(targetUuid, Punishment.Type.WARN, true);
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(targetUuid);
                if (online != null) {
                    online.sendSystemMessage(Component.literal(
                            "§e[VSU] You have been warned by §f" + issuerName + "§e.\n" +
                            "§7Reason: §f" + reason + "\n" +
                            "§7Active warnings: §f" + active));
                }
                broadcastToOps(server,
                        "§e[VSU] §e" + issuerName + " §7warned §e" + targetName
                                + "§7 (§f" + active + "§7 active): §f" + reason);
            });
        });
    }

    public static void clearWarnings(MinecraftServer server, UUID target, String targetName, String issuer) {
        VonixServerUtilities.dbAsync(() -> {
            int n = PunishmentRepository.clearActive(target, Punishment.Type.WARN, issuer);
            server.execute(() -> broadcastToOps(server,
                    "§a[VSU] §e" + issuer + " §7cleared §f" + n + " §7warning(s) from §e" + targetName));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Expiry sweeper hook
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Invoked by {@link ExpirySweeper}. Marks expired rows inactive, updates
     * the mute cache, and broadcasts to ops on each state change.
     */
    public static void runExpirySweep(MinecraftServer server) {
        List<Punishment> swept = PunishmentRepository.sweepExpired();
        if (swept.isEmpty()) return;
        Map<UUID, Boolean> activeMutesAfterSweep = new HashMap<>();
        for (Punishment p : swept) {
            if (p.type() == Punishment.Type.MUTE) {
                try {
                    boolean active = PunishmentRepository.hasActiveMute(p.targetUuid());
                    activeMutesAfterSweep.put(p.targetUuid(), active);
                    MuteState.reconcilePersisted(p.targetUuid(), active);
                } catch (Exception e) {
                    activeMutesAfterSweep.put(p.targetUuid(), true);
                    VonixServerUtilities.LOGGER.error("[VSU/mod] expired mute state reconciliation failed", e);
                }
            }
        }
        server.execute(() -> {
            for (Punishment p : swept) {
                if (p.type() == Punishment.Type.MUTE) {
                    ServerPlayer online = server.getPlayerList().getPlayer(p.targetUuid());
                    boolean anotherMuteRemains = activeMutesAfterSweep.getOrDefault(p.targetUuid(), true);
                    if (online != null && !anotherMuteRemains) {
                        online.sendSystemMessage(Component.literal("§a[VSU] Your mute has expired."));
                    }
                }
                if (p.type() != Punishment.Type.MUTE || !activeMutesAfterSweep.getOrDefault(p.targetUuid(), true)) {
                    broadcastToOps(server,
                            "§7[VSU] §e" + p.targetName() + "§7's §f" + p.type().name().toLowerCase()
                                    + " §7has expired.");
                } else {
                    broadcastToOps(server,
                            "§7[VSU] One of §e" + p.targetName() + "§7's mutes expired; another active mute remains.");
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** True if an active ban exists for {@code target}. Blocking; call from DB executor. */
    public static Optional<Punishment> activeBan(UUID target) {
        return PunishmentRepository.findActive(target, Punishment.Type.BAN);
    }

    public static Optional<Punishment> activeMute(UUID target) {
        return PunishmentRepository.findActive(target, Punishment.Type.MUTE);
    }

    /** Broadcast a system message to all server ops + console. */
    public static void broadcastToOps(MinecraftServer server, String message) {
        Component c = Component.literal(message);
        server.sendSystemMessage(c);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(p.getGameProfile())) {
                p.sendSystemMessage(c);
            }
        }
    }
}
