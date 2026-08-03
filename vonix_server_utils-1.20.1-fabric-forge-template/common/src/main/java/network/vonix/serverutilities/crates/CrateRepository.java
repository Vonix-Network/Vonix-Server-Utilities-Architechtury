package network.vonix.serverutilities.crates;

import network.vonix.serverutilities.VonixServerUtilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SQLite repository for typed virtual keys and command-backed crates.
 *
 * <p>Every method must execute on the single {@code VonixSU-DB} executor. The
 * shared connection is deliberately never closed by this class; its lifecycle
 * belongs to {@link network.vonix.serverutilities.database.Database}.</p>
 */
public final class CrateRepository {
    private static final CrateRepository INSTANCE = new CrateRepository();
    private CrateRepository() {}
    public static CrateRepository getInstance() { return INSTANCE; }

    public void ensureSchema(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) throw new SQLException("Database connection is not ready");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vsu_crates (name TEXT PRIMARY KEY, key_type TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vsu_crate_prizes (crate_name TEXT NOT NULL, prize_id INTEGER NOT NULL, label TEXT NOT NULL, command TEXT NOT NULL, weight INTEGER NOT NULL, PRIMARY KEY(crate_name, prize_id), FOREIGN KEY(crate_name) REFERENCES vsu_crates(name) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vsu_player_keys (uuid TEXT NOT NULL, key_type TEXT NOT NULL, amount INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, PRIMARY KEY(uuid, key_type))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vsu_crate_claims (claim_id TEXT PRIMARY KEY, uuid TEXT NOT NULL, crate_name TEXT NOT NULL, key_type TEXT NOT NULL, prize_id INTEGER NOT NULL, label TEXT NOT NULL, command TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, completed_at INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vsu_playtime_key_progress (uuid TEXT PRIMARY KEY, awarded_intervals INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_vsu_crate_prizes_crate ON vsu_crate_prizes(crate_name)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_vsu_crate_claims_status ON vsu_crate_claims(status)");
            statement.executeUpdate("PRAGMA foreign_keys=ON");
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = VonixServerUtilities.getInstance().getDatabase().getConnection();
        ensureSchema(connection);
        return connection;
    }

    public boolean createCrate(String crateName, String keyType) throws SQLException {
        crateName = id(crateName);
        keyType = id(keyType);
        try (PreparedStatement statement = connection().prepareStatement("INSERT OR IGNORE INTO vsu_crates(name,key_type,enabled,updated_at) VALUES(?,?,1,?)")) {
            statement.setString(1, crateName);
            statement.setString(2, keyType);
            statement.setLong(3, now());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean setCrateKeyType(String crateName, String keyType) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("UPDATE vsu_crates SET key_type=?, updated_at=? WHERE name=?")) {
            statement.setString(1, id(keyType));
            statement.setLong(2, now());
            statement.setString(3, id(crateName));
            return statement.executeUpdate() == 1;
        }
    }

    public boolean setCrateEnabled(String crateName, boolean enabled) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("UPDATE vsu_crates SET enabled=?, updated_at=? WHERE name=?")) {
            statement.setInt(1, enabled ? 1 : 0);
            statement.setLong(2, now());
            statement.setString(3, id(crateName));
            return statement.executeUpdate() == 1;
        }
    }

    public boolean deleteCrate(String crateName) throws SQLException {
        Connection connection = connection();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int changed;
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM vsu_crates WHERE name=?")) {
                statement.setString(1, id(crateName));
                changed = statement.executeUpdate();
            }
            if (changed == 1) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM vsu_crate_prizes WHERE crate_name=?")) {
                    statement.setString(1, id(crateName));
                    statement.executeUpdate();
                }
            }
            connection.commit();
            return changed == 1;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public boolean addPrize(String crateName, String label, String command, int weight) throws SQLException {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Prize label cannot be blank");
        if (command == null || command.isBlank()) throw new IllegalArgumentException("Prize command cannot be blank");
        if (weight < 1 || weight > 1_000_000) throw new IllegalArgumentException("Prize weight must be between 1 and 1000000");
        Connection connection = connection();
        String crate = id(crateName);
        if (!crateExists(connection, crate)) return false;
        int nextId;
        try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(prize_id),0)+1 FROM vsu_crate_prizes WHERE crate_name=?")) {
            statement.setString(1, crate);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Could not allocate prize id");
                nextId = result.getInt(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO vsu_crate_prizes(crate_name,prize_id,label,command,weight) VALUES(?,?,?,?,?)")) {
            statement.setString(1, crate);
            statement.setInt(2, nextId);
            statement.setString(3, label.trim());
            statement.setString(4, normaliseCommand(command));
            statement.setInt(5, weight);
            statement.executeUpdate();
            return true;
        }
    }

    public boolean removePrize(String crateName, int prizeId) throws SQLException {
        if (prizeId < 1) throw new IllegalArgumentException("Prize id must be positive");
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM vsu_crate_prizes WHERE crate_name=? AND prize_id=?")) {
            statement.setString(1, id(crateName));
            statement.setInt(2, prizeId);
            return statement.executeUpdate() == 1;
        }
    }

    public List<CrateInfo> listCrates() throws SQLException {
        List<CrateInfo> crates = new ArrayList<>();
        try (Statement statement = connection().createStatement(); ResultSet result = statement.executeQuery("SELECT name,key_type,enabled FROM vsu_crates ORDER BY name")) {
            while (result.next()) crates.add(new CrateInfo(result.getString(1), result.getString(2), result.getInt(3) != 0));
        }
        return List.copyOf(crates);
    }

    public CrateInfo getCrate(String crateName) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT name,key_type,enabled FROM vsu_crates WHERE name=?")) {
            statement.setString(1, id(crateName));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new CrateInfo(result.getString(1), result.getString(2), result.getInt(3) != 0) : null;
            }
        }
    }

    public List<PrizeView> listPrizes(String crateName) throws SQLException {
        String crate = id(crateName);
        int total = totalWeight(crate);
        List<PrizeView> prizes = new ArrayList<>();
        try (PreparedStatement statement = connection().prepareStatement("SELECT prize_id,label,command,weight FROM vsu_crate_prizes WHERE crate_name=? ORDER BY prize_id")) {
            statement.setString(1, crate);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int weight = result.getInt(4);
                    prizes.add(new PrizeView(result.getInt(1), result.getString(2), result.getString(3), weight, total == 0 ? 0.0 : weight * 100.0 / total));
                }
            }
        }
        return List.copyOf(prizes);
    }

    public int adjustKeys(UUID uuid, String keyType, int delta) throws SQLException {
        if (uuid == null) throw new IllegalArgumentException("Player UUID is required");
        if (delta == 0 || Math.abs((long) delta) > 1_000_000) throw new IllegalArgumentException("Key amount must be between 1 and 1000000");
        Connection connection = connection();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String type = id(keyType);
            int current = readBalance(connection, uuid, type);
            long next = (long) current + delta;
            if (next < 0 || next > Integer.MAX_VALUE) {
                connection.rollback();
                return -1;
            }
            writeBalance(connection, uuid, type, (int) next);
            connection.commit();
            return (int) next;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public int getBalance(UUID uuid, String keyType) throws SQLException {
        return readBalance(connection(), uuid, id(keyType));
    }

    /** Atomically checks the typed key, debits it, selects a weighted prize, and creates a pending claim. */
    public OpenResult open(UUID uuid, String crateName) throws SQLException {
        if (uuid == null) throw new IllegalArgumentException("Player UUID is required");
        Connection connection = connection();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            CrateInfo crate = readCrate(connection, id(crateName));
            if (crate == null) return rollbackResult(connection, oldAutoCommit, OpenResult.failure("crate_not_found"));
            if (!crate.enabled()) return rollbackResult(connection, oldAutoCommit, OpenResult.failure("crate_disabled"));
            int balance = readBalance(connection, uuid, crate.keyType());
            if (balance < 1) return rollbackResult(connection, oldAutoCommit, OpenResult.failure("missing_key:" + crate.keyType()));
            List<CrateDefinition.PrizeDefinition> prizes = readPrizes(connection, crate.name());
            if (prizes.isEmpty()) return rollbackResult(connection, oldAutoCommit, OpenResult.failure("crate_empty"));
            CrateDefinition.PrizeDefinition prize = WeightedPrizeSelector.choose(prizes);
            String claimId = UUID.randomUUID().toString();
            writeBalance(connection, uuid, crate.keyType(), balance - 1);
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO vsu_crate_claims(claim_id,uuid,crate_name,key_type,prize_id,label,command,status,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, claimId);
                statement.setString(2, uuid.toString());
                statement.setString(3, crate.name());
                statement.setString(4, crate.keyType());
                statement.setInt(5, prize.id());
                statement.setString(6, prize.label());
                statement.setString(7, prize.command());
                statement.setString(8, "PENDING");
                statement.setLong(9, now());
                statement.executeUpdate();
            }
            connection.commit();
            return new OpenResult(true, claimId, crate.keyType(), prize.label(), prize.command(), null);
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public void completeClaim(String claimId) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("UPDATE vsu_crate_claims SET status='COMPLETED', completed_at=? WHERE claim_id=? AND status='PENDING'")) {
            statement.setLong(1, now());
            statement.setString(2, claimId);
            statement.executeUpdate();
        }
    }

    /** Refunds a failed pending command exactly once. */
    public boolean refundClaim(String claimId) throws SQLException {
        Connection connection = connection();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String uuid;
            String keyType;
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid,key_type FROM vsu_crate_claims WHERE claim_id=? AND status='PENDING'")) {
                statement.setString(1, claimId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return rollbackResult(connection, oldAutoCommit, false);
                    uuid = result.getString(1);
                    keyType = result.getString(2);
                }
            }
            UUID player = UUID.fromString(uuid);
            writeBalance(connection, player, keyType, Math.addExact(readBalance(connection, player, keyType), 1));
            try (PreparedStatement statement = connection.prepareStatement("UPDATE vsu_crate_claims SET status='REFUNDED', completed_at=? WHERE claim_id=? AND status='PENDING'")) {
                statement.setLong(1, now());
                statement.setString(2, claimId);
                statement.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    /** Refunded on startup so a crash between debit and command execution is not a lost key. */
    public int recoverPendingClaims() throws SQLException {
        List<String> pending = new ArrayList<>();
        try (Statement statement = connection().createStatement(); ResultSet result = statement.executeQuery("SELECT claim_id FROM vsu_crate_claims WHERE status='PENDING'")) {
            while (result.next()) pending.add(result.getString(1));
        }
        int recovered = 0;
        for (String claim : pending) if (refundClaim(claim)) recovered++;
        return recovered;
    }

    /** Grants one key for each newly completed playtime interval, atomically. */
    public int grantPlaytimeIntervals(UUID uuid, long completedIntervals) throws SQLException {
        if (uuid == null || completedIntervals < 0) return 0;
        Connection connection = connection();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long already = 0;
            try (PreparedStatement statement = connection.prepareStatement("SELECT awarded_intervals FROM vsu_playtime_key_progress WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) { if (result.next()) already = result.getLong(1); }
            }
            long delta = completedIntervals - already;
            if (delta <= 0) return rollbackResult(connection, oldAutoCommit, 0);
            if (delta > Integer.MAX_VALUE) delta = Integer.MAX_VALUE;
            int balance = readBalance(connection, uuid, "playtime");
            if ((long) balance + delta > Integer.MAX_VALUE) throw new SQLException("Playtime key balance overflow");
            writeBalance(connection, uuid, "playtime", (int) (balance + delta));
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO vsu_playtime_key_progress(uuid,awarded_intervals,updated_at) VALUES(?,?,?) ON CONFLICT(uuid) DO UPDATE SET awarded_intervals=excluded.awarded_intervals,updated_at=excluded.updated_at")) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, already + delta);
                statement.setLong(3, now());
                statement.executeUpdate();
            }
            connection.commit();
            return (int) delta;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    private static <T> T rollbackResult(Connection connection, boolean oldAutoCommit, T value) throws SQLException {
        connection.rollback();
        connection.setAutoCommit(oldAutoCommit);
        return value;
    }

    private static int readBalance(Connection connection, UUID uuid, String keyType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT amount FROM vsu_player_keys WHERE uuid=? AND key_type=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, keyType);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    private static void writeBalance(Connection connection, UUID uuid, String keyType, int amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO vsu_player_keys(uuid,key_type,amount,updated_at) VALUES(?,?,?,?) ON CONFLICT(uuid,key_type) DO UPDATE SET amount=excluded.amount,updated_at=excluded.updated_at")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, keyType);
            statement.setInt(3, amount);
            statement.setLong(4, now());
            statement.executeUpdate();
        }
    }

    private static CrateInfo readCrate(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT name,key_type,enabled FROM vsu_crates WHERE name=?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? new CrateInfo(result.getString(1), result.getString(2), result.getInt(3) != 0) : null; }
        }
    }

    private static List<CrateDefinition.PrizeDefinition> readPrizes(Connection connection, String crateName) throws SQLException {
        List<CrateDefinition.PrizeDefinition> prizes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT prize_id,label,command,weight FROM vsu_crate_prizes WHERE crate_name=? AND weight>0 ORDER BY prize_id")) {
            statement.setString(1, crateName);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) prizes.add(new CrateDefinition.PrizeDefinition(result.getInt(1), result.getString(2), result.getString(3), result.getInt(4)));
            }
        }
        return prizes;
    }

    private int totalWeight(String crateName) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT COALESCE(SUM(weight),0) FROM vsu_crate_prizes WHERE crate_name=? AND weight>0")) {
            statement.setString(1, crateName);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    private static boolean crateExists(Connection connection, String name) throws SQLException {
        return readCrate(connection, name) != null;
    }

    private static String id(String value) {
        return new KeyType(value).value();
    }

    private static String normaliseCommand(String command) {
        String value = command.trim();
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static long now() { return System.currentTimeMillis(); }

    public record CrateInfo(String name, String keyType, boolean enabled) {}
    public record PrizeView(int id, String label, String command, int weight, double percentage) {}
    public record OpenResult(boolean success, String claimId, String keyType, String label, String command, String error) {
        public static OpenResult failure(String error) { return new OpenResult(false, null, null, null, null, error); }
    }
}
