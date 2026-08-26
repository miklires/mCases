package io.github.miklires.mcases;

import io.github.miklires.mcases.api.MCasesApi;
import io.github.miklires.mcases.casebox.CaseCatalog;
import io.github.miklires.mcases.casebox.CaseService;
import io.github.miklires.mcases.casebox.RecoveryListener;
import io.github.miklires.mcases.command.CaseCommand;
import io.github.miklires.mcases.gui.CaseGui;
import io.github.miklires.mcases.storage.CaseRepository;
import io.github.miklires.mcases.util.PluginScheduler;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class MCasesPlugin extends JavaPlugin {
    private PluginScheduler scheduler;
    private CaseCatalog catalog;
    private CaseRepository repository;

    @Override public void onEnable() {
        saveDefaultConfig();
        scheduler = new PluginScheduler(this);
        try {
            catalog = new CaseCatalog(this);
            catalog.reload();
            repository = new CaseRepository(getDataFolder().toPath().resolve("data").resolve("mcases"));
            CaseService service = new CaseService(this, catalog, repository);
            CaseGui gui = new CaseGui(this, service);
            CaseCommand command = new CaseCommand(this, service, gui);
            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                    event -> event.registrar().register("case", "Open and administer reward cases", java.util.List.of("cases", "mcase"), command));
            getServer().getPluginManager().registerEvents(gui, this);
            getServer().getPluginManager().registerEvents(new RecoveryListener(service), this);
            getServer().getServicesManager().register(MCasesApi.class, new CaseApiService(service), this, ServicePriority.Normal);
            startMetrics();
            getServer().getOnlinePlayers().forEach(service::recover);
            getLogger().info("mCases " + getPluginMeta().getVersion() + " is ready with " + catalog.all().size() + " case(s)");
        } catch (RuntimeException exception) {
            getLogger().severe("mCases failed to start: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (repository != null) repository.close();
    }

    public PluginScheduler scheduler() { return scheduler; }
    public void reloadRuntime() { reloadConfig(); catalog.reload(); }

    private void startMetrics() {
        int id = Math.max(0, getConfig().getInt("metrics.bstats-id", 0));
        if (getConfig().getBoolean("metrics.enabled", true) && id > 0) new Metrics(this, id);
    }
}
