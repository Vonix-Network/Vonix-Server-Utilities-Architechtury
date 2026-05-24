package network.vonix.serverutilities.kits;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import network.vonix.serverutilities.VonixServerUtilities;

import java.sql.*;
import java.util.*;

/**
 * Manages kits — predefined item sets players can claim with cooldowns.
 *
 * The eligibility check (DB read + write) is done on the DB thread via
 * {@link #checkAndClaim(UUID, String)}; item distribution is done on the
 * main thread via {@link #distributeItems(ServerPlayer, String)}.
 * This two-step design keeps all SQLite I/O off the server tick thread.
 */
public final class KitManager {
    private static final KitManager INSTANCE = new KitManager();
    public static KitManager getInstance() { return INSTANCE; }

    private final Map<String, Kit> kits = new LinkedHashMap<>();

    private KitManager() { loadDefaultKits(); }

    private void loadDefaultKits() {
        register(new Kit("starter", List.of(
                new KitItem("minecraft:stone_sword",    1),
                new KitItem("minecraft:stone_pickaxe",  1),
                new KitItem("minecraft:stone_axe",      1),
                new KitItem("minecraft:bread",          16),
                new KitItem("minecraft:torch",          32)),
                3600, false));

        register(new Kit("tools", List.of(
                new KitItem("minecraft:iron_pickaxe",   1),
                new KitItem("minecraft:iron_axe",       1),
                new KitItem("minecraft:iron_shovel",    1),
                new KitItem("minecraft:iron_hoe",       1)),
                7200, false));

        register(new Kit("food", List.of(
                new KitItem("minecraft:cooked_beef",    32),
                new KitItem("minecraft:golden_apple",   2),
                new KitItem("minecraft:cake",           1)),
                1800, false));
    }

    /** Register a custom kit (replace if same name exists). */
    public void register(Kit kit) { kits.put(kit.name().toLowerCase(), kit); }

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

        long lastUsed = getLastUsed(uuid, kitName);
        long now = System.currentTimeMillis() / 1000L;

        if (kit.oneTime() && lastUsed > 0) return ClaimResult.alreadyClaimed();

        long remaining = (lastUsed + kit.cooldownSeconds()) - now;
        if (remaining > 0) return ClaimResult.onCooldown((int) remaining);

        setLastUsed(uuid, kitName, now);
        return ClaimResult.success();
    }

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

    private void setLastUsed(UUID uuid, String kitName, long time) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT OR REPLACE INTO vsu_kit_cooldowns VALUES(?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kitName.toLowerCase());
            ps.setLong  (3, time);
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
            var item = BuiltInRegistries.ITEM.get(loc);
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

    public record Kit(String name, List<KitItem> items, int cooldownSeconds, boolean oneTime) {}
    public record KitItem(String itemId, int count) {}
}
