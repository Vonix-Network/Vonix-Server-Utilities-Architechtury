package network.vonix.serverutilities.features;

import net.minecraft.commands.CommandSourceStack;
import network.vonix.serverutilities.donation_ranks.LuckPermsBridge;

import java.util.function.Predicate;

/**
 * Composed permission + feature gate helper.
 *
 * <p>This is the canonical entry point every command's {@code .requires(...)}
 * chain should use. It composes the existing {@link FeatureGate} (runtime
 * feature flag) with a proper permission check:
 * <ul>
 *     <li>If LuckPerms is on the classpath ({@link LuckPermsBridge#isPresent()}),
 *         the permission node is consulted via {@code LuckPermsBridge.hasPermission}.</li>
 *     <li>Otherwise the source's vanilla {@code hasPermission(opFallback)}
 *         op-level check is used.</li>
 *     <li>The console (non-player {@link CommandSourceStack}) always passes —
 *         command blocks, functions, server console all keep working.</li>
 * </ul>
 *
 * <p>This class intentionally exposes ONLY plain Java + Brigadier types so
 * it can be linked safely regardless of LuckPerms presence — the LP probe
 * stays inside {@link LuckPermsBridge}, which is holder-class-isolated from
 * LP types.
 */
public final class PermissionGate {

    private PermissionGate() {}

    /**
     * True iff the source has {@code node} (LP path) or satisfies the
     * {@code opFallback} op-level (vanilla path). Console always passes.
     */
    public static boolean check(CommandSourceStack source, String node, int opFallback) {
        if (!source.isPlayer()) return true; // console / command blocks / functions
        if (LuckPermsBridge.isPresent()) {
            return LuckPermsBridge.hasPermission(source.getPlayer().getUUID(), node);
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
