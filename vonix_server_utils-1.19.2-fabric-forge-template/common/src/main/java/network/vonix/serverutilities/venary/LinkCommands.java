package network.vonix.serverutilities.venary;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registers /link and /unlink, plus helpers for the /vonixsu status extension.
 *
 * <p>All chat messages use 1.21 mutable-component builders; see PORT-NOTE
 * comments at each 1.21-specific call site for back-port guidance.
 *
 * <p>Rate limiting: per-player cooldown enforced client-side. The server is
 * not the source of truth for rate limits — Venary still enforces its own —
 * but the local cooldown spares the network round-trip and gives instant
 * feedback to a spamming player.
 */
public final class LinkCommands {

    private static final String LINK_URL = "https://vonix.network/account/link-minecraft";

    /** Per-player last /link request timestamp (epoch ms). */
    private static final ConcurrentMap<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    private LinkCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("link").executes(LinkCommands::linkExecute));
        d.register(Commands.literal("unlink").executes(LinkCommands::unlinkExecute));
    }

    // ── /link ────────────────────────────────────────────────────────────────

    private static int linkExecute(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        VenaryClient client = VenaryClient.get();
        if (client == null || !client.getConfig().isEnabled()) {
            player.sendSystemMessage(Component.literal("§c[VSU] Site link feature disabled by server admin."));
            return 0;
        }

        VenaryConfig cfg = client.getConfig();
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        long cooldownMs = cfg.getLinkCooldownSeconds() * 1000L;
        Long prev = lastRequest.get(uuid);
        if (prev != null && (now - prev) < cooldownMs) {
            long remain = (cooldownMs - (now - prev) + 999) / 1000L;
            player.sendSystemMessage(Component.literal(
                    "§c[VSU] Please wait §e" + remain + "s §cbefore requesting another link code."));
            return 0;
        }
        lastRequest.put(uuid, now);

        String username = player.getName().getString();
        player.sendSystemMessage(Component.literal("§7[VSU] Requesting link code…"));

        client.generateLinkCode(uuid, username).thenAccept(json -> {
            // Bounce back to the main thread for any Minecraft-API touch.
            player.server.execute(() -> handleLinkResponse(player, json));
        }).exceptionally(t -> {
            // Defensive — VenaryClient already fail-opens, but in case anything escapes.
            player.server.execute(() -> player.sendSystemMessage(
                    Component.literal("§c[VSU] Site unreachable, try again in a minute.")));
            return null;
        });
        return 1;
    }

    private static void handleLinkResponse(ServerPlayer player, JsonObject json) {
        if (json == null
                || !json.has("success")
                || !json.get("success").getAsBoolean()
                || !json.has("code")) {
            player.sendSystemMessage(Component.literal("§c[VSU] Site unreachable, try again in a minute."));
            return;
        }

        String code = json.get("code").getAsString();
        int expiresIn = json.has("expiresIn") ? json.get("expiresIn").getAsInt() : 300;
        int minutes = Math.max(1, expiresIn / 60);

        // PORT-NOTE: 1.21 uses Component.literal + Style.EMPTY.withClickEvent/withHoverEvent.
        // 1.20.1 and 1.19.2 work identically. 1.18.2 still has the same API surface but
        // uses TextComponent/MutableComponent.create — adjust the literal() call only.
        MutableComponent codeChip = Component.literal(code).setStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withBold(true)
                // PORT-NOTE: ClickEvent.Action.COPY_TO_CLIPBOARD added in 1.15; identical on all targets.
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to copy"))));

        // PORT-NOTE: ClickEvent.Action.OPEN_URL is stable across all listed MC versions.
        MutableComponent urlChip = Component.literal(LINK_URL).setStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, LINK_URL))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Open in browser"))));

        player.sendSystemMessage(Component.literal("§6§l[VSU] §r§eYour Vonix link code:"));
        player.sendSystemMessage(Component.literal("    ").append(codeChip)
                .append(Component.literal("  §7(click to copy)")));
        player.sendSystemMessage(Component.literal("§eGo to: ").append(urlChip));
        player.sendSystemMessage(Component.literal(
                "§7Paste the code on the site to finish linking. Expires in §e" + minutes + " min§7."));
    }

    // ── /unlink ──────────────────────────────────────────────────────────────

    private static int unlinkExecute(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        // PORT-NOTE: identical chat-component API across 1.18.2 – 1.21.1.
        MutableComponent urlChip = Component.literal("vonix.network/account/link-minecraft")
                .setStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, LINK_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Open in browser"))));

        player.sendSystemMessage(Component.literal(
                "§e[VSU] To unlink your Minecraft account, sign in at "
        ).append(urlChip).append(Component.literal(" §eand click Unlink.")));
        return 1;
    }

    // ── Status helper, called from ModCommands#showStatus ───────────────────

    /** Appends the Venary section to /vonixsu status output. */
    public static void appendStatusLines(CommandSourceStack source) {
        VenaryClient client = VenaryClient.get();
        if (client == null) {
            source.sendSuccess(Component.literal("§7Venary: §cnot initialized"), false);
            return;
        }
        VenaryConfig cfg = client.getConfig();
        String state = cfg.isEnabled() ? "§aenabled" : "§cdisabled";
        source.sendSuccess(Component.literal("§7Venary: " + state
                + "§7, base=§f" + cfg.getApiBase()
                + "§7, api_key=§f" + cfg.getMaskedApiKey()), false);
        source.sendSuccess(Component.literal("§7  login-jwt=§f" + cfg.isLoginJwtEnabled()
                + "§7, stats-sync=§f" + cfg.isStatsSyncEnabled()
                + "§7 (every §f" + cfg.getStatsSyncIntervalMinutes() + "m§7)"
                + "§7, link-cooldown=§f" + cfg.getLinkCooldownSeconds() + "s"), false);
    }

    /** Clear cooldown state for a player who left, to keep the map bounded. */
    public static void onPlayerLeave(UUID uuid) {
        lastRequest.remove(uuid);
    }

    /** For tests / reload. */
    public static void clearCooldowns() {
        lastRequest.clear();
        VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] /link cooldowns cleared");
    }
}
