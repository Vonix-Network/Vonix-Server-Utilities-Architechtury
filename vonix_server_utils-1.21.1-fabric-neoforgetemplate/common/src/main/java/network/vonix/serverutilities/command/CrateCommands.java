package network.vonix.serverutilities.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.crates.CrateRepository;
import network.vonix.serverutilities.features.PermissionGate;

import java.util.function.Consumer;

/**
 * Brigadier adapter for command-backed crates and typed virtual keys.
 * Database work is serialized off-thread; Minecraft command execution and
 * player messaging always return to the server thread.
 */
public final class CrateCommands {
    private static final String FEATURE = "crates";
    private static final String PLAYER_NODE = "vsu.command.crate";
    private static final String ADMIN_NODE = "vsu.admin.crate";
    private static final int ADMIN_OP_LEVEL = 2;

    private CrateCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("crate")
                .requires(PermissionGate.requires(FEATURE, PLAYER_NODE, 0));
        root.then(Commands.literal("list").executes(CrateCommands::listCrates));
        root.then(Commands.literal("open").then(Commands.argument("crate", StringArgumentType.word()).executes(CrateCommands::openCrate)));
        root.then(Commands.literal("prizes").then(Commands.argument("crate", StringArgumentType.word()).executes(CrateCommands::listPrizes)));

        var key = Commands.literal("key");
        key.then(Commands.literal("balance").then(Commands.argument("type", StringArgumentType.word()).executes(CrateCommands::balance)));
        var giveAmount = Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000)).executes(CrateCommands::giveKeys);
        var giveType = Commands.argument("type", StringArgumentType.word()).then(giveAmount);
        var givePlayer = Commands.argument("player", EntityArgument.player()).then(giveType);
        key.then(Commands.literal("give").requires(PermissionGate.requires(FEATURE, ADMIN_NODE, ADMIN_OP_LEVEL)).then(givePlayer));
        var takeAmount = Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000)).executes(CrateCommands::takeKeys);
        var takeType = Commands.argument("type", StringArgumentType.word()).then(takeAmount);
        var takePlayer = Commands.argument("player", EntityArgument.player()).then(takeType);
        key.then(Commands.literal("take").requires(PermissionGate.requires(FEATURE, ADMIN_NODE, ADMIN_OP_LEVEL)).then(takePlayer));
        root.then(key);

        root.then(Commands.literal("create").requires(PermissionGate.requires(FEATURE, ADMIN_NODE, ADMIN_OP_LEVEL))
                .then(Commands.argument("crate", StringArgumentType.word())
                        .then(Commands.argument("key_type", StringArgumentType.word()).executes(CrateCommands::createCrate))));
        root.then(Commands.literal("delete").requires(PermissionGate.requires(FEATURE, ADMIN_NODE, ADMIN_OP_LEVEL))
                .then(Commands.argument("crate", StringArgumentType.word()).executes(CrateCommands::deleteCrate)));
        root.then(Commands.literal("enable").requires(PermissionGate.requires(FEATURE, ADMIN_NODE, ADMIN_OP_LEVEL))
                .then(Commands.argument("crate", StringArgumentType.word()).executes(ctx -> setEnabled(ctx, true))));
        root.then(Commands.literal("disable").requires(PermissionGate.requires(FEATURE, ADMIN_NODE, ADMIN_OP_LEVEL))
                .then(Commands.argument("crate", StringArgumentType.word()).executes(ctx -> setEnabled(ctx, false))));

        var prize = Commands.literal("prize").requires(PermissionGate.requires(FEATURE, ADMIN_NODE, ADMIN_OP_LEVEL));
        var addCommand = Commands.argument("command", StringArgumentType.greedyString()).executes(CrateCommands::addPrize);
        var addWeight = Commands.argument("weight", IntegerArgumentType.integer(1, 1_000_000)).then(addCommand);
        var addLabel = Commands.argument("label", StringArgumentType.word()).then(addWeight);
        var addCrate = Commands.argument("crate", StringArgumentType.word()).then(addLabel);
        prize.then(Commands.literal("add").then(addCrate));
        var removeId = Commands.argument("id", IntegerArgumentType.integer(1)).executes(CrateCommands::removePrize);
        prize.then(Commands.literal("remove").then(Commands.argument("crate", StringArgumentType.word()).then(removeId)));
        root.then(prize);
        dispatcher.register(root);
    }

    private static int listCrates(CommandContext<CommandSourceStack> context) {
        return database(context, CrateRepository.getInstance()::listCrates, crates -> {
            if (crates.isEmpty()) {
                reply(context, "§7[VSU] No crates are configured.");
                return;
            }
            reply(context, "§6[VSU] Crates:");
            for (CrateRepository.CrateInfo crate : crates) {
                reply(context, "§e- " + crate.name() + " §7(key=" + crate.keyType() + ", " + (crate.enabled() ? "enabled" : "disabled") + ")");
            }
        });
    }

    private static int listPrizes(CommandContext<CommandSourceStack> context) {
        String crate = StringArgumentType.getString(context, "crate");
        return database(context, () -> CrateRepository.getInstance().listPrizes(crate), prizes -> {
            if (prizes.isEmpty()) {
                reply(context, "§c[VSU] No active prizes are configured for '" + crate + "'.");
                return;
            }
            reply(context, "§6[VSU] Prize percentages for " + crate + ":");
            for (CrateRepository.PrizeView prize : prizes) {
                reply(context, String.format("§e#%d §f%s §7%.2f%% §8(weight %d)", prize.id(), prize.label(), prize.percentage(), prize.weight()));
            }
        });
    }

    private static int createCrate(CommandContext<CommandSourceStack> context) {
        String crate = StringArgumentType.getString(context, "crate");
        String keyType = StringArgumentType.getString(context, "key_type");
        return database(context, () -> CrateRepository.getInstance().createCrate(crate, keyType), created ->
                reply(context, created ? "§a[VSU] Crate '" + crate + "' created with key type '" + keyType + "'." : "§c[VSU] Crate already exists."));
    }

    private static int deleteCrate(CommandContext<CommandSourceStack> context) {
        String crate = StringArgumentType.getString(context, "crate");
        return database(context, () -> CrateRepository.getInstance().deleteCrate(crate), deleted ->
                reply(context, deleted ? "§a[VSU] Crate '" + crate + "' deleted." : "§c[VSU] Crate not found."));
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        String crate = StringArgumentType.getString(context, "crate");
        return database(context, () -> CrateRepository.getInstance().setCrateEnabled(crate, enabled), changed ->
                reply(context, changed ? "§a[VSU] Crate '" + crate + "' is now " + (enabled ? "enabled" : "disabled") + "." : "§c[VSU] Crate not found."));
    }

    private static int addPrize(CommandContext<CommandSourceStack> context) {
        String crate = StringArgumentType.getString(context, "crate");
        String label = StringArgumentType.getString(context, "label");
        int weight = IntegerArgumentType.getInteger(context, "weight");
        String command = StringArgumentType.getString(context, "command");
        return database(context, () -> CrateRepository.getInstance().addPrize(crate, label, command, weight), added ->
                reply(context, added ? "§a[VSU] Prize added to '" + crate + "'." : "§c[VSU] Crate not found."));
    }

    private static int removePrize(CommandContext<CommandSourceStack> context) {
        String crate = StringArgumentType.getString(context, "crate");
        int id = IntegerArgumentType.getInteger(context, "id");
        return database(context, () -> CrateRepository.getInstance().removePrize(crate, id), removed ->
                reply(context, removed ? "§a[VSU] Prize removed." : "§c[VSU] Prize not found."));
    }

    private static int giveKeys(CommandContext<CommandSourceStack> context) {
        return adjustKeys(context, 1);
    }

    private static int takeKeys(CommandContext<CommandSourceStack> context) {
        return adjustKeys(context, -1);
    }

    private static int adjustKeys(CommandContext<CommandSourceStack> context, int direction) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            String type = StringArgumentType.getString(context, "type");
            int amount = IntegerArgumentType.getInteger(context, "amount") * direction;
            return database(context, () -> CrateRepository.getInstance().adjustKeys(target.getUUID(), type, amount), balance -> {
                if (balance < 0) {
                    reply(context, "§c[VSU] Key balance cannot go below zero.");
                } else {
                    reply(context, "§a[VSU] " + target.getGameProfile().getName() + " now has " + balance + " " + type + " key(s).");
                }
            });
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("§c[VSU] Invalid player or key arguments."));
            return 0;
        }
    }

    private static int balance(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            reply(context, "§c[VSU] This command must be run by a player.");
            return 0;
        }
        String type = StringArgumentType.getString(context, "type");
        return database(context, () -> CrateRepository.getInstance().getBalance(player.getUUID(), type), amount ->
                reply(context, "§6[VSU] " + type + " keys: §e" + amount));
    }

    private static int openCrate(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            reply(context, "§c[VSU] This command must be run by a player.");
            return 0;
        }
        String crate = StringArgumentType.getString(context, "crate");
        MinecraftServer server = context.getSource().getServer();
        return database(context, () -> CrateRepository.getInstance().open(player.getUUID(), crate), result -> {
            if (!result.success()) {
                reply(context, "§c[VSU] Cannot open crate: " + result.error());
                return;
            }
            String command = result.command().replace("{player}", player.getGameProfile().getName());
            server.execute(() -> {
                try {
                    int commandResult = server.getCommands().performCommand(
                            server.createCommandSourceStack().withSuppressedOutput(), command);
                    if (commandResult > 0) {
                        VonixServerUtilities.dbAsync(() -> completeClaim(server, context, player, result));
                    } else {
                        VonixServerUtilities.dbAsync(() -> refundClaim(server, context, player, result));
                    }
                } catch (Throwable throwable) {
                    VonixServerUtilities.LOGGER.error("[VSU] Prize command failed for claim {}", result.claimId(), throwable);
                    VonixServerUtilities.dbAsync(() -> refundClaim(server, context, player, result));
                }
            });
        });
    }

    private static void completeClaim(MinecraftServer server, CommandContext<CommandSourceStack> context, ServerPlayer player, CrateRepository.OpenResult result) {
        try {
            CrateRepository.getInstance().completeClaim(result.claimId());
            server.execute(() -> reply(context, "§a[VSU] You won: §e" + result.label()));
        } catch (Exception exception) {
            VonixServerUtilities.LOGGER.error("[VSU] Could not complete crate claim {}", result.claimId(), exception);
        }
    }

    private static void refundClaim(MinecraftServer server, CommandContext<CommandSourceStack> context, ServerPlayer player, CrateRepository.OpenResult result) {
        try {
            if (CrateRepository.getInstance().refundClaim(result.claimId())) {
                server.execute(() -> reply(context, "§c[VSU] Prize command failed; your key was refunded."));
            }
        } catch (Exception exception) {
            VonixServerUtilities.LOGGER.error("[VSU] Could not refund crate claim {}", result.claimId(), exception);
        }
    }

    private static <T> int database(CommandContext<CommandSourceStack> context, SqlWork<T> work, Consumer<T> reply) {
        MinecraftServer server = context.getSource().getServer();
        VonixServerUtilities.dbAsync(() -> {
            try {
                T value = work.get();
                server.execute(() -> reply.accept(value));
            } catch (Throwable throwable) {
                VonixServerUtilities.LOGGER.error("[VSU] Crate/key operation failed", throwable);
                server.execute(() -> context.getSource().sendFailure(Component.literal("§c[VSU] Operation failed; check the server log.")));
            }
        });
        return 1;
    }

    private static void reply(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(Component.literal(message), false);
    }

    @FunctionalInterface
    private interface SqlWork<T> { T get() throws Exception; }
}
