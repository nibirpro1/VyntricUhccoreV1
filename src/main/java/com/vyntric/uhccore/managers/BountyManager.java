package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounty system: /vuhc bounty <player> shows the sender a chest to drop items
 * into. Whatever is left in it when they close the chest becomes that player's
 * bounty reward pool - it's announced to everyone, and whoever gets the killing
 * blow on the bountied player collects the items (dropped on the ground if their
 * inventory can't hold it all).
 *
 * Bounties are in-memory only (a round-scoped mechanic, same lifetime as the
 * timer/pvp state), so they're simply cleared on a fresh /vuhc timer start if
 * you want a clean slate - nothing here forces that automatically.
 */
public class BountyManager {

    private final VyntricUhcCore plugin;

    // target UUID -> reward pool
    private final Map<UUID, List<ItemStack>> bounties = new HashMap<>();
    private final Map<UUID, String> bountyTargetNames = new HashMap<>();

    public BountyManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("bounty.enabled", true);
    }

    // -------------------------------------------------------------------- gui

    /** Opens the bounty-setup chest for {@code opener}, targeting {@code targetName}. */
    public void openSetupGui(Player opener, UUID targetId, String targetName) {
        int rows = clampRows(plugin.getConfig().getInt("bounty.gui-rows", 3));
        String titleTemplate = plugin.getConfig().getString("bounty.gui-title", "&5Bounty on {player}");
        String title = Msg.c(titleTemplate.replace("{player}", targetName));

        BountyGuiHolder holder = new BountyGuiHolder(targetId, targetName);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);
        opener.openInventory(inv);
    }

    private int clampRows(int rows) {
        if (rows < 1) return 1;
        if (rows > 6) return 6;
        return rows;
    }

    /**
     * Called when a bounty-setup chest is closed. Collects whatever items were left
     * inside as the reward pool. Returns the number of items collected (0 = nothing
     * placed, no bounty was set/changed).
     */
    public int confirmFromGui(BountyGuiHolder holder, ItemStack[] contents, String setBy) {
        List<ItemStack> placed = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                placed.add(item.clone());
            }
        }
        if (placed.isEmpty()) return 0;

        UUID targetId = holder.getTargetId();
        String targetName = holder.getTargetName();

        boolean stack = plugin.getConfig().getBoolean("bounty.stack-bounties", true);
        List<ItemStack> pool = bounties.computeIfAbsent(targetId, k -> new ArrayList<>());
        if (!stack) pool.clear();
        pool.addAll(placed);
        bountyTargetNames.put(targetId, targetName);

        Msg.broadcast("&c&l[Bounty] &f" + targetName
                + " &7now has a bounty on their head! &7Set by &f" + setBy + "&7. Reward: &f" + describe(pool));

        return placed.size();
    }

    // ---------------------------------------------------------------- lookup

    public boolean hasBounty(UUID playerId) {
        List<ItemStack> pool = bounties.get(playerId);
        return pool != null && !pool.isEmpty();
    }

    public List<ItemStack> getBountyItems(UUID playerId) {
        return bounties.getOrDefault(playerId, java.util.Collections.emptyList());
    }

    public String describeBounty(UUID playerId) {
        List<ItemStack> pool = bounties.get(playerId);
        return pool == null || pool.isEmpty() ? "" : describe(pool);
    }

    private String describe(List<ItemStack> items) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (ItemStack item : items) {
            String name = prettyName(item);
            counts.merge(name, item.getAmount(), Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            parts.add(e.getValue() + "x " + e.getKey());
        }
        return String.join(", ", parts);
    }

    private String prettyName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String raw = item.getType().name().toLowerCase().replace('_', ' ');
        String[] words = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    // ----------------------------------------------------------------- claim

    /** Call from PlayerDeathEvent when the victim had a bounty. Pays out the killer (or drops on the ground). */
    public void claim(Player victim, Player killer) {
        List<ItemStack> pool = bounties.remove(victim.getUniqueId());
        bountyTargetNames.remove(victim.getUniqueId());
        if (pool == null || pool.isEmpty()) return;

        String rewardDesc = describe(pool);

        if (killer != null) {
            for (ItemStack item : pool) {
                Map<Integer, ItemStack> leftover = killer.getInventory().addItem(item);
                for (ItemStack over : leftover.values()) {
                    killer.getWorld().dropItemNaturally(killer.getLocation(), over);
                }
            }
            Msg.broadcast("&a&l[Bounty] &f" + killer.getName() + " &7claimed the bounty on &f" + victim.getName()
                    + " &7and received: &f" + rewardDesc);
        } else {
            Location loc = victim.getLocation();
            for (ItemStack item : pool) {
                loc.getWorld().dropItemNaturally(loc, item);
            }
            Msg.broadcast("&e&l[Bounty] &f" + victim.getName() + "&7's bounty (&f" + rewardDesc
                    + "&7) dropped on the ground - no killer to claim it.");
        }
    }

    public void clearAll() {
        bounties.clear();
        bountyTargetNames.clear();
    }
}
