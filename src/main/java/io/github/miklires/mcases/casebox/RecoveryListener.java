package io.github.miklires.mcases.casebox;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class RecoveryListener implements Listener {
    private final CaseService service;
    public RecoveryListener(CaseService service) { this.service = service; }
    @EventHandler public void onJoin(PlayerJoinEvent event) { service.recover(event.getPlayer()); }
}
