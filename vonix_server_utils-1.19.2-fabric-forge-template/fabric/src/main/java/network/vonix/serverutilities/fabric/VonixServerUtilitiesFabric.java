package network.vonix.serverutilities.fabric;

import net.fabricmc.api.ModInitializer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.fabric.moderation.FabricModerationListener;

public final class VonixServerUtilitiesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        network.vonix.serverutilities.platform.PlatformEvents.Holder.install(new VsuPlatformEvents());
        VonixServerUtilities.init();
        FabricModerationListener.register();
    }
}
