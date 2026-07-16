package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LeaveZombieListener implements Listener {

    private final VyntricUhcCore plugin;

    public LeaveZombieListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.leaveZombies().isEnabled()) return;
        if (!plugin.leaveZombies().isGameRunning()) return;

        Player player = event.getPlayer();
        // already spectating/eliminated, or already turned into a zombie once (shouldn't
        // normally happen twice in a row, but guard against double-firing anyway)
        if (plugin.leaveZombies().isEliminated(player.getUniqueId())) return;

        plugin.leaveZombies().turnIntoZombie(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.leaveZombies().isEnabled()) return;
        plugin.leaveZombies().handleRejoin(event.getPlayer());
    }

    @EventHandler
    public void onZombieDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        if (!plugin.leaveZombies().isTrackedZombie(event.getEntity().getUniqueId())) return;

        plugin.leaveZombies().onZombieKilled(event.getEntity().getUniqueId());
    }
}
