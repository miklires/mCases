package io.github.miklires.mcases.gui;

import io.github.miklires.mcases.casebox.CaseDefinition;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class CaseHolder implements InventoryHolder {
    private final CaseDefinition definition;
    private Inventory inventory;
    public CaseHolder(CaseDefinition definition) { this.definition = definition; }
    public CaseDefinition definition() { return definition; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
