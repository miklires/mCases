package io.github.miklires.mcases.util;

import io.github.miklires.mcases.MCasesPlugin;
import org.bukkit.entity.Player;

public final class PluginScheduler {
    private final MCasesPlugin plugin;
    public PluginScheduler(MCasesPlugin plugin) { this.plugin = plugin; }
    public void global(Runnable task) { plugin.getServer().getGlobalRegionScheduler().execute(plugin, task); }
    public void player(Player player, Runnable task) { player.getScheduler().execute(plugin, task, null, 1L); }
    public void playerLater(Player player, Runnable task, long ticks) { player.getScheduler().runDelayed(plugin, ignored -> task.run(), null, ticks); }
}
