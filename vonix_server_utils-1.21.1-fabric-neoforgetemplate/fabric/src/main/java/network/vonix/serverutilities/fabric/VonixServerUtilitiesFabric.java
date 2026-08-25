package network.vonix.serverutilities.fabric;

import net.fabricmc.api.ModInitializer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.fabric.moderation.FabricModerationListener;
import network.vonix.serverutilities.platform.PlatformEvents;

public final class VonixServerUtilitiesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlatformEvents.Holder.install(new VsuPlatformEvents());
        VonixServerUtilities.init();
        FabricModerationListener.register();
    }
}
