package com.vyntric.uhccore.managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker InventoryHolder for the kit-selection menu opened when a player
 * right-clicks the lobby's kit-selector item. See KitListener for click
 * handling and KitManager#openSelectionGui for how the menu is built.
 */
public class KitGuiHolder implements InventoryHolder {

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
