package io.github.miklires.mcases.casebox;

import io.github.miklires.mcases.MCasesPlugin;
import io.github.miklires.mcases.api.Rarity;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class CaseCatalog {
    private final MCasesPlugin plugin;
    private volatile Map<String, CaseDefinition> cases = Map.of();

    public CaseCatalog(MCasesPlugin plugin) { this.plugin = plugin; }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "cases.yml");
        if (!file.isFile()) plugin.saveResource("cases.yml", false);
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("cases");
        Map<String, CaseDefinition> loaded = new LinkedHashMap<>();
        if (root != null) for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection node = root.getConfigurationSection(rawId);
            if (node == null) continue;
            ConfigurationSection rewardsNode = node.getConfigurationSection("rewards");
            if (rewardsNode == null) throw new IllegalArgumentException("No rewards configured for case " + id);
            List<RewardDefinition> rewards = rewardsNode.getKeys(false).stream().map(rewardId -> {
                ConfigurationSection reward = rewardsNode.getConfigurationSection(rewardId);
                if (reward == null) throw new IllegalArgumentException("Invalid reward " + rewardId);
                return new RewardDefinition(rewardId, reward.getString("name", rewardId),
                        Rarity.valueOf(reward.getString("rarity", "COMMON").toUpperCase(Locale.ROOT)),
                        reward.getDouble("weight"), reward.getString("material", "CHEST"), reward.getStringList("commands"));
            }).toList();
            loaded.put(id, new CaseDefinition(id, node.getString("name", id), node.getInt("pity", 0), rewards));
        }
        if (loaded.isEmpty()) throw new IllegalArgumentException("At least one case must be configured");
        cases = Map.copyOf(loaded);
    }

    public Optional<CaseDefinition> find(String id) { return Optional.ofNullable(cases.get(id.toLowerCase(Locale.ROOT))); }
    public Map<String, CaseDefinition> all() { return cases; }
}
