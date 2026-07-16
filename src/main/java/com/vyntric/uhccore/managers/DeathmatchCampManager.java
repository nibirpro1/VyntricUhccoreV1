package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Once the game enters {@link TimerManager.Phase#DEATHMATCH}, this stops
 * players from camping above a configured height (building a sky base to
 * dodge the fight): anyone above deathmatch.height-limit takes damage every
 * second until they come back down. Placing blocks above that height during
 * deathmatch is blocked separately by {@link com.vyntric.uhccore.listeners.DeathmatchCampListener}.
 */
public class DeathmatchCampManager {

    private final VyntricUhcCore plugin;

    private boolean enabled;
    private int heightLimit;
    private double damagePerSecond;
    private String warningMessage;
    private String blockPlaceMessage;

    private BukkitTask task;

    public DeathmatchCampManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /** Re-reads settings from config.yml — safe to call from /vuhc reload. */
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("deathmatch.enabled", true);
        this.heightLimit = plugin.getConfig().getInt("deathmatch.height-limit", 90);
        this.damagePerSecond = plugin.getConfig().getDouble("deathmatch.damage-per-second", 2.0);
        this.warningMessage = plugin.getConfig().getString("deathmatch.warning-message",
                "&c&lYou are too high! &fCome down or you will keep taking damage.");
        this.blockPlaceMessage = plugin.getConfig().getString("deathmatch.block-place-blocked-message",
                "&cYou can't build above Y={limit} during deathmatch.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getHeightLimit() {
        return heightLimit;
    }

    /** Changes the deathmatch height limit on the fly (e.g. via /vuhc highlimit set <amount>). */
    public void setHeightLimit(int newLimit) {
        this.heightLimit = newLimit;
    }

    public String getBlockPlaceMessage() {
        return blockPlaceMessage.replace("{limit}", String.valueOf(heightLimit));
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!enabled) return;
        if (plugin.timers().getPhase() != TimerManager.Phase.DEATHMATCH) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            if (p.getLocation().getY() > heightLimit) {
                if (damagePerSecond > 0 && p.getHealth() > 0) {
                    double newHealth = Math.max(0, p.getHealth() - damagePerSecond);
                    p.setHealth(newHealth);
                }
                Msg.send(p, warningMessage);
            }
        }
    }
}
