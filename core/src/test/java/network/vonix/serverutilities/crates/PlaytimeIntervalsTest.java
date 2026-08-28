package network.vonix.serverutilities.crates;

public final class PlaytimeIntervalsTest {
    public static void main(String[] args) {
        assertContract();
        System.out.println("PlaytimeIntervalsTest: PASS");
    }

    static void assertContract() {
        if (PlaytimeIntervals.completed(0, 60) != 0) throw new AssertionError("zero ticks is zero intervals");
        if (PlaytimeIntervals.completed(71999, 60) != 0) throw new AssertionError("one tick short of one hour");
        if (PlaytimeIntervals.completed(72000, 60) != 1) throw new AssertionError("one interval expected");
        if (PlaytimeIntervals.completed(144000, 60) != 2) throw new AssertionError("two intervals expected");
        if (PlaytimeIntervals.completed(-1, 60) != 0) throw new AssertionError("negative playtime rejected");
        if (PlaytimeIntervals.completed(72000, 0) != 0) throw new AssertionError("zero-minute interval rejected");
        if (PlaytimeIntervals.completed(72000, -5) != 0) throw new AssertionError("negative-minute interval rejected");
    }
}
