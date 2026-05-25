package network.vonix.serverutilities.warps;

import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages server warps backed by the SQLite database.
 * All methods access the DB and must be called from the VonixSU-DB executor thread.
 */
public final class WarpManager {
    private static final WarpManager INSTANCE = new WarpManager();
    public static WarpManager getInstance() { return INSTANCE; }

    private Connection conn() {
        return VonixServerUtilities.getInstance().getDatabase().getConnection();
    }

    /**
     * Create or replace a warp using pre-captured player data.
     * Accepts raw values so this method can be safely called from the DB thread.
     */
    public boolean setWarp(String name, UUID createdBy, String world,
                           double x, double y, double z, float yaw, float pitch) {
        try (PreparedStatement ps = conn().prepareStatement(
                "INSERT OR REPLACE INTO vsu_warps(name,world,x,y,z,yaw,pitch,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, name.toLowerCase());
            ps.setString(2, world);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setFloat (6, yaw);
            ps.setFloat (7, pitch);
            ps.setString(8, createdBy != null ? createdBy.toString() : null);
            ps.setLong  (9, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] setWarp failed", e);
            return false;
        }
    }

    /** Convenience overload that captures player position on the caller's thread. */
    public boolean setWarp(String name, ServerPlayer player) {
        return setWarp(name, player.getUUID(),
                player.level.dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    /** Delete a warp. Returns true if a row was removed. */
    public boolean deleteWarp(String name) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM vsu_warps WHERE name=?")) {
            ps.setString(1, name.toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] deleteWarp failed", e);
            return false;
        }
    }

    /** Get a warp by name, or null if not found. */
    public Warp getWarp(String name) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT world,x,y,z,yaw,pitch FROM vsu_warps WHERE name=?")) {
            ps.setString(1, name.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new Warp(name,
                    rs.getString(1), rs.getDouble(2), rs.getDouble(3),
                    rs.getDouble(4), rs.getFloat(5), rs.getFloat(6));
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getWarp failed", e);
        }
        return null;
    }

    /** Get all warps sorted by name. */
    public List<Warp> getWarps() {
        List<Warp> list = new ArrayList<>();
        try (Statement s = conn().createStatement()) {
            ResultSet rs = s.executeQuery(
                    "SELECT name,world,x,y,z,yaw,pitch FROM vsu_warps ORDER BY name");
            while (rs.next()) list.add(new Warp(
                    rs.getString(1), rs.getString(2),
                    rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                    rs.getFloat(6), rs.getFloat(7)));
        } catch (SQLException e) {
            VonixServerUtilities.LOGGER.error("[VonixSU] getWarps failed", e);
        }
        return list;
    }

    public record Warp(String name, String world,
                       double x, double y, double z,
                       float yaw, float pitch) {}
}

