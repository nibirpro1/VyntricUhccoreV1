package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists per-player kill/death stats to a real database (SQLite by default, or
 * MySQL if configured under stats-database in config.yml) so leaderboards survive
 * restarts instead of resetting every game like a plain in-memory counter would.
 *
 * Writes are dispatched off the main thread (see recordKillAsync/recordDeathAsync)
 * so a slow disk/network round trip never causes a server hitch.
 */
public class StatsManager {

    private final VyntricUhcCore plugin;
    private Connection connection;
    private boolean usingMysql;

    // lowercase name -> latest known stats. Refreshed on every write, checked before hitting the
    // DB on read - keeps frequent PlaceholderAPI/scoreboard lookups cheap.
    private final Map<String, PlayerStats> cache = new ConcurrentHashMap<>();

    public StatsManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        connect();
        createTable();
    }

    // ------------------------------------------------------------- connection

    private void connect() {
        String type = plugin.getConfig().getString("stats-database.type", "sqlite");
        usingMysql = type.equalsIgnoreCase("mysql");

        try {
            if (usingMysql) {
                String host = plugin.getConfig().getString("stats-database.mysql.host", "localhost");
                int port = plugin.getConfig().getInt("stats-database.mysql.port", 3306);
                String database = plugin.getConfig().getString("stats-database.mysql.database", "vyntricuhc");
                String user = plugin.getConfig().getString("stats-database.mysql.username", "root");
                String pass = plugin.getConfig().getString("stats-database.mysql.password", "");
                boolean useSSL = plugin.getConfig().getBoolean("stats-database.mysql.useSSL", false);

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=" + useSSL + "&autoReconnect=true&characterEncoding=utf8";
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(url, user, pass);
                plugin.getLogger().info("Stats: connected to MySQL (" + host + ":" + port + "/" + database + ").");
            } else {
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                String fileName = plugin.getConfig().getString("stats-database.sqlite.file", "stats.db");
                File file = new File(plugin.getDataFolder(), fileName);
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                plugin.getLogger().info("Stats: using SQLite (" + file.getName() + ").");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Stats: could not connect to the " + (usingMysql ? "MySQL" : "SQLite")
                    + " database - stats persistence is disabled this session. (" + e.getMessage() + ")");
            connection = null;
        }
    }

    private void createTable() {
        if (connection == null) return;
        String ddl = "CREATE TABLE IF NOT EXISTS vyntric_stats ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "name VARCHAR(16) NOT NULL, "
                + "kills INTEGER NOT NULL DEFAULT 0, "
                + "deaths INTEGER NOT NULL DEFAULT 0"
                + ")";
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(ddl);
        } catch (SQLException e) {
            plugin.getLogger().warning("Stats: failed to create table - " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return connection != null;
    }

    public void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    // ------------------------------------------------------------------ writes

    public void recordKillAsync(UUID killerUuid, String killerName) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> upsert(killerUuid, killerName, 1, 0));
    }

    public void recordDeathAsync(UUID deadUuid, String deadName) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> upsert(deadUuid, deadName, 0, 1));
    }

    private synchronized void upsert(UUID uuid, String name, int killsDelta, int deathsDelta) {
        if (connection == null) return;
        String sql = usingMysql
                ? "INSERT INTO vyntric_stats (uuid, name, kills, deaths) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), kills = kills + VALUES(kills), deaths = deaths + VALUES(deaths)"
                : "INSERT INTO vyntric_stats (uuid, name, kills, deaths) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, kills = kills + excluded.kills, deaths = deaths + excluded.deaths";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, killsDelta);
            ps.setInt(4, deathsDelta);
            ps.executeUpdate();

            PlayerStats previous = cache.get(name.toLowerCase());
            int newKills = (previous == null ? 0 : previous.kills) + killsDelta;
            int newDeaths = (previous == null ? 0 : previous.deaths) + deathsDelta;
            cache.put(name.toLowerCase(), new PlayerStats(name, newKills, newDeaths));
        } catch (SQLException e) {
            plugin.getLogger().warning("Stats: failed to record kill/death for " + name + " - " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------- reads

    public static class PlayerStats {
        public final String name;
        public final int kills;
        public final int deaths;

        public PlayerStats(String name, int kills, int deaths) {
            this.name = name;
            this.kills = kills;
            this.deaths = deaths;
        }

        public double kdr() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
    }

    /** Checks the in-memory cache first; falls back to a blocking DB read (and caches the result). */
    public PlayerStats getStats(String name) {
        PlayerStats cached = cache.get(name.toLowerCase());
        if (cached != null) return cached;

        if (connection == null) return null;
        String sql = "SELECT name, kills, deaths FROM vyntric_stats WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerStats stats = new PlayerStats(rs.getString("name"), rs.getInt("kills"), rs.getInt("deaths"));
                    cache.put(name.toLowerCase(), stats);
                    return stats;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Stats: failed to read stats for " + name + " - " + e.getMessage());
        }
        return null;
    }

    public List<PlayerStats> getTop(String orderBy, int limit) {
        List<PlayerStats> results = new ArrayList<>();
        if (connection == null) return results;

        String column;
        switch (orderBy.toLowerCase()) {
            case "deaths":
                column = "deaths";
                break;
            case "kdr":
                // avoid divide-by-zero in SQL; 0 deaths ranks by kills (same rule as kdr() above)
                column = "CASE WHEN deaths = 0 THEN kills ELSE kills * 1.0 / deaths END";
                break;
            case "kills":
            default:
                column = "kills";
                break;
        }

        String sql = "SELECT name, kills, deaths FROM vyntric_stats ORDER BY " + column + " DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new PlayerStats(rs.getString("name"), rs.getInt("kills"), rs.getInt("deaths")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Stats: failed to read leaderboard - " + e.getMessage());
        }
        return results;
    }
}
