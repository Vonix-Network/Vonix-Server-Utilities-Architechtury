package network.vonix.serverutilities.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.forge.moderation.ForgeModerationListener;

@Mod(VonixServerUtilities.MOD_ID)
public final class VonixServerUtilitiesForge {
    public VonixServerUtilitiesForge() {
        network.vonix.serverutilities.platform.PlatformEvents.Holder.install(new VsuPlatformEvents());
        EventBuses.registerModEventBus(VonixServerUtilities.MOD_ID,
                FMLJavaModLoadingContext.get().getModEventBus());
        VonixServerUtilities.init();
        ForgeModerationListener.register(null);
    }
}
