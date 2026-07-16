package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The moment a player dies while online (a real PlayerDeathEvent - not the
 * "disconnected and their zombie got killed" case, which LeaveZombieManager
 * already forces into spectator on its own), they're switched to spectator mode
 * on respawn instead of getting a normal survival respawn. Lets eliminated
 * players keep watching the game to the end instead of just staring at a
 * respawn screen.
 */
public class SpectatorManager {

    private final VyntricUhcCore plugin;
    private final Set<UUID> pendingSpectate = ConcurrentHashMap.newKeySet();

    public SpectatorManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("spectator-mode.enabled", true);
    }

    /** Mirrors leave-zombie's own "only once the game has started" toggle. */
    public boolean isGameRunning() {
        if (!plugin.getConfig().getBoolean("spectator-mode.require-game-running", true)) return true;
        return plugin.timers().isRunning();
    }

    public boolean isSpectator(UUID playerId) {
        return pendingSpectate.contains(playerId);
    }

    /** Call from PlayerDeathEvent - marks this player to be dropped into spectator on their next respawn. */
    public void markForSpectate(Player player) {
        pendingSpectate.add(player.getUniqueId());

        if (plugin.getConfig().getBoolean("spectator-mode.broadcast-elimination", true)) {
            Msg.broadcast("&c&l" + player.getName() + " &fhas died and is now spectating.");
        }
    }

    /** Call from PlayerRespawnEvent - actually flips the gamemode if they were marked above. */
    public void applyOnRespawn(Player player) {
        if (!pendingSpectate.remove(player.getUniqueId())) return;

        player.setGameMode(GameMode.SPECTATOR);
        String message = plugin.getConfig().getString("spectator-mode.message",
                "&7You died and are now spectating. Enjoy the rest of the show!");
        Msg.send(player, message);
    }

    /** Clears the flag without applying it - used if an admin revives someone before they respawn. */
    public void clear(UUID playerId) {
        pendingSpectate.remove(playerId);
    }

    public Set<UUID> pendingUnmodifiable() {
        return Collections.unmodifiableSet(pendingSpectate);
    }
}
