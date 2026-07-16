package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.managers.BountyGuiHolder;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class BountyListener implements Listener {

    private final VyntricUhcCore plugin;

    public BountyListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder rawHolder = event.getInventory().getHolder();
        if (!(rawHolder instanceof BountyGuiHolder)) return;
        if (!(event.getPlayer() instanceof Player)) return;

        BountyGuiHolder holder = (BountyGuiHolder) rawHolder;
        Player closer = (Player) event.getPlayer();

        int collected = plugin.bounty().confirmFromGui(holder, event.getInventory().getContents(), closer.getName());
        if (collected == 0) {
            Msg.send(closer, "&7You closed the bounty chest without placing any items - no bounty was set.");
        } else {
            Msg.send(closer, "&aBounty placed on &f" + holder.getTargetName() + "&a!");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.bounty().hasBounty(victim.getUniqueId())) return;

        plugin.bounty().claim(victim, victim.getKiller());
    }
}
