package network.vonix.serverutilities.neoforge;

import net.neoforged.fml.common.Mod;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.inventory.ItemHandlerAccess;
import network.vonix.serverutilities.inventory.neoforge.NeoForgeItemHandlerAccess;
import network.vonix.serverutilities.neoforge.moderation.NeoForgeModerationListener;

@Mod(VonixServerUtilities.MOD_ID)
public final class VonixServerUtilitiesNeoForge {
    public VonixServerUtilitiesNeoForge() {
        ItemHandlerAccess.Holder.install(new NeoForgeItemHandlerAccess());
        VonixServerUtilities.init();
        NeoForgeModerationListener.register(null);
    }
}
