package network.vonix.serverutilities.donation_ranks;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Package-private LP-typed implementation. ONLY ever referenced from inside
 * method bodies on {@link LuckPermsBridge}, behind the {@code LP_PRESENT}
 * guard. The JVM defers linking this class until the first call site is
 * reached; when LP is absent, no call site is ever reached and this class
 * is never linked, so the LP-typed field/parameter types in here can never
 * trigger a {@link NoClassDefFoundError}.
 *
 * <p>Do not add this class to any public API surface, static initialiser,
 * field type, or method parameter on {@link LuckPermsBridge}. Doing so will
 * re-introduce the crash class.
 */
final class LuckPermsBridgeImpl {

    private LuckPermsBridgeImpl() {}

    /** Cached API instance — only resolved on first use. */
    private static volatile LuckPerms cachedApi;

    private static Optional<LuckPerms> api() {
        LuckPerms api = cachedApi;
        if (api != null) return Optional.of(api);
        try {
            api = LuckPermsProvider.get();
            cachedApi = api;
            return Optional.of(api);
        } catch (IllegalStateException notReady) {
            // LP class is present but service hasn't registered yet (early startup) — fail-soft.
            return Optional.empty();
        }
    }

    static CompletableFuture<LuckPermsBridge.GroupEnsureResult> ensureGroupExists(
            String groupName, int weight, String prefix,
            Map<String, String> meta, List<String> permissions) {
        Optional<LuckPerms> lpOpt = api();
        if (lpOpt.isEmpty()) {
            return CompletableFuture.completedFuture(LuckPermsBridge.GroupEnsureResult.SKIPPED);
        }
        LuckPerms lp = lpOpt.get();

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
                    return CompletableFuture.completedFuture(LuckPermsBridge.GroupEnsureResult.OK);
                }
                return lp.getGroupManager().saveGroup(group)
                        .thenApply(v -> createdFlag
                                ? LuckPermsBridge.GroupEnsureResult.CREATED
                                : LuckPermsBridge.GroupEnsureResult.UPDATED);
            });
        }).exceptionally(t -> {
            VonixServerUtilities.LOGGER.warn("[VonixSU] ensureGroupExists({}) failed: {}", groupName, t.getMessage());
            return LuckPermsBridge.GroupEnsureResult.ERROR;
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
        // Permissions — reconcile within our managed prefix.
        if (permissions != null) {
            java.util.Set<String> desired = new java.util.HashSet<>();
            for (String pp : permissions) if (pp != null && !pp.isBlank()) desired.add(pp);
            java.util.Set<String> current = new java.util.HashSet<>();
            for (Node n : data.toCollection()) {
                if (n.getType() == NodeType.PERMISSION) current.add(n.getKey());
            }
            for (String pp : desired) {
                if (!current.contains(pp)) {
                    data.add(net.luckperms.api.node.types.PermissionNode.builder(pp).build());
                    changed = true;
                }
            }
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

    static CompletableFuture<LuckPermsBridge.Diff> setUserGroups(UUID player,
                                                                Set<String> shouldHaveGroups,
                                                                Set<String> allManagedGroups) {
        Optional<LuckPerms> lpOpt = api();
        if (lpOpt.isEmpty()) {
            return CompletableFuture.completedFuture(LuckPermsBridge.Diff.EMPTY);
        }
        Set<String> desired = lower(shouldHaveGroups);
        Set<String> managed = lower(allManagedGroups);
        desired.removeIf(LuckPermsBridge::isReservedGroupName);
        managed.removeIf(LuckPermsBridge::isReservedGroupName);
        Set<String> effectiveDesired = new LinkedHashSet<>(desired);
        effectiveDesired.retainAll(managed);

        LuckPerms lp = lpOpt.get();
        return lp.getUserManager().loadUser(player).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(LuckPermsBridge.Diff.EMPTY);
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
                return CompletableFuture.completedFuture(LuckPermsBridge.Diff.EMPTY);
            }
            return applyDiff(lp, user, toAdd, toRemove);
        }).exceptionally(t -> {
            VonixServerUtilities.LOGGER.warn("[VonixSU] setUserGroups({}) failed: {}", player, t.getMessage());
            return LuckPermsBridge.Diff.EMPTY;
        });
    }

    private static CompletableFuture<LuckPermsBridge.Diff> applyDiff(LuckPerms lp, User user,
                                                                    Set<String> toAdd, Set<String> toRemove) {
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
                .thenApply(v -> new LuckPermsBridge.Diff(Set.copyOf(toAdd), Set.copyOf(toRemove)));
    }

    static Optional<LuckPermsBridge.UserPrefixInfo> getUserPrefixInfo(UUID player) {
        Optional<LuckPerms> lpOpt = api();
        if (lpOpt.isEmpty()) return Optional.empty();
        User user = lpOpt.get().getUserManager().getUser(player);
        if (user == null) return Optional.empty();
        CachedMetaData meta = user.getCachedData().getMetaData();
        return Optional.of(new LuckPermsBridge.UserPrefixInfo(
                meta.getPrefix(), meta.getSuffix(), meta.getMetaValue("name-color")));
    }

    private static Set<String> lower(Set<String> in) {
        if (in == null) return new LinkedHashSet<>();
        Set<String> out = new LinkedHashSet<>(in.size());
        for (String s : in) {
            if (s != null && !s.isBlank()) out.add(s.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
