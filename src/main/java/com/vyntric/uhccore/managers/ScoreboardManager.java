package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a clean branded sidebar scoreboard + tab list (header/footer + colored
 * names) for every online player. Each player gets their own Scoreboard object
 * (so we can show personalised lines later if needed), refreshed once a second.
 *
 * All of the visible text lives in scoreboard.yml (in the plugin's data folder)
 * instead of being hardcoded, so server owners can restyle it and apply changes
 * live with /vuhc reload -- no restart needed.
 */
@SuppressWarnings("deprecation")
public class ScoreboardManager {

    private final VyntricUhcCore plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();

    private BukkitTask task;

    // Server display name used on the board / tab footer
    private String serverIp;

    // scoreboard.yml contents, reloaded via loadScoreboardConfig()
    private File scoreboardFile;
    private YamlConfiguration sbConfig;

    public ScoreboardManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        this.serverIp = plugin.getConfig().getString("branding.server-ip", "play.myserver.com");
        loadScoreboardConfig();
    }

    /** (Re)loads scoreboard.yml from disk, saving the bundled default first if it doesn't exist yet. */
    public void loadScoreboardConfig() {
        this.serverIp = plugin.getConfig().getString("branding.server-ip", "play.myserver.com");

        scoreboardFile = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!scoreboardFile.exists()) {
            plugin.saveResource("scoreboard.yml", false);
        }
        sbConfig = YamlConfiguration.loadConfiguration(scoreboardFile);

        // Merge in any new keys added in a plugin update without wiping the server owner's edits.
        try {
            java.io.InputStream defStream = plugin.getResource("scoreboard.yml");
            if (defStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
                sbConfig.setDefaults(defConfig);
                sbConfig.options().copyDefaults(true);
                sbConfig.save(scoreboardFile);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not re-save scoreboard.yml after merging defaults: " + e.getMessage());
        }
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
        String title = Msg.c(sbConfig.getString("sidebar.title", "&d&lVYNTRIC &f&lUHC"));

        Objective obj = board.getObjective("vuhc_side");
        if (obj == null) {
            obj = board.registerNewObjective("vuhc_side", "dummy", title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.setDisplayName(title);
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
        TimerManager t = plugin.timers();
        boolean running = t.isRunning();

        List<String> template = sbConfig.getStringList(running ? "sidebar.running-lines" : "sidebar.waiting-lines");

        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String raw : template) {
            lines.add(Msg.c(applyPlaceholders(raw, viewer, t)));
        }
        return lines;
    }

    private String applyPlaceholders(String raw, Player viewer, TimerManager t) {
        String pvp = t.isPvpEnabled()
                ? "&a&lENABLED"
                : "&e" + Msg.formatTime(t.getPvpSecondsLeft());

        return raw
                .replace("{players}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{phase}", Msg.c(t.getPhaseDisplay()))
                .replace("{meetup}", Msg.formatTime(t.getMeetupSecondsLeft()))
                .replace("{pvp}", pvp)
                .replace("{kills}", String.valueOf(getKills(viewer)))
                .replace("{alive}", String.valueOf(countAlive()))
                .replace("{server_ip}", serverIp);
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

        String aliveColor = sbConfig.getString("tab.alive-color", "&a");
        String specColor = sbConfig.getString("tab.spectator-color", "&7");

        Team aliveTeam = board.getTeam("vuhc_alive");
        if (aliveTeam == null) aliveTeam = board.registerNewTeam("vuhc_alive");
        aliveTeam.setPrefix(Msg.c(aliveColor));
        aliveTeam.setColor(colorOf(aliveColor, ChatColor.GREEN));

        Team specTeam = board.getTeam("vuhc_spec");
        if (specTeam == null) specTeam = board.registerNewTeam("vuhc_spec");
        specTeam.setPrefix(Msg.c(specColor));
        specTeam.setColor(colorOf(specColor, ChatColor.GRAY));

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

        String divider = sbConfig.getString("tab.divider", "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
        String headerTemplate = sbConfig.getString("tab.header", "&d&lVYNTRIC &f&lUHC\\n&5{line}");
        String footerTemplate = sbConfig.getString("tab.footer", "&5{line}\\n&7IP: &d{server_ip}");

        String header = Msg.c(headerTemplate.replace("\\n", "\n").replace("{line}", divider));
        String footer = Msg.c(footerTemplate.replace("\\n", "\n").replace("{line}", divider).replace("{server_ip}", serverIp));
        viewer.setPlayerListHeaderFooter("\n" + header + "\n", "\n" + footer + "\n");
    }

    private ChatColor colorOf(String legacyCode, ChatColor fallback) {
        try {
            String translated = Msg.c(legacyCode);
            if (translated.length() >= 2 && translated.charAt(0) == ChatColor.COLOR_CHAR) {
                ChatColor c = ChatColor.getByChar(translated.charAt(1));
                if (c != null) return c;
            }
        } catch (Exception ignored) {
            // fall through to fallback
        }
        return fallback;
    }

    private void removeFromOtherTeams(Scoreboard board, String name) {
        for (Team team : board.getTeams()) {
            if (team.hasEntry(name)) team.removeEntry(name);
        }
    }
}
