package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.managers.KitGuiHolder;
import com.vyntric.uhccore.managers.KitManager;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Right-click the lobby's kit-selector item -> opens the kit menu (KitManager).
 * Click a kit inside that menu -> gives the kit's items and closes the menu.
 */
public class KitListener implements Listener {

    private final VyntricUhcCore plugin;

    public KitListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        Material selectorMat = materialOr(plugin.getConfig().getString("lobby.kit-selector.material", "COMPASS"), Material.COMPASS);
        if (item.getType() != selectorMat) return;

        String configuredName = Msg.c(plugin.getConfig().getString("lobby.kit-selector.name", "&aRight click to choose a kit"));
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !configuredName.equals(meta.getDisplayName())) return;

        event.setCancelled(true);

        if (plugin.kits().isEmpty()) {
            Msg.send(event.getPlayer(), "&cThere are no kits configured yet.");
            return;
        }

        plugin.kits().openSelectionGui(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitGuiHolder)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof KitGuiHolder)) {
            return;
        }
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        KitManager.Kit kit = plugin.kits().kitAtSlot(event.getSlot());
        if (kit == null) return;

        if (plugin.kits().selectKit(player, kit)) {
            player.closeInventory();
        }
    }

    private Material materialOr(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
