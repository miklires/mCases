package io.github.miklires.mcases.casebox;

import java.util.List;
import java.util.random.RandomGenerator;

public final class RewardSelector {
    private final RandomGenerator random;

    public RewardSelector(RandomGenerator random) {
        this.random = random;
    }

    public RewardDefinition select(CaseDefinition definition, int openingsSinceRare) {
        boolean pity = definition.pityThreshold() > 0 && openingsSinceRare + 1 >= definition.pityThreshold();
        List<RewardDefinition> pool = pity
                ? definition.rewards().stream().filter(r -> r.rarity().isPityEligible()).toList()
                : definition.rewards();
        double total = pool.stream().mapToDouble(RewardDefinition::weight).sum();
        double target = random.nextDouble(total);
        double cursor = 0;
        for (RewardDefinition reward : pool) {
            cursor += reward.weight();
            if (target < cursor) return reward;
        }
        return pool.getLast();
    }
}
