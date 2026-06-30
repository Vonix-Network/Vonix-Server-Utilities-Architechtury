package network.vonix.serverutilities.fabric.mixin;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import network.vonix.serverutilities.fabric.moderation.FabricModerationListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Mute enforcement on Fabric 1.18.2.
 *
 * <p>Fabric API 0.77.0+1.18.2 does NOT ship {@code fabric-message-api-v1}, so
 * {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE} / {@code ALLOW_COMMAND_MESSAGE}
 * cannot be used. This Mixin is the fallback hook: intercept the chat packet
 * at the listener entry point and cancel before vanilla broadcasts.
 *
 * <p>Logic:
 * <ul>
 *   <li>Raw chat (no leading {@code "/"}): cancel if {@link
 *       FabricModerationListener#checkAndNotifyMuted(ServerPlayer)} returns true.</li>
 *   <li>Slash commands: only intercept the chat-style ones (/me /msg /tell /w /r
 *       /reply /broadcast /bc /gc). All other commands pass through so admins
 *       can still issue /unmute on themselves via console etc.</li>
 * </ul>
 *
 * <p>Bypass node {@code vsu.bypass.mute} is honoured inside {@code MuteState.isMuted}.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    private static final Set<String> CHAT_COMMANDS =
            Set.of("me", "msg", "tell", "w", "r", "reply", "broadcast", "bc", "gc");

    @Shadow public ServerPlayer player;

    @Inject(method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V",
            at = @At("HEAD"), cancellable = true)
    private void vsu$muteCheck(ServerboundChatPacket packet, CallbackInfo ci) {
        ServerPlayer p = this.player;
        if (p == null) return;
        String msg = packet.getMessage();
        if (msg == null) return;
        if (msg.startsWith("/")) {
            String body = msg.substring(1);
            int sp = body.indexOf(' ');
            String root = (sp < 0 ? body : body.substring(0, sp)).toLowerCase();
            if (!CHAT_COMMANDS.contains(root)) return;
        }
        if (FabricModerationListener.checkAndNotifyMuted(p)) {
            ci.cancel();
        }
    }
}
