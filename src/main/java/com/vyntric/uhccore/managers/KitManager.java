package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads kits.yml (in the plugin's data folder, restyleable like scoreboard.yml)
 * and drives the lobby kit-selection menu: right-clicking the kit-selector item
 * (see LobbyManager/config.yml `lobby.kit-selector`) opens a chest GUI built
 * from this file's contents, one slot per kit — see KitListener for the click
 * handling that actually gives the items.
 */
public class KitManager {

    public static class Kit {
        public final String id;
        public final Material symbolMaterial;
        public final String symbolName;
        public final List<String> symbolLore;
        public final String permission;
        public final List<ItemStack> items;

        Kit(String id, Material symbolMaterial, String symbolName, List<String> symbolLore,
            String permission, List<ItemStack> items) {
            this.id = id;
            this.symbolMaterial = symbolMaterial;
            this.symbolName = symbolName;
            this.symbolLore = symbolLore;
            this.permission = permission == null ? "" : permission;
            this.items = items;
        }
    }

    private final VyntricUhcCore plugin;
    private File kitsFile;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, String> selected = new java.util.HashMap<>();

    public KitManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        load();
    }

    /** (Re)loads kits.yml from disk, copying the bundled default there first if it doesn't exist yet. */
    public void load() {
        kits.clear();

        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        if (!kitsFile.exists()) {
            plugin.saveResource("kits.yml", false);
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(kitsFile);
        ConfigurationSection root = cfg.getConfigurationSection("kits");
        if (root == null) {
            plugin.getLogger().info("[Kits] kits.yml has no kits defined (kits: {} or missing section).");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            ConfigurationSection symbol = section.getConfigurationSection("symbol");
            Material symbolMaterial = materialOr(symbol != null ? symbol.getString("material") : null, Material.CHEST);
            String symbolName = symbol != null ? symbol.getString("name", "&aKit " + id) : "&aKit " + id;
            List<String> symbolLore = symbol != null ? symbol.getStringList("lore") : new ArrayList<>();

            String permission = section.getString("permission", "");

            List<ItemStack> items = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("items")) {
                Object matObj = raw.get("material");
                Material mat = materialOr(matObj != null ? matObj.toString() : null, null);
                if (mat == null) {
                    plugin.getLogger().warning("[Kits] Skipping an item in kit '" + id + "' - missing/invalid material.");
                    continue;
                }
                int amount = 1;
                Object amtObj = raw.get("amount");
                if (amtObj instanceof Number) {
                    amount = Math.max(1, ((Number) amtObj).intValue());
                }
                items.add(new ItemStack(mat, amount));
            }

            kits.put(id.toLowerCase(), new Kit(id, symbolMaterial, symbolName, symbolLore, permission, items));
        }

        plugin.getLogger().info("[Kits] Loaded " + kits.size() + " kit(s) from kits.yml.");
    }

    private Material materialOr(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public boolean isEmpty() {
        return kits.isEmpty();
    }

    public Kit get(String id) {
        return kits.get(id.toLowerCase());
    }

    public Map<String, Kit> all() {
        return kits;
    }

    // ------------------------------------------------------------------ gui

    /** Opens the kit-selection chest menu for {@code player}, one slot per kit in file order. */
    public void openSelectionGui(Player player) {
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(kits.size() / 9.0)));
        KitGuiHolder holder = new KitGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Msg.c("&2Kit selection"));
        holder.setInventory(inv);

        int slot = 0;
        for (Kit kit : kits.values()) {
            if (slot >= inv.getSize()) break;

            ItemStack display = new ItemStack(kit.symbolMaterial);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Msg.c(kit.symbolName));
                List<String> lore = new ArrayList<>();
                for (String line : kit.symbolLore) {
                    lore.add(Msg.c(line));
                }
                if (!kit.permission.isEmpty() && !player.hasPermission(kit.permission)) {
                    lore.add(Msg.c("&cYou don't have the permission to use that kit"));
                }
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(slot, display);
            slot++;
        }

        player.openInventory(inv);
    }

    /** Returns the kit shown at that slot in the menu's fixed (file) order, or null. */
    public Kit kitAtSlot(int slot) {
        int i = 0;
        for (Kit kit : kits.values()) {
            if (i == slot) return kit;
            i++;
        }
        return null;
    }

    // --------------------------------------------------------------- select

    /** Gives {@code player} the kit's items (permission-checked) and remembers the pick. */
    public boolean selectKit(Player player, Kit kit) {
        if (!kit.permission.isEmpty() && !player.hasPermission(kit.permission)) {
            Msg.send(player, "&cYou don't have the permission to use that kit");
            return false;
        }

        for (ItemStack item : kit.items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack over : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), over);
            }
        }

        selected.put(player.getUniqueId(), kit.id);
        Msg.send(player, "&aYou selected the kit " + kit.id);
        return true;
    }

    public String getSelectedKit(Player player) {
        return selected.get(player.getUniqueId());
    }

    public void clearSelection(UUID playerId) {
        selected.remove(playerId);
    }
}
