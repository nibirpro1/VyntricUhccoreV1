package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ScoreboardListener implements Listener {

    private final VyntricUhcCore plugin;

    public ScoreboardListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            plugin.scoreboard().addKill(killer);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.scoreboard().remove(event.getPlayer());
    }
}
