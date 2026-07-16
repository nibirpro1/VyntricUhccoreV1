package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

public class LoginListener implements Listener {

    private final VyntricUhcCore plugin;
    private final Set<java.util.UUID> pendingKickTasks = new HashSet<>();

    public LoginListener(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // --- Alt detection ---
        InetSocketAddress addr = player.getAddress();
        if (addr != null && addr.getAddress() != null) {
            String ip = addr.getAddress().getHostAddress();
            plugin.alts().checkAndRecord(player, ip);
        }

        // --- Login enforcement ---
        if (!plugin.login().isEnabled()) return;

        plugin.login().setLoggedIn(player, false);

        if (plugin.login().isRegistered(player)) {
            Msg.send(player, "&eThis account is registered. Please /login <password> within "
                    + plugin.login().timeoutSeconds() + " seconds.");
        } else {
            Msg.send(player, "&eWelcome! Please /register <password> <confirmPassword> within "
                    + plugin.login().timeoutSeconds() + " seconds.");
        }

        int timeout = plugin.login().timeoutSeconds();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !plugin.login().isLoggedIn(player)) {
                    player.kickPlayer(Msg.c(plugin.prefix() + "&cYou took too long to log in."));
                }
            }
        }.runTaskLater(plugin, timeout * 20L);
    }

    private boolean isBlocked(Player player) {
        return plugin.login().isEnabled() && !plugin.login().isLoggedIn(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isBlocked(event.getPlayer())) return;
        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/login") || cmd.startsWith("/register")) return;
        event.setCancelled(true);
        Msg.send(event.getPlayer(), "&cYou must log in first.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (isBlocked(event.getPlayer())) {
            event.setCancelled(true);
            Msg.send(event.getPlayer(), "&cYou must log in before chatting.");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.login().freezeUntilLoggedIn()) return;
        if (isBlocked(event.getPlayer())) {
            // allow head movement, block actual position change
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isBlocked(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.login().setLoggedIn(event.getPlayer(), false);
    }
}
