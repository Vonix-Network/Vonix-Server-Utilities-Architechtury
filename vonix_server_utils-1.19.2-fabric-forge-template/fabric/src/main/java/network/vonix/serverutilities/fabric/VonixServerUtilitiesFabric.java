package network.vonix.serverutilities.fabric;

import net.fabricmc.api.ModInitializer;
import network.vonix.serverutilities.VonixServerUtilities;

public final class VonixServerUtilitiesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        VonixServerUtilities.init();
    }
}
