package network.vonix.serverutilities.crates;

public final class PlaytimeIntervalsTest {
    public static void main(String[] args) {
        if (PlaytimeIntervals.completed(72000, 60) != 1) throw new AssertionError("one interval expected");
        if (PlaytimeIntervals.completed(144000, 60) != 2) throw new AssertionError("two intervals expected");
        if (PlaytimeIntervals.completed(-1, 60) != 0) throw new AssertionError("negative playtime rejected");
        System.out.println("PlaytimeIntervalsTest: PASS");
    }
}
