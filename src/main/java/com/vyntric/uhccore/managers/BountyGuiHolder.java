package com.vyntric.uhccore.managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Marker InventoryHolder for the chest a player is shown when running
 * /vuhc bounty <player>. Whatever is left inside it when the viewer closes the
 * inventory becomes the bounty reward pool - see BountyListener#onClose.
 */
public class BountyGuiHolder implements InventoryHolder {

    private final UUID targetId;
    private final String targetName;
    private Inventory inventory;

    public BountyGuiHolder(UUID targetId, String targetName) {
        this.targetId = targetId;
        this.targetName = targetName;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
