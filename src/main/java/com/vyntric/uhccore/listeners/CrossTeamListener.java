package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Feeds player-vs-player combat into the CrossTeamManager so it can be weighed
 * against proximity time (see CrossTeamManager for the reasoning).
 */
public class CrossTeamListener implements Listener {

    private final VyntricUhcCore plugin;

    public CrossTeamListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        plugin.crossTeam().recordHit(attacker, victim);
    }
}
