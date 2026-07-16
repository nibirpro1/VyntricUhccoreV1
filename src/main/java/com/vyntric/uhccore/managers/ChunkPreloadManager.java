package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads/generates every chunk inside the world border on server start, so the
 * whole area the UHC border will ever shrink through is already generated
 * before anyone plays (no lag spikes from on-the-fly generation later, and no
 * players wandering into ungenerated terrain mid-game).
 *
 * While this is running, {@link com.vyntric.uhccore.listeners.ChunkPreloadListener}
 * blocks logins with a "still loading, try again in a bit" message.
 */
public class ChunkPreloadManager {

    private final VyntricUhcCore plugin;

    private boolean enabled;
    private String worldName;
    private int chunksPerTick;
    private int periodTicks;

    private volatile boolean loading = false;
    private volatile boolean finished = false;

    private int totalChunks = 0;
    private final AtomicInteger loadedChunks = new AtomicInteger(0);
    private int lastLoggedPercent = -1;

    private BukkitTask task;

    public ChunkPreloadManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("chunk-preload.enabled", true);
        this.worldName = plugin.getConfig().getString("chunk-preload.world", "");
        this.chunksPerTick = Math.max(1, plugin.getConfig().getInt("chunk-preload.chunks-per-tick", 20));
        this.periodTicks = Math.max(1, plugin.getConfig().getInt("chunk-preload.period-ticks", 1));
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isFinished() {
        return finished;
    }

    public int getProgressPercent() {
        if (totalChunks <= 0) return 100;
        return (int) Math.min(100, Math.round((loadedChunks.get() * 100.0) / totalChunks));
    }

    /** Kicks off the preload. Safe to call even if disabled (it just no-ops). */
    public void start() {
        if (!enabled) {
            finished = true;
            return;
        }

        World world = resolveWorld();
        if (world == null) {
            plugin.getLogger().warning("[ChunkPreload] Could not resolve a world to preload — skipping.");
            finished = true;
            return;
        }

        WorldBorder border = world.getWorldBorder();
        double size = border.getSize();
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        double half = size / 2.0;
        int minChunkX = (int) Math.floor((centerX - half) / 16.0);
        int maxChunkX = (int) Math.floor((centerX + half) / 16.0);
        int minChunkZ = (int) Math.floor((centerZ - half) / 16.0);
        int maxChunkZ = (int) Math.floor((centerZ + half) / 16.0);

        Deque<long[]> queue = new ArrayDeque<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                queue.add(new long[]{x, z});
            }
        }

        this.totalChunks = queue.size();
        this.loadedChunks.set(0);
        this.loading = true;
        this.finished = false;

        plugin.getLogger().info("[ChunkPreload] Starting preload of " + totalChunks
                + " chunks in world '" + world.getName() + "' (border size " + (int) size + ")...");

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int dispatched = 0;
            while (dispatched < chunksPerTick && !queue.isEmpty()) {
                long[] coords = queue.poll();
                int x = (int) coords[0];
                int z = (int) coords[1];
                world.getChunkAtAsync(x, z, true).thenAccept(chunk -> {
                    int done = loadedChunks.incrementAndGet();
                    maybeLogProgress(done);
                    if (done >= totalChunks) {
                        onFinished(world);
                    }
                });
                dispatched++;
            }

            if (queue.isEmpty() && task != null) {
                task.cancel();
                task = null;
            }
        }, 0L, periodTicks);
    }

    private void maybeLogProgress(int done) {
        int percent = totalChunks <= 0 ? 100 : (int) ((done * 100.0) / totalChunks);
        if (percent != lastLoggedPercent && percent % 10 == 0) {
            lastLoggedPercent = percent;
            plugin.getLogger().info("[ChunkPreload] " + percent + "% (" + done + "/" + totalChunks + ")");
        }
    }

    private void onFinished(World world) {
        if (finished) return;
        finished = true;
        loading = false;
        plugin.getLogger().info("[ChunkPreload] Finished preloading '" + world.getName()
                + "' — players can now join.");
    }

    private World resolveWorld() {
        if (worldName != null && !worldName.isEmpty()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) return w;
            plugin.getLogger().warning("[ChunkPreload] World '" + worldName + "' not found, falling back to the default world.");
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
