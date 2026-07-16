package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class TimerManager {

    private final VyntricUhcCore plugin;

    private boolean running = false;
    private boolean pvpEnabled = false;

    private long meetupSecondsLeft;
    private long pvpSecondsLeft;

    private BukkitTask task;

    public TimerManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        this.meetupSecondsLeft = plugin.getConfig().getLong("timers.meetup-default-seconds", 1200);
        this.pvpSecondsLeft = plugin.getConfig().getLong("timers.pvp-default-seconds", 300);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public long getMeetupSecondsLeft() {
        return meetupSecondsLeft;
    }

    public long getPvpSecondsLeft() {
        return pvpSecondsLeft;
    }

    /** Starts the internal 1-second tick timer. Call this when the UHC game starts. */
    public void start() {
        if (running) return;
        running = true;
        pvpEnabled = false;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!pvpEnabled) {
            if (pvpSecondsLeft > 0) {
                pvpSecondsLeft--;
            }
            if (pvpSecondsLeft <= 0) {
                enablePvp(false);
            }
        }

        if (meetupSecondsLeft > 0) {
            meetupSecondsLeft--;
            if (meetupSecondsLeft == 0) {
                Msg.broadcast("&6Meetup/Deathmatch timer reached zero — border should now be closing in!");
            }
        }
    }

    /**
     * Sets/changes the meetup (deathmatch) timer. Works before AND after the game
     * has already started, at any point — this is the "change mid-game with a command" feature.
     */
    public void setMeetup(long seconds) {
        this.meetupSecondsLeft = Math.max(0, seconds);
    }

    public void addMeetup(long deltaSeconds) {
        this.meetupSecondsLeft = Math.max(0, this.meetupSecondsLeft + deltaSeconds);
    }

    /**
     * Force-starts PvP right now, regardless of how much time was left on the
     * normal 5 minute (configurable) pre-PvP timer.
     */
    public void forcePvp() {
        enablePvp(true);
    }

    private void enablePvp(boolean forced) {
        if (pvpEnabled) return;
        pvpEnabled = true;
        pvpSecondsLeft = 0;
        if (forced) {
            Msg.broadcast("&c&lPVP has been force-enabled by an admin!");
        } else {
            Msg.broadcast("&a&lPVP is now enabled!");
        }
    }

    public void resetPvp(long seconds) {
        this.pvpEnabled = false;
        this.pvpSecondsLeft = Math.max(0, seconds);
    }
}
