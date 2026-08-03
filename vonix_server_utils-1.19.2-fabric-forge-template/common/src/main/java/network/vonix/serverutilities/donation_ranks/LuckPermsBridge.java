package network.vonix.serverutilities.donation_ranks;

import network.vonix.serverutilities.VonixServerUtilities;

import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Probe-only public surface for the LuckPerms integration.
 *
 * <p><b>This class deliberately imports zero {@code net.luckperms.*} types.</b>
 * Why: when the JVM links this class (first reference from {@code RankSyncTask}
 * etc.), it must be able to resolve every type appearing in its constant pool.
 * If a type from {@code net.luckperms.api.*} appears anywhere in the public
 * shape of this class and LP isn't on the classpath, linking throws
 * {@link NoClassDefFoundError} BEFORE any code in this class runs — meaning
 * the try/catch we used to have around {@code LuckPermsProvider.get()} would
 * never get a chance to fire. That's the v1.4.0 crash class: Sunlit Cobblemon
 * (and any modpack without LuckPerms) crashed player join with NCDFE on
 * {@code net/luckperms/api/node/Node}.
 *
 * <p>Fix — holder-class isolation (JDK canonical pattern, see
 * {@code java.lang.invoke.MethodHandleStatics}):
 * <ul>
 *   <li>This class is pure Java types. The LP-presence probe runs in a
 *       static initialiser via {@link Class#forName(String, boolean, ClassLoader)}
 *       with {@code initialize=false} — that only LOADS the LP class, it
 *       doesn't link any signature of ours against it.</li>
 *   <li>Every public method first checks the cached {@link #LP_PRESENT} flag
 *       and short-circuits to an empty/no-op result when LP is absent.</li>
 *   <li>The real LP-typed code lives in package-private {@link LuckPermsBridgeImpl}.
 *       Each call into {@code LuckPermsBridgeImpl} is INSIDE a method body
 *       (not a static field), so the JVM defers linking the Impl class until
 *       the first call site is reached — which only happens when
 *       {@code LP_PRESENT == true}. When LP is missing, {@code LuckPermsBridgeImpl}
 *       is never linked, the LP-typed signatures it carries never get
 *       resolved, and NCDFE cannot fire.</li>
 * </ul>
 *
 * <p>All publicly exposed types are plain Java ({@code Optional<String>},
 * {@code Set<String>}, etc.) or VSU-local types ({@link Diff},
 * {@link GroupEnsureResult}, {@link UserPrefixInfo}). No LP type ever
 * appears on the public surface.
 */
public final class LuckPermsBridge {

    /** Group names we refuse to touch under any circumstances. */
    private static final Set<String> RESERVED_GROUPS = Set.of(
            "default", "admin", "op", "owner", "vonix"
    );

    /**
     * Resolved once, in this class's static initialiser, with
     * {@code initialize=false} so loading LP's marker class does not
     * trigger any LP-side static init. If false, every method on this
     * class is a guaranteed no-op and {@link LuckPermsBridgeImpl} is
     * never referenced.
     */
    private static final boolean LP_PRESENT;
    private static final AtomicBoolean warnedAbsent = new AtomicBoolean(false);

    static {
        boolean present = false;
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider",
                    false, LuckPermsBridge.class.getClassLoader());
            present = true;
        } catch (Throwable ignored) {
            // ClassNotFoundException / LinkageError / SecurityException — all fail-closed.
        }
        LP_PRESENT = present;
        if (!present) {
            VonixServerUtilities.LOGGER.info(
                    "[VonixSU] LuckPerms not detected on classpath — donation-rank sync disabled.");
        }
    }

    private LuckPermsBridge() {}

    /** True iff LuckPerms is on the classpath. Cheap, side-effect-free. */
    public static boolean isPresent() {
        return LP_PRESENT;
    }

    /** True if the name is on the hard-coded refusal list. */
    public static boolean isReservedGroupName(String name) {
        return name != null && RESERVED_GROUPS.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Idempotently ensure a LuckPerms group exists with the given weight,
     * chat prefix, and metadata. Safe to call on every startup.
     *
     * @throws IllegalArgumentException if {@code groupName} is on the reserved list.
     */
    public static CompletableFuture<GroupEnsureResult> ensureGroupExists(String groupName,
                                                                        int weight,
                                                                        String prefix,
                                                                        Map<String, String> meta,
                                                                        List<String> permissions) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("groupName must be non-blank");
        }
        if (isReservedGroupName(groupName)) {
            throw new IllegalArgumentException(
                    "Refusing to manage reserved LuckPerms group: " + groupName);
        }
        if (!LP_PRESENT) {
            return CompletableFuture.completedFuture(GroupEnsureResult.SKIPPED);
        }
        try {
            return LuckPermsBridgeImpl.ensureGroupExists(groupName, weight, prefix, meta, permissions);
        } catch (LinkageError | RuntimeException t) {
            logBridgeFailureOnce("ensureGroupExists", t);
            return CompletableFuture.completedFuture(GroupEnsureResult.ERROR);
        }
    }

    /**
     * Reconcile a player's group memberships against the donation tiers they
     * SHOULD have.
     *
     * <p>The diff is computed strictly inside {@code allManagedGroups}: groups
     * outside that set are never added and never removed.
     */
    public static CompletableFuture<Diff> setUserGroups(UUID player,
                                                       Set<String> shouldHaveGroups,
                                                       Set<String> allManagedGroups) {
        if (!LP_PRESENT) {
            return CompletableFuture.completedFuture(Diff.EMPTY);
        }
        try {
            return LuckPermsBridgeImpl.setUserGroups(player, shouldHaveGroups, allManagedGroups);
        } catch (LinkageError | RuntimeException t) {
            logBridgeFailureOnce("setUserGroups", t);
            return CompletableFuture.completedFuture(Diff.EMPTY);
        }
    }

    /**
     * Resolve a user's cached metadata (prefix, suffix, {@code name-color}
     * meta value) — used by the chat formatter. Returns {@link Optional#empty()}
     * if LP is absent, the user is unknown, or any reflective probe fails.
     * All returned strings are plain Java {@link String}; no LP type leaks
     * across the surface.
     */
    public static Optional<UserPrefixInfo> getUserPrefixInfo(UUID player) {
        if (!LP_PRESENT) return Optional.empty();
        try {
            return LuckPermsBridgeImpl.getUserPrefixInfo(player);
        } catch (LinkageError | RuntimeException t) {
            logBridgeFailureOnce("getUserPrefixInfo", t);
            return Optional.empty();
        }
    }

    private static void logBridgeFailureOnce(String where, Throwable t) {
        if (warnedAbsent.compareAndSet(false, true)) {
            VonixServerUtilities.LOGGER.warn(
                    "[VonixSU] LuckPerms bridge call '{}' failed (will not warn again this run): {}: {}",
                    where, t.getClass().getSimpleName(), t.getMessage());
        }
    }

    // ── Result types (all VSU-local, no LP types) ───────────────────────────

    /**
     * Synchronous, cached permission check for {@code player} against
     * {@code node}. Returns {@code false} on any failure path: LP absent,
     * user not loaded, exception thrown — fail-closed by design.
     *
     * <p>Callers ({@code PermissionGate}) gate this behind {@link #isPresent()},
     * so a {@code false} returned here from an LP-present server unambiguously
     * means "user doesn't hold the node" and not "couldn't check".
     */
    public static boolean hasPermission(UUID player, String node) {
        if (!LP_PRESENT) return false;
        if (player == null || node == null || node.isEmpty()) return false;
        try {
            return LuckPermsBridgeImpl.hasPermission(player, node);
        } catch (LinkageError | RuntimeException t) {
            logBridgeFailureOnce("hasPermission", t);
            return false;
        }
    }

    public enum GroupEnsureResult { CREATED, UPDATED, OK, SKIPPED, ERROR }

    /** Result of {@link #setUserGroups}. */
    public static final class Diff {
        public static final Diff EMPTY = new Diff(Collections.emptySet(), Collections.emptySet());
        public final Set<String> added;
        public final Set<String> removed;
        public Diff(Set<String> added, Set<String> removed) {
            this.added = added;
            this.removed = removed;
        }
        public boolean isEmpty() { return added.isEmpty() && removed.isEmpty(); }
    }

    /**
     * Snapshot of a user's chat-render-relevant LP metadata. All fields are
     * plain Java strings; nullable on absence.
     */
    public static final class UserPrefixInfo {
        public final String prefix;
        public final String suffix;
        public final String nameColor;
        public UserPrefixInfo(String prefix, String suffix, String nameColor) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.nameColor = nameColor;
        }
    }
}
