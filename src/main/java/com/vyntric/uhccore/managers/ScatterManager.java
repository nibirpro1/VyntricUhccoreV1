package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the "game start" scatter: teleports every online player to a random,
 * spread-out spot inside the world border, freezes them in place for a few
 * seconds (see config `scatter.freeze-seconds`), then releases everyone at once
 * so the game actually begins fairly for everyone.
 *
 * Movement blocking during the freeze window is enforced by
 * {@link com.vyntric.uhccore.listeners.ScatterListener}.
 */
public class ScatterManager {

    private final VyntricUhcCore plugin;
    private final Random random = new Random();

    private final Set<UUID> frozen = new HashSet<>();
    private boolean scattering = false;

    public ScatterManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    public boolean isScattering() {
        return scattering;
    }

    /**
     * Scatters every online player to a random point inside the world border,
     * then freezes them for `scatter.freeze-seconds` before letting them move.
     */
    public void scatterAndStart() {
        if (scattering) {
            return;
        }

        World world = resolveWorld();
        if (world == null) {
            Bukkit.getLogger().warning("[Scatter] Could not resolve a world to scatter players in.");
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            Msg.broadcast("&cNo online players to scatter.");
            return;
        }

        scattering = true;

        int freezeSeconds = Math.max(0, plugin.getConfig().getInt("scatter.freeze-seconds", 15));
        double minDistance = plugin.getConfig().getDouble("scatter.min-distance-between-players", 100.0);
        int attemptsPerPlayer = Math.max(1, plugin.getConfig().getInt("scatter.placement-attempts", 30));

        Msg.broadcast(plugin.getConfig().getString("scatter.teleport-message",
                "&aScattering players across the map..."));

        List<Location> placed = new ArrayList<>();
        WorldBorder border = world.getWorldBorder();
        double half = border.getSize() / 2.0;
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        for (Player player : players) {
            Location spot = pickSpot(world, centerX, centerZ, half, placed, minDistance, attemptsPerPlayer);
            placed.add(spot);
            frozen.add(player.getUniqueId());
            player.teleport(spot);
        }

        String freezeMsg = plugin.getConfig().getString("scatter.freeze-message",
                "&eYou are frozen for &f{seconds}&e seconds. Get ready!");
        for (Player player : players) {
            Msg.send(player, freezeMsg.replace("{seconds}", String.valueOf(freezeSeconds)));
        }

        if (!plugin.timers().isRunning()) {
            plugin.timers().start();
        }

        if (freezeSeconds <= 0) {
            release(players);
            return;
        }

        new BukkitRunnable() {
            int secondsLeft = freezeSeconds;

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    release(players);
                    cancel();
                    return;
                }
                if (secondsLeft <= 5 || secondsLeft % 5 == 0) {
                    for (Player player : players) {
                        if (player.isOnline()) {
                            player.sendTitle(Msg.c("&e" + secondsLeft), "", 0, 25, 5);
                        }
                    }
                }
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void release(List<Player> players) {
        scattering = false;
        for (Player player : players) {
            frozen.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.sendTitle(Msg.c("&a&lGO!"), Msg.c("&fGood luck!"), 0, 30, 10);
            }
        }
        Msg.broadcast(plugin.getConfig().getString("scatter.go-message",
                "&a&lGO! &fEveryone can move now — good luck!"));

        if (!plugin.timers().isPvpEnabled()) {
            Msg.broadcast("&b&lGRIND TIME &fhas begun — PvP is off for &f"
                    + Msg.formatTime(plugin.timers().getPvpSecondsLeft()) + "&f.");
        }
    }

    /** Picks a random point inside the border, biased towards spreading out from already-placed players. */
    private Location pickSpot(World world, double centerX, double centerZ, double half,
                               List<Location> placed, double minDistance, int attempts) {
        Location best = null;
        double bestScore = -1;

        for (int i = 0; i < attempts; i++) {
            double x = centerX - half + random.nextDouble() * (half * 2);
            double z = centerZ - half + random.nextDouble() * (half * 2);

            double closest = Double.MAX_VALUE;
            for (Location other : placed) {
                double dx = other.getX() - x;
                double dz = other.getZ() - z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < closest) closest = dist;
            }
            if (placed.isEmpty()) closest = Double.MAX_VALUE;

            if (closest >= minDistance) {
                return safeLocation(world, x, z);
            }
            if (closest > bestScore) {
                bestScore = closest;
                best = safeLocation(world, x, z);
            }
        }
        // Couldn't satisfy min-distance within the attempt budget — use the best spread we found.
        return best != null ? best : safeLocation(world, centerX, centerZ);
    }

    private Location safeLocation(World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int y = world.getHighestBlockYAt(blockX, blockZ) + 1;
        return new Location(world, blockX + 0.5, y, blockZ + 0.5);
    }

    private World resolveWorld() {
        String worldName = plugin.getConfig().getString("scatter.world", "");
        if (worldName != null && !worldName.isEmpty()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) return w;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }
}
