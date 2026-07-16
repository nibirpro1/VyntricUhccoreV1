package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class SpectatorListener implements Listener {

    private final VyntricUhcCore plugin;

    public SpectatorListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.spectator().isEnabled()) return;
        if (!plugin.spectator().isGameRunning()) return;

        Player player = event.getEntity();
        // already being handled as an "eliminated while offline" case elsewhere - don't double up.
        if (plugin.leaveZombies().isEliminated(player.getUniqueId())) return;

        plugin.spectator().markForSpectate(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.spectator().applyOnRespawn(event.getPlayer());
    }
}
