package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class StatsListener implements Listener {

    private final VyntricUhcCore plugin;

    public StatsListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.stats().isAvailable()) return;

        Player victim = event.getEntity();
        plugin.stats().recordDeathAsync(victim.getUniqueId(), victim.getName());

        Player killer = victim.getKiller();
        if (killer != null) {
            plugin.stats().recordKillAsync(killer.getUniqueId(), killer.getName());
        }
    }
}
