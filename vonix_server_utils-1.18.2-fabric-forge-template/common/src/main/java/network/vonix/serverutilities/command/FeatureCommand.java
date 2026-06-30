package network.vonix.serverutilities.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.features.FeatureRegistry;
import network.vonix.serverutilities.features.PermissionGate;
import network.vonix.serverutilities.features.ServerConfigClient;
import network.vonix.serverutilities.venary.VenaryClient;

import java.util.Map;
import java.util.TreeMap;

/**
 * Sub-tree under {@code /vonixsu feature}:
 * <pre>
 *   /vonixsu feature list
 *   /vonixsu feature enable  &lt;key&gt;
 *   /vonixsu feature disable &lt;key&gt;
 *   /vonixsu feature reload
 *   /vonixsu feature status  &lt;key&gt;
 * </pre>
 *
 * <p>All operations require permission level 3 (server-op) or the console.
 *
 * <h2>In-game toggle limitation</h2>
 * The admin dashboard endpoint that mutates the canonical feature row uses
 * {@code authenticateToken + requireAdmin}; the mod's API key is NOT a user
 * token, so we cannot call it directly. Instead /vonixsu feature
 * enable/disable updates the LOCAL {@link FeatureRegistry} immediately AND
 * posts an audit row (event_type=feature_toggle). On the very next
 * /server-config poll the canonical dashboard state will sync back and
 * <b>overwrite</b> the local toggle. So in-game toggles are best thought of
 * as "until the next poll" — for permanent changes operators must use the
 * admin dashboard.
 *
 * <p>TODO(operator-review): add a mod-facing PATCH endpoint authenticated by
 * the api_key in Phase 2 so in-game toggles can persist.
 */
public final class FeatureCommand {

    private FeatureCommand() {}

    /** Returns a builder that can be {@code .then()}-attached to /vonixsu. */
    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("feature")
                .requires(PermissionGate.requires("vsu.admin.manage", 3))
                .then(Commands.literal("list").executes(FeatureCommand::list))
                .then(Commands.literal("reload").executes(FeatureCommand::reload))
                .then(Commands.literal("enable")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(ctx -> setEnabled(ctx, StringArgumentType.getString(ctx, "key"), true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(ctx -> setEnabled(ctx, StringArgumentType.getString(ctx, "key"), false))))
                .then(Commands.literal("status")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(ctx -> status(ctx, StringArgumentType.getString(ctx, "key")))))
                .executes(FeatureCommand::list);
    }

    // ── list ────────────────────────────────────────────────────────────────

    private static int list(CommandContext<CommandSourceStack> ctx) {
        FeatureRegistry reg = FeatureRegistry.getInstance();
        Map<String, Boolean> snap = new TreeMap<>(reg.snapshot());
        ctx.getSource().sendSuccess(new TextComponent(
                "§6[VSU] §fFeatures §7(config v" + reg.getConfigVersion()
                        + (reg.isHydratedFromBackend() ? "" : ", §enot yet synced") + "§7):"), false);
        if (snap.isEmpty()) {
            ctx.getSource().sendSuccess(new TextComponent("  §7(none known yet — waiting for first /server-config)"), false);
            return 1;
        }
        for (Map.Entry<String, Boolean> e : snap.entrySet()) {
            String tag = e.getValue() ? "§a[ON] " : "§c[OFF]";
            ctx.getSource().sendSuccess(new TextComponent("  " + tag + " §f" + e.getKey()), false);
        }
        return snap.size();
    }

    // ── enable / disable ────────────────────────────────────────────────────

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, String key, boolean enable) {
        FeatureRegistry reg = FeatureRegistry.getInstance();
        boolean changed = reg.setEnabledLocal(key, enable);
        String label = enable ? "enabled" : "disabled";

        ctx.getSource().sendSuccess(new TextComponent(
                "§a[VSU] Feature §e" + key + "§a " + label + " §7(local-only — will be overwritten by next /server-config poll)."), true);
        if (!changed) {
            ctx.getSource().sendSuccess(new TextComponent(
                    "§7    (already " + label + ", no change)"), false);
        }

        // Refresh command trees on the main thread so the change takes effect immediately.
        var server = ctx.getSource().getServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    for (var p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
                } catch (Exception ignored) {}
            });
        }

        // Audit (fail-open).
        VenaryClient v = VenaryClient.get();
        if (v != null) {
            JsonObject p = new JsonObject();
            p.addProperty("feature_key", key);
            p.addProperty("enabled", enable);
            p.addProperty("scope", "local");
            String actorType = ctx.getSource().getEntity() == null ? "console" : "ingame";
            String actorId   = actorType.equals("ingame")
                    ? ctx.getSource().getTextName() : "console";
            v.postAudit("feature_toggle", actorType, actorId, null, p);
        }
        return 1;
    }

    // ── reload ──────────────────────────────────────────────────────────────

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(new TextComponent(
                "§a[VSU] Forcing /server-config fetch — check logs in a few seconds."), true);
        ServerConfigClient.requestImmediateFetch();
        VonixServerUtilities.LOGGER.info("[VonixSU] /vonixsu feature reload requested by {}",
                ctx.getSource().getTextName());
        return 1;
    }

    // ── status ──────────────────────────────────────────────────────────────

    private static int status(CommandContext<CommandSourceStack> ctx, String key) {
        FeatureRegistry reg = FeatureRegistry.getInstance();
        boolean enabled = reg.isEnabled(key);
        JsonObject settings = reg.getSettings(key);
        ctx.getSource().sendSuccess(new TextComponent(
                "§6[VSU] Feature §e" + key + "§6: " + (enabled ? "§aON" : "§cOFF")), false);
        if (settings == null || settings.size() == 0) {
            ctx.getSource().sendSuccess(new TextComponent("  §7(no settings)"), false);
        } else {
            String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(settings);
            for (String line : pretty.split("\n")) {
                ctx.getSource().sendSuccess(new TextComponent("  §7" + line), false);
            }
        }
        return 1;
    }

    /** Used by /vonixsu status — short, one-line summary. */
    public static String summaryLine() {
        FeatureRegistry reg = FeatureRegistry.getInstance();
        Map<String, Boolean> snap = reg.snapshot();
        int on = 0, off = 0;
        for (boolean v : snap.values()) { if (v) on++; else off++; }
        return "§7Features: §a" + on + " on§7 / §c" + off + " off§7 (config v" + reg.getConfigVersion()
                + (reg.isHydratedFromBackend() ? "" : ", not yet synced") + ")";
    }
}
