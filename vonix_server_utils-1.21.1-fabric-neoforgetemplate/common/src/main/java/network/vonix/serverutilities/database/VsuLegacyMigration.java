package network.vonix.serverutilities.database;

import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;

/**
 * Imports retained data.db snapshots produced by older Vonix Server Utilities releases.
 * The source is opened read-only and the destination transaction is all-or-nothing.
 */
public final class VsuLegacyMigration {
    private VsuLegacyMigration() {}

    public record ImportResult(boolean recognized, int homesInserted, int backLocationsInserted,
                               String sourceFingerprint) {}

    public static String fingerprint(Path source) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(source)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest.digest()) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static ImportResult importInto(Connection destination, Path source,
                                          String sourceFingerprint) throws SQLException {
        if (!Files.isRegularFile(source)) {
            return new ImportResult(false, 0, 0, sourceFingerprint);
        }

        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        String url = "jdbc:sqlite:" + source.toAbsolutePath();
        try (Connection src = DriverManager.getConnection(url, config.toProperties())) {
            boolean hasHomes = tableExists(src, "vsu_homes");
            boolean hasBack = tableExists(src, "vsu_back_locations");
            if (!hasHomes && !hasBack) {
                return new ImportResult(false, 0, 0, sourceFingerprint);
            }

            if (hasHomes) requireColumns(src, "vsu_homes",
                    "uuid", "name", "world", "x", "y", "z", "yaw", "pitch");
            if (hasBack) requireColumns(src, "vsu_back_locations",
                    "uuid", "world", "x", "y", "z", "yaw", "pitch", "kind", "updated_at");

            boolean previousAutoCommit = destination.getAutoCommit();
            destination.setAutoCommit(false);
            try {
                int homes = hasHomes ? importHomes(destination, src) : 0;
                int back = hasBack ? importBackLocations(destination, src) : 0;
                destination.commit();
                return new ImportResult(true, homes, back, sourceFingerprint);
            } catch (Exception e) {
                try { destination.rollback(); } catch (SQLException ignored) {}
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("VSU legacy import failed", e);
            } finally {
                try { destination.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) {}
            }
        }
    }

    private static int importHomes(Connection destination, Connection source) throws SQLException {
        int inserted = 0;
        try (Statement select = source.createStatement();
             ResultSet rows = select.executeQuery(
                     "SELECT uuid,name,world,x,y,z,yaw,pitch FROM vsu_homes ORDER BY uuid,name");
             PreparedStatement insert = destination.prepareStatement(
                     "INSERT OR IGNORE INTO vsu_homes(uuid,name,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?,?)")) {
            while (rows.next()) {
                String uuid = requiredText(rows, "uuid");
                String name = requiredText(rows, "name");
                String world = requiredText(rows, "world");
                double x = requiredDouble(rows, "x");
                double y = requiredDouble(rows, "y");
                double z = requiredDouble(rows, "z");
                float yaw = requiredFloat(rows, "yaw");
                float pitch = requiredFloat(rows, "pitch");
                insert.setString(1, uuid);
                insert.setString(2, name);
                insert.setString(3, world);
                insert.setDouble(4, x);
                insert.setDouble(5, y);
                insert.setDouble(6, z);
                insert.setFloat(7, yaw);
                insert.setFloat(8, pitch);
                inserted += insert.executeUpdate();
            }
        }
        return inserted;
    }

    private static int importBackLocations(Connection destination, Connection source) throws SQLException {
        int inserted = 0;
        try (Statement select = source.createStatement();
             ResultSet rows = select.executeQuery(
                     "SELECT uuid,world,x,y,z,yaw,pitch,kind,updated_at FROM vsu_back_locations ORDER BY uuid,kind");
             PreparedStatement insert = destination.prepareStatement(
                     "INSERT OR IGNORE INTO vsu_back_locations(uuid,world,x,y,z,yaw,pitch,kind,updated_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
            while (rows.next()) {
                String uuid = requiredText(rows, "uuid");
                String world = requiredText(rows, "world");
                double x = requiredDouble(rows, "x");
                double y = requiredDouble(rows, "y");
                double z = requiredDouble(rows, "z");
                float yaw = requiredFloat(rows, "yaw");
                float pitch = requiredFloat(rows, "pitch");
                String kind = requiredText(rows, "kind");
                if (!"tp".equals(kind) && !"death".equals(kind)) {
                    throw new SQLException("unsupported VSU back-location kind: " + kind);
                }
                long updatedAt = rows.getLong("updated_at");
                if (rows.wasNull()) throw new SQLException("null updated_at in vsu_back_locations");
                insert.setString(1, uuid);
                insert.setString(2, world);
                insert.setDouble(3, x);
                insert.setDouble(4, y);
                insert.setDouble(5, z);
                insert.setFloat(6, yaw);
                insert.setFloat(7, pitch);
                insert.setString(8, kind);
                insert.setLong(9, updatedAt);
                inserted += insert.executeUpdate();
            }
        }
        return inserted;
    }

    private static String requiredText(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        if (value == null || value.isBlank()) throw new SQLException("blank " + column);
        return value;
    }

    private static double requiredDouble(ResultSet rows, String column) throws SQLException {
        double value = rows.getDouble(column);
        if (rows.wasNull() || !Double.isFinite(value)) throw new SQLException("invalid " + column);
        return value;
    }

    private static float requiredFloat(ResultSet rows, String column) throws SQLException {
        float value = rows.getFloat(column);
        if (rows.wasNull() || !Float.isFinite(value)) throw new SQLException("invalid " + column);
        return value;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void requireColumns(Connection connection, String table, String... required)
            throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + table + " LIMIT 0")) {
            ResultSetMetaData metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                columns.add(metadata.getColumnName(i).toLowerCase());
            }
        }
        for (String column : required) {
            if (!columns.contains(column)) {
                throw new SQLException("legacy VSU table " + table + " is missing column " + column);
            }
        }
    }
}
