package network.vonix.serverutilities.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.admin.AdminManager;
import network.vonix.serverutilities.config.ModConfig;
import network.vonix.serverutilities.homes.HomeManager;
import network.vonix.serverutilities.kits.KitManager;
import network.vonix.serverutilities.teleport.TeleportManager;
import network.vonix.serverutilities.warps.WarpManager;

import java.util.UUID;

/**
 * Registers all Vonix Server Utilities commands.
 *
 * DB-bound operations (homes, warps, kits) are dispatched to the VonixSU-DB
 * executor thread. Minecraft side-effects are always bounced back to the main
 * tick thread via {@code server.execute()}, keeping the tick thread lag-free.
 */
public final class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerHome(dispatcher);
        registerTpa(dispatcher);
        registerBack(dispatcher);
        registerWarps(dispatcher);
        registerKits(dispatcher);
        registerAdmin(dispatcher);
        registerSpawn(dispatcher);
        registerVonixSu(dispatcher);
    }

    // â”€â”€ Shared helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static String formatTime(int seconds) {
        if (seconds < 60)   return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // HOME
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static void registerHome(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("home")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> goHome(ctx, StringArgumentType.getString(ctx, "name"))))
                .executes(ctx -> goHome(ctx, "home")));

        d.register(Commands.literal("sethome")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> setHome(ctx, StringArgumentType.getString(ctx, "name"))))
                .executes(ctx -> setHome(ctx, "home")));

        d.register(Commands.literal("delhome")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> delHome(ctx, StringArgumentType.getString(ctx, "name")))));

        d.register(Commands.literal("homes").executes(ModCommands::listHomes));
    }

    private static int goHome(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        UUID uuid = player.getUUID();

        VonixServerUtilities.dbAsync(() -> {
            HomeManager.Home home = HomeManager.getInstance().getHome(uuid, name);
            server.execute(() -> {
                if (home == null) {
                    player.sendSystemMessage(Component.literal("Â§c[VSU] Home '" + name + "' not found."));
                    return;
                }
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.dimension().location().toString().equals(home.world())) {
                        TeleportManager.getInstance().teleportPlayer(
                                player, level, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
                        player.sendSystemMessage(Component.literal("Â§a[VSU] Teleported to home '" + name + "'."));
                        return;
                    }
                }
                player.sendSystemMessage(Component.literal("Â§c[VSU] Home world is unavailable."));
            });
        });
        return 1;
    }

    private static int setHome(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();

        // Capture player state on the main thread before going async
        UUID uuid    = player.getUUID();
        String world = player.level().dimension().location().toString();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYRot(), pitch = player.getXRot();
        int maxHomes = ModConfig.INSTANCE.getMaxHomes();

        VonixServerUtilities.dbAsync(() -> {
            boolean ok = HomeManager.getInstance().setHome(uuid, name, world, x, y, z, yaw, pitch);
            server.execute(() -> {
                if (ok) player.sendSystemMessage(Component.literal("Â§a[VSU] Home '" + name + "' set."));
                else    player.sendSystemMessage(Component.literal(
                        "Â§c[VSU] Home limit reached (" + maxHomes + ")."));
            });
        });
        return 1;
    }

    private static int delHome(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        UUID uuid = player.getUUID();

        VonixServerUtilities.dbAsync(() -> {
            boolean ok = HomeManager.getInstance().deleteHome(uuid, name);
            server.execute(() -> {
                if (ok) player.sendSystemMessage(Component.literal("Â§a[VSU] Home '" + name + "' deleted."));
                else    player.sendSystemMessage(Component.literal("Â§c[VSU] Home '" + name + "' not found."));
            });
        });
        return 1;
    }

    private static int listHomes(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        UUID uuid = player.getUUID();
        int maxHomes = ModConfig.INSTANCE.getMaxHomes();

        VonixServerUtilities.dbAsync(() -> {
            var homes = HomeManager.getInstance().getHomes(uuid);
            server.execute(() -> {
                if (homes.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Â§7[VSU] You have no homes set."));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "Â§6[VSU] Your homes Â§7(" + homes.size() + "/" + maxHomes + "): Â§e"
                            + String.join(", ", homes.stream().map(HomeManager.Home::name).toList())));
                }
            });
        });
        return 1;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // TPA
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static void registerTpa(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tpa")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> sendTpa(ctx, false))));

        d.register(Commands.literal("tpahere")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> sendTpa(ctx, true))));

        d.register(Commands.literal("tpaccept").executes(ModCommands::tpAccept));
        d.register(Commands.literal("tpdeny").executes(ModCommands::tpDeny));
    }

    private static int sendTpa(CommandContext<CommandSourceStack> ctx, boolean tpaHere)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        if (player.getUUID().equals(target.getUUID())) {
            player.sendSystemMessage(Component.literal("Â§c[VSU] You cannot TPA to yourself."));
            return 0;
        }
        if (!TeleportManager.getInstance().sendTpaRequest(player, target, tpaHere)) {
            player.sendSystemMessage(Component.literal(
                    "Â§c[VSU] " + target.getName().getString() + " already has a pending request."));
            return 0;
        }
        if (tpaHere) {
            player.sendSystemMessage(Component.literal(
                    "Â§a[VSU] Requested " + target.getName().getString() + " to teleport to you."));
            target.sendSystemMessage(Component.literal(
                    "Â§e[VSU] " + player.getName().getString()
                    + " wants you to teleport to them. Use Â§6/tpaccept Â§eor Â§6/tpdenyÂ§e."));
        } else {
            player.sendSystemMessage(Component.literal(
                    "Â§a[VSU] Sent teleport request to " + target.getName().getString() + "."));
            target.sendSystemMessage(Component.literal(
                    "Â§e[VSU] " + player.getName().getString()
                    + " wants to teleport to you. Use Â§6/tpaccept Â§eor Â§6/tpdenyÂ§e."));
        }
        return 1;
    }

    private static int tpAccept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (TeleportManager.getInstance().acceptTpaRequest(player, ctx.getSource().getServer())) {
            player.sendSystemMessage(Component.literal("Â§a[VSU] Teleport accepted."));
            return 1;
        }
        player.sendSystemMessage(Component.literal("Â§c[VSU] No pending teleport request."));
        return 0;
    }

    private static int tpDeny(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (TeleportManager.getInstance().denyTpaRequest(player, ctx.getSource().getServer())) {
            player.sendSystemMessage(Component.literal("Â§c[VSU] Teleport request denied."));
            return 1;
        }
        player.sendSystemMessage(Component.literal("Â§c[VSU] No pending teleport request."));
        return 0;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // BACK / BACKDEATH
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static void registerBack(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("back").executes(ModCommands::back));
        d.register(Commands.literal("backdeath").executes(ModCommands::backDeath));
    }

    private static int back(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        TeleportManager.Location loc = TeleportManager.getInstance().getLastLocation(player.getUUID());
        if (loc == null) {
            player.sendSystemMessage(Component.literal("Â§c[VSU] No previous location to return to."));
            return 0;
        }
        return doBack(ctx, player, loc, "Â§a[VSU] Returned to your previous location.");
    }

    private static int backDeath(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        TeleportManager.Location loc = TeleportManager.getInstance().getDeathLocation(player.getUUID());
        if (loc == null) {
            player.sendSystemMessage(Component.literal("Â§c[VSU] No death location on record."));
            return 0;
        }
        int delay = ModConfig.INSTANCE.getDeathBackDelaySeconds();
        if (delay > 0) {
            long elapsed = (System.currentTimeMillis() - loc.timestamp()) / 1000L;
            if (elapsed < delay) {
                player.sendSystemMessage(Component.literal(
                        "Â§c[VSU] Wait Â§e" + formatTime((int) (delay - elapsed))
                        + " Â§cbefore returning to your death location."));
                return 0;
            }
        }
        return doBack(ctx, player, loc, "Â§a[VSU] Returned to your death location.");
    }

    private static int doBack(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                               TeleportManager.Location loc, String msg) {
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            if (level.dimension().location().toString().equals(loc.world())) {
                TeleportManager.getInstance().teleportPlayer(
                        player, level, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                player.sendSystemMessage(Component.literal(msg));
                return 1;
            }
        }
        player.sendSystemMessage(Component.literal("Â§c[VSU] That world is no longer available."));
        return 0;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // WARPS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static void registerWarps(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("warp")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> teleportWarp(ctx, StringArgumentType.getString(ctx, "name"))))
                .executes(ModCommands::listWarps));

        d.register(Commands.literal("warps").executes(ModCommands::listWarps));

        d.register(Commands.literal("setwarp")
                .requires(s -> s.hasPermission(3))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> setWarp(ctx, StringArgumentType.getString(ctx, "name")))));

        d.register(Commands.literal("delwarp")
                .requires(s -> s.hasPermission(3))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> deleteWarp(ctx, StringArgumentType.getString(ctx, "name")))));
    }

    private static int setWarp(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();

        UUID uuid    = player.getUUID();
        String world = player.level().dimension().location().toString();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYRot(), pitch = player.getXRot();

        VonixServerUtilities.dbAsync(() -> {
            boolean ok = WarpManager.getInstance().setWarp(name, uuid, world, x, y, z, yaw, pitch);
            server.execute(() -> {
                if (ok) ctx.getSource().sendSuccess(
                        () -> Component.literal("Â§a[VSU] Warp '" + name + "' created."), true);
            });
        });
        return 1;
    }

    private static int teleportWarp(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();

        VonixServerUtilities.dbAsync(() -> {
            WarpManager.Warp warp = WarpManager.getInstance().getWarp(name);
            server.execute(() -> {
                if (warp == null) {
                    player.sendSystemMessage(Component.literal("Â§c[VSU] Warp '" + name + "' not found."));
                    return;
                }
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.dimension().location().toString().equals(warp.world())) {
                        TeleportManager.getInstance().teleportPlayer(
                                player, level, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
                        player.sendSystemMessage(Component.literal("Â§a[VSU] Warped to '" + name + "'."));
                        return;
                    }
                }
                player.sendSystemMessage(Component.literal("Â§c[VSU] Warp world is unavailable."));
            });
        });
        return 1;
    }

    private static int deleteWarp(CommandContext<CommandSourceStack> ctx, String name) {
        MinecraftServer server = ctx.getSource().getServer();

        VonixServerUtilities.dbAsync(() -> {
            boolean ok = WarpManager.getInstance().deleteWarp(name);
            server.execute(() -> {
                if (ok) ctx.getSource().sendSuccess(
                        () -> Component.literal("Â§a[VSU] Warp '" + name + "' deleted."), true);
                else ctx.getSource().sendFailure(
                        Component.literal("Â§c[VSU] Warp '" + name + "' not found."));
            });
        });
        return 1;
    }

    private static int listWarps(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();

        VonixServerUtilities.dbAsync(() -> {
            var warps = WarpManager.getInstance().getWarps();
            server.execute(() -> {
                if (warps.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("Â§7[VSU] No warps available."), false);
                } else {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Â§6[VSU] Warps: Â§e" + String.join(", ",
                                    warps.stream().map(WarpManager.Warp::name).toList())), false);
                }
            });
        });
        return 1;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // KITS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static void registerKits(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kit")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> kitCommand(ctx, StringArgumentType.getString(ctx, "name"))))
                .executes(ModCommands::listKits));

        d.register(Commands.literal("kits").executes(ModCommands::listKits));
    }

    private static int kitCommand(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        UUID uuid = player.getUUID();

        VonixServerUtilities.dbAsync(() -> {
            KitManager.ClaimResult result = KitManager.getInstance().checkAndClaim(uuid, name);
            server.execute(() -> {
                switch (result.status()) {
                    case SUCCESS -> {
                        KitManager.getInstance().distributeItems(player, name);
                        player.sendSystemMessage(Component.literal("Â§a[VSU] Kit '" + name + "' received!"));
                    }
                    case NOT_FOUND ->
                        player.sendSystemMessage(Component.literal("Â§c[VSU] Kit '" + name + "' not found."));
                    case ON_COOLDOWN ->
                        player.sendSystemMessage(Component.literal(
                                "Â§c[VSU] Kit on cooldown â€” Â§e" + formatTime(result.remainingSeconds()) + " Â§cremaining."));
                    case ALREADY_CLAIMED ->
                        player.sendSystemMessage(Component.literal("Â§c[VSU] You have already claimed this one-time kit."));
                }
            });
        });
        return 1;
    }

    private static int listKits(CommandContext<CommandSourceStack> ctx) {
        var kits = KitManager.getInstance().getKitNames();
        if (kits.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Â§7[VSU] No kits available."), false);
        } else {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Â§6[VSU] Kits: Â§e" + String.join(", ", kits)), false);
        }
        return 1;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // ADMIN (heal, feed, fly, god, vanish, gm) â€” main-thread only, no DB
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("heal")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) AdminManager.getInstance().healPlayer(p);
                    return 1;
                })
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            AdminManager.getInstance().healPlayer(target);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Â§a[VSU] Healed Â§e" + target.getName().getString()), true);
                            return 1;
                        })));

        d.register(Commands.literal("feed")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) AdminManager.getInstance().feedPlayer(p);
                    return 1;
                })
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            AdminManager.getInstance().feedPlayer(target);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Â§a[VSU] Fed Â§e" + target.getName().getString()), true);
                            return 1;
                        })));

        d.register(Commands.literal("fly")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) AdminManager.getInstance().toggleFly(p);
                    return 1;
                })
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            AdminManager.getInstance().toggleFly(EntityArgument.getPlayer(ctx, "player"));
                            return 1;
                        })));

        d.register(Commands.literal("god")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) AdminManager.getInstance().toggleGodMode(p);
                    return 1;
                }));

        d.register(Commands.literal("vanish")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) AdminManager.getInstance().toggleVanish(p, ctx.getSource().getServer());
                    return 1;
                }));

        d.register(Commands.literal("gm")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("0").executes(ctx -> setGameMode(ctx, net.minecraft.world.level.GameType.SURVIVAL)))
                .then(Commands.literal("1").executes(ctx -> setGameMode(ctx, net.minecraft.world.level.GameType.CREATIVE)))
                .then(Commands.literal("2").executes(ctx -> setGameMode(ctx, net.minecraft.world.level.GameType.ADVENTURE)))
                .then(Commands.literal("3").executes(ctx -> setGameMode(ctx, net.minecraft.world.level.GameType.SPECTATOR)))
                .then(Commands.literal("s").executes(ctx -> setGameMode(ctx, net.minecraft.world.level.GameType.SURVIVAL)))
                .then(Commands.literal("c").executes(ctx -> setGameMode(ctx, net.minecraft.world.level.GameType.CREATIVE)))
                .then(Commands.literal("a").executes(ctx -> setGameMode(ctx, net.minecraft.world.level.GameType.ADVENTURE)))
                .then(Commands.literal("sp").executes(ctx -> setGameMode(ctx, net.minecraft.world.level.GameType.SPECTATOR))));
    }

    private static int setGameMode(CommandContext<CommandSourceStack> ctx, net.minecraft.world.level.GameType mode) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p != null) {
            p.setGameMode(mode);
            p.sendSystemMessage(Component.literal("Â§a[VSU] Gamemode set to " + mode.getName()));
        }
        return 1;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SPAWN
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static void registerSpawn(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("spawn").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            if (p == null) return 0;
            var spawnPos = ctx.getSource().getServer().overworld().getSharedSpawnPos();
            TeleportManager.getInstance().teleportPlayer(
                    p, ctx.getSource().getServer().overworld(),
                    spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0f, 0f);
            p.sendSystemMessage(Component.literal("Â§a[VSU] Teleported to spawn."));
            return 1;
        }));
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // /vonixsu â€” admin meta-command
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static void registerVonixSu(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("vonixsu")
                .requires(s -> s.hasPermission(3))
                .then(Commands.literal("version").executes(ModCommands::showVersion))
                .then(Commands.literal("status").executes(ModCommands::showStatus))
                .then(Commands.literal("reload")
                        .executes(ModCommands::reloadConfig))
                .executes(ModCommands::showHelp));
    }

    private static int showVersion(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("Â§6[VSU] Â§fVersion: Â§e" + VonixServerUtilities.VERSION), false);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Â§7Platform: Architectury 1.18.2"), false);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        int players = ctx.getSource().getServer().getPlayerList().getPlayerCount();
        int max     = ctx.getSource().getServer().getMaxPlayers();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Â§6[VSU] Â§fStatus: Â§aOnline Â§7(" + players + "/" + max + ")"), false);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Â§7Modules: homes, warps, kits, TPA, back, admin, world"), false);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("Â§e[VSU] Config reloads require a server restart."), true);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("Â§6Â§l=== Vonix Server Utilities ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Â§e/vonixsu version Â§7â€” show version"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Â§e/vonixsu status  Â§7â€” show server status"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Â§e/vonixsu reload  Â§7â€” reload info"), false);
        return 1;
    }
}

