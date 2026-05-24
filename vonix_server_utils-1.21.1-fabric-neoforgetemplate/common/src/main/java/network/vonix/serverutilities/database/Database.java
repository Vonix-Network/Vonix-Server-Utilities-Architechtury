package network.vonix.serverutilities.database;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import network.vonix.serverutilities.VonixServerUtilities;

import java.io.File;
import java.nio.file.*;
import java.sql.*;

/**
 * Lightweight SQLite wrapper backed by a single persistent connection.
 * All public methods must be called from the VonixSU-DB executor thread only —
 * never from the main tick thread directly.
 *
 * On first start the database scans for an existing VonixCore database and
 * migrates homes, warps and kit-cooldown data into the cleaner vsu_* schema.
 *
 * DB file: config/vonix_server_utilities/data.db
 */
public final class Database {

    private static final Path DB_PATH =
            Paths.get("config", "vonix_server_utilities", "data.db");

    private Connection connection;

    /**
     * Initialise the database. Table creation runs synchronously so that the
     * connection is immediately usable; the VonixCore migration is submitted
     * to the DB executor so it does not delay server startup.
     */
    public void init(MinecraftServer server) {
        try {
            Files.createDirectories(DB_PATH.getParent());
            // Explicitly load the SQLite JDBC driver class.
            // On Forge/NeoForge, DriverManager uses the bootstrap class loader which
            // cannot see drivers loaded by FML's mod class loader, so auto-discovery
            // via ServiceLoader silently fails. Class.forName() forces registration.
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH.toAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
            }
            createTables();
            VonixServerUtilities.LOGGER.info("[VonixSU] Database ready at {}", DB_PATH.toAbsolutePath());
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] Database initialisation failed", e);
            return;
        }

        // Migration runs off the main thread; all later DB tasks queue behind it.
        VonixServerUtilities.dbAsync(() -> attemptMigration(server));
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    private void createTables() throws SQLException {
        try (Statement s = connection.createStatement()) {
            // Homes
            s.execute("""
                CREATE TABLE IF NOT EXISTS vsu_homes (
                    uuid  TEXT NOT NULL,
                    name  TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x     REAL NOT NULL,
                    y     REAL NOT NULL,
                    z     REAL NOT NULL,
                    yaw   REAL NOT NULL,
                    pitch REAL NOT NULL,
                    PRIMARY KEY (uuid, name)
                )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_vsu_homes_uuid ON vsu_homes (uuid)");

            // Warps
            s.execute("""
                CREATE TABLE IF NOT EXISTS vsu_warps (
                    name       TEXT PRIMARY KEY,
                    world      TEXT NOT NULL,
                    x          REAL NOT NULL,
                    y          REAL NOT NULL,
                    z          REAL NOT NULL,
                    yaw        REAL NOT NULL,
                    pitch      REAL NOT NULL,
                    created_by TEXT,
                    created_at INTEGER NOT NULL DEFAULT 0
                )""");

            // Kit cooldowns
            s.execute("""
                CREATE TABLE IF NOT EXISTS vsu_kit_cooldowns (
                    uuid      TEXT NOT NULL,
                    kit_name  TEXT NOT NULL,
                    last_used INTEGER NOT NULL,
                    PRIMARY KEY (uuid, kit_name)
                )""");

            // One-time migration tracking
            s.execute("""
                CREATE TABLE IF NOT EXISTS vsu_migration (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )""");
        }
    }

    // ── Migration from VonixCore ──────────────────────────────────────────────

    private void attemptMigration(MinecraftServer server) {
        try {
            // Skip if already completed
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT value FROM vsu_migration WHERE key='vonixcore_migrated'")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next() && "true".equals(rs.getString(1))) return;
            }

            File oldDb = findVonixCoreDb(server);
            if (oldDb == null) {
                markMigrated();
                VonixServerUtilities.LOGGER.info("[VonixSU] No VonixCore database found — skipping migration.");
                return;
            }

            VonixServerUtilities.LOGGER.info(
                    "[VonixSU] Migrating data from VonixCore database at {} …", oldDb.getAbsolutePath());

            try (Connection src = DriverManager.getConnection("jdbc:sqlite:" + oldDb.getAbsolutePath())) {
                int homes = migrateHomes(src);
                int warps = migrateWarps(src);
                int kits  = migrateKitCooldowns(src);
                VonixServerUtilities.LOGGER.info(
                        "[VonixSU] Migration complete — {} homes, {} warps, {} kit-cooldowns imported.",
                        homes, warps, kits);
            }

            markMigrated();
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.warn("[VonixSU] Migration non-fatal error: {}", e.getMessage());
        }
    }

    private File findVonixCoreDb(MinecraftServer server) {
        // Standard VonixCore path: <world_root>/vonixcore/vonixcore.db
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        File f1 = worldRoot.resolve("vonixcore/vonixcore.db").toFile();
        if (f1.exists()) return f1;

        // Alternate: server directory / vonixcore / vonixcore.db
        File f2 = server.getServerDirectory().resolve("vonixcore/vonixcore.db").toFile();
        if (f2.exists()) return f2;

        // Fall back: scan for any .db file in the vonixcore subfolder
        File dir = worldRoot.resolve("vonixcore").toFile();
        if (dir.isDirectory()) {
            File[] dbs = dir.listFiles((d, n) -> n.endsWith(".db"));
            if (dbs != null && dbs.length > 0) return dbs[0];
        }
        return null;
    }

    /** Prefer vc_homes (active in VonixCore), fall back to vonixcore_homes. */
    private int migrateHomes(Connection src) {
        for (String table : new String[]{"vc_homes", "vonixcore_homes"}) {
            if (!tableExists(src, table)) continue;
            int count = 0;
            try (PreparedStatement sel = src.prepareStatement(
                        "SELECT uuid,name,world,x,y,z,yaw,pitch FROM " + table);
                 PreparedStatement ins = connection.prepareStatement(
                        "INSERT OR IGNORE INTO vsu_homes VALUES(?,?,?,?,?,?,?,?)")) {
                ResultSet rs = sel.executeQuery();
                while (rs.next()) {
                    ins.setString(1, rs.getString("uuid"));
                    ins.setString(2, rs.getString("name"));
                    ins.setString(3, rs.getString("world"));
                    ins.setDouble(4, rs.getDouble("x"));
                    ins.setDouble(5, rs.getDouble("y"));
                    ins.setDouble(6, rs.getDouble("z"));
                    ins.setFloat (7, rs.getFloat("yaw"));
                    ins.setFloat (8, rs.getFloat("pitch"));
                    ins.executeUpdate();
                    count++;
                }
                VonixServerUtilities.LOGGER.info("[VonixSU] Migrated {} homes from '{}'.", count, table);
                return count;
            } catch (SQLException e) {
                VonixServerUtilities.LOGGER.warn("[VonixSU] homes migration error on '{}': {}", table, e.getMessage());
            }
        }
        return 0;
    }

    /** Prefer vc_warps, fall back to vonixcore_warps. */
    private int migrateWarps(Connection src) {
        for (String table : new String[]{"vc_warps", "vonixcore_warps"}) {
            if (!tableExists(src, table)) continue;
            int count = 0;
            try (Statement sel = src.createStatement();
                 PreparedStatement ins = connection.prepareStatement(
                        "INSERT OR IGNORE INTO vsu_warps(name,world,x,y,z,yaw,pitch,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
                ResultSet rs = sel.executeQuery("SELECT name,world,x,y,z,yaw,pitch FROM " + table);
                while (rs.next()) {
                    ins.setString(1, rs.getString("name"));
                    ins.setString(2, rs.getString("world"));
                    ins.setDouble(3, rs.getDouble("x"));
                    ins.setDouble(4, rs.getDouble("y"));
                    ins.setDouble(5, rs.getDouble("z"));
                    ins.setFloat (6, rs.getFloat("yaw"));
                    ins.setFloat (7, rs.getFloat("pitch"));
                    ins.setNull  (8, Types.VARCHAR);
                    ins.setLong  (9, System.currentTimeMillis() / 1000L);
                    ins.executeUpdate();
                    count++;
                }
                VonixServerUtilities.LOGGER.info("[VonixSU] Migrated {} warps from '{}'.", count, table);
                return count;
            } catch (SQLException e) {
                VonixServerUtilities.LOGGER.warn("[VonixSU] warps migration error on '{}': {}", table, e.getMessage());
            }
        }
        return 0;
    }

    private int migrateKitCooldowns(Connection src) {
        if (!tableExists(src, "vc_kit_cooldowns")) return 0;
        int count = 0;
        try (Statement sel = src.createStatement();
             PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO vsu_kit_cooldowns VALUES(?,?,?)")) {
            ResultSet rs = sel.executeQuery("SELECT uuid,kit_name,last_used FROM vc_kit_cooldowns");
            while (rs.next()) {
                ins.setString(1, rs.getString("uuid"));
                ins.setString(2, rs.getString("kit_name"));
                ins.setLong  (3, rs.getLong("last_used"));
                ins.executeUpdate();
                count++;
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.warn("[VonixSU] kit-cooldowns migration error: {}", e.getMessage());
        }
        return count;
    }

    private boolean tableExists(Connection c, String table) {
        try (Statement s = c.createStatement()) {
            s.executeQuery("SELECT 1 FROM " + table + " LIMIT 1");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private void markMigrated() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO vsu_migration VALUES('vonixcore_migrated','true')")) {
            ps.executeUpdate();
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the persistent connection. Must only be accessed from the DB executor thread. */
    public Connection getConnection() { return connection; }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                VonixServerUtilities.LOGGER.info("[VonixSU] Database connection closed.");
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] Error closing database", e);
        }
    }
}
