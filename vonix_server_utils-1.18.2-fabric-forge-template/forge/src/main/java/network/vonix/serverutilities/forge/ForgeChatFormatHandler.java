package network.vonix.serverutilities.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.chat.ChatFormatter;

import java.util.Optional;

/**
 * Forge-side {@link ServerChatEvent} subscriber: rewrites the outgoing
 * chat component with LuckPerms-driven prefix/suffix/name-color.
 *
 * <p>Runs at {@link EventPriority#LOW} so other mods that want first crack
 * (filter/moderation) still see the raw message; we are the final render
 * stage before Vanilla broadcasts.</p>
 *
 * <p>If {@link ChatFormatter} returns empty (LP absent / user not loaded /
 * exception) the event is left untouched so vanilla chat still renders.</p>
 */
public final class ForgeChatFormatHandler {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            if (player == null) return;
            String raw = event.getMessage();
            Optional<Component> formatted = ChatFormatter.format(player, raw);
            formatted.ifPresent(event::setComponent);
        } catch (Throwable t) {
            VonixServerUtilities.LOGGER.error("[VonixSU] ForgeChatFormatHandler error", t);
        }
    }
}
