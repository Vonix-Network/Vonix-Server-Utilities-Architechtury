package network.vonix.serverutilities.crates;

import dev.architectury.injectables.annotations.ExpectPlatform;

/** Optional integration boundary for EasyNPC or another NPC provider. */
public final class EasyNpcBridge {
    private EasyNpcBridge() {}

    @ExpectPlatform
    public static boolean isInstalled() {
        throw new AssertionError("Architectury platform implementation missing");
    }

    /** No-op unless a platform adapter with EasyNPC support is supplied. */
    @ExpectPlatform
    public static void registerOptionalInteraction() {
        throw new AssertionError("Architectury platform implementation missing");
    }
}
