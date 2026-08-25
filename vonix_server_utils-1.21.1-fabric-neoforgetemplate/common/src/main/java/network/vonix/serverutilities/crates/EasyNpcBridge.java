package network.vonix.serverutilities.crates;

import network.vonix.serverutilities.platform.PlatformEvents;

/** Optional integration boundary for EasyNPC or another NPC provider. */
public final class EasyNpcBridge {
    private EasyNpcBridge() {}
    public static boolean isInstalled() { return PlatformEvents.Holder.get().easyNpcInstalled(); }
    public static void registerOptionalInteraction() { PlatformEvents.Holder.get().registerEasyNpcInteraction(); }
}
