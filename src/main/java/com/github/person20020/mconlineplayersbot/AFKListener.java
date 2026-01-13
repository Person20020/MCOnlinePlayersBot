package com.github.person20020.mconlineplayersbot;

import net.ess3.api.events.AfkStatusChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AFKListener implements Listener {

    private final MCOnlinePlayersBot plugin;

    public AFKListener(MCOnlinePlayersBot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAFKStatusChange(AfkStatusChangeEvent event) {
        // Update player list on AFK status change
        Player player = event.getAffected().getBase();

        // Delay by 1 tick
        Bukkit.getScheduler().runTaskLater(plugin, plugin::discordUpdatePlayerList, 1L);
    }
}
