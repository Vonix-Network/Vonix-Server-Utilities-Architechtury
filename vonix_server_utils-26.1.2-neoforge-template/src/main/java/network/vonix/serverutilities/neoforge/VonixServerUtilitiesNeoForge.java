package network.vonix.serverutilities.neoforge;

import net.neoforged.fml.common.Mod;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.neoforge.moderation.NeoForgeModerationListener;

@Mod(VonixServerUtilities.MOD_ID)
public final class VonixServerUtilitiesNeoForge {
    public VonixServerUtilitiesNeoForge() {
        VonixServerUtilities.init();
        NeoForgeModerationListener.register(null);
    }
}
