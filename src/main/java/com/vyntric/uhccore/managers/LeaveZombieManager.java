package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Once the game has started (see isGameRunning()), a player who disconnects gets
 * replaced with a real Zombie standing at their exact spot, so they're not just
 * "safely gone" — someone still has to go kill it.
 *
 *  - If another player kills that zombie before the owner reconnects, the owner
 *    is marked eliminated. When they rejoin, they're dropped into spectator mode
 *    and told they died while offline.
 *  - If the owner reconnects while the zombie is still alive, the zombie is
 *    removed and they're teleported back to it, no harm done.
 *
 * Data (which zombie belongs to which player, and who's been eliminated) is kept
 * in leave-zombies.yml so it survives a server restart.
 */
public class LeaveZombieManager {

    private final VyntricUhcCore plugin;
    private final File file;
    private final YamlConfiguration data;

    // zombie entity UUID -> owning player UUID
    private final Map<UUID, UUID> zombieToPlayer = new HashMap<>();
    // player UUID -> last known zombie location (so we can teleport them back on safe rejoin)
    private final Map<UUID, Location> lastZombieLocation = new HashMap<>();
    private final Set<UUID> eliminated = new HashSet<>();

    public LeaveZombieManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "leave-zombies.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("leave-zombie.enabled", true);
    }

    /** The feature only kicks in once the game is actually running. */
    public boolean isGameRunning() {
        if (!plugin.getConfig().getBoolean("leave-zombie.require-game-running", true)) return true;
        return plugin.timers().isRunning();
    }

    // ------------------------------------------------------------------ quit

    public void turnIntoZombie(Player player) {
        Location loc = player.getLocation().clone();

        Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.ZOMBIE);
        zombie.setAdult();
        zombie.setRemoveWhenFarAway(false);
        zombie.setCanPickupItems(false);
        zombie.setPersistent(true);
        if (plugin.getConfig().getBoolean("leave-zombie.prevent-daylight-burn", true)) {
            // Paper API - stops it from dying instantly to sunlight so it can actually be hunted down
            zombie.setShouldBurnInDay(false);
        }
        if (plugin.getConfig().getBoolean("leave-zombie.show-nameplate", true)) {
            zombie.setCustomName(Msg.c("&c" + player.getName() + " &7(offline)"));
            zombie.setCustomNameVisible(true);
        }

        zombieToPlayer.put(zombie.getUniqueId(), player.getUniqueId());
        lastZombieLocation.put(player.getUniqueId(), loc);
        save();
    }

    // ----------------------------------------------------------------- death

    /** Call when a tracked zombie dies. Marks its owner eliminated. */
    public void onZombieKilled(UUID zombieUuid) {
        UUID ownerId = zombieToPlayer.remove(zombieUuid);
        if (ownerId == null) return;

        eliminated.add(ownerId);
        lastZombieLocation.remove(ownerId);
        save();

        if (plugin.getConfig().getBoolean("leave-zombie.broadcast-elimination", true)) {
            String name = Bukkit.getOfflinePlayer(ownerId).getName();
            Msg.broadcast("&c&l" + (name == null ? "A player" : name)
                    + " &fwas eliminated while offline &7(their zombie was killed)&f.");
        }
    }

    public boolean isTrackedZombie(UUID zombieUuid) {
        return zombieToPlayer.containsKey(zombieUuid);
    }

    // ------------------------------------------------------------------ join

    /** Call on PlayerJoinEvent. Handles both the "safe rejoin" and "eliminated" cases. */
    public void handleRejoin(Player player) {
        UUID id = player.getUniqueId();

        // Case 1: their zombie is still alive somewhere -> safe rejoin, clean it up.
        UUID zombieUuid = findZombieOf(id);
        if (zombieUuid != null) {
            removeZombieEntity(zombieUuid);
            zombieToPlayer.remove(zombieUuid);

            Location loc = lastZombieLocation.remove(id);
            if (loc != null && plugin.getConfig().getBoolean("leave-zombie.teleport-back-on-safe-rejoin", true)) {
                player.teleport(loc);
            }
            Msg.send(player, "&aYour zombie survived — welcome back, no harm done.");
            save();
            return;
        }

        // Case 2: they were eliminated while away.
        if (eliminated.contains(id)) {
            player.setGameMode(GameMode.SPECTATOR);
            Msg.send(player, "&cYou died while you were offline — your zombie was killed. You're now spectating.");
        }
    }

    private UUID findZombieOf(UUID playerId) {
        for (Map.Entry<UUID, UUID> e : zombieToPlayer.entrySet()) {
            if (e.getValue().equals(playerId)) return e.getKey();
        }
        return null;
    }

    private void removeZombieEntity(UUID zombieUuid) {
        org.bukkit.entity.Entity entity = Bukkit.getEntity(zombieUuid);
        if (entity != null) entity.remove();
    }

    // --------------------------------------------------------------- admin

    public boolean isEliminated(UUID playerId) {
        return eliminated.contains(playerId);
    }

    /** /vuhc revive <player> — clears the elimination flag so an admin can undo a mistake/grief. */
    public boolean revive(UUID playerId) {
        boolean was = eliminated.remove(playerId);
        save();
        return was;
    }

    // ------------------------------------------------------------- persistence

    private void load() {
        if (data.isConfigurationSection("zombies")) {
            for (String key : data.getConfigurationSection("zombies").getKeys(false)) {
                try {
                    UUID zombieUuid = UUID.fromString(key);
                    UUID ownerId = UUID.fromString(data.getString("zombies." + key + ".owner"));
                    zombieToPlayer.put(zombieUuid, ownerId);
                    String locStr = data.getString("zombies." + key + ".location");
                    Location loc = deserializeLocation(locStr);
                    if (loc != null) lastZombieLocation.put(ownerId, loc);
                } catch (IllegalArgumentException ignored) {
                    // corrupt/legacy entry, skip it
                }
            }
        }
        for (String uuid : data.getStringList("eliminated")) {
            try {
                eliminated.add(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        data.set("zombies", null);
        for (Map.Entry<UUID, UUID> e : zombieToPlayer.entrySet()) {
            String key = "zombies." + e.getKey();
            data.set(key + ".owner", e.getValue().toString());
            Location loc = lastZombieLocation.get(e.getValue());
            if (loc != null) data.set(key + ".location", serializeLocation(loc));
        }

        java.util.List<String> eliminatedList = new java.util.ArrayList<>();
        for (UUID u : eliminated) eliminatedList.add(u.toString());
        data.set("eliminated", eliminatedList);

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save leave-zombies.yml: " + e.getMessage());
        }
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location deserializeLocation(String s) {
        if (s == null) return null;
        String[] parts = s.split(",");
        if (parts.length != 6) return null;
        org.bukkit.World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        return new Location(world,
                Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
    }
}
