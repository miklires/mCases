package io.github.miklires.mcases.gui;

import io.github.miklires.mcases.MCasesPlugin;
import io.github.miklires.mcases.casebox.CaseDefinition;
import io.github.miklires.mcases.casebox.CaseService;
import io.github.miklires.mcases.casebox.RewardDefinition;
import io.github.miklires.mcases.storage.NoKeyException;
import io.github.miklires.mcases.storage.PreparedOpen;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class CaseGui implements Listener {
    private static final DecimalFormat CHANCE = new DecimalFormat("0.####");
    private final MCasesPlugin plugin;
    private final CaseService service;

    public CaseGui(MCasesPlugin plugin, CaseService service) { this.plugin = plugin; this.service = service; }

    public void open(Player player, CaseDefinition definition) {
        service.balance(player.getUniqueId(), definition.id()).whenComplete((balance, error) -> plugin.scheduler().player(player, () -> {
            if (error != null) { player.sendMessage(CaseService.color("&cНе удалось загрузить ключи.")); return; }
            CaseHolder holder = new CaseHolder(definition);
            Inventory inventory = Bukkit.createInventory(holder, 54, CaseService.color(definition.displayName()));
            holder.inventory(inventory);
            double total = definition.rewards().stream().mapToDouble(RewardDefinition::weight).sum();
            int slot = 0;
            for (RewardDefinition reward : definition.rewards()) {
                if (slot >= 45) break;
                inventory.setItem(slot++, item(material(reward.material()), reward.displayName(), List.of(
                        "&7Редкость: &f" + reward.rarity(), "&7Шанс: &f" + CHANCE.format(reward.weight() * 100 / total) + "%")));
            }
            inventory.setItem(49, item(Material.TRIPWIRE_HOOK, "&aОткрыть кейс", List.of(
                    "&7Ключей: &f" + balance, definition.pityThreshold() > 0 ? "&7Гарант: &f" + definition.pityThreshold() : "&7Без гаранта")));
            player.openInventory(inventory);
        }));
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof CaseHolder holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 49 || !(event.getWhoClicked() instanceof Player player)) return;
        player.closeInventory();
        service.prepare(player, holder.definition()).whenComplete((prepared, error) -> plugin.scheduler().player(player, () -> {
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException ? error.getCause() : error;
                player.sendMessage(CaseService.color(cause instanceof NoKeyException ? "&cУ вас нет ключа от этого кейса." : "&cНе удалось открыть кейс."));
                return;
            }
            animate(player, prepared);
        }));
    }

    private void animate(Player player, PreparedOpen prepared) {
        player.sendMessage(CaseService.color("&eКейс открывается... Награда уже безопасно зафиксирована."));
        plugin.scheduler().playerLater(player, () -> {
            if (!player.isOnline()) return;
            player.sendTitle(CaseService.color("&6" + prepared.reward().displayName()), CaseService.color("&fВаша награда"), 5, 40, 10);
            service.deliver(player, prepared);
        }, Math.max(1, plugin.getConfig().getLong("opening.animation-ticks", 40)));
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(CaseService.color(name));
        meta.setLore(lore.stream().map(CaseService::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static Material material(String name) {
        Material material = Material.matchMaterial(name);
        return material == null ? Material.CHEST : material;
    }
}
