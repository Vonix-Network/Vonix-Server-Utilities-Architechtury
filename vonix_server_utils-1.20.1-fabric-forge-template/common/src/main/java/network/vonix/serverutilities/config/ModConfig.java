package network.vonix.serverutilities.config;

import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.venary.VenaryConfig;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Simple properties-file config for Vonix Server Utilities.
 * File location: config/vonix_server_utilities.properties
 */
public final class ModConfig {
    public static final ModConfig INSTANCE = new ModConfig();

    private int maxHomes = 5;
    private int tpaTimeoutSeconds = 120;
    private int deathBackDelaySeconds = 0;

    // ── Venary site-link integration ─────────────────────────────────────────
    // All defaults keep the network layer OFF. Operator must opt in explicitly.
    private boolean venaryEnabled              = false;
    private String  venaryApiBase              = VenaryConfig.DEFAULT_API_BASE;
    private String  venaryApiKey               = "";
    private boolean venaryLoginJwtEnabled      = false;
    private boolean venaryStatsSyncEnabled     = false;
    private int     venaryStatsSyncIntervalMin = 15;
    private int     venaryLinkCooldownSeconds  = 30;

    private ModConfig() {}

    /** Remember the config dir so {@link #reload()} can re-read without arguments. */
    private Path configDir;

    /**
     * Re-read the properties file from the previously-cached config directory.
     * Safe to call from any thread; refreshes all fields in place.
     * @return true if reload completed (file present, or defaults rewritten); false if load() was never called yet.
     */
    public boolean reload() {
        if (configDir == null) {
            VonixServerUtilities.LOGGER.warn("[VonixSU] ModConfig.reload() called before initial load()");
            return false;
        }
        load(configDir);
        return true;
    }

    public void load(Path configDir) {
        this.configDir = configDir;
        Path file = configDir.resolve("vonix_server_utilities.properties");
        if (!Files.exists(file)) {
            writeDefaults(file);
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file)) {
            p.load(r);
        } catch (IOException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] Failed to read config, using defaults", e);
            return;
        }
        maxHomes              = intOf(p, "max_homes", 5);
        tpaTimeoutSeconds     = intOf(p, "tpa_timeout_seconds", 120);
        deathBackDelaySeconds = intOf(p, "death_back_delay_seconds", 0);

        venaryEnabled              = boolOf(p, "venary_enabled", false);
        venaryApiBase              = strOf(p,  "venary_api_base", VenaryConfig.DEFAULT_API_BASE);
        venaryApiKey               = strOf(p,  "venary_api_key", "");
        venaryLoginJwtEnabled      = boolOf(p, "venary_login_jwt_enabled", false);
        venaryStatsSyncEnabled     = boolOf(p, "venary_stats_sync_enabled", false);
        venaryStatsSyncIntervalMin = intOf(p,  "venary_stats_sync_interval_minutes", 15);
        venaryLinkCooldownSeconds  = intOf(p,  "venary_link_cooldown_seconds", 30);

        VonixServerUtilities.LOGGER.info("[VonixSU] Config loaded (max_homes={}, tpa_timeout={}s, death_back_delay={}s)",
                maxHomes, tpaTimeoutSeconds, deathBackDelaySeconds);
        // NOTE: never log the raw api key. The Venary section uses the masked accessor.
        VonixServerUtilities.LOGGER.info("[VonixSU] {}", getVenaryConfig());
    }

    private void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                w.write("""
                        # Vonix Server Utilities Configuration
                        # Maximum homes per player
                        max_homes=5
                        # Seconds before a TPA request expires
                        tpa_timeout_seconds=120
                        # Seconds after death before /backdeath is usable (0 = instant)
                        death_back_delay_seconds=0

                        # ─── Venary site-link integration ───────────────────────────────────
                        # Master kill switch. When false the mod NEVER contacts the Venary API.
                        venary_enabled=false
                        # Base URL of the Venary API (no trailing slash).
                        venary_api_base=https://api.vonix.network
                        # API key issued by Venary for this server (mc_servers.api_key).
                        # Paste it here exactly once. KEEP THIS FILE PRIVATE.
                        venary_api_key=
                        # If true, /login mints a Vonix JWT for the player. Default OFF.
                        venary_login_jwt_enabled=false
                        # If true, periodically POST per-player stats to Venary. Default OFF.
                        venary_stats_sync_enabled=false
                        # Stats sync interval in minutes (min 1).
                        venary_stats_sync_interval_minutes=15
                        # Per-player /link cooldown in seconds.
                        venary_link_cooldown_seconds=30
                        """);
            }
        } catch (IOException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] Failed to write default config", e);
        }
    }

    private static int intOf(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean boolOf(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        v = v.trim().toLowerCase();
        return v.equals("true") || v.equals("yes") || v.equals("1") || v.equals("on");
    }

    private static String strOf(Properties p, String key, String def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        v = v.trim();
        return v.isEmpty() ? def : v;
    }

    public int getMaxHomes()              { return maxHomes; }
    public long getTpaTimeoutMs()         { return tpaTimeoutSeconds * 1000L; }
    public int getDeathBackDelaySeconds() { return deathBackDelaySeconds; }

    /** Builds an immutable snapshot of the current Venary settings. */
    public VenaryConfig getVenaryConfig() {
        return new VenaryConfig(
                venaryEnabled,
                venaryApiBase,
                venaryApiKey,
                venaryLoginJwtEnabled,
                venaryStatsSyncEnabled,
                venaryStatsSyncIntervalMin,
                venaryLinkCooldownSeconds);
    }
}
