package network.vonix.serverutilities.database;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import network.vonix.serverutilities.VonixServerUtilities;

import java.io.File;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            // NOTE: We use `org.sqlite.JDBC.class.getName()` rather than a string
            // literal so shadow's `relocate` rewrites the bytecode reference. After
            // relocation the actual class name at runtime is
            // network.vonix.serverutilities.shadow.sqlite.JDBC — string literals
            // would NOT be rewritten and the driver would fail to load.
            Class.forName(org.sqlite.JDBC.class.getName());
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

            // Back-location store (kind='tp' or 'death')
            s.execute("""
                CREATE TABLE IF NOT EXISTS vsu_back_locations (
                    uuid       TEXT NOT NULL,
                    world      TEXT NOT NULL,
                    x          REAL NOT NULL,
                    y          REAL NOT NULL,
                    z          REAL NOT NULL,
                    yaw        REAL NOT NULL,
                    pitch      REAL NOT NULL,
                    kind       TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (uuid, kind)
                )""");

            // Nicknames
            s.execute("""
                CREATE TABLE IF NOT EXISTS vsu_nicknames (
                    uuid       TEXT PRIMARY KEY,
                    nickname   TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )""");

            // Ignore list
            s.execute("""
                CREATE TABLE IF NOT EXISTS vsu_ignore_list (
                    owner_uuid  TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    created_at  INTEGER NOT NULL,
                    PRIMARY KEY (owner_uuid, target_uuid)
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
                if (rs.next() && "true".equals(rs.getString(1))) {
                    VonixServerUtilities.LOGGER.info(
                            "[VonixSU] VonixCore migration already completed — skipping.");
                    return;
                }
            }

            File oldDb = findVonixCoreDb(server);
            if (oldDb == null) {
                markMigrated();
                VonixServerUtilities.LOGGER.info("[VonixSU] No VonixCore database found — skipping migration.");
                return;
            }

            VonixServerUtilities.LOGGER.info(
                    "[VonixSU] Migrating data from VonixCore database at {} …", oldDb.getAbsolutePath());

            // Top Fix #2 — preserve source DB before reading it.
            try {
                Path oldDbPath = oldDb.toPath();
                Path backup = oldDbPath.resolveSibling(
                        "vonixcore.db.bak-" + System.currentTimeMillis());
                Files.copy(oldDbPath, backup, StandardCopyOption.REPLACE_EXISTING);
                VonixServerUtilities.LOGGER.info(
                        "[VonixSU] Backed up source DB to {}", backup.getFileName());
            } catch (Exception bkErr) {
                VonixServerUtilities.LOGGER.warn(
                        "[VonixSU] Could not create source-DB backup: {}", bkErr.getMessage());
            }

            // Top Fix #2 — open the source read-only so a bug here can never harm it.
            String srcUrl = "jdbc:sqlite:" + oldDb.getAbsolutePath() + "?open_mode=1";
            boolean allOk = true;
            int homes = 0, warps = 0, kits = 0;

            // Top Fix #3 — wrap all per-table migrations in a single transaction.
            boolean prevAuto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Connection src = DriverManager.getConnection(srcUrl)) {
                try { homes = migrateHomes(src); }
                catch (Exception e) { allOk = false;
                    VonixServerUtilities.LOGGER.warn("[VonixSU] migrateHomes failed: {}", e.getMessage()); }

                try { warps = migrateWarps(src); }
                catch (Exception e) { allOk = false;
                    VonixServerUtilities.LOGGER.warn("[VonixSU] migrateWarps failed: {}", e.getMessage()); }

                try { kits = migrateKitCooldowns(src); }
                catch (Exception e) { allOk = false;
                    VonixServerUtilities.LOGGER.warn("[VonixSU] migrateKitCooldowns failed: {}", e.getMessage()); }

                if (allOk) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
            } catch (Exception outer) {
                allOk = false;
                try { connection.rollback(); } catch (SQLException ignore) {}
                VonixServerUtilities.LOGGER.warn(
                        "[VonixSU] Migration aborted: {}", outer.getMessage());
            } finally {
                try { connection.setAutoCommit(prevAuto); } catch (SQLException ignore) {}
            }

            if (allOk) {
                VonixServerUtilities.LOGGER.info(
                        "[VonixSU] Migration complete — {} homes, {} warps, {} kit-cooldowns imported.",
                        homes, warps, kits);
                markMigrated();
            } else {
                VonixServerUtilities.LOGGER.warn(
                        "[VonixSU] Migration encountered errors — vonixcore_migrated NOT set. "
                        + "Investigate logs and re-attempt after correcting the source DB.");
            }
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
        File f2 = server.getServerDirectory().toPath().resolve("vonixcore/vonixcore.db").toFile();
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
    private int migrateHomes(Connection src) throws SQLException {
        SQLException last = null;
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
                last = e;
                VonixServerUtilities.LOGGER.warn("[VonixSU] homes migration error on '{}': {}", table, e.getMessage());
            }
        }
        if (last != null) throw last;
        return 0;
    }

    /** Prefer vc_warps, fall back to vonixcore_warps. Preserves created_by / created_at when present. */
    private int migrateWarps(Connection src) throws SQLException {
        SQLException last = null;
        for (String table : new String[]{"vc_warps", "vonixcore_warps"}) {
            if (!tableExists(src, table)) continue;
            // Probe column set so legacy schemas missing created_by/created_at still work.
            Set<String> cols = columnsOf(src, table);
            boolean hasCreatedBy = cols.contains("created_by");
            boolean hasCreatedAt = cols.contains("created_at");

            StringBuilder selSql = new StringBuilder("SELECT name,world,x,y,z,yaw,pitch");
            if (hasCreatedBy) selSql.append(",created_by");
            if (hasCreatedAt) selSql.append(",created_at");
            selSql.append(" FROM ").append(table);

            int count = 0;
            try (Statement sel = src.createStatement();
                 PreparedStatement ins = connection.prepareStatement(
                        "INSERT OR IGNORE INTO vsu_warps(name,world,x,y,z,yaw,pitch,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
                ResultSet rs = sel.executeQuery(selSql.toString());
                long nowSec = System.currentTimeMillis() / 1000L;
                while (rs.next()) {
                    ins.setString(1, rs.getString("name"));
                    ins.setString(2, rs.getString("world"));
                    ins.setDouble(3, rs.getDouble("x"));
                    ins.setDouble(4, rs.getDouble("y"));
                    ins.setDouble(5, rs.getDouble("z"));
                    ins.setFloat (6, rs.getFloat("yaw"));
                    ins.setFloat (7, rs.getFloat("pitch"));
                    if (hasCreatedBy) {
                        String cb = rs.getString("created_by");
                        if (cb == null) ins.setNull(8, Types.VARCHAR);
                        else ins.setString(8, cb);
                    } else {
                        ins.setNull(8, Types.VARCHAR);
                    }
                    ins.setLong(9, hasCreatedAt ? rs.getLong("created_at") : nowSec);
                    ins.executeUpdate();
                    count++;
                }
                VonixServerUtilities.LOGGER.info("[VonixSU] Migrated {} warps from '{}'.", count, table);
                return count;
            } catch (SQLException e) {
                last = e;
                VonixServerUtilities.LOGGER.warn("[VonixSU] warps migration error on '{}': {}", table, e.getMessage());
            }
        }
        if (last != null) throw last;
        return 0;
    }

    /** Try vc_kit_cooldowns, then vonixcore_kit_cooldowns, then bare kit_cooldowns. */
    private int migrateKitCooldowns(Connection src) throws SQLException {
        SQLException last = null;
        for (String table : new String[]{"vc_kit_cooldowns", "vonixcore_kit_cooldowns", "kit_cooldowns"}) {
            if (!tableExists(src, table)) continue;
            int count = 0;
            try (Statement sel = src.createStatement();
                 PreparedStatement ins = connection.prepareStatement(
                        "INSERT OR IGNORE INTO vsu_kit_cooldowns VALUES(?,?,?)")) {
                ResultSet rs = sel.executeQuery("SELECT uuid,kit_name,last_used FROM " + table);
                while (rs.next()) {
                    ins.setString(1, rs.getString("uuid"));
                    ins.setString(2, rs.getString("kit_name"));
                    ins.setLong  (3, rs.getLong("last_used"));
                    ins.executeUpdate();
                    count++;
                }
                VonixServerUtilities.LOGGER.info("[VonixSU] Migrated {} kit-cooldowns from '{}'.", count, table);
                return count;
            } catch (SQLException e) {
                last = e;
                VonixServerUtilities.LOGGER.warn(
                        "[VonixSU] kit-cooldowns migration error on '{}': {}", table, e.getMessage());
            }
        }
        if (last != null) throw last;
        return 0;
    }

    private boolean tableExists(Connection c, String table) {
        try (Statement s = c.createStatement()) {
            s.executeQuery("SELECT 1 FROM " + table + " LIMIT 1");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private Set<String> columnsOf(Connection c, String table) {
        Set<String> out = new HashSet<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM " + table + " LIMIT 0")) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                out.add(md.getColumnName(i).toLowerCase());
            }
        } catch (SQLException ignore) {}
        return out;
    }

    private void markMigrated() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO vsu_migration VALUES('vonixcore_migrated','true')")) {
            ps.executeUpdate();
        }
    }

    // ── Back-location persistence ─────────────────────────────────────────────

    public record BackLocation(String world, double x, double y, double z,
                               float yaw, float pitch, String kind, long updatedAt) {}

    /** Upsert a back-location for the given player. kind = "tp" or "death". */
    public void setBackLocation(java.util.UUID uuid, String world,
                                 double x, double y, double z, float yaw, float pitch,
                                 String kind) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO vsu_back_locations VALUES(?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, world);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setFloat (6, yaw);
            ps.setFloat (7, pitch);
            ps.setString(8, kind);
            ps.setLong  (9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] setBackLocation failed", e);
        }
    }

    public BackLocation getBackLocation(java.util.UUID uuid, String kind) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world,x,y,z,yaw,pitch,updated_at FROM vsu_back_locations WHERE uuid=? AND kind=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kind);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new BackLocation(
                    rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                    rs.getFloat(5), rs.getFloat(6), kind, rs.getLong(7));
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getBackLocation failed", e);
        }
        return null;
    }

    public void deleteBackLocation(java.util.UUID uuid, String kind) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM vsu_back_locations WHERE uuid=? AND kind=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kind);
            ps.executeUpdate();
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] deleteBackLocation failed", e);
        }
    }

    /** All persisted back-locations (used to rehydrate the in-memory cache on startup). */
    public List<Object[]> getAllBackLocations() {
        List<Object[]> out = new ArrayList<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT uuid,world,x,y,z,yaw,pitch,kind,updated_at FROM vsu_back_locations")) {
            while (rs.next()) {
                out.add(new Object[]{
                        rs.getString(1), rs.getString(2),
                        rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                        rs.getFloat(6), rs.getFloat(7),
                        rs.getString(8), rs.getLong(9)});
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getAllBackLocations failed", e);
        }
        return out;
    }

    // ── Nickname persistence ──────────────────────────────────────────────────

    public void setNickname(java.util.UUID uuid, String nickname) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO vsu_nicknames VALUES(?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, nickname);
            ps.setLong  (3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] setNickname failed", e);
        }
    }

    public String getNickname(java.util.UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT nickname FROM vsu_nicknames WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getNickname failed", e);
        }
        return null;
    }

    public void deleteNickname(java.util.UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM vsu_nicknames WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] deleteNickname failed", e);
        }
    }

    /** Returns all (uuid, nickname) pairs. */
    public List<String[]> getAllNicknames() {
        List<String[]> out = new ArrayList<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT uuid,nickname FROM vsu_nicknames")) {
            while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2)});
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getAllNicknames failed", e);
        }
        return out;
    }

    // ── Ignore-list persistence ───────────────────────────────────────────────

    public void addIgnore(java.util.UUID owner, java.util.UUID target) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO vsu_ignore_list VALUES(?,?,?)")) {
            ps.setString(1, owner.toString());
            ps.setString(2, target.toString());
            ps.setLong  (3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] addIgnore failed", e);
        }
    }

    public void removeIgnore(java.util.UUID owner, java.util.UUID target) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM vsu_ignore_list WHERE owner_uuid=? AND target_uuid=?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, target.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] removeIgnore failed", e);
        }
    }

    public List<String[]> getAllIgnores() {
        List<String[]> out = new ArrayList<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT owner_uuid,target_uuid FROM vsu_ignore_list")) {
            while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2)});
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getAllIgnores failed", e);
        }
        return out;
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
