package io.github.miklires.mcases.casebox;

import java.util.List;

public record CaseDefinition(String id, String displayName, int pityThreshold, List<RewardDefinition> rewards) {
    public CaseDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Case id is blank");
        if (pityThreshold < 0) throw new IllegalArgumentException("Pity threshold cannot be negative");
        rewards = List.copyOf(rewards);
        if (rewards.isEmpty()) throw new IllegalArgumentException("Case needs at least one reward");
        long distinct = rewards.stream().map(RewardDefinition::id).distinct().count();
        if (distinct != rewards.size()) throw new IllegalArgumentException("Duplicate reward id in " + id);
        if (pityThreshold > 0 && rewards.stream().noneMatch(r -> r.rarity().isPityEligible())) {
            throw new IllegalArgumentException("Pity is enabled but no eligible reward exists");
        }
    }
}
