package network.vonix.serverutilities.crates;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Awards one Playtime Key per configured completed playtime interval. */
public final class CratePlaytimeTask {
    private static final int CHECK_INTERVAL_TICKS = 20 * 60;
    private static int ticksSinceCheck;
    private static boolean registered;
    private CratePlaytimeTask() {}
    public static void register() { registered = true; }
    public static void onServerTick(MinecraftServer server) {
        if (++ticksSinceCheck < CHECK_INTERVAL_TICKS) return;
        ticksSinceCheck = 0;
        int minutes = ModConfig.INSTANCE.getPlaytimeKeyIntervalMinutes();
        if (minutes < 1) return;
        List<Progress> snapshot = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long ticks = player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
            snapshot.add(new Progress(player.getUUID(), PlaytimeIntervals.completed(ticks, minutes)));
        }
        if (snapshot.isEmpty()) return;
        VonixServerUtilities.dbAsync(() -> { CrateRepository repository = CrateRepository.getInstance(); for (Progress progress : snapshot) try { repository.grantPlaytimeIntervals(progress.uuid(), progress.completedIntervals()); } catch (Exception e) { VonixServerUtilities.LOGGER.error("[VSU] Could not grant playtime keys to {}", progress.uuid(), e); } });
    }
    public static void clear() { ticksSinceCheck = 0; }
    private record Progress(UUID uuid, long completedIntervals) {}
}
