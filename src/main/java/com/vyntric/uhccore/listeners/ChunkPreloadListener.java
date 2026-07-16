package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.managers.ChunkPreloadManager;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Kicks anyone trying to join while the world's chunks are still being
 * preloaded (see ChunkPreloadManager), so nobody spawns into a half-loaded
 * world or lags the server out mid-generation.
 */
public class ChunkPreloadListener implements Listener {

    private final VyntricUhcCore plugin;

    public ChunkPreloadListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        ChunkPreloadManager preload = plugin.chunkPreload();
        if (preload == null || !preload.isLoading()) return;

        String message = plugin.getConfig().getString(
                "chunk-preload.join-blocked-message",
                "&d[&fVyntric&5Uhc&d] &fServer is still loading the world (&e{percent}%&f). Please try again in a few minutes."
        ).replace("{percent}", String.valueOf(preload.getProgressPercent()));

        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Msg.c(message));
    }
}
