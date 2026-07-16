package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The waiting lobby: a glass box built automatically at the location configured
 * under `lobby.*` in config.yml every time the plugin starts (see build(),
 * called from onEnable when lobby.auto-build is true). Players who join (or
 * finish logging in, if the login system is enabled) before the game has been
 * started with /vuhc start are teleported inside and handed the kit-selector
 * item — see LobbyListener and KitManager.
 *
 * The box footprint is `lobby.size` x `lobby.size`, glass walls `lobby.height`
 * blocks tall, with a glass floor and a glass ceiling so it's fully sealed.
 */
public class LobbyManager {

    private final VyntricUhcCore plugin;
    private Location center;

    public LobbyManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        reloadLocation();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("lobby.enabled", true);
    }

    public boolean isAutoBuild() {
        return plugin.getConfig().getBoolean("lobby.auto-build", true);
    }

    /** Re-reads world/x/y/z from config.yml. Does not rebuild the box - call build() for that. */
    public void reloadLocation() {
        String worldName = plugin.getConfig().getString("lobby.world", "");
        World world = (worldName != null && !worldName.isEmpty())
                ? Bukkit.getWorld(worldName)
                : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));

        if (world == null) {
            center = null;
            return;
        }

        double x = plugin.getConfig().getDouble("lobby.x", 0);
        double y = plugin.getConfig().getDouble("lobby.y", 100);
        double z = plugin.getConfig().getDouble("lobby.z", 0);
        center = new Location(world, x, y, z);
    }

    public Location getCenter() {
        return center;
    }

    /** Where players are actually teleported to: standing on the glass floor, centered in the box. */
    public Location getSpawnPoint() {
        if (center == null) return null;
        return center.clone().add(0.5, 1.0, 0.5);
    }

    /** Builds (or rebuilds) the glass box around the configured center. Safe to call repeatedly. */
    public void build() {
        if (!isEnabled()) return;

        if (center == null) {
            plugin.getLogger().warning("[Lobby] Could not resolve a world for the waiting lobby - "
                    + "check lobby.world in config.yml.");
            return;
        }

        int size = Math.max(3, plugin.getConfig().getInt("lobby.size", 10));
        int height = Math.max(1, plugin.getConfig().getInt("lobby.height", 4));
        Material glass = materialOr(plugin.getConfig().getString("lobby.glass-material", "GLASS"), Material.GLASS);
        Material floor = materialOr(plugin.getConfig().getString("lobby.floor-material", "GLASS"), Material.GLASS);

        World world = center.getWorld();
        int baseX = center.getBlockX() - size / 2;
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ() - size / 2;

        for (int x = 0; x <= size; x++) {
            for (int z = 0; z <= size; z++) {
                boolean edge = (x == 0 || x == size || z == 0 || z == size);

                // Floor and ceiling, sealing the box top and bottom.
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
                world.getBlockAt(baseX + x, baseY + height + 1, baseZ + z).setType(glass);

                // Walls on the edge, open air everywhere else inside.
                for (int y = 1; y <= height; y++) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(edge ? glass : Material.AIR);
                }
            }
        }

        plugin.getLogger().info("[Lobby] Waiting lobby built at " + baseX + "," + baseY + "," + baseZ
                + " (" + size + "x" + size + ", height " + height + ", world " + world.getName() + ").");
    }

    private Material materialOr(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /** Teleports a player into the lobby, resets flight/gamemode and hands them the kit-selector item. */
    public void sendToLobby(Player player) {
        if (!isEnabled()) return;

        Location spawn = getSpawnPoint();
        if (spawn == null) return;

        player.teleport(spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFallDistance(0f);

        giveKitSelector(player);
    }

    /** Gives the configured kit-selector item, skipping if the player already has one. */
    public void giveKitSelector(Player player) {
        Material mat = materialOr(plugin.getConfig().getString("lobby.kit-selector.material", "COMPASS"), Material.COMPASS);
        String coloredName = Msg.c(plugin.getConfig().getString("lobby.kit-selector.name", "&aRight click to choose a kit"));

        for (ItemStack existing : player.getInventory().getContents()) {
            if (existing != null && existing.getType() == mat && existing.hasItemMeta()
                    && coloredName.equals(existing.getItemMeta().getDisplayName())) {
                return;
            }
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(coloredName);
            item.setItemMeta(meta);
        }
        player.getInventory().addItem(item);
    }

    /** Used to protect the lobby's blocks from being broken/placed while it's in use. */
    public boolean isInsideLobby(Location loc) {
        if (!isEnabled() || center == null || loc.getWorld() == null || !loc.getWorld().equals(center.getWorld())) {
            return false;
        }

        int size = Math.max(3, plugin.getConfig().getInt("lobby.size", 10));
        int height = Math.max(1, plugin.getConfig().getInt("lobby.height", 4));
        int baseX = center.getBlockX() - size / 2;
        int baseZ = center.getBlockZ() - size / 2;
        int baseY = center.getBlockY();

        return loc.getBlockX() >= baseX && loc.getBlockX() <= baseX + size
                && loc.getBlockZ() >= baseZ && loc.getBlockZ() <= baseZ + size
                && loc.getBlockY() >= baseY && loc.getBlockY() <= baseY + height + 1;
    }
}
