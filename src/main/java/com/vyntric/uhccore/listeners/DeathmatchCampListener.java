package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.managers.TimerManager;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Stops players from placing blocks above the deathmatch height limit —
 * paired with DeathmatchCampManager's damage-over-time, this closes off
 * building a sky base to dodge the deathmatch fight entirely.
 */
public class DeathmatchCampListener implements Listener {

    private final VyntricUhcCore plugin;

    public DeathmatchCampListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.deathmatchCamp().isEnabled()) return;
        if (plugin.timers().getPhase() != TimerManager.Phase.DEATHMATCH) return;

        if (event.getBlock().getY() > plugin.deathmatchCamp().getHeightLimit()) {
            event.setCancelled(true);
            Msg.send(event.getPlayer(), plugin.deathmatchCamp().getBlockPlaceMessage());
        }
    }
}
