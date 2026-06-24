package network.vonix.serverutilities.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.features.FeatureGate;
import network.vonix.serverutilities.inventory.InvseeContainer;
import network.vonix.serverutilities.inventory.AccessoryHelper;
import network.vonix.serverutilities.teleport.TeleportManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility commands:
 *   Teleport admin: /tp, /tphere, /tpall, /tppos, /setspawn
 *   Player info:    /nick, /seen, /whois, /ping, /near, /getpos, /playtime, /suicide, /list
 *   Messaging:      /msg, /tell, /r, /reply, /ignore
 *   Items:          /hat, /more, /clear, /repair
 *   Server:         /broadcast, /bc, /gc, /lag, /invsee, /enderchest, /workbench, /anvil
 *
 * None of these commands access the database, so they all run on the main tick thread.
 */
public final class UtilityCommands {

    // In-memory state (session-scoped)
    private static final Map<UUID, String> nicknames     = new ConcurrentHashMap<>();
    private static final Map<UUID, Long>   lastSeen      = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID>   lastMessaged  = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> ignoreList = new ConcurrentHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerTeleportCommands(dispatcher);
        registerPlayerUtilityCommands(dispatcher);
        registerMessagingCommands(dispatcher);
        registerItemCommands(dispatcher);
        registerServerCommands(dispatcher);
        VonixServerUtilities.LOGGER.info("[VonixSU] Utility commands registered.");
    }

    // ── Admin teleport ────────────────────────────────────────────────────────

    private static void registerTeleportCommands(CommandDispatcher<CommandSourceStack> d) {
        // /tp <player>  or  /tp <target> <destination>
        d.register(Commands.literal("tp")
                .requires(FeatureGate.requires("teleport_admin", s -> s.hasPermission(2)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> teleportTo(ctx, EntityArgument.getPlayer(ctx, "target")))
                        .then(Commands.argument("destination", EntityArgument.player())
                                .executes(ctx -> teleportPlayerTo(ctx,
                                        EntityArgument.getPlayer(ctx, "target"),
                                        EntityArgument.getPlayer(ctx, "destination"))))));

        // /tphere <player>
        d.register(Commands.literal("tphere")
                .requires(FeatureGate.requires("teleport_admin", s -> s.hasPermission(2)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> teleportHere(ctx, EntityArgument.getPlayer(ctx, "target")))));

        // /tpall
        d.register(Commands.literal("tpall")
                .requires(FeatureGate.requires("teleport_admin", s -> s.hasPermission(2)))
                .executes(UtilityCommands::teleportAll));

        // /tppos <x> <y> <z>
        d.register(Commands.literal("tppos")
                .requires(FeatureGate.requires("teleport_admin", s -> s.hasPermission(2)))
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(UtilityCommands::teleportPos)))));

        // /setspawn
        d.register(Commands.literal("setspawn")
                .requires(FeatureGate.requires("spawn", s -> s.hasPermission(2)))
                .executes(UtilityCommands::setSpawn));
    }

    private static int teleportTo(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        TeleportManager.getInstance().saveLastLocation(player);
        player.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(),
                target.getYRot(), target.getXRot());
        player.sendSystemMessage(Component.literal("§aTeleported to §e" + target.getName().getString()));
        return 1;
    }

    private static int teleportPlayerTo(CommandContext<CommandSourceStack> ctx,
                                         ServerPlayer target, ServerPlayer dest) {
        TeleportManager.getInstance().saveLastLocation(target);
        target.teleportTo(dest.serverLevel(), dest.getX(), dest.getY(), dest.getZ(),
                dest.getYRot(), dest.getXRot());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§aTeleported §e" + target.getName().getString() + "§a to §e" + dest.getName().getString()), true);
        target.sendSystemMessage(Component.literal(
                "§aYou were teleported to §e" + dest.getName().getString()));
        return 1;
    }

    private static int teleportHere(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        TeleportManager.getInstance().saveLastLocation(target);
        target.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal(
                "§aTeleported §e" + target.getName().getString() + "§a to you"));
        target.sendSystemMessage(Component.literal(
                "§aYou were teleported to §e" + player.getName().getString()));
        return 1;
    }

    private static int teleportAll(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        int count = 0;
        for (ServerPlayer t : player.server.getPlayerList().getPlayers()) {
            if (t != player) {
                TeleportManager.getInstance().saveLastLocation(t);
                t.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot());
                t.sendSystemMessage(Component.literal(
                        "§aYou were teleported to §e" + player.getName().getString()));
                count++;
            }
        }
        player.sendSystemMessage(Component.literal("§aTeleported §e" + count + "§a players to you"));
        return count;
    }

    private static int teleportPos(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        TeleportManager.getInstance().saveLastLocation(player);
        player.teleportTo(player.serverLevel(), x, y, z, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal(
                String.format("§aTeleported to §e%.1f, %.1f, %.1f", x, y, z)));
        return 1;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        BlockPos pos = player.blockPosition();
        player.serverLevel().setDefaultSpawnPos(pos, 0);
        player.sendSystemMessage(Component.literal(
                String.format("§aSpawn set to §e%d, %d, %d", pos.getX(), pos.getY(), pos.getZ())));
        return 1;
    }

    // ── Player info ───────────────────────────────────────────────────────────

    private static void registerPlayerUtilityCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("nick")
                .requires(FeatureGate.requires("utility"))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> setNickname(ctx, StringArgumentType.getString(ctx, "name"))))
                .executes(UtilityCommands::clearNickname));

        d.register(Commands.literal("seen")
                .requires(FeatureGate.requires("utility"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> showSeen(ctx, StringArgumentType.getString(ctx, "player")))));

        d.register(Commands.literal("whois")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> showWhois(ctx, EntityArgument.getPlayer(ctx, "target")))));

        d.register(Commands.literal("ping")
                .requires(FeatureGate.requires("utility"))
                .executes(UtilityCommands::showPing));

        d.register(Commands.literal("near")
                .requires(FeatureGate.requires("utility"))
                .executes(ctx -> showNear(ctx, 100))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                        .executes(ctx -> showNear(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));

        d.register(Commands.literal("getpos")
                .requires(FeatureGate.requires("utility"))
                .executes(UtilityCommands::getPos));
        d.register(Commands.literal("playtime")
                .requires(FeatureGate.requires("utility"))
                .executes(UtilityCommands::showPlaytime));
        d.register(Commands.literal("suicide")
                .requires(FeatureGate.requires("utility"))
                .executes(UtilityCommands::suicide));
        d.register(Commands.literal("list")
                .requires(FeatureGate.requires("utility"))
                .executes(UtilityCommands::showPlayerList));
    }

    private static int setNickname(CommandContext<CommandSourceStack> ctx, String name) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        String colored = name.replace("&", "§");
        UUID uuid = player.getUUID();
        nicknames.put(uuid, colored);
        // Write-through to SQLite on the DB executor.
        VonixServerUtilities.dbAsync(() ->
                VonixServerUtilities.getInstance().getDatabase().setNickname(uuid, colored));
        player.setCustomName(Component.literal(colored));
        player.setCustomNameVisible(false);
        broadcastTabListUpdate(player);
        player.sendSystemMessage(Component.literal("§aNickname set to: " + colored));
        return 1;
    }

    private static int clearNickname(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        UUID uuid = player.getUUID();
        nicknames.remove(uuid);
        VonixServerUtilities.dbAsync(() ->
                VonixServerUtilities.getInstance().getDatabase().deleteNickname(uuid));
        player.setCustomName(null);
        broadcastTabListUpdate(player);
        player.sendSystemMessage(Component.literal("§aNickname cleared."));
        return 1;
    }

    private static void broadcastTabListUpdate(ServerPlayer player) {
        var packet = new ClientboundPlayerInfoUpdatePacket(
                java.util.EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), java.util.List.of(player));
        player.server.getPlayerList().broadcastAll(packet);
    }

    public static String getNickname(UUID uuid) { return nicknames.get(uuid); }

    private static int showSeen(CommandContext<CommandSourceStack> ctx, String playerName) {
        if (ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName) != null) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("§e" + playerName + " §7is currently §aonline"), false);
        } else {
            Long ts = lastSeen.values().stream().findFirst().orElse(null); // placeholder
            ctx.getSource().sendSuccess(
                    () -> Component.literal("§e" + playerName + " §7is §coffline"), false);
        }
        return 1;
    }

    private static int showWhois(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        String name    = target.getName().getString();
        String display = nicknames.getOrDefault(target.getUUID(), name);
        int ping       = target.latency;
        BlockPos pos   = target.blockPosition();
        String dim     = target.level().dimension().location().toString();

        ctx.getSource().sendSuccess(() -> Component.literal("§6=== §e" + name + " §6==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Display: " + display), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7UUID: §f" + target.getUUID()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Ping: §f" + ping + "ms"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("§7Location: §f%d, %d, %d §7in §f%s", pos.getX(), pos.getY(), pos.getZ(), dim)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7Health: §c" + (int) target.getHealth() + "§7/§c" + (int) target.getMaxHealth()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7Food: §e" + target.getFoodData().getFoodLevel() + "§7/§e20"), false);
        return 1;
    }

    private static int showPing(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        int ping  = player.latency;
        String col = ping < 50 ? "§a" : ping < 150 ? "§e" : "§c";
        player.sendSystemMessage(Component.literal("§7Your ping: " + col + ping + "ms"));
        return 1;
    }

    private static int showNear(CommandContext<CommandSourceStack> ctx, int radius) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        List<String> nearby = new ArrayList<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player && other.level() == player.level()) {
                double dist = player.distanceTo(other);
                if (dist <= radius) nearby.add(String.format("§e%s §7(%.0fm)", other.getName().getString(), dist));
            }
        }
        if (nearby.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7No players within " + radius + " blocks."));
        } else {
            player.sendSystemMessage(Component.literal("§6Nearby: " + String.join(", ", nearby)));
        }
        return 1;
    }

    private static int getPos(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        BlockPos pos = player.blockPosition();
        player.sendSystemMessage(Component.literal(
                String.format("§7Position: §eX: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ())));
        return 1;
    }

    private static int showPlaytime(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        int ticks = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM, net.minecraft.stats.Stats.PLAY_TIME);
        long seconds = ticks / 20L;
        player.sendSystemMessage(Component.literal(
                String.format("§7Playtime: §e%dh %dm", seconds / 3600, (seconds % 3600) / 60)));
        return 1;
    }

    private static int suicide(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        player.kill();
        return 1;
    }

    private static int showPlayerList(CommandContext<CommandSourceStack> ctx) {
        var players = ctx.getSource().getServer().getPlayerList().getPlayers();
        int max     = ctx.getSource().getServer().getMaxPlayers();
        ctx.getSource().sendSuccess(
                () -> Component.literal("§6Players Online: §e" + players.size() + "/" + max), false);
        StringBuilder sb = new StringBuilder();
        for (ServerPlayer p : players) {
            if (!sb.isEmpty()) sb.append("§7, ");
            String nick = nicknames.get(p.getUUID());
            sb.append(nick != null ? nick : "§e" + p.getName().getString());
        }
        String list = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(list), false);
        return 1;
    }

    // ── Messaging ─────────────────────────────────────────────────────────────

    private static void registerMessagingCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("msg")
                .requires(FeatureGate.requires("utility"))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> sendMessage(ctx,
                                        EntityArgument.getPlayer(ctx, "target"),
                                        StringArgumentType.getString(ctx, "message"))))));

        d.register(Commands.literal("tell")
                .requires(FeatureGate.requires("utility"))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> sendMessage(ctx,
                                        EntityArgument.getPlayer(ctx, "target"),
                                        StringArgumentType.getString(ctx, "message"))))));

        d.register(Commands.literal("r")
                .requires(FeatureGate.requires("utility"))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> replyMessage(ctx, StringArgumentType.getString(ctx, "message")))));

        d.register(Commands.literal("reply")
                .requires(FeatureGate.requires("utility"))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> replyMessage(ctx, StringArgumentType.getString(ctx, "message")))));

        d.register(Commands.literal("ignore")
                .requires(FeatureGate.requires("utility"))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> toggleIgnore(ctx, EntityArgument.getPlayer(ctx, "target")))));
    }

    private static int sendMessage(CommandContext<CommandSourceStack> ctx,
                                    ServerPlayer target, String message) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sender)) return 0;
        Set<UUID> ignored = ignoreList.getOrDefault(target.getUUID(), Set.of());
        if (ignored.contains(sender.getUUID())) {
            sender.sendSystemMessage(Component.literal("§cThis player is ignoring you."));
            return 0;
        }
        sender.sendSystemMessage(Component.literal(
                "§7[§6me §7→ §e" + target.getName().getString() + "§7] §f" + message));
        target.sendSystemMessage(Component.literal(
                "§7[§e" + sender.getName().getString() + " §7→ §6me§7] §f" + message));
        lastMessaged.put(sender.getUUID(), target.getUUID());
        lastMessaged.put(target.getUUID(), sender.getUUID());
        return 1;
    }

    private static int replyMessage(CommandContext<CommandSourceStack> ctx, String message) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sender)) return 0;
        UUID lastUuid = lastMessaged.get(sender.getUUID());
        if (lastUuid == null) {
            sender.sendSystemMessage(Component.literal("§cNo one to reply to."));
            return 0;
        }
        ServerPlayer target = sender.server.getPlayerList().getPlayer(lastUuid);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("§cPlayer is offline."));
            return 0;
        }
        return sendMessage(ctx, target, message);
    }

    private static int toggleIgnore(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        UUID owner = player.getUUID();
        UUID tgt   = target.getUUID();
        Set<UUID> ignored = ignoreList.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet());
        if (ignored.remove(tgt)) {
            VonixServerUtilities.dbAsync(() ->
                    VonixServerUtilities.getInstance().getDatabase().removeIgnore(owner, tgt));
            player.sendSystemMessage(Component.literal("§aNo longer ignoring §e" + target.getName().getString()));
        } else {
            ignored.add(tgt);
            VonixServerUtilities.dbAsync(() ->
                    VonixServerUtilities.getInstance().getDatabase().addIgnore(owner, tgt));
            player.sendSystemMessage(Component.literal("§cNow ignoring §e" + target.getName().getString()));
        }
        return 1;
    }

    // ── Item commands ─────────────────────────────────────────────────────────

    private static void registerItemCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hat")
                .requires(FeatureGate.requires("utility"))
                .executes(UtilityCommands::wearHat));

        d.register(Commands.literal("more")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .executes(UtilityCommands::moreItems));

        d.register(Commands.literal("clear")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .executes(ctx -> clearInventory(ctx, null))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> clearInventory(ctx, EntityArgument.getPlayer(ctx, "target")))));

        d.register(Commands.literal("repair")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .executes(UtilityCommands::repairItem));
    }

    private static int wearHat(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        var hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cHold an item to wear as a hat."));
            return 0;
        }
        var helmet = player.getInventory().armor.get(3);
        player.getInventory().armor.set(3, hand.copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, helmet);
        player.sendSystemMessage(Component.literal("§aWearing §e" + hand.getHoverName().getString()));
        return 1;
    }

    private static int moreItems(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        var hand = player.getMainHandItem();
        if (hand.isEmpty()) return 0;
        hand.setCount(hand.getMaxStackSize());
        player.sendSystemMessage(Component.literal("§aStack filled to §e" + hand.getCount()));
        return 1;
    }

    private static int clearInventory(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (target == null && ctx.getSource().getEntity() instanceof ServerPlayer p) target = p;
        if (target == null) return 0;
        target.getInventory().clearContent();
        target.sendSystemMessage(Component.literal("§aInventory cleared."));
        return 1;
    }

    private static int repairItem(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        var hand = player.getMainHandItem();
        if (hand.isEmpty() || !hand.isDamageableItem()) {
            player.sendSystemMessage(Component.literal("§cHold a repairable item."));
            return 0;
        }
        hand.setDamageValue(0);
        player.sendSystemMessage(Component.literal("§aItem repaired."));
        return 1;
    }

    // ── Server management ─────────────────────────────────────────────────────

    private static void registerServerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("broadcast")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> broadcast(ctx, StringArgumentType.getString(ctx, "message")))));

        d.register(Commands.literal("bc")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> broadcast(ctx, StringArgumentType.getString(ctx, "message")))));

        d.register(Commands.literal("gc")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .executes(UtilityCommands::showServerStats));

        d.register(Commands.literal("lag")
                .requires(FeatureGate.requires("utility"))
                .executes(UtilityCommands::showLag));

        d.register(Commands.literal("invsee")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> openInventory(ctx, EntityArgument.getPlayer(ctx, "target")))));

        d.register(Commands.literal("backsee")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> openBackpack(ctx, EntityArgument.getPlayer(ctx, "target")))));

        d.register(Commands.literal("accsee")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> openAccessory(ctx, EntityArgument.getPlayer(ctx, "target")))));

        d.register(Commands.literal("enderchest")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .executes(ctx -> openEnderChest(ctx, null))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> openEnderChest(ctx, EntityArgument.getPlayer(ctx, "target")))));

        d.register(Commands.literal("workbench")
                .requires(FeatureGate.requires("utility"))
                .executes(UtilityCommands::openWorkbench));

        d.register(Commands.literal("anvil")
                .requires(FeatureGate.requires("utility", s -> s.hasPermission(2)))
                .executes(UtilityCommands::openAnvil));
    }

    private static int broadcast(CommandContext<CommandSourceStack> ctx, String message) {
        String colored = message.replace("&", "§");
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal("§4[Broadcast] §f" + colored));
        }
        return 1;
    }

    private static int showServerStats(CommandContext<CommandSourceStack> ctx) {
        Runtime rt  = Runtime.getRuntime();
        long used   = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long max    = rt.maxMemory() / 1024 / 1024;
        System.gc();
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Server Stats ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Memory: §e" + used + "MB §7/ §e" + max + "MB"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Threads: §e" + Thread.activeCount()), false);
        return 1;
    }

    private static int showLag(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§7TPS: §acheck F3 for debug info"), false);
        return 1;
    }

    private static int openInventory(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        InvseeContainer view = new InvseeContainer(target);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, playerInv, p) -> net.minecraft.world.inventory.ChestMenu.sixRows(id, playerInv, view),
                Component.literal("\u00a76[INVSEE] \u00a7e" + target.getName().getString())));
        return 1;
    }

    private static int openBackpack(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        // PORT-NOTE: 1.21 used DataComponents.CONTAINER + ItemContainerContents (added in 1.20.5).
        // On 1.20.1 we read/write the legacy NBT layout (Items / inventory / BlockEntityTag.Items).
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = target.getInventory().getItem(i);
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag != null) {
                net.minecraft.nbt.ListTag listTag = null;
                if (tag.contains("Items", 9)) {
                    listTag = tag.getList("Items", 10);
                } else if (tag.contains("inventory", 9)) {
                    listTag = tag.getList("inventory", 10);
                } else if (tag.contains("BlockEntityTag", 10)) {
                    net.minecraft.nbt.CompoundTag bet = tag.getCompound("BlockEntityTag");
                    if (bet.contains("Items", 9)) {
                        listTag = bet.getList("Items", 10);
                    }
                }

                if (listTag != null) {
                    final int slotIndex = i;
                    final net.minecraft.world.item.ItemStack finalStack = stack;
                    net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(54) {
                        @Override
                        public void setChanged() {
                            super.setChanged();
                            net.minecraft.nbt.ListTag newListTag = new net.minecraft.nbt.ListTag();
                            for (int j = 0; j < this.getContainerSize(); j++) {
                                net.minecraft.world.item.ItemStack item = this.getItem(j);
                                if (!item.isEmpty()) {
                                    net.minecraft.nbt.CompoundTag itemTag = new net.minecraft.nbt.CompoundTag();
                                    itemTag.putByte("Slot", (byte) j);
                                    item.save(itemTag);
                                    newListTag.add(itemTag);
                                }
                            }
                            net.minecraft.nbt.CompoundTag t = finalStack.getOrCreateTag();
                            if (t.contains("BlockEntityTag", 10)) {
                                t.getCompound("BlockEntityTag").put("Items", newListTag);
                            } else if (t.contains("inventory", 9)) {
                                t.put("inventory", newListTag);
                            } else {
                                t.put("Items", newListTag);
                            }
                            target.getInventory().setItem(slotIndex, finalStack);
                            target.inventoryMenu.sendAllDataToRemote();
                        }
                    };

                    for (int j = 0; j < listTag.size(); j++) {
                        net.minecraft.nbt.CompoundTag itemTag = listTag.getCompound(j);
                        int slot = itemTag.getByte("Slot") & 255;
                        if (slot >= 0 && slot < container.getContainerSize()) {
                            container.setItem(slot, net.minecraft.world.item.ItemStack.of(itemTag));
                        }
                    }

                    player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inv, p) -> net.minecraft.world.inventory.ChestMenu.sixRows(id, inv, container),
                            Component.literal("Backpack: " + target.getName().getString())));
                    return 1;
                }
            }
        }
        player.sendSystemMessage(Component.literal("No backpack found"));
        return 0;
    }

    private static int openEnderChest(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        final ServerPlayer enderTarget = target != null ? target : player;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv,
                        enderTarget.getEnderChestInventory()),
                Component.literal("Ender Chest")));
        return 1;
    }

    private static int openAccessory(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        AccessoryHelper.openAccessoryMenu(target, player);
        return 1;
    }

    private static int openWorkbench(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new net.minecraft.world.inventory.CraftingMenu(id, inv,
                        net.minecraft.world.inventory.ContainerLevelAccess.create(
                                player.level(), player.blockPosition())),
                Component.literal("Crafting")));
        return 1;
    }

    private static int openAnvil(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new net.minecraft.world.inventory.AnvilMenu(id, inv,
                        net.minecraft.world.inventory.ContainerLevelAccess.create(
                                player.level(), player.blockPosition())),
                Component.literal("Anvil")));
        return 1;
    }

    // ── Lifecycle hooks (called from EventHandler) ────────────────────────────

    /**
     * Rehydrate persisted state into the in-memory caches.
     * Called once at SERVER_STARTING on the DB executor thread.
     */
    public static void hydrateFromDb() {
        try {
            var db = VonixServerUtilities.getInstance().getDatabase();
            for (String[] row : db.getAllNicknames()) {
                try { nicknames.put(UUID.fromString(row[0]), row[1]); }
                catch (Exception ignore) {}
            }
            for (String[] row : db.getAllIgnores()) {
                try {
                    UUID owner  = UUID.fromString(row[0]);
                    UUID target = UUID.fromString(row[1]);
                    ignoreList.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(target);
                } catch (Exception ignore) {}
            }
            VonixServerUtilities.LOGGER.info(
                    "[VonixSU] Hydrated {} nicknames, {} ignore-list owners from DB.",
                    nicknames.size(), ignoreList.size());
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] UtilityCommands.hydrateFromDb failed", e);
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        // Re-apply persisted nickname so it survives restarts.
        String nick = nicknames.get(player.getUUID());
        if (nick != null) {
            player.setCustomName(Component.literal(nick));
            player.setCustomNameVisible(false);
            broadcastTabListUpdate(player);
        }
    }

    /** Legacy overload kept for source compat with old EventHandler calls. */
    public static void onPlayerJoin(UUID uuid) {
        // Reserved — see overload above.
    }

    public static void onPlayerLeave(UUID uuid) {
        lastSeen.put(uuid, System.currentTimeMillis());
        // Drop only transient session state; nicknames + ignoreList are persisted
        // and stay in cache so messaging checks still work for offline targets.
        lastMessaged.remove(uuid);
    }
}
