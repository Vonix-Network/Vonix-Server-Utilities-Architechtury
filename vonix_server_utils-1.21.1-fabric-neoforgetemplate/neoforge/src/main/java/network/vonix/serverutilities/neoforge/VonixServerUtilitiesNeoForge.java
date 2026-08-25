package network.vonix.serverutilities.neoforge;

import net.neoforged.fml.common.Mod;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.neoforge.moderation.NeoForgeModerationListener;
import network.vonix.serverutilities.platform.PlatformEvents;

@Mod(VonixServerUtilities.MOD_ID)
public final class VonixServerUtilitiesNeoForge {
    public VonixServerUtilitiesNeoForge() {
        PlatformEvents.Holder.install(new VsuPlatformEvents());
        VonixServerUtilities.init();
        NeoForgeModerationListener.register(null);
    }
}
