package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class TimerManager {

    /**
     * The four phases a UHC game moves through, in order:
     *  WAITING       - before /vuhc start (or /vuhc timer start) has been run.
     *  GRACE_PERIOD  - game has started, but PvP is not enabled yet (the usual
     *                  few-minutes head start to spread out/gear up safely).
     *  ACTIVE        - PvP is enabled, meetup/deathmatch timer is still counting down.
     *  DEATHMATCH    - the meetup timer has hit zero; border should be closing in
     *                  and everyone is expected to fight it out.
     */
    public enum Phase {
        WAITING,
        GRACE_PERIOD,
        ACTIVE,
        DEATHMATCH
    }

    private final VyntricUhcCore plugin;

    private boolean running = false;
    private boolean pvpEnabled = false;
    private Phase phase = Phase.WAITING;

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

    public Phase getPhase() {
        return phase;
    }

    /** Colored, human-readable phase name for scoreboards/messages. */
    public String getPhaseDisplay() {
        switch (phase) {
            case WAITING: return "&eWaiting to start";
            case GRACE_PERIOD: return "&bGrind Time";
            case ACTIVE: return "&aActive";
            case DEATHMATCH: return "&c&lDEATHMATCH";
            default: return "&7Unknown";
        }
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
        setPhase(pvpSecondsLeft > 0 ? Phase.GRACE_PERIOD : Phase.ACTIVE);
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        running = false;
        phase = Phase.WAITING;
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

        if (phase != Phase.DEATHMATCH && meetupSecondsLeft > 0) {
            meetupSecondsLeft--;
            if (meetupSecondsLeft == 0) {
                setPhase(Phase.DEATHMATCH);
                Msg.broadcast("&c&lDEATHMATCH PHASE has begun! &fThe border should now be closing in - fight!");
            }
        }
    }

    /**
     * Sets/changes the meetup (deathmatch) timer. Works before AND after the game
     * has already started, at any point - this is the "change mid-game with a command" feature.
     */
    public void setMeetup(long seconds) {
        this.meetupSecondsLeft = Math.max(0, seconds);
        if (this.meetupSecondsLeft > 0 && phase == Phase.DEATHMATCH) {
            // Admin pushed the deathmatch timer back up - drop back out of deathmatch phase.
            setPhase(pvpEnabled ? Phase.ACTIVE : Phase.GRACE_PERIOD);
        }
    }

    public void addMeetup(long deltaSeconds) {
        setMeetup(this.meetupSecondsLeft + deltaSeconds);
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
        if (phase != Phase.DEATHMATCH) {
            setPhase(Phase.ACTIVE);
        }
        if (forced) {
            Msg.broadcast("&c&lPVP has been force-enabled by an admin! &fGrind time is over.");
        } else {
            Msg.broadcast("&a&lGrind time has ended - PVP is now enabled!");
        }
    }

    public void resetPvp(long seconds) {
        this.pvpEnabled = false;
        this.pvpSecondsLeft = Math.max(0, seconds);
        if (running && phase != Phase.DEATHMATCH) {
            setPhase(pvpSecondsLeft > 0 ? Phase.GRACE_PERIOD : Phase.ACTIVE);
        }
    }

    private void setPhase(Phase newPhase) {
        this.phase = newPhase;
    }
}
