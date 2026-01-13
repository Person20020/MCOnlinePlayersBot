package com.github.person20020.mconlineplayersbot;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final MCOnlinePlayersBot plugin;

    public PlayerListener(MCOnlinePlayersBot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Update player list on player join after 1 tick
        Bukkit.getScheduler().runTaskLater(plugin, plugin::discordUpdatePlayerList, 1L);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        // Update player list on player leave after 1 tick
        Bukkit.getScheduler().runTaskLater(plugin, plugin::discordUpdatePlayerList, 1L);
    }
}
