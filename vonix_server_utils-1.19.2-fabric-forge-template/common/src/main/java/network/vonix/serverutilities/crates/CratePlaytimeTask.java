package network.vonix.serverutilities.crates;

import dev.architectury.event.events.common.TickEvent;
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

    public static void register() {
        if (registered) return;
        registered = true;
        TickEvent.SERVER_POST.register(CratePlaytimeTask::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (++ticksSinceCheck < CHECK_INTERVAL_TICKS) return;
        ticksSinceCheck = 0;
        int minutes = ModConfig.INSTANCE.getPlaytimeKeyIntervalMinutes();
        if (minutes < 1) return;
        long intervalTicks = minutes * 60L * 20L;
        List<Progress> snapshot = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long completed = player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) / intervalTicks;
            snapshot.add(new Progress(player.getUUID(), completed));
        }
        if (snapshot.isEmpty()) return;
        VonixServerUtilities.dbAsync(() -> {
            CrateRepository repository = CrateRepository.getInstance();
            for (Progress progress : snapshot) {
                try { repository.grantPlaytimeIntervals(progress.uuid(), progress.completedIntervals()); }
                catch (Exception exception) { VonixServerUtilities.LOGGER.error("[VSU] Could not grant playtime keys to {}", progress.uuid(), exception); }
            }
        });
    }

    public static void clear() { ticksSinceCheck = 0; }
    private record Progress(UUID uuid, long completedIntervals) {}
}
