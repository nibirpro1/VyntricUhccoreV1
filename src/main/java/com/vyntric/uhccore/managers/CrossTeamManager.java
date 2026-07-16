package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-team ("teaming"/alliance) detector.
 *
 * This replaces the old standalone Vyntric_Cross_Team_Tracker add-on. That version
 * only ever counted PvP hits between teams, which misses the actual thing "cross
 * teaming" means on a UHC server: two teams that agree NOT to fight each other.
 * A pair of teams that simply never engages produces zero data in a hits-only
 * tracker and is never flagged — the exact case that matters most.
 *
 * This version tracks two numbers per team-pair, every second:
 *   - proximityTicks: how long members of the two teams have spent near each other
 *   - hits: how many times they've actually damaged each other
 *
 * A pair that racks up a lot of proximity time with a very low hit ratio is
 * suspicious and gets reported to staff automatically (rate-limited so it
 * doesn't spam chat). /vuhc track <team> (and the standalone /track <team>
 * command, kept for muscle-memory from the old plugin) gives a full breakdown.
 */
public class CrossTeamManager {

    private final VyntricUhcCore plugin;

    // "teamA|teamB" (alphabetically sorted so lookup order never matters) -> stats
    private final Map<String, PairStats> stats = new HashMap<>();
    private final Map<String, Long> lastAlert = new HashMap<>();

    private BukkitTask task;

    public CrossTeamManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("cross-team-tracker.enabled", true);
    }

    public void start() {
        if (task != null || !isEnabled()) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L); // once per second
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ---------------------------------------------------------------- combat

    /** Call from the damage listener whenever one player damages another. */
    public void recordHit(Player attacker, Player victim) {
        if (!isEnabled()) return;

        Team a = teamOf(attacker);
        Team b = teamOf(victim);
        if (a == null || b == null || a.getName().equals(b.getName())) return;

        pairStats(a.getName(), b.getName()).hits++;
    }

    // ------------------------------------------------------------- proximity

    private void tick() {
        double radius = plugin.getConfig().getDouble("cross-team-tracker.proximity-radius", 15.0);
        double radiusSq = radius * radius;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < players.size(); i++) {
            Player p1 = players.get(i);
            Team t1 = teamOf(p1);
            if (t1 == null) continue;

            for (int j = i + 1; j < players.size(); j++) {
                Player p2 = players.get(j);
                Team t2 = teamOf(p2);
                if (t2 == null || t1.getName().equals(t2.getName())) continue;
                if (!p1.getWorld().equals(p2.getWorld())) continue;

                if (p1.getLocation().distanceSquared(p2.getLocation()) <= radiusSq) {
                    pairStats(t1.getName(), t2.getName()).proximityTicks++;
                }
            }
        }

        checkSuspicion();
    }

    private void checkSuspicion() {
        int minProximity = plugin.getConfig().getInt("cross-team-tracker.min-proximity-seconds", 90);
        long alertCooldownMs = plugin.getConfig().getInt("cross-team-tracker.alert-cooldown-seconds", 300) * 1000L;
        double maxHitRatio = plugin.getConfig().getDouble("cross-team-tracker.max-hit-ratio", 0.05);

        long now = System.currentTimeMillis();

        for (Map.Entry<String, PairStats> entry : stats.entrySet()) {
            PairStats s = entry.getValue();
            if (s.proximityTicks < minProximity) continue;
            if (hitRatio(s) > maxHitRatio) continue; // they're actually fighting, not suspicious

            long last = lastAlert.getOrDefault(entry.getKey(), 0L);
            if (now - last < alertCooldownMs) continue;

            lastAlert.put(entry.getKey(), now);
            alertStaff(entry.getKey(), s);
        }
    }

    private void alertStaff(String pairKey, PairStats s) {
        String[] teams = pairKey.split("\\|");
        String message = "&c&l[CrossTeam] &fTeams &e" + teams[0] + " &fand &e" + teams[1]
                + " &fhave been near each other for &e" + Msg.formatTime(s.proximityTicks)
                + " &fwith only &e" + s.hits + " &fhit(s) between them &7- possible teaming.";

        boolean staffOnline = false;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("vyntricuhc.crossteamalert")) {
                Msg.send(staff, message);
                staffOnline = true;
            }
        }
        if (!staffOnline) {
            plugin.getLogger().info("[CrossTeam] Teams " + teams[0] + " and " + teams[1]
                    + " flagged as possible teaming (" + s.hits + " hits / " + s.proximityTicks + "s proximity).");
        }
    }

    // -------------------------------------------------------------- reporting

    /** Used by both /vuhc track <team> and the standalone /track <team> command. */
    public List<String> report(String teamName) {
        List<String> lines = new ArrayList<>();

        Team team = resolveTeam(teamName);
        if (team == null) {
            lines.add("&cNo team found named &f" + teamName);
            return lines;
        }

        lines.add("&d&lCross-team report for &f" + team.getName());

        boolean any = false;
        for (Map.Entry<String, PairStats> entry : stats.entrySet()) {
            String[] teams = entry.getKey().split("\\|");
            if (!teams[0].equals(team.getName()) && !teams[1].equals(team.getName())) continue;

            String other = teams[0].equals(team.getName()) ? teams[1] : teams[0];
            PairStats s = entry.getValue();
            any = true;

            lines.add("&7 vs &e" + other + "&7: &f" + s.hits + " &7hit(s), &f"
                    + Msg.formatTime(s.proximityTicks) + " &7near each other &7(&f"
                    + String.format("%.1f%%", hitRatio(s) * 100) + "&7 hit ratio)"
                    + (isSuspicious(s) ? " &c&l<- SUSPICIOUS" : ""));
        }

        if (!any) {
            lines.add("&7No combat/proximity data recorded yet for this team.");
        }

        return lines;
    }

    public void reset() {
        stats.clear();
        lastAlert.clear();
    }

    // ------------------------------------------------------------- internals

    private boolean isSuspicious(PairStats s) {
        int minProximity = plugin.getConfig().getInt("cross-team-tracker.min-proximity-seconds", 90);
        double maxHitRatio = plugin.getConfig().getDouble("cross-team-tracker.max-hit-ratio", 0.05);
        return s.proximityTicks >= minProximity && hitRatio(s) <= maxHitRatio;
    }

    private double hitRatio(PairStats s) {
        return s.proximityTicks == 0 ? 0.0 : (s.hits / (double) s.proximityTicks);
    }

    private Team teamOf(Player p) {
        return p.getScoreboard().getPlayerTeam(p);
    }

    private Team resolveTeam(String name) {
        if (Bukkit.getScoreboardManager() == null) return null;
        return Bukkit.getScoreboardManager().getMainScoreboard().getTeam(name);
    }

    private PairStats pairStats(String a, String b) {
        String key = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
        return stats.computeIfAbsent(key, k -> new PairStats());
    }

    private static class PairStats {
        long proximityTicks = 0;
        int hits = 0;
    }
}
