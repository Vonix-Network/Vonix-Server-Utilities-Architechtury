package network.vonix.serverutilities.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.database.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory feature-flag + feature-settings + donation-rank table.
 *
 * <p>Single source of truth at runtime: every command predicate and every
 * RankSyncTask consult this registry. The canonical source over time is the
 * Venary backend ({@code GET /minecraft/server-config}) — {@link ServerConfigClient}
 * polls it and calls {@link #update(JsonObject)} on every change.
 *
 * <p><b>First-run heuristic.</b> On first construction we ask the SQLite DB
 * whether {@code vsu_homes}, {@code vsu_warps}, or {@code vsu_kit_cooldowns}
 * contains any rows. If so, the corresponding feature is auto-enabled in
 * memory so a freshly-upgraded server isn't accidentally locked out of its
 * own data while we wait for the first /server-config poll to land. The
 * auto-enabled flags are <i>local-only</i> — the next successful poll
 * overwrites them with the canonical value from the dashboard.
 *
 * <p>Thread-safety: backed by {@link ConcurrentHashMap}, all snapshots are
 * read-only views.
 */
public final class FeatureRegistry {

    private static volatile FeatureRegistry instance;

    /** featureKey → enabled */
    private final Map<String, Boolean> features = new ConcurrentHashMap<>();
    /** featureKey → settings JSON object (may be empty) */
    private final Map<String, JsonObject> settings = new ConcurrentHashMap<>();
    /** Cached donation_ranks list (last seen from /server-config). */
    private volatile List<DonationRank> donationRanks = Collections.emptyList();
    /** Last seen config_version — used by the poller to detect changes. */
    private volatile int configVersion = -1;
    /** True once a real /server-config response has populated this registry. */
    private volatile boolean hydratedFromBackend = false;

    private FeatureRegistry() {}

    /** Lazy singleton accessor — first call runs the heuristic. */
    public static FeatureRegistry getInstance() {
        FeatureRegistry r = instance;
        if (r == null) {
            synchronized (FeatureRegistry.class) {
                if (instance == null) {
                    instance = new FeatureRegistry();
                    instance.runFirstRunHeuristic();
                }
                r = instance;
            }
        }
        return r;
    }

    /** Test seam — wipes the singleton so tests can re-bootstrap. */
    static synchronized void resetForTesting() { instance = null; }

    // ── Queries ─────────────────────────────────────────────────────────────

    /** Returns true if a feature is enabled. Unknown keys default to true
     *  (fail-open): we'd rather show a command than silently hide it before
     *  the first poll has run. */
    public boolean isEnabled(String featureKey) {
        if (featureKey == null) return true;
        Boolean v = features.get(featureKey);
        return v == null || v;
    }

    @SuppressWarnings("unchecked")
    public <T> T getSetting(String featureKey, String settingKey, T defaultValue) {
        JsonObject o = settings.get(featureKey);
        if (o == null || !o.has(settingKey)) return defaultValue;
        try {
            JsonElement el = o.get(settingKey);
            if (defaultValue instanceof Integer)  return (T) Integer.valueOf(el.getAsInt());
            if (defaultValue instanceof Long)     return (T) Long.valueOf(el.getAsLong());
            if (defaultValue instanceof Double)   return (T) Double.valueOf(el.getAsDouble());
            if (defaultValue instanceof Boolean)  return (T) Boolean.valueOf(el.getAsBoolean());
            if (defaultValue instanceof String)   return (T) el.getAsString();
            return (T) el;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public List<DonationRank> getDonationRanks() { return donationRanks; }
    public int  getConfigVersion()               { return configVersion; }
    public boolean isHydratedFromBackend()       { return hydratedFromBackend; }

    /** Snapshot of all currently-known feature flags (key → enabled). */
    public Map<String, Boolean> snapshot() {
        return Map.copyOf(features);
    }

    public Set<String> knownFeatureKeys() {
        return Set.copyOf(features.keySet());
    }

    /** Returns the raw settings JSON for a feature, or null. */
    public JsonObject getSettings(String featureKey) {
        return settings.get(featureKey);
    }

    // ── Mutation: from /server-config ──────────────────────────────────────

    /**
     * Replaces flags + settings + donation_ranks with the contents of a
     * /server-config response body. Returns a {@link Delta} summarising what
     * changed (used by the poller to log human-readable diffs).
     */
    public synchronized Delta update(JsonObject configResponse) {
        if (configResponse == null) return Delta.EMPTY;
        Map<String, Boolean> before = Map.copyOf(features);

        // features: { homes: true, warps: false, ... }
        if (configResponse.has("features") && configResponse.get("features").isJsonObject()) {
            JsonObject f = configResponse.getAsJsonObject("features");
            features.clear();
            for (Map.Entry<String, JsonElement> e : f.entrySet()) {
                try { features.put(e.getKey(), e.getValue().getAsBoolean()); }
                catch (Exception ignored) {}
            }
        }
        // feature_settings: { homes: {max_homes: 5}, ... }
        if (configResponse.has("feature_settings") && configResponse.get("feature_settings").isJsonObject()) {
            JsonObject s = configResponse.getAsJsonObject("feature_settings");
            settings.clear();
            for (Map.Entry<String, JsonElement> e : s.entrySet()) {
                if (e.getValue().isJsonObject()) settings.put(e.getKey(), e.getValue().getAsJsonObject());
            }
        }
        // donation_ranks: [ {slug, name, luckperms_group, lp_weight, chat_prefix, mod_permissions, lp_meta?} ]
        if (configResponse.has("donation_ranks") && configResponse.get("donation_ranks").isJsonArray()) {
            JsonArray arr = configResponse.getAsJsonArray("donation_ranks");
            this.donationRanks = DonationRank.parseAll(arr);
        }
        if (configResponse.has("config_version")) {
            try { this.configVersion = configResponse.get("config_version").getAsInt(); }
            catch (Exception ignored) {}
        }
        this.hydratedFromBackend = true;
        return Delta.between(before, features);
    }

    /** Local-only toggle from /vonixsu feature enable|disable. */
    public synchronized boolean setEnabledLocal(String key, boolean enabled) {
        Boolean prev = features.put(key, enabled);
        return prev == null || prev != enabled;
    }

    // ── First-run heuristic ─────────────────────────────────────────────────

    /**
     * If the SQLite DB has rows in homes/warps/kit_cooldowns, mark those
     * features enabled in memory. Best-effort, ignored on any error.
     */
    private void runFirstRunHeuristic() {
        try {
            Database db = VonixServerUtilities.getInstance() == null
                    ? null : VonixServerUtilities.getInstance().getDatabase();
            if (db == null) return;
            Connection conn = db.getConnection();
            if (conn == null) return;
            try (Statement st = conn.createStatement()) {
                tryAutoEnable(st, "vsu_homes",         "homes");
                tryAutoEnable(st, "vsu_warps",         "warps");
                tryAutoEnable(st, "vsu_kit_cooldowns", "kits");
            }
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.debug("[VonixSU] First-run heuristic skipped: {}", e.getMessage());
        }
    }

    private void tryAutoEnable(Statement st, String table, String featureKey) {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) {
                long n = rs.getLong(1);
                if (n > 0) {
                    features.putIfAbsent(featureKey, true);
                    VonixServerUtilities.LOGGER.info(
                            "[VonixSU] Auto-enabled {} based on existing data ({} rows).",
                            featureKey, n);
                }
            }
        } catch (Exception ignored) {
            // table may not exist yet — ignore, default behaviour is fail-open
        }
    }

    // ── Helper types ────────────────────────────────────────────────────────

    /** Immutable view of a donation rank entry from /server-config. */
    public static final class DonationRank {
        public final String slug;
        public final String name;
        public final String luckPermsGroup;
        public final int    lpWeight;
        public final String chatPrefix;
        public final List<String> modPermissions;
        public final JsonObject   lpMeta;

        private DonationRank(String slug, String name, String group,
                             int weight, String prefix,
                             List<String> perms, JsonObject lpMeta) {
            this.slug = slug;
            this.name = name;
            this.luckPermsGroup = group;
            this.lpWeight = weight;
            this.chatPrefix = prefix;
            this.modPermissions = perms;
            this.lpMeta = lpMeta;
        }

        static List<DonationRank> parseAll(JsonArray arr) {
            java.util.ArrayList<DonationRank> out = new java.util.ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                try {
                    String slug   = optStr(o, "slug", null);
                    String name   = optStr(o, "display_name", optStr(o, "name", slug));
                    String group  = optStr(o, "luckperms_group", slug);
                    int    weight = o.has("weight") ? o.get("weight").getAsInt()
                                  : (o.has("lp_weight") ? o.get("lp_weight").getAsInt() : 0);
                    String prefix = optStr(o, "prefix", optStr(o, "chat_prefix", ""));
                    java.util.ArrayList<String> perms = new java.util.ArrayList<>();
                    String permsKey = o.has("permissions") ? "permissions" : "mod_permissions";
                    if (o.has(permsKey) && o.get(permsKey).isJsonArray()) {
                        for (JsonElement p : o.getAsJsonArray(permsKey)) {
                            try { perms.add(p.getAsString().toLowerCase(Locale.ROOT)); } catch (Exception ignored) {}
                        }
                    }
                    JsonObject lpMeta = (o.has("lp_meta") && o.get("lp_meta").isJsonObject())
                            ? o.getAsJsonObject("lp_meta") : new JsonObject();
                    if (group != null && !group.isBlank()) {
                        out.add(new DonationRank(slug, name, group, weight, prefix, List.copyOf(perms), lpMeta));
                    }
                } catch (Exception ignored) {}
            }
            return List.copyOf(out);
        }

        private static String optStr(JsonObject o, String k, String dflt) {
            if (!o.has(k) || o.get(k).isJsonNull()) return dflt;
            try { return o.get(k).getAsString(); } catch (Exception e) { return dflt; }
        }
    }

    /** Result of {@link #update(JsonObject)} — used for human-readable logging. */
    public static final class Delta {
        public static final Delta EMPTY = new Delta(Collections.emptyMap());
        /** key → "before→after" pair, only for changed keys. */
        public final Map<String, String> changes;
        private Delta(Map<String, String> changes) { this.changes = changes; }
        public boolean isEmpty() { return changes.isEmpty(); }

        static Delta between(Map<String, Boolean> before, Map<String, Boolean> after) {
            java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
            java.util.TreeSet<String> keys = new java.util.TreeSet<>();
            keys.addAll(before.keySet()); keys.addAll(after.keySet());
            for (String k : keys) {
                Boolean a = before.get(k); Boolean b = after.get(k);
                if (!java.util.Objects.equals(a, b)) {
                    out.put(k, String.valueOf(a) + "\u2192" + String.valueOf(b));
                }
            }
            return new Delta(Map.copyOf(out));
        }
    }
}
