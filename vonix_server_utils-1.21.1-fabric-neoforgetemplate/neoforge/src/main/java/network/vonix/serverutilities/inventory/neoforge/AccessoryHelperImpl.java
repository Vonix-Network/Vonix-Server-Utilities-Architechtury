package network.vonix.serverutilities.inventory.neoforge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public class AccessoryHelperImpl {
    public static void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        viewer.sendSystemMessage(Component.literal("Curios integration not fully mapped yet."));
    }
}
