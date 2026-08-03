package network.vonix.serverutilities.crates;

import java.util.Locale;

/** Validated identifier for a virtual key type or crate name. */
public record KeyType(String value) {
    public KeyType {
        if (value == null || !value.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Key types must match [a-z0-9_-]{1,32}");
        }
        value = value.toLowerCase(Locale.ROOT);
    }
}
