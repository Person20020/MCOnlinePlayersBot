package com.github.person20020.mconlineplayersbot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final MCOnlinePlayersBot plugin;

    public PlayerListener(MCOnlinePlayersBot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Update player list on player join
        plugin.discordUpdatePlayerList();
    }
}
