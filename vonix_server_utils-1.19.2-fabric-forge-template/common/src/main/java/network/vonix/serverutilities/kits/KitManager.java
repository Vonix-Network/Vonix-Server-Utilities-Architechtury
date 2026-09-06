package network.vonix.serverutilities.kits;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import network.vonix.serverutilities.VonixServerUtilities;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

/**
 * Manages kits — predefined item sets players can claim with cooldowns.
 *
 * Kit definitions live in {@code config/vonix_server_utilities/kits.json}. On
 * first launch the file is seeded with three default kits (starter / tools /
 * food); thereafter operators edit the JSON directly and run
 * {@code /vonixsu reload} (or {@code /kit reload}) to apply changes without
 * restarting.
 *
 * The eligibility check (DB read + write) is done on the DB thread via
 * {@link #checkAndClaim(UUID, String)}; item distribution is done on the
 * main thread via {@link #distributeItems(ServerPlayer, String)}.
 * This two-step design keeps all SQLite I/O off the server tick thread.
 */
public final class KitManager {
    private static final KitManager INSTANCE = new KitManager();
    public static KitManager getInstance() { return INSTANCE; }

    private static final Path KITS_PATH =
            Paths.get("config", "vonix_server_utilities", "kits.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Kit> kits = new LinkedHashMap<>();

    private KitManager() { /* loadFromJson invoked by EventHandler at SERVER_STARTING */ }

    /** Load kit definitions from disk. Writes a default file on first launch. */
    public void loadFromJson(MinecraftServer server) {
        try {
            Files.createDirectories(KITS_PATH.getParent());
            if (!Files.exists(KITS_PATH)) {
                writeDefaultsFile();
                VonixServerUtilities.LOGGER.info(
                        "[VonixSU] Created default kits.json at {}", KITS_PATH.toAbsolutePath());
            }
            parseKitsFile();
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] Failed to load kits.json, falling back to in-memory defaults", e);
            kits.clear();
            seedHardcodedDefaults();
        }
    }

    /** Re-read kits.json. Triggered by /kit reload and /vonixsu reload. */
    public void reloadFromJson(MinecraftServer server) {
        VonixServerUtilities.LOGGER.info("[VonixSU] Reloading kits.json…");
        loadFromJson(server);
    }

    private void parseKitsFile() throws IOException {
        try (Reader r = Files.newBufferedReader(KITS_PATH)) {
            JsonElement root = JsonParser.parseReader(r);
            if (!root.isJsonObject()) {
                VonixServerUtilities.LOGGER.warn("[VonixSU] kits.json: root is not an object — using defaults");
                seedHardcodedDefaults();
                return;
            }
            JsonObject obj = root.getAsJsonObject();
            JsonArray arr = obj.has("kits") && obj.get("kits").isJsonArray()
                    ? obj.getAsJsonArray("kits") : new JsonArray();

            Map<String, Kit> next = new LinkedHashMap<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject k = el.getAsJsonObject();
                String name = k.has("name") ? k.get("name").getAsString() : null;
                
                if (name == null || name.isBlank()) {
                    VonixServerUtilities.LOGGER.warn("[VonixSU] kits.json: skipping nameless entry");
                    continue;
                }

                String group = k.has("group") ? k.get("group").getAsString() : name;
                KitGroup kitGroup = new KitGroup(group.toLowerCase());

                int cooldown = k.has("cooldown_seconds") ? k.get("cooldown_seconds").getAsInt() : 3600;
                boolean oneTime = k.has("one_time") && k.get("one_time").getAsBoolean();

                List<KitItem> items = new ArrayList<>();
                if (k.has("items") && k.get("items").isJsonArray()) {
                    for (JsonElement iEl : k.getAsJsonArray("items")) {
                        if (!iEl.isJsonObject()) continue;
                        JsonObject io = iEl.getAsJsonObject();
                        String itemId = io.has("item") ? io.get("item").getAsString() : null;
                        int count = io.has("count") ? io.get("count").getAsInt() : 1;
                        if (itemId == null) continue;
                        if (!isValidItemId(itemId)) {
                            VonixServerUtilities.LOGGER.warn(
                                    "[VonixSU] kits.json: kit '{}' references unknown item '{}' — skipping that item.",
                                    name, itemId);
                            continue;
                        }
                        items.add(new KitItem(itemId, count));
                    }
                }
                next.put(name.toLowerCase(), new Kit(name.toLowerCase(), kitGroup, items, cooldown, oneTime));
            }
            kits.clear();
            kits.putAll(next);
            VonixServerUtilities.LOGGER.info("[VonixSU] Loaded {} kits from kits.json.", kits.size());
        }
    }

    private static boolean isValidItemId(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return false;
            return Registry.ITEM.get(rl) != Items.AIR || "minecraft:air".equals(id);
        } catch (Exception e) {
            return false;
        }
    }

    private void writeDefaultsFile() throws IOException {
        // Mirror the three legacy hard-coded defaults exactly.
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        arr.add(makeKitJson("starter", "starter", 3600, false, new String[][]{
                {"minecraft:stone_sword",    "1"},
                {"minecraft:stone_pickaxe",  "1"},
                {"minecraft:stone_axe",      "1"},
                {"minecraft:bread",         "16"},
                {"minecraft:torch",         "32"},
        }));
        arr.add(makeKitJson("tools", "tools", 7200, false, new String[][]{
                {"minecraft:iron_pickaxe", "1"},
                {"minecraft:iron_axe",     "1"},
                {"minecraft:iron_shovel",  "1"},
                {"minecraft:iron_hoe",     "1"},
        }));
        arr.add(makeKitJson("food", "food", 1800, false, new String[][]{
                {"minecraft:cooked_beef",  "32"},
                {"minecraft:golden_apple",  "2"},
                {"minecraft:cake",          "1"},
        }));
        root.add("kits", arr);
        try (Writer w = Files.newBufferedWriter(KITS_PATH)) {
            GSON.toJson(root, w);
        }
    }

    private static JsonObject makeKitJson(String name, String group, int cd, boolean oneTime, String[][] items) {
        JsonObject k = new JsonObject();
        k.addProperty("name", name);
        k.addProperty("group", group);
        k.addProperty("cooldown_seconds", cd);
        k.addProperty("one_time", oneTime);
        JsonArray arr = new JsonArray();
        for (String[] it : items) {
            JsonObject io = new JsonObject();
            io.addProperty("item", it[0]);
            io.addProperty("count", Integer.parseInt(it[1]));
            arr.add(io);
        }
        k.add("items", arr);
        return k;
    }

    /** Fallback used only if kits.json is broken and unrecoverable. */
    private void seedHardcodedDefaults() {
        register(new Kit("starter", new KitGroup("starter"), List.of(
                new KitItem("minecraft:stone_sword", 1),
                new KitItem("minecraft:stone_pickaxe", 1),
                new KitItem("minecraft:stone_axe", 1),
                new KitItem("minecraft:bread", 16),
                new KitItem("minecraft:torch", 32)), 3600, false));
        register(new Kit("tools", new KitGroup("tools"), List.of(
                new KitItem("minecraft:iron_pickaxe", 1),
                new KitItem("minecraft:iron_axe", 1),
                new KitItem("minecraft:iron_shovel", 1),
                new KitItem("minecraft:iron_hoe", 1)), 7200, false));
        register(new Kit("food", new KitGroup("food"), List.of(
                new KitItem("minecraft:cooked_beef", 32),
                new KitItem("minecraft:golden_apple", 2),
                new KitItem("minecraft:cake", 1)), 1800, false));
    }

    /** Register a custom kit (replace if same name exists). Kept for tests + hard-coded fallback. */
    public void register(Kit kit) { kits.put(kit.name().toLowerCase(), kit); }

    /** Test-friendly overload: register a kit purely from an ItemStack list. */
    public void register(String name, ItemStack... stacks) {
        List<KitItem> items = new ArrayList<>(stacks.length);
        for (ItemStack s : stacks) {
            if (s == null || s.isEmpty()) continue;
            ResourceLocation id = Registry.ITEM.getKey(s.getItem());
            items.add(new KitItem(id.toString(), s.getCount()));
        }
        register(new Kit(name.toLowerCase(), new KitGroup(name.toLowerCase()), items, 3600, false));
    }

    // ── DB-thread operations ──────────────────────────────────────────────────

    /**
     * Check if {@code uuid} may claim kit {@code kitName} and, if so, record
     * the usage immediately so no second claim can race through.
     * Must be called from the DB executor thread.
     *
     * @return a {@link ClaimResult} describing the outcome and any cooldown info.
     */
    public ClaimResult checkAndClaim(UUID uuid, String kitName) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null) return ClaimResult.notFound();

        // long lastUsed = getLastUsed(uuid, kitName);
        long now = System.currentTimeMillis() / 1000L;

        long lastGroupUsed = getLastGroupUsed(uuid, kit.group().groupName());

        if (kit.oneTime() && lastGroupUsed > 0) return ClaimResult.alreadyClaimed();

        long remaining = (lastGroupUsed + kit.cooldownSeconds()) - now;
        if (remaining > 0) return ClaimResult.onCooldown((int) remaining);

        setLastUsed(uuid, kitName, kit.group().groupName(), now);
        return ClaimResult.success();
    }

    /*
    
    Legacy Kit System that ran via Name and not Group
    
    private long getLastUsed(UUID uuid, String kitName) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT last_used FROM vsu_kit_cooldowns WHERE uuid=? AND kit_name=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kitName.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getLastUsed failed", e);
        }
        return 0;
    }
    */
   
    private long getLastGroupUsed(UUID uuid, String groupName) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT last_used FROM vsu_kit_cooldowns WHERE uuid=? AND claim_group=? ORDER BY last_used DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, groupName.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getLastGroupUsed failed", e);
        }
        return 0;
    }

    private void setLastUsed(UUID uuid, String kitName, String group, long time) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT OR REPLACE INTO vsu_kit_cooldowns (uuid, kit_name, claim_group, last_used) VALUES(?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kitName.toLowerCase());
            ps.setString(3, group.toLowerCase());
            ps.setLong  (4, time);
            ps.executeUpdate();
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] setLastUsed failed", e);
        }
    }
    

    // ── Main-thread operations ────────────────────────────────────────────────

    /**
     * Give kit items to the player. Must be called on the main tick thread.
     * Returns false if the kit name is unknown.
     */
    public boolean distributeItems(ServerPlayer player, String kitName) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        for (KitItem item : kit.items()) {
            ItemStack stack = buildStack(item.itemId(), item.count());
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Set<String> getKitNames() { return Collections.unmodifiableSet(kits.keySet()); }

    private Connection conn() {
        return VonixServerUtilities.getInstance().getDatabase().getConnection();
    }

    private static ItemStack buildStack(String itemId, int count) {
        try {
            ResourceLocation loc = ResourceLocation.tryParse(itemId);
            if (loc == null) return ItemStack.EMPTY;
            var item = Registry.ITEM.get(loc);
            if (item != Items.AIR) return new ItemStack(item, count);
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.warn("[VonixSU] Invalid item id: {}", itemId);
        }
        return ItemStack.EMPTY;
    }

    // ── Records & results ─────────────────────────────────────────────────────

    public enum ClaimStatus { SUCCESS, NOT_FOUND, ON_COOLDOWN, ALREADY_CLAIMED }

    public record ClaimResult(ClaimStatus status, int remainingSeconds) {
        public static ClaimResult success()         { return new ClaimResult(ClaimStatus.SUCCESS,         0); }
        public static ClaimResult notFound()        { return new ClaimResult(ClaimStatus.NOT_FOUND,       0); }
        public static ClaimResult alreadyClaimed()  { return new ClaimResult(ClaimStatus.ALREADY_CLAIMED, 0); }
        public static ClaimResult onCooldown(int s) { return new ClaimResult(ClaimStatus.ON_COOLDOWN,     s); }
    }

    public record Kit(String name, KitGroup group, List<KitItem> items, int cooldownSeconds, boolean oneTime) {}
    public record KitItem(String itemId, int count) {}
    public record KitGroup(String groupName) {}
}
