package network.vonix.serverutilities.inventory;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.platform.PlatformEvents;

public class AccessoryHelper {
    public static void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        PlatformEvents.Holder.get().openAccessoryMenu(target, viewer);
    }
}
