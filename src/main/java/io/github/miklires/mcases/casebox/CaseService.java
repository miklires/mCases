package io.github.miklires.mcases.casebox;

import io.github.miklires.mcases.MCasesPlugin;
import io.github.miklires.mcases.storage.CaseRepository;
import io.github.miklires.mcases.storage.PreparedOpen;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CaseService {
    private final MCasesPlugin plugin;
    private final CaseCatalog catalog;
    private final CaseRepository repository;

    public CaseService(MCasesPlugin plugin, CaseCatalog catalog, CaseRepository repository) {
        this.plugin = plugin; this.catalog = catalog; this.repository = repository;
    }

    public CompletableFuture<PreparedOpen> prepare(Player player, CaseDefinition definition) {
        return repository.prepareOpen(player.getUniqueId(), definition, new RewardSelector(ThreadLocalRandom.current()));
    }

    public void deliver(Player player, PreparedOpen prepared) {
        plugin.scheduler().global(() -> {
            for (String raw : prepared.reward().commands()) {
                String command = raw.replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
            repository.markDelivered(prepared.transactionId()).whenComplete((ignored, error) -> plugin.scheduler().player(player, () -> {
                if (error == null) player.sendMessage(color("&aНаграда получена: &f" + prepared.reward().displayName()));
                else player.sendMessage(color("&cНаграда выдана, но журнал не обновлён. Сообщите администрации."));
            }));
        });
    }

    public void recover(Player player) {
        repository.pending(player.getUniqueId()).thenAccept(rows -> {
            for (CaseRepository.PendingReward pending : rows) {
                CaseDefinition definition = catalog.find(pending.caseId()).orElse(null);
                RewardDefinition reward = definition == null ? null : definition.rewards().stream()
                        .filter(item -> item.id().equals(pending.rewardId())).findFirst().orElse(null);
                if (reward == null) {
                    plugin.getLogger().severe("Cannot recover reward " + pending.transactionId() + ": definition is missing");
                    continue;
                }
                plugin.scheduler().player(player, () -> deliver(player,
                        new PreparedOpen(pending.transactionId(), player.getUniqueId(), pending.caseId(), reward)));
            }
        });
    }

    public CompletableFuture<Integer> balance(UUID player, String caseId) { return repository.keyBalance(player, caseId); }
    public CompletableFuture<Void> addKeys(UUID player, String caseId, int amount) { return repository.addKeys(player, caseId, amount); }
    public CaseCatalog catalog() { return catalog; }
    public CaseRepository repository() { return repository; }
    public static String color(String text) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', text); }
}
