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

        // â”€â”€ Weather â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        d.register(Commands.literal("weather")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("clear").executes(ctx -> setWeather(ctx, "clear", 6000)))
                .then(Commands.literal("rain").executes(ctx -> setWeather(ctx, "rain", 6000)))
                .then(Commands.literal("storm").executes(ctx -> setWeather(ctx, "storm", 6000)))
                .then(Commands.literal("thunder").executes(ctx -> setWeather(ctx, "storm", 6000))));

        d.register(Commands.literal("sun")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> setWeather(ctx, "clear", 24000)));

        // /rain and /storm registered as aliases (only if not already claimed above)
        // Using unique literals to avoid collision with the /weather sub-commands.
        d.register(Commands.literal("rain")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> setWeather(ctx, "rain", 6000)));

        d.register(Commands.literal("storm")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> setWeather(ctx, "storm", 6000)));

        // â”€â”€ Time â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        d.register(Commands.literal("time")
                .requires(s -> s.hasPermission(2))
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
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> setTime(ctx, 1000)));

        d.register(Commands.literal("night")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> setTime(ctx, 13000)));

        // â”€â”€ Lightning â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        d.register(Commands.literal("lightning")
                .requires(s -> s.hasPermission(2))
                .executes(WorldCommands::lightningAtPlayer)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> lightningAtTarget(ctx, EntityArgument.getPlayer(ctx, "target")))));

        d.register(Commands.literal("smite")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> lightningAtTarget(ctx, EntityArgument.getPlayer(ctx, "target")))));

        // â”€â”€ Extinguish â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        d.register(Commands.literal("ext")
                .executes(WorldCommands::extinguishSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> extinguishTarget(ctx, EntityArgument.getPlayer(ctx, "target")))));

        // â”€â”€ AFK â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        d.register(Commands.literal("afk")
                .executes(ctx -> toggleAfk(ctx, null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> toggleAfk(ctx, StringArgumentType.getString(ctx, "message")))));

        VonixServerUtilities.LOGGER.info("[VonixSU] World commands registered.");
    }

    // â”€â”€ Weather â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static int setWeather(CommandContext<CommandSourceStack> ctx, String type, int duration) {
        ServerLevel level = ctx.getSource().getLevel();
        switch (type) {
            case "clear" -> {
                level.setWeatherParameters(duration, 0, false, false);
                ctx.getSource().sendSuccess(new net.minecraft.network.chat.TextComponent("Â§aWeather set to clear."), true);
            }
            case "rain" -> {
                level.setWeatherParameters(0, duration, true, false);
                ctx.getSource().sendSuccess(new net.minecraft.network.chat.TextComponent("Â§aWeather set to rain."), true);
            }
            case "storm" -> {
                level.setWeatherParameters(0, duration, true, true);
                ctx.getSource().sendSuccess(new net.minecraft.network.chat.TextComponent("Â§aWeather set to thunderstorm."), true);
            }
        }
        return 1;
    }

    // â”€â”€ Time â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static int setTime(CommandContext<CommandSourceStack> ctx, int ticks) {
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            level.setDayTime(ticks);
        }
        ctx.getSource().sendSuccess(new net.minecraft.network.chat.TextComponent("Â§aTime set to Â§e" + ticks + "Â§a ticks."), true);
        return 1;
    }

    private static int addTime(CommandContext<CommandSourceStack> ctx, int ticks) {
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            level.setDayTime(level.getDayTime() + ticks);
        }
        ctx.getSource().sendSuccess(new net.minecraft.network.chat.TextComponent("Â§aAdded Â§e" + ticks + "Â§a ticks."), true);
        return 1;
    }

    // â”€â”€ Lightning â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static int lightningAtPlayer(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        spawnLightning(player);
        player.sendMessage(new net.minecraft.network.chat.TextComponent("Â§eLightning struck at your location!"), net.minecraft.Util.NIL_UUID);
        return 1;
    }

    private static int lightningAtTarget(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        spawnLightning(target);
        ctx.getSource().sendSuccess(
                new net.minecraft.network.chat.TextComponent("Â§eStruck Â§6" + target.getName().getString() + "Â§e with lightning!"), true);
        target.sendMessage(new net.minecraft.network.chat.TextComponent("Â§cYou were struck by lightning!"), net.minecraft.Util.NIL_UUID);
        return 1;
    }

    private static void spawnLightning(ServerPlayer player) {
        var bolt = EntityType.LIGHTNING_BOLT.create(((net.minecraft.server.level.ServerLevel) player.level));
        if (bolt != null) {
            bolt.moveTo(player.getX(), player.getY(), player.getZ());
            ((net.minecraft.server.level.ServerLevel) player.level).addFreshEntity(bolt);
        }
    }

    // â”€â”€ Extinguish â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static int extinguishSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        player.clearFire();
        player.sendMessage(new net.minecraft.network.chat.TextComponent("Â§aYou have been extinguished."), net.minecraft.Util.NIL_UUID);
        return 1;
    }

    private static int extinguishTarget(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        target.clearFire();
        ctx.getSource().sendSuccess(
                new net.minecraft.network.chat.TextComponent("Â§aExtinguished Â§e" + target.getName().getString()), true);
        target.sendMessage(new net.minecraft.network.chat.TextComponent("Â§aYou have been extinguished."), net.minecraft.Util.NIL_UUID);
        return 1;
    }

    // â”€â”€ AFK â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static int toggleAfk(CommandContext<CommandSourceStack> ctx, String message) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        UUID uuid = player.getUUID();

        if (afkTime.containsKey(uuid)) {
            afkTime.remove(uuid);
            afkMessage.remove(uuid);
            broadcastAll(player.server, "Â§7" + player.getName().getString() + " is no longer AFK.");
            player.sendMessage(new net.minecraft.network.chat.TextComponent("Â§aYou are no longer AFK."), net.minecraft.Util.NIL_UUID);
        } else {
            afkTime.put(uuid, System.currentTimeMillis());
            if (message != null) afkMessage.put(uuid, message);
            String text = message != null
                    ? "Â§7" + player.getName().getString() + " is now AFK: " + message
                    : "Â§7" + player.getName().getString() + " is now AFK.";
            broadcastAll(player.server, text);
            player.sendMessage(new net.minecraft.network.chat.TextComponent(
                    "Â§eYou are now AFK" + (message != null ? ": " + message : ".")), net.minecraft.Util.NIL_UUID);
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
            p.sendMessage(new net.minecraft.network.chat.TextComponent(message), net.minecraft.Util.NIL_UUID);
        }
    }
}

