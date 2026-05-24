package network.vonix.serverutilities.homes;

import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.config.ModConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages player homes backed by the SQLite database.
 * All methods access the DB and must be called from the VonixSU-DB executor thread.
 */
public final class HomeManager {
    private static final HomeManager INSTANCE = new HomeManager();
    public static HomeManager getInstance() { return INSTANCE; }

    private Connection conn() {
        return VonixServerUtilities.getInstance().getDatabase().getConnection();
    }

    /**
     * Set or overwrite a named home using pre-captured player data.
     * Returns false if the player has already reached their home limit and
     * {@code name} does not already exist.
     *
     * Accepts raw values so this method can be safely called from the DB thread
     * without touching live player state.
     */
    public boolean setHome(UUID uuid, String name, String world,
                           double x, double y, double z, float yaw, float pitch) {
        String nameLow = name.toLowerCase();
        try {
            Connection c = conn();
            if (!exists(c, uuid.toString(), nameLow) && count(c, uuid.toString()) >= ModConfig.INSTANCE.getMaxHomes()) {
                return false;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR REPLACE INTO vsu_homes VALUES(?,?,?,?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, nameLow);
                ps.setString(3, world);
                ps.setDouble(4, x);
                ps.setDouble(5, y);
                ps.setDouble(6, z);
                ps.setFloat (7, yaw);
                ps.setFloat (8, pitch);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] setHome failed", e);
            return false;
        }
    }

    /** Convenience overload that captures player position on the caller's thread. */
    public boolean setHome(ServerPlayer player, String name) {
        return setHome(
                player.getUUID(), name,
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    /** Delete a named home. Returns true if a row was removed. */
    public boolean deleteHome(UUID uuid, String name) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM vsu_homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] deleteHome failed", e);
            return false;
        }
    }

    /** Get a single home by name, or null if not found. */
    public Home getHome(UUID uuid, String name) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT world,x,y,z,yaw,pitch FROM vsu_homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new Home(name,
                    rs.getString(1), rs.getDouble(2), rs.getDouble(3),
                    rs.getDouble(4), rs.getFloat(5), rs.getFloat(6));
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getHome failed", e);
        }
        return null;
    }

    /** Get all homes for a player, sorted by name. */
    public List<Home> getHomes(UUID uuid) {
        List<Home> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT name,world,x,y,z,yaw,pitch FROM vsu_homes WHERE uuid=? ORDER BY name")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Home(
                    rs.getString(1), rs.getString(2),
                    rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                    rs.getFloat(6), rs.getFloat(7)));
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getHomes failed", e);
        }
        return list;
    }

    private boolean exists(Connection c, String uuid, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM vsu_homes WHERE uuid=? AND name=? LIMIT 1")) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            return ps.executeQuery().next();
        }
    }

    private int count(Connection c, String uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM vsu_homes WHERE uuid=?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public record Home(String name, String world,
                       double x, double y, double z,
                       float yaw, float pitch) {}
}

