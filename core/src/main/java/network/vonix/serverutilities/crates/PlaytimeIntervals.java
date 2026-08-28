package network.vonix.serverutilities.crates;

/**
 * Pure playtime-interval conversion shared by every VSU cell.
 *
 * <p>Grant paths must call {@link #completed(long, int)} instead of inlining
 * {@code ticks / (minutes * 60 * 20)}. Minecraft {@code Stats} lookups stay
 * in version/loader code.
 */
public final class PlaytimeIntervals {
    private PlaytimeIntervals() {}

    /**
     * Number of completed intervals for a playtime tick total.
     *
     * <p>Negative playtime and non-positive interval minutes yield {@code 0}
     * so callers can reject those inputs without dividing by zero.
     */
    public static long completed(long playtimeTicks, int intervalMinutes) {
        if (playtimeTicks < 0 || intervalMinutes < 1) return 0;
        return playtimeTicks / (intervalMinutes * 60L * 20L);
    }
}
