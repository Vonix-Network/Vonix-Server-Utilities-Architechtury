package network.vonix.serverutilities.moderation;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 1.18.2-adapted moderation service.
 *
 * <p>API drift from the 1.21.1 reference:
 * <ul>
 *   <li>{@code Component.literal(s)} -&gt; {@code new TextComponent(s)}</li>
 *   <li>{@code player.sendSystemMessage(c)} -&gt; {@code player.sendMessage(c, Util.NIL_UUID)}</li>
 *   <li>{@code server.sendSystemMessage(c)} -&gt; {@code server.sendMessage(c, Util.NIL_UUID)}</li>
 * </ul>
 */
public final class PunishmentService {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

    private PunishmentService() {}

    // ─────────────────────────────────────────────────────────────────────────
    // BAN / TEMPBAN
    // ─────────────────────────────────────────────────────────────────────────

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
        Component banMsg = new TextComponent(
                "§4§l[VONIX] §r§cYou have been banned.\n" +
                "§7Reason: §f" + (reason == null ? "(no reason)" : reason) + "\n" +
                "§7" + (expiresAt == null
                        ? "This ban is §cpermanent§7."
                        : "Expires: §f" + ISO.format(Instant.ofEpochMilli(expiresAt))));
        player.sendMessage(banMsg, Util.NIL_UUID);
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
        return new TextComponent(sb.toString());
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

        VonixServerUtilities.dbAsync(() -> {
            PunishmentRepository.insert(row);
            MuteState.add(targetUuid);
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(targetUuid);
                if (online != null) {
                    online.sendMessage(new TextComponent(
                            "§c[VSU] You have been muted.\n" +
                            "§7Reason: §f" + (reason == null ? "(no reason)" : reason) + "\n" +
                            (expiresAt == null
                                    ? "§7This mute is §cpermanent§7."
                                    : "§7Expires: §f" + ISO.format(Instant.ofEpochMilli(expiresAt)))),
                            Util.NIL_UUID);
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
            if (ok) MuteState.remove(target);
            server.execute(() -> {
                if (ok) {
                    ServerPlayer online = server.getPlayerList().getPlayer(target);
                    if (online != null) {
                        online.sendMessage(new TextComponent("§a[VSU] You have been unmuted."), Util.NIL_UUID);
                    }
                }
                broadcastToOps(server,
                        ok ? "§a[VSU] §e" + revokedBy + " §7unmuted §e" + targetName
                           : "§7[VSU] §e" + targetName + " §7was not muted.");
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
        return new TextComponent(sb.toString());
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
                    Component msg = new TextComponent(
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
                    online.sendMessage(new TextComponent(
                            "§e[VSU] You have been warned by §f" + issuerName + "§e.\n" +
                            "§7Reason: §f" + reason + "\n" +
                            "§7Active warnings: §f" + active), Util.NIL_UUID);
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

    public static void runExpirySweep(MinecraftServer server) {
        List<Punishment> swept = PunishmentRepository.sweepExpired();
        if (swept.isEmpty()) return;
        server.execute(() -> {
            for (Punishment p : swept) {
                if (p.type() == Punishment.Type.MUTE) {
                    MuteState.remove(p.targetUuid());
                    ServerPlayer online = server.getPlayerList().getPlayer(p.targetUuid());
                    if (online != null) {
                        online.sendMessage(new TextComponent("§a[VSU] Your mute has expired."), Util.NIL_UUID);
                    }
                }
                broadcastToOps(server,
                        "§7[VSU] §e" + p.targetName() + "§7's §f" + p.type().name().toLowerCase()
                                + " §7has expired.");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static Optional<Punishment> activeBan(UUID target) {
        return PunishmentRepository.findActive(target, Punishment.Type.BAN);
    }

    public static Optional<Punishment> activeMute(UUID target) {
        return PunishmentRepository.findActive(target, Punishment.Type.MUTE);
    }

    /** Broadcast a system message to all server ops + console. */
    public static void broadcastToOps(MinecraftServer server, String message) {
        Component c = new TextComponent(message);
        server.sendMessage(c, Util.NIL_UUID);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(p.getGameProfile())) {
                p.sendMessage(c, Util.NIL_UUID);
            }
        }
    }
}
