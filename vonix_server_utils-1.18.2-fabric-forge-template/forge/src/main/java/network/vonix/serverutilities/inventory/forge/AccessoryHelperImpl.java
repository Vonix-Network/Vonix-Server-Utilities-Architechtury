package network.vonix.serverutilities.inventory.forge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.TextComponent;

public class AccessoryHelperImpl {
    public static void openAccessoryMenu(ServerPlayer target, ServerPlayer viewer) {
        viewer.sendMessage(new TextComponent("Curios integration not fully mapped yet."), net.minecraft.Util.NIL_UUID);
    }
}
