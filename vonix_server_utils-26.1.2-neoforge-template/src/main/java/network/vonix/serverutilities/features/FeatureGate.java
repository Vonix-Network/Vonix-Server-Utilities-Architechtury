package network.vonix.serverutilities.features;

import net.minecraft.commands.CommandSourceStack;

import java.util.function.Predicate;

/**
 * Helper for Brigadier {@code .requires(...)} predicates that gate commands
 * on a runtime feature key.
 *
 * <p>When a feature is disabled the predicate returns false, which causes
 * Brigadier to (a) omit the command from tab-completion / the player's
 * command tree and (b) reject the command at parse time with the normal
 * "unknown command" reply. Console users with full perms see the same
 * behaviour — disabled means disabled for everyone, no escape hatch.
 *
 * <p>The predicate is re-evaluated every time {@code server.getCommands()
 * .sendCommands(player)} is called, so a flag flip followed by a resend
 * is enough for clients to see the new command set without relogging.
 *
 * <p>PORT-NOTE: {@link CommandSourceStack} is the mapped command-source type
 * on this target. Older supported ports use the same fully-qualified name,
 * so no platform shim is required.
 */
public final class FeatureGate {

    private FeatureGate() {}

    /** Returns a predicate that is true iff the named feature is enabled. */
    public static Predicate<CommandSourceStack> requires(String featureKey) {
        return source -> FeatureRegistry.getInstance().isEnabled(featureKey);
    }

    /** Same, but AND-combined with an additional predicate (perm checks etc). */
    public static Predicate<CommandSourceStack> requires(String featureKey,
                                                         Predicate<CommandSourceStack> and) {
        return source -> FeatureRegistry.getInstance().isEnabled(featureKey) && and.test(source);
    }
}
