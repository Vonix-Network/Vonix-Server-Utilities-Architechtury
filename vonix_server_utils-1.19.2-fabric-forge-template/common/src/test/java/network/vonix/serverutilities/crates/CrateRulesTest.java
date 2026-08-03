package network.vonix.serverutilities.crates;

import java.util.List;

/** Small executable regression suite for the pure weighted selection rules. */
public final class CrateRulesTest {
    private CrateRulesTest() {}

    public static void main(String[] args) {
        List<CrateDefinition.PrizeDefinition> prizes = List.of(
                new CrateDefinition.PrizeDefinition(1, "Common", "give {player} stone", 70),
                new CrateDefinition.PrizeDefinition(2, "Rare", "give {player} diamond", 30));
        List<Double> percentages = WeightedPrizeSelector.percentages(prizes);
        assertClose(percentages.get(0), 70.0);
        assertClose(percentages.get(1), 30.0);
        if (Math.abs(percentages.stream().mapToDouble(Double::doubleValue).sum() - 100.0) > 0.000001) {
            throw new AssertionError("percentages do not total 100");
        }
        for (int i = 0; i < 100; i++) {
            if (WeightedPrizeSelector.choose(prizes) == null) throw new AssertionError("selection returned null");
        }
        boolean rejected = false;
        try { new KeyType("bad key"); } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("invalid key identifier was accepted");
        System.out.println("CrateRulesTest: PASS");
    }

    private static void assertClose(double actual, double expected) {
        if (Math.abs(actual - expected) > 0.000001) throw new AssertionError(actual + " != " + expected);
    }
}
