package network.vonix.serverutilities.neoforge;

import net.neoforged.fml.common.Mod;
import network.vonix.serverutilities.VonixServerUtilities;

@Mod(VonixServerUtilities.MOD_ID)
public final class VonixServerUtilitiesNeoForge {
    public VonixServerUtilitiesNeoForge() {
        VonixServerUtilities.init();
    }
}
