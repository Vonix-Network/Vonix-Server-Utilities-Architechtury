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
 * All other semantics match the 1.21.1 reference: console always passes;
 * LP grant and op-fallback form a UNION (v1.6.1+).
 *
 * <p><b>v1.6.0 note.</b> v1.6.0 shipped LP as authoritative: when LP was
 * present, the op-level fallback was never consulted, so an unset node
 * read as "deny" for every non-console source. That silently locked out
 * basic player commands on every server that installed LP without also
 * running the {@code default-player} recipe from {@code docs/PERMISSIONS.md}.
 * v1.6.1 changes the composition to LP-OR-op so op-level fallback is
 * always available as a floor, restoring v1.5.x behaviour for unconfigured
 * LP installs while still honouring explicit LP grants for non-op players.
 */
public final class PermissionGate {

    private PermissionGate() {}

    /**
     * True iff the source is console, holds {@code node} in LuckPerms, or
     * satisfies the {@code opFallback} op-level. LP grant and op-fallback
     * form a UNION — either alone is sufficient.
     */
    public static boolean check(CommandSourceStack source, String node, int opFallback) {
        Entity e = source.getEntity();
        if (!(e instanceof ServerPlayer player)) return true; // console / command blocks / functions
        if (LuckPermsBridge.isPresent()
                && LuckPermsBridge.hasPermission(player.getUUID(), node)) {
            return true;
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
