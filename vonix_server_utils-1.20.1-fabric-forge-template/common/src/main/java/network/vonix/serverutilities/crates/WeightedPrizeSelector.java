package network.vonix.serverutilities.crates;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Pure weighted-prize selection; no Minecraft or database dependency. */
public final class WeightedPrizeSelector {
    private WeightedPrizeSelector() {}

    public static CrateDefinition.PrizeDefinition choose(List<CrateDefinition.PrizeDefinition> prizes) {
        if (prizes == null || prizes.isEmpty()) throw new IllegalArgumentException("At least one prize is required");
        long total = 0;
        for (CrateDefinition.PrizeDefinition prize : prizes) total = Math.addExact(total, prize.weight());
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("Total prize weight is too large");
        int pick = ThreadLocalRandom.current().nextInt((int) total);
        int cursor = 0;
        for (CrateDefinition.PrizeDefinition prize : prizes) {
            cursor += prize.weight();
            if (pick < cursor) return prize;
        }
        return prizes.get(prizes.size() - 1);
    }

    public static List<Double> percentages(List<CrateDefinition.PrizeDefinition> prizes) {
        if (prizes == null || prizes.isEmpty()) return List.of();
        int total = prizes.stream().mapToInt(CrateDefinition.PrizeDefinition::weight).sum();
        if (total <= 0) return List.of();
        List<Double> values = new ArrayList<>(prizes.size());
        for (CrateDefinition.PrizeDefinition p : prizes) values.add(p.weight() * 100.0 / total);
        return List.copyOf(values);
    }
}
