package network.vonix.serverutilities.crates;


/** Optional integration boundary for EasyNPC or another NPC provider. */
public final class EasyNpcBridge {
    private EasyNpcBridge() {}
    public static boolean isInstalled() { return false; }
    public static void registerOptionalInteraction() {}
}
