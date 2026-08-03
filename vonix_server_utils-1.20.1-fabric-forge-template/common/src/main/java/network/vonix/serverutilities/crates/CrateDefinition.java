package network.vonix.serverutilities.crates;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable crate configuration held by the service layer. */
public record CrateDefinition(String name, KeyType keyType, boolean enabled, Map<Integer, PrizeDefinition> prizes) {
    public CrateDefinition {
        if (name == null || name.isBlank() || keyType == null) throw new IllegalArgumentException("Crate name and key type are required");
        prizes = Collections.unmodifiableMap(new LinkedHashMap<>(prizes == null ? Map.of() : prizes));
    }

    public int totalWeight() {
        return prizes.values().stream().mapToInt(PrizeDefinition::weight).sum();
    }

    public record PrizeDefinition(int id, String label, String command, int weight) {
        public PrizeDefinition {
            if (id < 1 || label == null || label.isBlank() || command == null || command.isBlank() || weight < 1) {
                throw new IllegalArgumentException("Prize id, label, command, and positive weight are required");
            }
        }
    }
}
