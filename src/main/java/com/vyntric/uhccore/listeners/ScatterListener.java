package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Blocks actual position changes (but still allows looking around) for
 * players ScatterManager has frozen right after a game-start scatter.
 */
public class ScatterListener implements Listener {

    private final VyntricUhcCore plugin;

    public ScatterListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.scatter().isFrozen(player)) return;

        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }
}
