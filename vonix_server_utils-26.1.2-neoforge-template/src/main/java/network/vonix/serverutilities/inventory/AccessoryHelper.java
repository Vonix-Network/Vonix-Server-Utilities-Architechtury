package network.vonix.serverutilities.inventory;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.inventory.neoforge.AccessoryHelperImpl;

public class AccessoryHelper {
    public static boolean openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        return AccessoryHelperImpl.openAccessoryMenu(target, viewer);
    }
}
