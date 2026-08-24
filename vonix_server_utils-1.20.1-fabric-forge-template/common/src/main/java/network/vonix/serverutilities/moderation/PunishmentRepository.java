package network.vonix.serverutilities.moderation;

import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite CRUD for the {@code punishments} table.
 *
 * Co-locates with the existing VSU database (see {@link Database}). Bootstraps
 * its schema on first use via {@link #ensureSchema(Connection)} which is invoked
 * lazily from every public method — this avoids editing {@link Database#init}
 * itself while still guaranteeing the table exists before first read/write.
 *
 * All public methods are blocking JDBC calls and MUST be invoked from the
 * VonixSU-DB executor thread (i.e. wrapped in {@link VonixServerUtilities#dbAsync}).
 */
public final class PunishmentRepository {

    /** Authoritative schema -- copy/paste from V1.6.0-SPEC.md. */
    public static final String SCHEMA_SQL =
            "CREATE TABLE IF NOT EXISTS punishments (" +
            "    id            INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    type          TEXT NOT NULL," +
            "    target_uuid   TEXT NOT NULL," +
            "    target_name   TEXT NOT NULL," +
            "    issuer_uuid   TEXT," +
            "    issuer_name   TEXT NOT NULL," +
            "    reason        TEXT," +
            "    issued_at     INTEGER NOT NULL," +
            "    expires_at    INTEGER," +
            "    active        INTEGER NOT NULL DEFAULT 1," +
            "    revoked_by    TEXT," +
            "    revoked_at    INTEGER" +
            ")";

    private static final String IDX_TARGET_SQL =
            "CREATE INDEX IF NOT EXISTS idx_punishments_target_active ON punishments(target_uuid, active, type)";
    private static final String IDX_EXPIRES_SQL =
            "CREATE INDEX IF NOT EXISTS idx_punishments_expires ON punishments(active, expires_at)";

    private static volatile boolean schemaReady = false;

    private PunishmentRepository() {}

    /** Idempotent; safe to call from every code path. */
    public static synchronized void ensureSchema(Connection c) throws SQLException {
        if (schemaReady) return;
        try (Statement s = c.createStatement()) {
            s.execute(SCHEMA_SQL);
            s.execute(IDX_TARGET_SQL);
            s.execute(IDX_EXPIRES_SQL);
        }
        schemaReady = true;
    }

    private static Connection conn() throws SQLException {
        Connection c = VonixServerUtilities.getInstance().getDatabase().getConnection();
        if (c == null) throw new SQLException("Database connection not initialised");
        ensureSchema(c);
        return c;
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    /** Insert a new punishment row. Returns generated id, or -1 on error. */
    public static long insert(Punishment p) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT INTO punishments(type,target_uuid,target_name,issuer_uuid,issuer_name," +
                "reason,issued_at,expires_at,active,revoked_by,revoked_at)" +
                " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.type().name());
            ps.setString(2, p.targetUuid().toString());
            ps.setString(3, p.targetName());
            if (p.issuerUuid() != null) ps.setString(4, p.issuerUuid().toString());
            else                        ps.setNull  (4, Types.VARCHAR);
            ps.setString(5, p.issuerName());
            if (p.reason() != null) ps.setString(6, p.reason());
            else                    ps.setNull  (6, Types.VARCHAR);
            ps.setLong(7, p.issuedAt());
            if (p.expiresAt() != null) ps.setLong(8, p.expiresAt());
            else                       ps.setNull(8, Types.INTEGER);
            ps.setInt(9, p.active() ? 1 : 0);
            if (p.revokedBy() != null) ps.setString(10, p.revokedBy());
            else                       ps.setNull  (10, Types.VARCHAR);
            if (p.revokedAt() != null) ps.setLong(11, p.revokedAt());
            else                       ps.setNull(11, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] insert punishment failed", e);
        }
        return -1L;
    }

    /**
     * Latest active row of {@code type} against {@code target}, or empty.
     * Filters out rows whose expiry has passed (does not mark them inactive --
     * that's the sweeper's job).
     */
    public static Optional<Punishment> findActive(UUID target, Punishment.Type type) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM punishments " +
                "WHERE target_uuid=? AND type=? AND active=1 " +
                "AND (expires_at IS NULL OR expires_at>?) " +
                "ORDER BY issued_at DESC LIMIT 1")) {
            ps.setString(1, target.toString());
            ps.setString(2, type.name());
            ps.setLong(3, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Punishment p = mapRow(rs);
                    if (p.isExpired(System.currentTimeMillis())) return Optional.empty();
                    return Optional.of(p);
                }
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] findActive failed", e);
        }
        return Optional.empty();
    }

    /** True when an unexpired active mute row exists for the target. */
    public static boolean hasActiveMute(UUID target) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT 1 FROM punishments WHERE target_uuid=? AND type='MUTE' AND active=1 " +
                "AND (expires_at IS NULL OR expires_at>?) LIMIT 1")) {
            ps.setString(1, target.toString());
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }


    /** Revoke (set active=0) the latest active row of {@code type} for {@code target}. */
    public static boolean revoke(UUID target, Punishment.Type type, String revokedBy) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE punishments SET active=0, revoked_by=?, revoked_at=? " +
                "WHERE id=(SELECT id FROM punishments WHERE target_uuid=? AND type=? AND active=1 " +
                "         ORDER BY issued_at DESC LIMIT 1)")) {
            ps.setString(1, revokedBy);
            ps.setLong  (2, System.currentTimeMillis());
            ps.setString(3, target.toString());
            ps.setString(4, type.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] revoke failed", e);
        }
        return false;
    }

    /** Mark all active rows whose expiry has passed as inactive. Returns rows affected. */
    public static List<Punishment> sweepExpired() {
        long now = System.currentTimeMillis();
        List<Punishment> swept = new ArrayList<>();
        try {
            Connection c = conn();
            // First snapshot what we're about to sweep so callers (MuteState) can react.
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM punishments WHERE active=1 AND expires_at IS NOT NULL AND expires_at<=?")) {
                ps.setLong(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) swept.add(mapRow(rs));
                }
            }
            if (swept.isEmpty()) return swept;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE punishments SET active=0 WHERE active=1 AND expires_at IS NOT NULL AND expires_at<=?")) {
                ps.setLong(1, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] sweepExpired failed", e);
        }
        return swept;
    }

    /** Paged list of active rows of given types (use null for "all"). */
    public static List<Punishment> list(Punishment.Type type, int page, int pageSize) {
        List<Punishment> out = new ArrayList<>();
        String sql = (type == null)
                ? "SELECT * FROM punishments WHERE active=1 ORDER BY issued_at DESC LIMIT ? OFFSET ?"
                : "SELECT * FROM punishments WHERE active=1 AND type=? ORDER BY issued_at DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            int idx = 1;
            if (type != null) ps.setString(idx++, type.name());
            ps.setInt(idx++, pageSize);
            ps.setInt(idx,   Math.max(0, (page - 1) * pageSize));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] list failed", e);
        }
        return out;
    }

    /** Full history of one target, optionally filtered by type. Newest first. */
    public static List<Punishment> history(UUID target, Punishment.Type type, int limit) {
        List<Punishment> out = new ArrayList<>();
        String sql = (type == null)
                ? "SELECT * FROM punishments WHERE target_uuid=? ORDER BY issued_at DESC LIMIT ?"
                : "SELECT * FROM punishments WHERE target_uuid=? AND type=? ORDER BY issued_at DESC LIMIT ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, target.toString());
            if (type != null) {
                ps.setString(2, type.name());
                ps.setInt(3, limit);
            } else {
                ps.setInt(2, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] history failed", e);
        }
        return out;
    }

    /** All UUIDs with an active MUTE row (used to seed MuteState on server start). */
    public static List<UUID> activeMuteUuids() {
        List<UUID> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT DISTINCT target_uuid FROM punishments " +
                "WHERE type='MUTE' AND active=1 AND (expires_at IS NULL OR expires_at>?)")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try { out.add(UUID.fromString(rs.getString(1))); }
                    catch (IllegalArgumentException ignore) {}
                }
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] activeMuteUuids failed", e);
        }
        return out;
    }

    /** Distinct historical names from the punishments table (used for tab-complete). */
    public static List<String> historicalNames() {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT DISTINCT target_name FROM punishments")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] historicalNames failed", e);
        }
        return out;
    }

    /** Count rows of the given type for a target (used for /warnings header). */
    public static int count(UUID target, Punishment.Type type, boolean activeOnly) {
        String sql = activeOnly
                ? "SELECT COUNT(*) FROM punishments WHERE target_uuid=? AND type=? AND active=1"
                : "SELECT COUNT(*) FROM punishments WHERE target_uuid=? AND type=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] count failed", e);
        }
        return 0;
    }

    /** Set active=0 on every row of {@code type} for {@code target}. Used by /clearwarnings. */
    public static int clearActive(UUID target, Punishment.Type type, String clearedBy) {
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE punishments SET active=0, revoked_by=?, revoked_at=? " +
                "WHERE target_uuid=? AND type=? AND active=1")) {
            ps.setString(1, clearedBy);
            ps.setLong  (2, System.currentTimeMillis());
            ps.setString(3, target.toString());
            ps.setString(4, type.name());
            return ps.executeUpdate();
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU/mod] clearActive failed", e);
        }
        return 0;
    }

    // ── Row mapping ──────────────────────────────────────────────────────────

    private static Punishment mapRow(ResultSet rs) throws SQLException {
        UUID target = parseUuid(rs.getString("target_uuid"));
        UUID issuer = parseUuid(rs.getString("issuer_uuid"));
        Long expiresAt = rs.getObject("expires_at") == null ? null : rs.getLong("expires_at");
        Long revokedAt = rs.getObject("revoked_at") == null ? null : rs.getLong("revoked_at");
        Punishment.Type type;
        try { type = Punishment.Type.valueOf(rs.getString("type")); }
        catch (IllegalArgumentException e) { type = Punishment.Type.WARN; }
        return new Punishment(
                rs.getLong("id"),
                type,
                target,
                rs.getString("target_name"),
                issuer,
                rs.getString("issuer_name"),
                rs.getString("reason"),
                rs.getLong("issued_at"),
                expiresAt,
                rs.getInt("active") == 1,
                rs.getString("revoked_by"),
                revokedAt);
    }

    private static UUID parseUuid(String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); }
        catch (IllegalArgumentException e) { return null; }
    }
}
