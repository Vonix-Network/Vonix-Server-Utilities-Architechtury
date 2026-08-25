package network.vonix.serverutilities.crates;

import java.util.List;

/** Pure percentage, identifier, and playtime regression suite shared by VSU versions. */
public final class CrateRulesTest {
    private CrateRulesTest() {}
    public static void main(String[] args) {
        var prizes = List.of(new CrateDefinition.PrizeDefinition(1, "Common", "give {player} stone", 70), new CrateDefinition.PrizeDefinition(2, "Rare", "give {player} diamond", 30));
        var percentages = WeightedPrizeSelector.percentages(prizes);
        if (Math.abs(percentages.get(0) - 70.0) > 0.000001 || Math.abs(percentages.get(1) - 30.0) > 0.000001) throw new AssertionError("wrong percentages");
        if (Math.abs(percentages.stream().mapToDouble(Double::doubleValue).sum() - 100.0) > 0.000001) throw new AssertionError("percentages do not total 100");
        for (int i = 0; i < 100; i++) if (WeightedPrizeSelector.choose(prizes) == null) throw new AssertionError("null selection");
        if (PlaytimeIntervals.completed(72000, 60) != 1 || PlaytimeIntervals.completed(144000, 60) != 2) throw new AssertionError("wrong playtime interval");
        boolean rejected = false;
        try { new KeyType("bad key"); } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("invalid key accepted");
        System.out.println("CrateRulesTest: PASS");
    }
}
