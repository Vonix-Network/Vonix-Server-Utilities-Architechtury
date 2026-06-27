package network.vonix.serverutilities.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import network.vonix.serverutilities.VonixServerUtilities;
import network.vonix.serverutilities.donation_ranks.LuckPermsBridge;

import java.util.Optional;

/**
 * Renders chat as {@code [Prefix] <Name> message} using LuckPerms metadata.
 *
 * <p>Prefix source: highest-weight inherited group's
 * {@code CachedMetaData.getPrefix()}. Supports legacy {@code &}-codes and
 * {@code &#RRGGBB} hex codes. Safe no-op when LuckPerms is absent or the
 * player has no prefix set.</p>
 *
 * <p>This formatter is invoked from the Forge {@code ServerChatEvent}
 * handler; it never blocks (LP cached lookups are O(1) in-memory).</p>
 */
public final class ChatFormatter {

    private ChatFormatter() {}

    /**
     * Build a fully styled chat component. Returns {@code Optional.empty()}
     * when LuckPerms is absent — caller should leave the vanilla event
     * component untouched.
     */
    public static Optional<Component> format(ServerPlayer player, String rawMessage) {
        try {
            Optional<LuckPermsBridge.UserPrefixInfo> info = LuckPermsBridge.getUserPrefixInfo(player.getUUID());
            if (!info.isPresent()) {
                return Optional.empty();
            }
            String prefix = info.get().prefix;
            String suffix = info.get().suffix;
            String nameColor = info.get().nameColor;

            MutableComponent out = new TextComponent("");
            if (prefix != null && !prefix.isEmpty()) {
                out.append(parseLegacy(prefix)).append(new TextComponent(" "));
            }
            out.append(new TextComponent("<"));
            MutableComponent nameComp = new TextComponent(player.getName().getString());
            if (nameColor != null && !nameColor.isEmpty()) {
                applyColor(nameComp, nameColor);
            }
            out.append(nameComp);
            out.append(new TextComponent("> "));
            if (suffix != null && !suffix.isEmpty()) {
                out.append(parseLegacy(suffix)).append(new TextComponent(" "));
            }
            // Render the raw message body, allowing players with chat color
            // perms to use color codes (LP perm check could be added here).
            out.append(parseLegacy(rawMessage == null ? "" : rawMessage));
            return Optional.of(out);
        } catch (LinkageError | RuntimeException t) {
            VonixServerUtilities.LOGGER.warn("[VonixSU] ChatFormatter failed for {}", player.getName().getString(), t);
            return Optional.empty();
        }
    }

    /**
     * Parse legacy color codes ({@code &a}, {@code &l}, {@code &#RRGGBB})
     * into a styled component.
     */
    public static MutableComponent parseLegacy(String input) {
        if (input == null || input.isEmpty()) return new TextComponent("");

        // Normalise & -> §
        String text = input.replace('&', '\u00a7');

        MutableComponent root = new TextComponent("");
        Style current = Style.EMPTY;
        StringBuilder buf = new StringBuilder();

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));

                // Hex form: §#RRGGBB
                if (code == '#' && i + 7 < text.length()) {
                    flush(root, buf, current);
                    String hex = text.substring(i + 2, i + 8);
                    try {
                        int rgb = Integer.parseInt(hex, 16);
                        current = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
                    } catch (NumberFormatException ignored) {}
                    i += 8;
                    continue;
                }

                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    flush(root, buf, current);
                    if (fmt == ChatFormatting.RESET) {
                        current = Style.EMPTY;
                    } else if (fmt.isColor()) {
                        current = Style.EMPTY.withColor(fmt);
                    } else {
                        current = current.applyFormat(fmt);
                    }
                    i += 2;
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        flush(root, buf, current);
        return root;
    }

    private static void flush(MutableComponent root, StringBuilder buf, Style style) {
        if (buf.length() == 0) return;
        root.append(new TextComponent(buf.toString()).setStyle(style));
        buf.setLength(0);
    }

    private static void applyColor(MutableComponent comp, String color) {
        try {
            if (color.startsWith("#")) {
                comp.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(color.substring(1), 16))));
            } else if (color.startsWith("&#")) {
                comp.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(color.substring(2), 16))));
            } else {
                ChatFormatting cf = ChatFormatting.getByName(color.toUpperCase());
                if (cf != null && cf.isColor()) comp.setStyle(Style.EMPTY.withColor(cf));
            }
        } catch (Exception ignored) {}
    }
}
