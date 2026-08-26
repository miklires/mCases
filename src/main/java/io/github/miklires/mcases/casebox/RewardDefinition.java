package io.github.miklires.mcases.casebox;

import io.github.miklires.mcases.api.Rarity;
import java.util.List;

public record RewardDefinition(
        String id, String displayName, Rarity rarity, double weight, String material, List<String> commands) {
    public RewardDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Reward id is blank");
        if (!Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Weight must be positive");
        commands = List.copyOf(commands);
    }
}
