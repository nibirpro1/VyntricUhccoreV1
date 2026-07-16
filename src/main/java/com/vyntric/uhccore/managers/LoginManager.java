package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Very simple register/login system (AuthMe-style, minimal).
 * Passwords are hashed with SHA-256 + a per-account random salt.
 * NOTE: this is intentionally lightweight — for a serious public server,
 * a more battle-tested auth plugin is usually preferable, but this covers
 * the basics: register, login, session tracking, freeze until logged in.
 */
public class LoginManager {

    private final VyntricUhcCore plugin;
    private final File file;
    private final YamlConfiguration data;

    private final Set<UUID> loggedIn = new HashSet<>();

    public LoginManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "accounts.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("login-system.enabled", true);
    }

    public boolean isRegistered(Player player) {
        return data.contains(key(player) + ".hash");
    }

    public boolean isLoggedIn(Player player) {
        return loggedIn.contains(player.getUniqueId());
    }

    public void setLoggedIn(Player player, boolean value) {
        if (value) loggedIn.add(player.getUniqueId());
        else loggedIn.remove(player.getUniqueId());
    }

    public boolean register(Player player, String password) {
        if (isRegistered(player)) return false;
        String salt = generateSalt();
        String hash = hash(password, salt);
        data.set(key(player) + ".salt", salt);
        data.set(key(player) + ".hash", hash);
        save();
        return true;
    }

    public boolean checkPassword(Player player, String password) {
        String salt = data.getString(key(player) + ".salt");
        String storedHash = data.getString(key(player) + ".hash");
        if (salt == null || storedHash == null) return false;
        return hash(password, salt).equals(storedHash);
    }

    public int minPasswordLength() {
        return plugin.getConfig().getInt("login-system.min-password-length", 4);
    }

    public int timeoutSeconds() {
        return plugin.getConfig().getInt("login-system.timeout-seconds", 60);
    }

    public boolean freezeUntilLoggedIn() {
        return plugin.getConfig().getBoolean("login-system.freeze-until-logged-in", true);
    }

    private String key(Player p) {
        return p.getUniqueId().toString();
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(saltBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : saltBytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes());
            byte[] hashed = digest.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save accounts.yml: " + e.getMessage());
        }
    }
}
