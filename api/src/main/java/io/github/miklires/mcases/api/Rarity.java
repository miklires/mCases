package io.github.miklires.mcases.api;

public enum Rarity {
    COMMON, RARE, EPIC, LEGENDARY;

    public boolean isPityEligible() {
        return ordinal() >= RARE.ordinal();
    }
}
