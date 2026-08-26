package io.github.miklires.mcases.command;

import io.github.miklires.mcases.MCasesPlugin;
import io.github.miklires.mcases.casebox.CaseDefinition;
import io.github.miklires.mcases.casebox.CaseService;
import io.github.miklires.mcases.casebox.RewardDefinition;
import io.github.miklires.mcases.casebox.RewardSelector;
import io.github.miklires.mcases.gui.CaseGui;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class CaseCommand implements BasicCommand {
    private final MCasesPlugin plugin;
    private final CaseService service;
    private final CaseGui gui;
    public CaseCommand(MCasesPlugin plugin, CaseService service, CaseGui gui) {
        this.plugin = plugin; this.service = service; this.gui = gui;
    }

    @Override public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { reload(sender); return; }
        if (args.length > 0 && args[0].equalsIgnoreCase("key")) { keys(sender, args); return; }
        if (args.length > 0 && args[0].equalsIgnoreCase("simulate")) { simulate(sender, args); return; }
        if (!(sender instanceof Player player)) { sender.sendMessage(CaseService.color("&cЭта команда доступна только игроку.")); return; }
        if (!player.hasPermission("mcases.use")) { noPermission(player); return; }
        if (args.length == 0) {
            player.sendMessage(CaseService.color("&eДоступные кейсы: &f" + String.join(", ", service.catalog().all().keySet())));
            return;
        }
        service.catalog().find(args[0]).ifPresentOrElse(definition -> gui.open(player, definition),
                () -> player.sendMessage(CaseService.color("&cНеизвестный кейс.")));
    }

    private void keys(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcases.admin")) { noPermission(sender); return; }
        if (args.length != 5 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("Usage: /case key give <player> <case> <amount>"); return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        CaseDefinition definition = service.catalog().find(args[3]).orElse(null);
        int amount;
        try { amount = Integer.parseInt(args[4]); } catch (NumberFormatException exception) { amount = 0; }
        if (target == null || definition == null || amount < 1) { sender.sendMessage(CaseService.color("&cПроверьте игрока, кейс и количество.")); return; }
        int finalAmount = amount;
        service.addKeys(target.getUniqueId(), definition.id(), amount).whenComplete((ignored, error) -> plugin.scheduler().global(() ->
                sender.sendMessage(CaseService.color(error == null ? "&aВыдано ключей: &f" + finalAmount : "&cОшибка хранилища."))));
    }

    private void simulate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcases.simulate")) { noPermission(sender); return; }
        if (args.length != 3) { sender.sendMessage("Usage: /case simulate <case> <rolls>"); return; }
        CaseDefinition definition = service.catalog().find(args[1]).orElse(null);
        int rolls;
        try { rolls = Integer.parseInt(args[2]); } catch (NumberFormatException exception) { rolls = 0; }
        int max = Math.max(1, plugin.getConfig().getInt("simulation.maximum-rolls", 1_000_000));
        if (definition == null || rolls < 1 || rolls > max) { sender.sendMessage(CaseService.color("&cКейс не найден или число попыток вне диапазона 1-" + max)); return; }
        Map<String, Integer> counts = new LinkedHashMap<>();
        RewardSelector selector = new RewardSelector(ThreadLocalRandom.current());
        int pity = 0;
        for (int i = 0; i < rolls; i++) {
            RewardDefinition reward = selector.select(definition, pity);
            counts.merge(reward.id(), 1, Integer::sum);
            pity = reward.rarity().isPityEligible() ? 0 : pity + 1;
        }
        sender.sendMessage(CaseService.color("&eСимуляция " + rolls + " открытий: &f" + counts));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("mcases.reload")) { noPermission(sender); return; }
        try { plugin.reloadRuntime(); sender.sendMessage(CaseService.color("&aКонфигурация перезагружена.")); }
        catch (RuntimeException exception) { sender.sendMessage(CaseService.color("&cОшибка конфигурации: " + exception.getMessage())); }
    }

    private static void noPermission(CommandSender sender) { sender.sendMessage(CaseService.color("&cНедостаточно прав.")); }

    @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length <= 1) {
            values.addAll(service.catalog().all().keySet());
            if (source.getSender().hasPermission("mcases.admin")) values.addAll(List.of("key", "simulate", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("key")) values.add("give");
        else if (args.length == 3 && args[0].equalsIgnoreCase("key")) Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        else if ((args.length == 4 && args[0].equalsIgnoreCase("key")) || (args.length == 2 && args[0].equalsIgnoreCase("simulate"))) values.addAll(service.catalog().all().keySet());
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
