package network.vonix.serverutilities.donation_ranks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.features.FeatureRegistry;
import network.vonix.serverutilities.venary.VenaryClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-shot reconciliation of the donation-rank LuckPerms groups, run on
 * SERVER_STARTED (after LP has had a chance to register its service).
 *
 * <p>For every donation rank known to {@link FeatureRegistry} we call
 * {@link LuckPermsBridge#ensureGroupExists} with the rank's name, weight,
 * chat prefix, and any meta from {@code lp_meta}. The result is counted and
 * a single summary line is logged.
 *
 * <p>If LuckPerms is missing the whole pass is a no-op (the bridge already
 * logs the warning).
 */
public final class RankGroupSyncer {

    private RankGroupSyncer() {}

    public static void syncAll() {
        if (LuckPermsBridge.get().isEmpty()) return; // bridge logged its own warning
        List<FeatureRegistry.DonationRank> ranks = FeatureRegistry.getInstance().getDonationRanks();
        if (ranks.isEmpty()) {
            VonixServerUtilities.LOGGER.info("[VonixSU] No donation ranks configured — skipping LP group sync.");
            return;
        }
        AtomicInteger created = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger ok      = new AtomicInteger();
        AtomicInteger errors  = new AtomicInteger();
        AtomicInteger pending = new AtomicInteger(ranks.size());

        for (FeatureRegistry.DonationRank rank : ranks) {
            try {
                if (LuckPermsBridge.isReservedGroupName(rank.luckPermsGroup)) {
                    VonixServerUtilities.LOGGER.warn(
                            "[VonixSU] Refusing reserved group name from dashboard: {}",
                            rank.luckPermsGroup);
                    errors.incrementAndGet();
                    if (pending.decrementAndGet() == 0) logSummary(created, updated, ok, errors);
                    continue;
                }
                Map<String, String> meta = metaFromRank(rank);
                LuckPermsBridge.ensureGroupExists(
                        rank.luckPermsGroup, rank.lpWeight, rank.chatPrefix, meta, rank.modPermissions
                ).whenComplete((res, err) -> {
                    if (err != null || res == LuckPermsBridge.GroupEnsureResult.ERROR) {
                        errors.incrementAndGet();
                    } else if (res == LuckPermsBridge.GroupEnsureResult.CREATED) {
                        created.incrementAndGet();
                        auditGroup("lp_group_create", rank);
                    } else if (res == LuckPermsBridge.GroupEnsureResult.UPDATED) {
                        updated.incrementAndGet();
                        auditGroup("lp_group_update", rank);
                    } else {
                        ok.incrementAndGet();
                    }
                    if (pending.decrementAndGet() == 0) logSummary(created, updated, ok, errors);
                });
            } catch (IllegalArgumentException reserved) {
                VonixServerUtilities.LOGGER.warn("[VonixSU] {}", reserved.getMessage());
                errors.incrementAndGet();
                if (pending.decrementAndGet() == 0) logSummary(created, updated, ok, errors);
            } catch (Exception e) {
                VonixServerUtilities.LOGGER.warn("[VonixSU] ensureGroupExists threw for {}: {}",
                        rank.luckPermsGroup, e.getMessage());
                errors.incrementAndGet();
                if (pending.decrementAndGet() == 0) logSummary(created, updated, ok, errors);
            }
        }
    }

    private static Map<String, String> metaFromRank(FeatureRegistry.DonationRank rank) {
        Map<String, String> out = new HashMap<>();
        if (rank.lpMeta != null) {
            for (Map.Entry<String, JsonElement> e : rank.lpMeta.entrySet()) {
                try { out.put(e.getKey(), e.getValue().getAsString()); }
                catch (Exception ignored) {}
            }
        }
        return out;
    }

    private static void auditGroup(String eventType, FeatureRegistry.DonationRank rank) {
        VenaryClient v = VenaryClient.get();
        if (v == null) return;
        JsonObject p = new JsonObject();
        p.addProperty("slug",            rank.slug);
        p.addProperty("luckperms_group", rank.luckPermsGroup);
        p.addProperty("lp_weight",       rank.lpWeight);
        p.addProperty("chat_prefix",     rank.chatPrefix);
        v.postAudit(eventType, "mod", "group_syncer", null, p);
    }

    private static void logSummary(AtomicInteger c, AtomicInteger u, AtomicInteger o, AtomicInteger e) {
        VonixServerUtilities.LOGGER.info(
                "[VonixSU] Donation rank groups synced (created {}, updated {}, ok {}{}).",
                c.get(), u.get(), o.get(),
                e.get() > 0 ? ", errors " + e.get() : "");
    }
}
