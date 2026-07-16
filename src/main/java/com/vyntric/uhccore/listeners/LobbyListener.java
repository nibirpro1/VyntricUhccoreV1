package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.managers.TimerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Sends players into the waiting lobby (LobbyManager) when they join while the
 * game is still in the WAITING phase, and protects the lobby's glass box from
 * being broken/built on while it's in use.
 *
 * If the login system is enabled, the actual teleport for a fresh/unauthenticated
 * player happens after a successful /login or /register instead (see
 * LoginCommand/RegisterCommand) — this listener only teleports immediately here
 * when login isn't required, or the player is already known to be logged in.
 */
public class LobbyListener implements Listener {

    private final VyntricUhcCore plugin;

    public LobbyListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.lobby().isEnabled()) return;
        if (plugin.timers().getPhase() != TimerManager.Phase.WAITING) return;
        if (!plugin.getConfig().getBoolean("lobby.teleport-on-join", true)) return;

        Player player = event.getPlayer();

        if (plugin.getConfig().getBoolean("login-system.enabled", true) && !plugin.login().isLoggedIn(player)) {
            // Not authenticated yet - LoginCommand/RegisterCommand send them to the lobby once they are.
            return;
        }

        plugin.lobby().sendToLobby(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.timers().getPhase() == TimerManager.Phase.WAITING
                && plugin.lobby().isInsideLobby(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.timers().getPhase() == TimerManager.Phase.WAITING
                && plugin.lobby().isInsideLobby(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
