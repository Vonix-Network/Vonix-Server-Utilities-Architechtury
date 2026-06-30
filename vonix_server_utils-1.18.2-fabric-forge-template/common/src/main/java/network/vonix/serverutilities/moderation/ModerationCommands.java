package network.vonix.serverutilities.moderation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.features.PermissionGate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 1.18.2-adapted moderation command registration.
 *
 * <p>API drift handled inline:
 * <ul>
 *   <li>{@code Component.literal} -&gt; {@code new TextComponent}</li>
 *   <li>{@code sendSuccess(Supplier<Component>, boolean)} -&gt; {@code sendSuccess(Component, boolean)}</li>
 *   <li>{@code source.getPlayer()} (returns ServerPlayer directly on 1.18.2 was REMOVED;
 *       only {@code getPlayerOrException()} exists). We use {@code getEntity() instanceof ServerPlayer}.</li>
 * </ul>
 */
public final class ModerationCommands {

    private static final String FEATURE_KEY = "moderation";

    private static final String NODE_BAN  = "vsu.mod.ban";
    private static final String NODE_MUTE = "vsu.mod.mute";
    private static final String NODE_KICK = "vsu.mod.kick";
    private static final String NODE_WARN = "vsu.mod.warn";

    private static final int FALLBACK_BAN  = 3;
    private static final int FALLBACK_MUTE = 3;
    private static final int FALLBACK_KICK = 2;
    private static final int FALLBACK_WARN = 2;

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

    private ModerationCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerBan(d);
        registerTempBan(d);
        registerUnban(d);
        registerBanList(d);
        registerMute(d);
        registerTempMute(d);
        registerUnmute(d);
        registerKick(d);
        registerWarn(d);
        registerWarnings(d);
        registerClearWarnings(d);
    }

    // ── Suggestion providers ────────────────────────────────────────────────

    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS =
            (ctx, builder) -> {
                MinecraftServer server = ctx.getSource().getServer();
                Set<String> names = new HashSet<>();
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    names.add(sp.getGameProfile().getName());
                }
                CompletableFuture<Suggestions> fut = new CompletableFuture<>();
                VonixServerUtilities.dbAsync(() -> {
                    try {
                        names.addAll(PunishmentRepository.historicalNames());
                    } catch (Throwable ignore) {}
                    SuggestionsBuilder fresh = builder.restart();
                    SharedSuggestionProvider.suggest(names, fresh);
                    fut.complete(fresh.build());
                });
                return fut;
            };

    private static final SuggestionProvider<CommandSourceStack> DURATION_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"1h", "6h", "1d", "7d", "30d", "perm"}, builder);

    // ── /ban ────────────────────────────────────────────────────────────────

    private static void registerBan(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("ban")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_BAN, FALLBACK_BAN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> doBan(ctx, null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> doBan(ctx, StringArgumentType.getString(ctx, "reason")))))
                .executes(ctx -> usage(ctx, "/ban <player> [reason...]   Reason: griefing in spawn")));
    }

    private static int doBan(CommandContext<CommandSourceStack> ctx, String reason) {
        return resolveAndRun(ctx, (uuid, name) -> PunishmentService.ban(
                ctx.getSource().getServer(), uuid, name,
                issuerUuid(ctx), issuerName(ctx), reason, null));
    }

    // ── /tempban ────────────────────────────────────────────────────────────

    private static void registerTempBan(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tempban")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_BAN, FALLBACK_BAN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .suggests(DURATION_SUGGESTIONS)
                                .executes(ctx -> doTempBan(ctx, null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> doTempBan(ctx, StringArgumentType.getString(ctx, "reason"))))))
                .executes(ctx -> usage(ctx, "/tempban <player> <duration> [reason...]   Reason: chat spam — duration e.g. 7d, 1d12h, perm")));
    }

    private static int doTempBan(CommandContext<CommandSourceStack> ctx, String reason) {
        String durStr = StringArgumentType.getString(ctx, "duration");
        long parsed = DurationParser.parseSafe(durStr);
        if (parsed == DurationParser.INVALID) {
            ctx.getSource().sendFailure(new TextComponent(
                    "§c[VSU] Invalid duration: §f" + durStr + "§c. Examples: 1h, 6h, 1d, 7d, 1d12h, perm"));
            return 0;
        }
        Long millis = (parsed == DurationParser.PERMANENT) ? null : parsed;
        return resolveAndRun(ctx, (uuid, name) -> PunishmentService.ban(
                ctx.getSource().getServer(), uuid, name,
                issuerUuid(ctx), issuerName(ctx), reason, millis));
    }

    // ── /unban ──────────────────────────────────────────────────────────────

    private static void registerUnban(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("unban")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_BAN, FALLBACK_BAN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> resolveAndRun(ctx, (uuid, name) -> PunishmentService.unban(
                                ctx.getSource().getServer(), uuid, name, issuerName(ctx)))))
                .executes(ctx -> usage(ctx, "/unban <player>")));
    }

    // ── /banlist ────────────────────────────────────────────────────────────

    private static void registerBanList(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("banlist")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_BAN, FALLBACK_BAN))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> doBanList(ctx, IntegerArgumentType.getInteger(ctx, "page"))))
                .executes(ctx -> doBanList(ctx, 1)));
    }

    private static int doBanList(CommandContext<CommandSourceStack> ctx, int page) {
        MinecraftServer server = ctx.getSource().getServer();
        VonixServerUtilities.dbAsync(() -> {
            List<Punishment> rows = PunishmentRepository.list(Punishment.Type.BAN, page, 10);
            server.execute(() -> {
                if (rows.isEmpty()) {
                    ctx.getSource().sendSuccess(new TextComponent(
                            "§7[VSU] No active bans on page " + page + "."), false);
                    return;
                }
                ctx.getSource().sendSuccess(new TextComponent(
                        "§6[VSU] Active bans — page §e" + page + "§6:"), false);
                for (Punishment p : rows) {
                    String exp = p.expiresAt() == null ? "permanent"
                            : ISO.format(Instant.ofEpochMilli(p.expiresAt()));
                    ctx.getSource().sendSuccess(new TextComponent(
                            "§7- §e" + p.targetName() + " §7by §f" + p.issuerName()
                                    + " §7(" + exp + "): §f"
                                    + (p.reason() == null ? "(no reason)" : p.reason())), false);
                }
            });
        });
        return 1;
    }

    // ── /mute  /tempmute  /unmute ───────────────────────────────────────────

    private static void registerMute(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mute")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_MUTE, FALLBACK_MUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> doMute(ctx, null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> doMute(ctx, StringArgumentType.getString(ctx, "reason")))))
                .executes(ctx -> usage(ctx, "/mute <player> [reason...]   Reason: chat spam")));
    }

    private static int doMute(CommandContext<CommandSourceStack> ctx, String reason) {
        return resolveAndRun(ctx, (uuid, name) -> PunishmentService.mute(
                ctx.getSource().getServer(), uuid, name,
                issuerUuid(ctx), issuerName(ctx), reason, null));
    }

    private static void registerTempMute(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tempmute")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_MUTE, FALLBACK_MUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .suggests(DURATION_SUGGESTIONS)
                                .executes(ctx -> doTempMute(ctx, null))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> doTempMute(ctx, StringArgumentType.getString(ctx, "reason"))))))
                .executes(ctx -> usage(ctx, "/tempmute <player> <duration> [reason...]   Reason: chat spam — duration e.g. 1h, 30m, perm")));
    }

    private static int doTempMute(CommandContext<CommandSourceStack> ctx, String reason) {
        String durStr = StringArgumentType.getString(ctx, "duration");
        long parsed = DurationParser.parseSafe(durStr);
        if (parsed == DurationParser.INVALID) {
            ctx.getSource().sendFailure(new TextComponent(
                    "§c[VSU] Invalid duration: §f" + durStr + "§c. Examples: 1h, 6h, 1d, 7d, 1d12h, perm"));
            return 0;
        }
        Long millis = (parsed == DurationParser.PERMANENT) ? null : parsed;
        return resolveAndRun(ctx, (uuid, name) -> PunishmentService.mute(
                ctx.getSource().getServer(), uuid, name,
                issuerUuid(ctx), issuerName(ctx), reason, millis));
    }

    private static void registerUnmute(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("unmute")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_MUTE, FALLBACK_MUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> resolveAndRun(ctx, (uuid, name) -> PunishmentService.unmute(
                                ctx.getSource().getServer(), uuid, name, issuerName(ctx)))))
                .executes(ctx -> usage(ctx, "/unmute <player>")));
    }

    // ── /kick ───────────────────────────────────────────────────────────────

    private static void registerKick(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kick")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_KICK, FALLBACK_KICK))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> doKick(ctx, null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> doKick(ctx, StringArgumentType.getString(ctx, "reason")))))
                .executes(ctx -> usage(ctx, "/kick <player> [reason...]   Reason: AFK kick")));
    }

    private static int doKick(CommandContext<CommandSourceStack> ctx, String reason) {
        return resolveAndRun(ctx, (uuid, name) -> PunishmentService.kick(
                ctx.getSource().getServer(), uuid, name,
                issuerUuid(ctx), issuerName(ctx), reason));
    }

    // ── /warn  /warnings  /clearwarnings ────────────────────────────────────

    private static void registerWarn(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("warn")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_WARN, FALLBACK_WARN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> resolveAndRun(ctx, (uuid, name) -> PunishmentService.warn(
                                        ctx.getSource().getServer(), uuid, name,
                                        issuerUuid(ctx), issuerName(ctx),
                                        StringArgumentType.getString(ctx, "reason"))))))
                .executes(ctx -> usage(ctx, "/warn <player> <reason...>   Reason: please use spawn protection chat channel")));
    }

    private static void registerWarnings(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("warnings")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_WARN, FALLBACK_WARN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> doWarnings(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> doWarnings(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                .executes(ctx -> usage(ctx, "/warnings <player> [page]")));
    }

    private static int doWarnings(CommandContext<CommandSourceStack> ctx, int page) {
        return resolveAndRun(ctx, (uuid, name) -> {
            MinecraftServer server = ctx.getSource().getServer();
            VonixServerUtilities.dbAsync(() -> {
                List<Punishment> rows = PunishmentRepository.history(uuid, Punishment.Type.WARN, 10 * page);
                int from = (page - 1) * 10;
                int to   = Math.min(rows.size(), from + 10);
                server.execute(() -> {
                    if (from >= rows.size()) {
                        ctx.getSource().sendSuccess(new TextComponent(
                                "§7[VSU] §e" + name + "§7 has no warnings on page " + page + "."), false);
                        return;
                    }
                    ctx.getSource().sendSuccess(new TextComponent(
                            "§6[VSU] Warnings for §e" + name + " §6— page §e" + page), false);
                    for (int i = from; i < to; i++) {
                        Punishment p = rows.get(i);
                        String when = ISO.format(Instant.ofEpochMilli(p.issuedAt()));
                        String mark = p.active() ? "§e●" : "§8○";
                        ctx.getSource().sendSuccess(new TextComponent(
                                mark + " §7" + when + " §7by §f" + p.issuerName()
                                        + "§7: §f" + (p.reason() == null ? "(no reason)" : p.reason())), false);
                    }
                });
            });
        });
    }

    private static void registerClearWarnings(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("clearwarnings")
                .requires(PermissionGate.requires(FEATURE_KEY, NODE_WARN, FALLBACK_WARN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> resolveAndRun(ctx, (uuid, name) -> PunishmentService.clearWarnings(
                                ctx.getSource().getServer(), uuid, name, issuerName(ctx)))))
                .executes(ctx -> usage(ctx, "/clearwarnings <player>")));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface PlayerAction { void run(UUID uuid, String name); }

    private static int resolveAndRun(CommandContext<CommandSourceStack> ctx, PlayerAction action) {
        String raw = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        ServerPlayer online = server.getPlayerList().getPlayerByName(raw);
        if (online != null) {
            action.run(online.getUUID(), online.getGameProfile().getName());
            return 1;
        }
        Optional<GameProfile> profile = server.getProfileCache() == null
                ? Optional.empty()
                : server.getProfileCache().get(raw);
        if (profile.isPresent() && profile.get().getId() != null) {
            action.run(profile.get().getId(), profile.get().getName());
            return 1;
        }
        ctx.getSource().sendFailure(new TextComponent(
                "§c[VSU] Unknown player: §f" + raw + "§c (never joined this server)"));
        return 0;
    }

    private static UUID issuerUuid(CommandContext<CommandSourceStack> ctx) {
        Entity e = ctx.getSource().getEntity();
        return (e instanceof ServerPlayer p) ? p.getUUID() : null;
    }

    private static String issuerName(CommandContext<CommandSourceStack> ctx) {
        Entity e = ctx.getSource().getEntity();
        return (e instanceof ServerPlayer p) ? p.getGameProfile().getName() : "CONSOLE";
    }

    private static int usage(CommandContext<CommandSourceStack> ctx, String hint) {
        ctx.getSource().sendSuccess(new TextComponent("§7[VSU] Usage: §f" + hint), false);
        return 1;
    }
}
