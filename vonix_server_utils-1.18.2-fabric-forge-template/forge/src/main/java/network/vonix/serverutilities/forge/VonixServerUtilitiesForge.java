package network.vonix.serverutilities.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import network.vonix.serverutilities.VonixServerUtilities;

@Mod(VonixServerUtilities.MOD_ID)
public final class VonixServerUtilitiesForge {
    public VonixServerUtilitiesForge() {
        EventBuses.registerModEventBus(VonixServerUtilities.MOD_ID,
                FMLJavaModLoadingContext.get().getModEventBus());
        VonixServerUtilities.init();

        // Chat formatter: rewrite ServerChatEvent component with LP prefix.
        MinecraftForge.EVENT_BUS.register(new ForgeChatFormatHandler());
    }
}
