package network.vonix.serverutilities.crates;

/** Optional integration boundary for EasyNPC or another NPC provider. */
public final class EasyNpcBridge {
    private EasyNpcBridge() {}
    public static boolean isInstalled() {
        try {
            Class.forName("com.github.alexthe666.easy_npc.EasyNPC");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
    public static void registerOptionalInteraction() {
        // EasyNPC is an optional integration. Its absence is a valid runtime state.
    }
}
