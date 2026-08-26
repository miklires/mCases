package io.github.miklires.mcases.casebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.github.miklires.mcases.api.Rarity;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RewardSelectorTest {
    private static final RewardDefinition COMMON = new RewardDefinition("common", "Common", Rarity.COMMON, 90, "STONE", List.of());
    private static final RewardDefinition RARE = new RewardDefinition("rare", "Rare", Rarity.RARE, 10, "DIAMOND", List.of());
    private static final CaseDefinition CASE = new CaseDefinition("test", "Test", 5, List.of(COMMON, RARE));

    @Test void weightedSelectionUsesConfiguredWeights() {
        assertEquals(COMMON, new RewardSelector(new FixedRandom(0.89)).select(CASE, 0));
        assertEquals(RARE, new RewardSelector(new FixedRandom(0.91)).select(CASE, 0));
    }

    @Test void pityRestrictsSelectionToEligibleRewards() {
        assertEquals(RARE, new RewardSelector(new FixedRandom(0)).select(CASE, 4));
    }

    private record FixedRandom(double value) implements RandomGenerator {
        @Override public long nextLong() { return (long) (value * Long.MAX_VALUE); }
        @Override public double nextDouble(double bound) { return value * bound; }
    }
}
