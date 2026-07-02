package network.vonix.serverutilities.features;

import net.minecraft.commands.CommandSourceStack;
import network.vonix.serverutilities.donation_ranks.LuckPermsBridge;

import java.util.function.Predicate;

/**
 * Composed permission + feature gate helper.
 *
 * <p>This is the canonical entry point every command's {@code .requires(...)}
 * chain should use. It composes the existing {@link FeatureGate} (runtime
 * feature flag) with a proper permission check. Semantics (v1.6.1+):
 * <ul>
 *     <li>The console (non-player {@link CommandSourceStack}) always passes —
 *         command blocks, functions, server console all keep working.</li>
 *     <li>If LuckPerms is on the classpath and grants the node, the check
 *         passes.</li>
 *     <li>Otherwise the source's vanilla {@code hasPermission(opFallback)}
 *         op-level check is used. This is the UNION path — a player with
 *         op-level {@code opFallback} passes even when LuckPerms is present
 *         but has not been configured with the node yet. Commands whose
 *         {@code opFallback} is 0 (basic player commands like {@code /home},
 *         {@code /tpa}, {@code /msg}) remain open to everyone by default
 *         even on LP-installed servers with no per-node config.</li>
 * </ul>
 *
 * <p><b>v1.6.0 note.</b> v1.6.0 shipped LP as authoritative: when LP was
 * present, the op-level fallback was never consulted, so an unset node
 * read as "deny" for every non-console source. That silently locked out
 * basic player commands on every server that installed LP without also
 * running the {@code default-player} recipe from {@code docs/PERMISSIONS.md}.
 * v1.6.1 changes the composition to LP-OR-op so op-level fallback is
 * always available as a floor, restoring v1.5.x behaviour for unconfigured
 * LP installs while still honouring explicit LP grants for non-op players.</p>
 *
 * <p>This class intentionally exposes ONLY plain Java + Brigadier types so
 * it can be linked safely regardless of LuckPerms presence — the LP probe
 * stays inside {@link LuckPermsBridge}, which is holder-class-isolated from
 * LP types.
 */
public final class PermissionGate {

    private PermissionGate() {}

    /**
     * True iff the source is console, holds {@code node} in LuckPerms, or
     * satisfies the {@code opFallback} op-level. LP grant and op-fallback
     * form a UNION — either alone is sufficient.
     */
    public static boolean check(CommandSourceStack source, String node, int opFallback) {
        if (!source.isPlayer()) return true; // console / command blocks / functions
        if (LuckPermsBridge.isPresent()
                && LuckPermsBridge.hasPermission(source.getPlayer().getUUID(), node)) {
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
