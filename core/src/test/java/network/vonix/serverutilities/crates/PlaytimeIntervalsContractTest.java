package network.vonix.serverutilities.crates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaytimeIntervalsContractTest {

    @Test
    void javaExecProbeStillHolds() {
        PlaytimeIntervalsTest.assertContract();
    }

    @ParameterizedTest
    @CsvSource({
            "0,60,0",
            "71999,60,0",
            "72000,60,1",
            "144000,60,2",
            "215999,60,2",
            "216000,60,3",
            "-1,60,0",
            "72000,0,0",
            "72000,-1,0",
            "1200,1,1",
            "1199,1,0"
    })
    void completedMatchesGrantSnapshotMath(long ticks, int minutes, long expected) {
        assertEquals(expected, PlaytimeIntervals.completed(ticks, minutes));
        assertEquals(expected, snapshotMath(ticks, minutes));
    }

    /** Inlined CratePlaytimeTask snapshot math, kept here as the behavioral oracle. */
    static long snapshotMath(long playtimeTicks, int intervalMinutes) {
        if (intervalMinutes < 1) return 0;
        if (playtimeTicks < 0) return 0;
        long intervalTicks = intervalMinutes * 60L * 20L;
        return playtimeTicks / intervalTicks;
    }
}
