package network.vonix.serverutilities.donation_ranks;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.WeightNode;
import network.vonix.serverutilities.VonixServerUtilities;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional bridge to the LuckPerms API (5.4+).
 *
 * <p><b>LuckPerms is an OPTIONAL dependency.</b> If it isn't installed on the
 * server, {@link #get()} returns {@link Optional#empty()} and every public
 * helper short-circuits without throwing. This is what lets the mod ship a
 * single JAR that works with-or-without LP.
 *
 * <p>Detection is done with a try/catch on {@code LuckPermsProvider.get()};
 * we catch BOTH {@link NoClassDefFoundError} (LP not on classpath at all) and
 * {@link IllegalStateException} (LP class present but service not yet
 * registered, e.g. early server-starting).
 *
 * <h2>Security invariants enforced here</h2>
 * <ul>
 *   <li><b>Reserved group names are refused.</b> {@code default}, {@code admin},
 *       {@code op}, {@code owner}, {@code vonix} (case-insensitive) cannot be
 *       managed by donation-rank sync — operators must never lose moderation
 *       authority because Venary thought a Patreon tier should be called
 *       "admin".</li>
 *   <li><b>Diff respects a managed-group whitelist.</b> {@link #setUserGroups}
 *       only ever adds/removes groups that appear in the passed
 *       {@code allManagedGroups} set. Any other group the player has — staff
 *       roles, region perks, anything an operator created by hand — is left
 *       untouched.</li>
 * </ul>
 *
 * <p>All I/O off-thread: every public method returns a
 * {@link CompletableFuture}, none of them block.
 */
public final class LuckPermsBridge {

    /** Group names we refuse to touch under any circumstances. */
    private static final Set<String> RESERVED_GROUPS = Set.of(
            "default", "admin", "op", "owner", "vonix"
    );

    private static volatile Boolean cachedPresent;
    private static volatile LuckPerms cachedApi;
    private static final AtomicBoolean warnedAbsent = new AtomicBoolean(false);

    private LuckPermsBridge() {}

    /**
     * Returns the LuckPerms API if installed, else {@link Optional#empty()}.
     * The first call to a server without LP logs a single visible warning;
     * subsequent calls are silent.
     */
    public static Optional<LuckPerms> get() {
        if (cachedPresent == Boolean.FALSE) return Optional.empty();
        if (cachedApi != null) return Optional.of(cachedApi);
        try {
            LuckPerms api = LuckPermsProvider.get();
            cachedApi = api;
            cachedPresent = Boolean.TRUE;
            return Optional.of(api);
        } catch (NoClassDefFoundError | IllegalStateException notLoaded) {
            if (warnedAbsent.compareAndSet(false, true)) {
                VonixServerUtilities.LOGGER.warn(
                        "[VonixSU] LuckPerms not detected — donation-rank sync is disabled. "
                      + "Install LuckPerms to enable. (Cause: {})",
                        notLoaded.getClass().getSimpleName());
            }
            cachedPresent = Boolean.FALSE;
            return Optional.empty();
        }
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
        Optional<LuckPerms> lpOpt = get();
        if (lpOpt.isEmpty()) {
            return CompletableFuture.completedFuture(GroupEnsureResult.SKIPPED);
        }
        LuckPerms lp = lpOpt.get();

        // PORT-NOTE: LuckPerms 5.4 GroupManager API is stable across all our
        // target MC versions; the LP JAR is the same artifact for all loaders.
        return lp.getGroupManager().loadGroup(groupName).thenCompose(opt -> {
            CompletableFuture<Group> created;
            boolean wasCreated;
            if (opt.isPresent()) {
                created = CompletableFuture.completedFuture(opt.get());
                wasCreated = false;
            } else {
                created = lp.getGroupManager().createAndLoadGroup(groupName);
                wasCreated = true;
            }
            final boolean createdFlag = wasCreated;
            return created.thenCompose(group -> {
                boolean changed = applyGroupAttributes(group, weight, prefix, meta, permissions);
                if (!changed && !createdFlag) {
                    return CompletableFuture.completedFuture(GroupEnsureResult.OK);
                }
                return lp.getGroupManager().saveGroup(group)
                        .thenApply(v -> createdFlag ? GroupEnsureResult.CREATED : GroupEnsureResult.UPDATED);
            });
        }).exceptionally(t -> {
            VonixServerUtilities.LOGGER.warn("[VonixSU] ensureGroupExists({}) failed: {}", groupName, t.getMessage());
            return GroupEnsureResult.ERROR;
        });
    }

    private static boolean applyGroupAttributes(Group group, int weight, String prefix, Map<String, String> meta, List<String> permissions) {
        boolean changed = false;
        NodeMap data = group.data();

        // Weight
        int currentWeight = group.getWeight().orElse(Integer.MIN_VALUE);
        if (currentWeight != weight) {
            for (Node n : new HashSet<>(data.toCollection())) {
                if (n.getType() == NodeType.WEIGHT) data.remove(n);
            }
            data.add(WeightNode.builder(weight).build());
            changed = true;
        }
        // Prefix
        if (prefix != null && !prefix.isEmpty()) {
            boolean hasPrefix = false;
            for (Node n : data.toCollection()) {
                if (n instanceof PrefixNode pn && weight == pn.getPriority() && prefix.equals(pn.getMetaValue())) {
                    hasPrefix = true; break;
                }
            }
            if (!hasPrefix) {
                for (Node n : new HashSet<>(data.toCollection())) {
                    if (n instanceof PrefixNode) data.remove(n);
                }
                data.add(PrefixNode.builder(prefix, weight).build());
                changed = true;
            }
        }
        // Free-form meta (color, etc.)
        if (meta != null) {
            for (Map.Entry<String, String> e : meta.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                Node n = net.luckperms.api.node.types.MetaNode.builder(e.getKey(), e.getValue()).build();
                DataMutateResult r = data.add(n);
                if (r == DataMutateResult.SUCCESS) changed = true;
            }
        }
        // Permissions — reconcile within our managed prefix to avoid touching
        // permissions LP operators added by hand. We mark our-managed perms
        // via a normal PermissionNode and only remove ours that are no longer
        // in the desired set.
        if (permissions != null) {
            java.util.Set<String> desired = new java.util.HashSet<>();
            for (String pp : permissions) if (pp != null && !pp.isBlank()) desired.add(pp);
            java.util.Set<String> current = new java.util.HashSet<>();
            for (Node n : data.toCollection()) {
                if (n.getType() == NodeType.PERMISSION) current.add(n.getKey());
            }
            // Add missing
            for (String pp : desired) {
                if (!current.contains(pp)) {
                    data.add(net.luckperms.api.node.types.PermissionNode.builder(pp).build());
                    changed = true;
                }
            }
            // Remove our-managed perms that aren't desired anymore.
            // Heuristic: only remove perms inside the "vonixsu." namespace —
            // those are the ones the dashboard owns. Anything else (manual
            // ops perms, other prefixes) we leave alone.
            for (Node n : new java.util.HashSet<>(data.toCollection())) {
                if (n.getType() == NodeType.PERMISSION
                        && n.getKey().startsWith("vonixsu.")
                        && !desired.contains(n.getKey())) {
                    data.remove(n);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Reconcile a player's group memberships against the donation tiers they
     * SHOULD have.
     *
     * <p>The diff is computed strictly inside {@code allManagedGroups}: groups
     * outside that set are never added and never removed. This is the
     * whitelist that prevents donation sync from clobbering staff or
     * per-region roles.
     *
     * @param player              UUID of the player.
     * @param shouldHaveGroups    desired donation group names (any case).
     * @param allManagedGroups    every group name we are allowed to touch.
     */
    public static CompletableFuture<Diff> setUserGroups(UUID player,
                                                       Set<String> shouldHaveGroups,
                                                       Set<String> allManagedGroups) {
        Optional<LuckPerms> lpOpt = get();
        if (lpOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Diff.EMPTY);
        }
        // Normalise + filter reserved (defence in depth).
        Set<String> desired  = lower(shouldHaveGroups);
        Set<String> managed  = lower(allManagedGroups);
        desired.removeIf(LuckPermsBridge::isReservedGroupName);
        managed.removeIf(LuckPermsBridge::isReservedGroupName);
        // The effective desired set is the INTERSECTION of desired and managed.
        Set<String> effectiveDesired = new LinkedHashSet<>(desired);
        effectiveDesired.retainAll(managed);

        LuckPerms lp = lpOpt.get();
        return lp.getUserManager().loadUser(player).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(Diff.EMPTY);
            Set<String> currentManaged = new LinkedHashSet<>();
            for (Node n : user.data().toCollection()) {
                if (n instanceof InheritanceNode in) {
                    String g = in.getGroupName().toLowerCase(Locale.ROOT);
                    if (managed.contains(g)) currentManaged.add(g);
                }
            }
            Set<String> toAdd    = new LinkedHashSet<>(effectiveDesired);
            toAdd.removeAll(currentManaged);
            Set<String> toRemove = new LinkedHashSet<>(currentManaged);
            toRemove.removeAll(effectiveDesired);

            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                return CompletableFuture.completedFuture(Diff.EMPTY);
            }
            return applyDiff(lp, user, toAdd, toRemove);
        }).exceptionally(t -> {
            VonixServerUtilities.LOGGER.warn("[VonixSU] setUserGroups({}) failed: {}", player, t.getMessage());
            return Diff.EMPTY;
        });
    }

    private static CompletableFuture<Diff> applyDiff(LuckPerms lp, User user,
                                                    Set<String> toAdd, Set<String> toRemove) {
        // Remove first, then add — order matters if the same node was being toggled.
        for (String g : toRemove) {
            for (Node n : new HashSet<>(user.data().toCollection())) {
                if (n instanceof InheritanceNode in
                        && in.getGroupName().equalsIgnoreCase(g)) {
                    user.data().remove(n);
                }
            }
        }
        for (String g : toAdd) {
            user.data().add(InheritanceNode.builder(g).build());
        }
        return lp.getUserManager().saveUser(user)
                .thenApply(v -> new Diff(Set.copyOf(toAdd), Set.copyOf(toRemove)));
    }

    private static Set<String> lower(Set<String> in) {
        if (in == null) return new LinkedHashSet<>();
        Set<String> out = new LinkedHashSet<>(in.size());
        for (String s : in) {
            if (s != null && !s.isBlank()) out.add(s.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    // ── Result types ────────────────────────────────────────────────────────

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
}
