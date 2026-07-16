package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a clean branded sidebar scoreboard + tab list (header/footer + colored
 * names) for every online player. Each player gets their own Scoreboard object
 * (so we can show personalised lines later if needed), refreshed once a second.
 */
@SuppressWarnings("deprecation")
public class ScoreboardManager {

    private final VyntricUhcCore plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();

    private BukkitTask task;

    // Server display name used on the board / tab footer
    private String serverIp;

    public ScoreboardManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        this.serverIp = plugin.getConfig().getString("branding.server-ip", "play.myserver.com");
    }

    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateBoard(p);
                    updateTab(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
    }

    public void addKill(Player killer) {
        kills.merge(killer.getUniqueId(), 1, Integer::sum);
    }

    public int getKills(Player p) {
        return kills.getOrDefault(p.getUniqueId(), 0);
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
    }

    // ---------------------------------------------------------------- sidebar

    private Scoreboard boardFor(Player p) {
        return boards.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
    }

    private void updateBoard(Player p) {
        Scoreboard board = boardFor(p);
        Objective obj = board.getObjective("vuhc_side");
        if (obj == null) {
            obj = board.registerNewObjective("vuhc_side", "dummy",
                    Msg.c("&d&lVYNTRIC &f&lUHC"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.setDisplayName(Msg.c("&d&lVYNTRIC &f&lUHC"));
        }

        // wipe old lines
        for (String entry : new java.util.ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }

        java.util.List<String> lines = buildLines(p);
        int score = lines.size();
        int blankId = 0;
        for (String line : lines) {
            String entry = line.isEmpty()
                    ? ChatColor.values()[blankId++ % ChatColor.values().length].toString()
                    : line;
            // Keep entries unique even if visible text repeats
            while (board.getEntries().contains(entry)) {
                entry = entry + ChatColor.RESET;
            }
            obj.getScore(entry).setScore(score--);
        }

        p.setScoreboard(board);
    }

    private java.util.List<String> buildLines(Player viewer) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        TimerManager t = plugin.timers();

        lines.add(" ");
        lines.add(Msg.c("&7Players: &a" + Bukkit.getOnlinePlayers().size()));

        if (t.isRunning()) {
            lines.add(Msg.c("&7Meetup: &a" + Msg.formatTime(t.getMeetupSecondsLeft())));
            lines.add(Msg.c("&7PvP: " + (t.isPvpEnabled() ? "&a&lENABLED" : "&e" + Msg.formatTime(t.getPvpSecondsLeft()))));
            lines.add(" ");
            lines.add(Msg.c("&7Kills: &a" + getKills(viewer)));
            lines.add(Msg.c("&7Alive: &a" + countAlive()));
        } else {
            lines.add(Msg.c("&7Status: &eWaiting to start"));
        }

        lines.add("  ");
        lines.add(Msg.c("&d" + serverIp));
        return lines;
    }

    private int countAlive() {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR) count++;
        }
        return count;
    }

    // -------------------------------------------------------------------- tab

    private void updateTab(Player viewer) {
        Scoreboard board = boardFor(viewer);

        Team aliveTeam = board.getTeam("vuhc_alive");
        if (aliveTeam == null) aliveTeam = board.registerNewTeam("vuhc_alive");
        aliveTeam.setPrefix(Msg.c("&a"));
        aliveTeam.setColor(ChatColor.GREEN);

        Team specTeam = board.getTeam("vuhc_spec");
        if (specTeam == null) specTeam = board.registerNewTeam("vuhc_spec");
        specTeam.setPrefix(Msg.c("&7"));
        specTeam.setColor(ChatColor.GRAY);

        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (p.getGameMode() == GameMode.SPECTATOR) {
                if (!specTeam.hasEntry(name)) {
                    removeFromOtherTeams(board, name);
                    specTeam.addEntry(name);
                }
            } else {
                if (!aliveTeam.hasEntry(name)) {
                    removeFromOtherTeams(board, name);
                    aliveTeam.addEntry(name);
                }
            }
        }

        String header = Msg.c("\n&d&lVYNTRIC &f&lUHC\n&5" + line() + "\n");
        String footer = Msg.c("\n&5" + line() + "\n&7IP: &d" + serverIp + "\n");
        viewer.setPlayerListHeaderFooter(header, footer);
    }

    private void removeFromOtherTeams(Scoreboard board, String name) {
        for (Team team : board.getTeams()) {
            if (team.hasEntry(name)) team.removeEntry(name);
        }
    }

    private String line() {
        return "▬▬▬▬▬▬▬▬▬▬▬▬▬";
    }
}
