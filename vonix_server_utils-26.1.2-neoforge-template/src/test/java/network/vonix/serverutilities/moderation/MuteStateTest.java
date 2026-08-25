package network.vonix.serverutilities.moderation;

import java.util.UUID;

/** Regression checks for optimistic mute enforcement and expiry-safe state removal. */
public final class MuteStateTest {
    private MuteStateTest() {}

    public static void main(String[] args) {
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000001");

        MuteState.clear();
        require(MuteState.isMuted(target), "mute enforcement must fail closed before startup hydration completes");
        MuteState.markHydrationComplete();
        require(!MuteState.isMuted(target), "successful hydration must restore normal per-player mute checks");
        MuteState.addPending(target);
        MuteState.addPending(target);
        require(MuteState.snapshot().contains(target), "pending mute must enforce immediately");

        MuteState.clearPending(target);
        require(MuteState.snapshot().contains(target), "one failed operation must not clear another pending mute");
        MuteState.clearPending(target);
        require(!MuteState.snapshot().contains(target), "all failed operations must roll back the optimistic mute");

        MuteState.addPending(target);
        MuteState.markPersisted(target);
        MuteState.clearPending(target);
        require(MuteState.snapshot().contains(target), "failed sibling must not clear a persisted mute");
        MuteState.reconcilePersisted(target, false);
        require(!MuteState.snapshot().contains(target), "expired persisted mute must clear enforcement when no active row remains");

        MuteState.clear();
        System.out.println("MuteStateTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
