package network.vonix.serverutilities.features;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import network.vonix.serverutilities.donation_ranks.LuckPermsBridge;

import java.util.function.Predicate;

/**
 * Composed permission + feature gate helper.
 *
 * <p>1.18.2 adaptation: {@code CommandSourceStack.isPlayer()} and {@code getPlayer()}
 * don't exist on 1.18.2 — we test {@code getEntity() instanceof ServerPlayer} instead.
 * All other semantics match the 1.21.1 reference: console always passes; LP node is
 * consulted when LP is present; otherwise vanilla op-level fallback.
 */
public final class PermissionGate {

    private PermissionGate() {}

    /**
     * True iff the source has {@code node} (LP path) or satisfies the
     * {@code opFallback} op-level (vanilla path). Console always passes.
     */
    public static boolean check(CommandSourceStack source, String node, int opFallback) {
        Entity e = source.getEntity();
        if (!(e instanceof ServerPlayer player)) return true; // console / command blocks / functions
        if (LuckPermsBridge.isPresent()) {
            return LuckPermsBridge.hasPermission(player.getUUID(), node);
        }
        return source.hasPermission(opFallback);
    }

    /** Predicate suitable for Brigadier {@code .requires()} — permission only. */
    public static Predicate<CommandSourceStack> requires(String node, int opFallback) {
        return s -> check(s, node, opFallback);
    }

    /**
     * Composed: feature flag AND permission gate. THIS is the primary helper
     * every command should use.
     */
    public static Predicate<CommandSourceStack> requires(String featureKey,
                                                        String node,
                                                        int opFallback) {
        return FeatureGate.requires(featureKey, requires(node, opFallback));
    }
}
