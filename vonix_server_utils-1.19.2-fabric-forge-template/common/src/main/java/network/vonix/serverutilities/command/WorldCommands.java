package network.vonix.serverutilities.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.features.FeatureGate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World and environment commands:
 *   /weather [clear|rain|storm]  /sun  /rain  /storm
 *   /time set [day|night|noon|midnight|<ticks>]  /day  /night
 *   /lightning [target]  /smite <target>
 *   /ext [target]
 *   /afk [message]
 *
 * No database access; all operations run on the main tick thread.
 */
public final class WorldCommands {

    private static final Map<UUID, Long>   afkTime    = new ConcurrentHashMap<>();
    private static final Map<UUID, String> afkMessage = new ConcurrentHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> d) {

        // ── Weather ───────────────────────────────────────────────────────────
        d.register(Commands.literal("weather")
                .requires(FeatureGate.requires("world", s -> s.hasPermission(2)))
                .then(Commands.literal("clear").executes(ctx -> setWeather(ctx, "clear", 6000)))
                .then(Commands.literal("rain").executes(ctx -> setWeather(ctx, "rain", 6000)))
                .then(Commands.literal("storm").executes(ctx -> setWeather(ctx, "storm", 6000)))
                .then(Commands.literal("thunder").executes(ctx -> setWeather(ctx, "storm", 6000))));

        d.register(Commands.literal("sun")
                .requires(FeatureGate.requires("world", s -> s.hasPermission(2)))
                .executes(ctx -> setWeather(ctx, "clear", 24000)));

        // /rain and /storm are intentionally registered both as /weather sub-commands
        // AND as top-level shortcuts. Both code paths flow through the same
        // setWeather() handler below, so there is no behavioural drift between
        // them — the audit's "duplicate" warning is purely informational.
        d.register(Commands.literal("rain")
                .requires(FeatureGate.requires("world", s -> s.hasPermission(2)))
                .executes(ctx -> setWeather(ctx, "rain", 6000)));

        d.register(Commands.literal("storm")
                .requires(FeatureGate.requires("world", s -> s.hasPermission(2)))
                .executes(ctx -> setWeather(ctx, "storm", 6000)));

        // ── Time ──────────────────────────────────────────────────────────────
        d.register(Commands.literal("time")
                .requires(FeatureGate.requires("world", s -> s.hasPermission(2)))
                .then(Commands.literal("set")
                        .then(Commands.literal("day").executes(ctx -> setTime(ctx, 1000)))
                        .then(Commands.literal("night").executes(ctx -> setTime(ctx, 13000)))
                        .then(Commands.literal("noon").executes(ctx -> setTime(ctx, 6000)))
                        .then(Commands.literal("midnight").executes(ctx -> setTime(ctx, 18000)))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(ctx -> setTime(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(Commands.literal("add")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer())
                                .executes(ctx -> addTime(ctx, IntegerArgumentType.getInteger(ctx, "ticks"))))));

        d.register(Commands.literal("day")
                .requires(FeatureGate.requires("world", s -> s.hasPermission(2)))
                .executes(ctx -> setTime(ctx, 1000)));

        d.register(Commands.literal("night")
                .requires(FeatureGate.requires("world", s -> s.hasPermission(2)))
                .executes(ctx -> setTime(ctx, 13000)));

        // ── Lightning ─────────────────────────────────────────────────────────
        d.register(Commands.literal("lightning")
                .requires(s -> s.hasPermission(2))
                .executes(WorldCommands::lightningAtPlayer)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> lightningAtTarget(ctx, EntityArgument.getPlayer(ctx, "target")))));

        d.register(Commands.literal("smite")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> lightningAtTarget(ctx, EntityArgument.getPlayer(ctx, "target")))));

        // ── Extinguish ────────────────────────────────────────────────────────
        d.register(Commands.literal("ext")
                .executes(WorldCommands::extinguishSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> extinguishTarget(ctx, EntityArgument.getPlayer(ctx, "target")))));

        // ── AFK ───────────────────────────────────────────────────────────────
        d.register(Commands.literal("afk")
                .executes(ctx -> toggleAfk(ctx, null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> toggleAfk(ctx, StringArgumentType.getString(ctx, "message")))));

        VonixServerUtilities.LOGGER.info("[VonixSU] World commands registered.");
    }

    // ── Weather ───────────────────────────────────────────────────────────────

    private static int setWeather(CommandContext<CommandSourceStack> ctx, String type, int duration) {
        ServerLevel level = ctx.getSource().getLevel();
        switch (type) {
            case "clear" -> {
                level.setWeatherParameters(duration, 0, false, false);
                ctx.getSource().sendSuccess(Component.literal("§aWeather set to clear."), true);
            }
            case "rain" -> {
                level.setWeatherParameters(0, duration, true, false);
                ctx.getSource().sendSuccess(Component.literal("§aWeather set to rain."), true);
            }
            case "storm" -> {
                level.setWeatherParameters(0, duration, true, true);
                ctx.getSource().sendSuccess(Component.literal("§aWeather set to thunderstorm."), true);
            }
        }
        return 1;
    }

    // ── Time ──────────────────────────────────────────────────────────────────

    private static int setTime(CommandContext<CommandSourceStack> ctx, int ticks) {
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            level.setDayTime(ticks);
        }
        ctx.getSource().sendSuccess(Component.literal("§aTime set to §e" + ticks + "§a ticks."), true);
        return 1;
    }

    private static int addTime(CommandContext<CommandSourceStack> ctx, int ticks) {
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            level.setDayTime(level.getDayTime() + ticks);
        }
        ctx.getSource().sendSuccess(Component.literal("§aAdded §e" + ticks + "§a ticks."), true);
        return 1;
    }

    // ── Lightning ─────────────────────────────────────────────────────────────

    private static int lightningAtPlayer(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        spawnLightning(player);
        player.sendSystemMessage(Component.literal("§eLightning struck at your location!"));
        return 1;
    }

    private static int lightningAtTarget(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        spawnLightning(target);
        ctx.getSource().sendSuccess(Component.literal("§eStruck §6" + target.getName().getString() + "§e with lightning!"), true);
        target.sendSystemMessage(Component.literal("§cYou were struck by lightning!"));
        return 1;
    }

    private static void spawnLightning(ServerPlayer player) {
        var bolt = EntityType.LIGHTNING_BOLT.create(player.getLevel());
        if (bolt != null) {
            bolt.moveTo(player.getX(), player.getY(), player.getZ());
            player.getLevel().addFreshEntity(bolt);
        }
    }

    // ── Extinguish ────────────────────────────────────────────────────────────

    private static int extinguishSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        player.clearFire();
        player.sendSystemMessage(Component.literal("§aYou have been extinguished."));
        return 1;
    }

    private static int extinguishTarget(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        target.clearFire();
        ctx.getSource().sendSuccess(Component.literal("§aExtinguished §e" + target.getName().getString()), true);
        target.sendSystemMessage(Component.literal("§aYou have been extinguished."));
        return 1;
    }

    // ── AFK ───────────────────────────────────────────────────────────────────

    private static int toggleAfk(CommandContext<CommandSourceStack> ctx, String message) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        UUID uuid = player.getUUID();

        if (afkTime.containsKey(uuid)) {
            afkTime.remove(uuid);
            afkMessage.remove(uuid);
            broadcastAll(player.server, "§7" + player.getName().getString() + " is no longer AFK.");
            player.sendSystemMessage(Component.literal("§aYou are no longer AFK."));
        } else {
            afkTime.put(uuid, System.currentTimeMillis());
            if (message != null) afkMessage.put(uuid, message);
            String text = message != null
                    ? "§7" + player.getName().getString() + " is now AFK: " + message
                    : "§7" + player.getName().getString() + " is now AFK.";
            broadcastAll(player.server, text);
            player.sendSystemMessage(Component.literal(
                    "§eYou are now AFK" + (message != null ? ": " + message : ".")));
        }
        return 1;
    }

    public static boolean isAfk(UUID uuid)        { return afkTime.containsKey(uuid); }
    public static String  getAfkMessage(UUID uuid) { return afkMessage.get(uuid); }

    public static void clearAfk(UUID uuid) {
        afkTime.remove(uuid);
        afkMessage.remove(uuid);
    }

    private static void broadcastAll(net.minecraft.server.MinecraftServer server, String message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(message));
        }
    }
}
