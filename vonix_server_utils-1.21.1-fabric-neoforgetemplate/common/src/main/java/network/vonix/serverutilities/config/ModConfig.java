package network.vonix.serverutilities.config;

import network.vonix.serverutilities.VonixServerUtilities;

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

    private ModConfig() {}

    public void load(Path configDir) {
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
        VonixServerUtilities.LOGGER.info("[VonixSU] Config loaded (max_homes={}, tpa_timeout={}s, death_back_delay={}s)",
                maxHomes, tpaTimeoutSeconds, deathBackDelaySeconds);
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

    public int getMaxHomes()              { return maxHomes; }
    public long getTpaTimeoutMs()         { return tpaTimeoutSeconds * 1000L; }
    public int getDeathBackDelaySeconds() { return deathBackDelaySeconds; }
}
