package network.vonix.serverutilities.crates;


/** Optional integration boundary for EasyNPC or another NPC provider. */
public final class EasyNpcBridge {
    private EasyNpcBridge() {}

    public static boolean isInstalled() { return false; }

    /** No-op unless a platform adapter with EasyNPC support is supplied. */
    public static void registerOptionalInteraction() {}
}
