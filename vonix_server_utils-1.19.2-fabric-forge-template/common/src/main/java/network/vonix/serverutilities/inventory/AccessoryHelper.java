package network.vonix.serverutilities.inventory;

import net.minecraft.server.level.ServerPlayer;

public class AccessoryHelper {
    public static void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        network.vonix.serverutilities.platform.PlatformEvents.Holder.get().openAccessoryMenu(target, viewer);
    }
}
