package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.scheduler.BukkitTask;

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

    // Hard safety cap so an unset/huge world border (vanilla default is
    // 60,000,000 blocks -> ~14 TRILLION chunks) can't blow up the heap on
    // startup. If the border works out to more chunks than this, we refuse
    // to preload and tell the admin to set a real border first.
    private static final long MAX_PRELOAD_CHUNKS = 250_000L;

    // Vanilla's untouched default border is 60,000,000 blocks. Anything at
    // or above this threshold is treated as "nobody has set a real border
    // yet" and gets replaced with defaultBorderSize below instead of being
    // preloaded as-is.
    private static final double UNSET_BORDER_THRESHOLD = 10_000_000.0;

    private final double defaultBorderSize;

    // Lazy cursor state for walking the chunk grid without ever building
    // the full coordinate list in memory.
    private int cursorX;
    private int cursorZ;
    private int minChunkX, maxChunkX, minChunkZ, maxChunkZ;

    public ChunkPreloadManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("chunk-preload.enabled", true);
        this.worldName = plugin.getConfig().getString("chunk-preload.world", "");
        this.chunksPerTick = Math.max(1, plugin.getConfig().getInt("chunk-preload.chunks-per-tick", 20));
        this.periodTicks = Math.max(1, plugin.getConfig().getInt("chunk-preload.period-ticks", 1));
        this.defaultBorderSize = Math.max(1, plugin.getConfig().getDouble("chunk-preload.default-border-size", 3000));
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

        // If nobody has ever set a real border on this world, vanilla's
        // untouched default is 60,000,000 blocks — apply our configured
        // default instead so preload has a sane, bounded area to work with.
        if (border.getSize() >= UNSET_BORDER_THRESHOLD) {
            border.setCenter(world.getSpawnLocation());
            border.setSize(defaultBorderSize);
            plugin.getLogger().info("[ChunkPreload] No world border was set on '" + world.getName()
                    + "' — applying default border of " + (int) defaultBorderSize
                    + " blocks centered on spawn.");
        }

        double size = border.getSize();
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        double half = size / 2.0;
        minChunkX = (int) Math.floor((centerX - half) / 16.0);
        maxChunkX = (int) Math.floor((centerX + half) / 16.0);
        minChunkZ = (int) Math.floor((centerZ - half) / 16.0);
        maxChunkZ = (int) Math.floor((centerZ + half) / 16.0);

        long width = (long) maxChunkX - minChunkX + 1L;
        long depth = (long) maxChunkZ - minChunkZ + 1L;
        long computedTotal = width * depth;

        // Safety net: an unset/huge world border must never be allowed to
        // turn into a multi-trillion-entry preload — that's what was
        // crashing the server with an OutOfMemoryError on startup.
        if (computedTotal <= 0 || computedTotal > MAX_PRELOAD_CHUNKS) {
            plugin.getLogger().warning("[ChunkPreload] World border for '" + world.getName()
                    + "' is " + (int) size + " blocks wide, which works out to "
                    + computedTotal + " chunks — that's above the safety limit of "
                    + MAX_PRELOAD_CHUNKS + " and would risk crashing the server.");
            plugin.getLogger().warning("[ChunkPreload] Skipping preload. Set a real border first "
                    + "(e.g. /vuhc border <size> or /worldborder set <size>), then run /vuhc reload "
                    + "or restart the server.");
            finished = true;
            loading = false;
            return;
        }

        this.totalChunks = (int) computedTotal;
        this.loadedChunks.set(0);
        this.cursorX = minChunkX;
        this.cursorZ = minChunkZ;
        this.loading = true;
        this.finished = false;

        plugin.getLogger().info("[ChunkPreload] Starting preload of " + totalChunks
                + " chunks in world '" + world.getName() + "' (border size " + (int) size + ")...");

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int dispatched = 0;
            while (dispatched < chunksPerTick && cursorZ <= maxChunkZ) {
                int x = cursorX;
                int z = cursorZ;

                world.getChunkAtAsync(x, z, true).thenAccept(chunk -> {
                    int done = loadedChunks.incrementAndGet();
                    maybeLogProgress(done);
                    if (done >= totalChunks) {
                        onFinished(world);
                    }
                });

                cursorX++;
                if (cursorX > maxChunkX) {
                    cursorX = minChunkX;
                    cursorZ++;
                }
                dispatched++;
            }

            if (cursorZ > maxChunkZ && task != null) {
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
