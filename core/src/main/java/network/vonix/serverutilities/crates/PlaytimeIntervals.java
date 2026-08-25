package network.vonix.serverutilities.crates;

/** Pure playtime interval conversion shared by the loader-specific pilot. */
public final class PlaytimeIntervals {
    private PlaytimeIntervals() {}

    public static long completed(long playtimeTicks, int intervalMinutes) {
        if (playtimeTicks < 0 || intervalMinutes < 1) return 0;
        return playtimeTicks / (intervalMinutes * 60L * 20L);
    }
}
