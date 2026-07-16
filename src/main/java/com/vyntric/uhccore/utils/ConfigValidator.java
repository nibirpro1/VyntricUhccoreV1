package com.vyntric.uhccore.utils;

import com.vyntric.uhccore.VyntricUhcCore;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Runs once in onEnable(), before any manager reads config.yml. Instead of letting a
 * typo'd or out-of-range value quietly break a manager (or throw mid-game), each
 * setting is checked here; anything invalid is corrected in-memory (not written back
 * to disk, so the admin's file isn't rewritten) and logged as a warning so it can be
 * fixed at the source.
 */
public final class ConfigValidator {

    private ConfigValidator() {
    }

    public static void validate(VyntricUhcCore plugin) {
        FileConfiguration cfg = plugin.getConfig();
        int issues = 0;

        issues += checkString(plugin, cfg, "branding.prefix", "&d[&fVyntric&5Uhc&d] &r", true);

        issues += checkLongMin(plugin, cfg, "timers.meetup-default-seconds", 1200, 0);
        issues += checkLongMin(plugin, cfg, "timers.pvp-default-seconds", 300, 0);

        issues += checkIntMin(plugin, cfg, "login-system.timeout-seconds", 60, 1);
        issues += checkIntMin(plugin, cfg, "login-system.min-password-length", 4, 1);

        issues += checkDoubleMin(plugin, cfg, "cross-team-tracker.proximity-radius", 15.0, 1.0);
        issues += checkLongMin(plugin, cfg, "cross-team-tracker.min-proximity-seconds", 90, 0);
        issues += checkDoubleRange(plugin, cfg, "cross-team-tracker.max-hit-ratio", 0.05, 0.0, 1.0);
        issues += checkLongMin(plugin, cfg, "cross-team-tracker.alert-cooldown-seconds", 300, 0);

        issues += checkOneOf(plugin, cfg, "stats-database.type", "sqlite", "sqlite", "mysql");
        issues += checkIntRange(plugin, cfg, "bounty.gui-rows", 3, 1, 6);
        issues += checkString(plugin, cfg, "bounty.gui-title", "&5Bounty on {player}", false);

        if (issues == 0) {
            plugin.getLogger().info("Config validation passed - no issues found.");
        } else {
            plugin.getLogger().warning("Config validation fixed " + issues
                    + " invalid/missing value(s) above - your config.yml on disk was left untouched, "
                    + "consider correcting it to match.");
        }
    }

    // ------------------------------------------------------------------ checks

    private static int checkString(VyntricUhcCore plugin, FileConfiguration cfg, String path, String fallback, boolean allowEmpty) {
        String value = cfg.getString(path);
        if (value == null || (!allowEmpty && value.trim().isEmpty())) {
            warn(plugin, path, value, fallback);
            cfg.set(path, fallback);
            return 1;
        }
        return 0;
    }

    private static int checkOneOf(VyntricUhcCore plugin, FileConfiguration cfg, String path, String fallback, String... allowed) {
        String value = cfg.getString(path, fallback);
        for (String option : allowed) {
            if (option.equalsIgnoreCase(value)) return 0;
        }
        warn(plugin, path, value, fallback);
        cfg.set(path, fallback);
        return 1;
    }

    private static int checkLongMin(VyntricUhcCore plugin, FileConfiguration cfg, String path, long fallback, long min) {
        if (!cfg.isSet(path)) {
            cfg.set(path, fallback);
            return 0; // missing entirely is fine, default.yml already ships this - not worth a warning
        }
        long value = cfg.getLong(path, fallback);
        if (value < min) {
            warn(plugin, path, value, fallback);
            cfg.set(path, fallback);
            return 1;
        }
        return 0;
    }

    private static int checkIntMin(VyntricUhcCore plugin, FileConfiguration cfg, String path, int fallback, int min) {
        if (!cfg.isSet(path)) {
            cfg.set(path, fallback);
            return 0;
        }
        int value = cfg.getInt(path, fallback);
        if (value < min) {
            warn(plugin, path, value, fallback);
            cfg.set(path, fallback);
            return 1;
        }
        return 0;
    }

    private static int checkIntRange(VyntricUhcCore plugin, FileConfiguration cfg, String path, int fallback, int min, int max) {
        if (!cfg.isSet(path)) {
            cfg.set(path, fallback);
            return 0;
        }
        int value = cfg.getInt(path, fallback);
        if (value < min || value > max) {
            warn(plugin, path, value, fallback);
            cfg.set(path, fallback);
            return 1;
        }
        return 0;
    }

    private static int checkDoubleMin(VyntricUhcCore plugin, FileConfiguration cfg, String path, double fallback, double min) {
        if (!cfg.isSet(path)) {
            cfg.set(path, fallback);
            return 0;
        }
        double value = cfg.getDouble(path, fallback);
        if (value < min) {
            warn(plugin, path, value, fallback);
            cfg.set(path, fallback);
            return 1;
        }
        return 0;
    }

    private static int checkDoubleRange(VyntricUhcCore plugin, FileConfiguration cfg, String path, double fallback, double min, double max) {
        if (!cfg.isSet(path)) {
            cfg.set(path, fallback);
            return 0;
        }
        double value = cfg.getDouble(path, fallback);
        if (value < min || value > max) {
            warn(plugin, path, value, fallback);
            cfg.set(path, fallback);
            return 1;
        }
        return 0;
    }

    private static void warn(VyntricUhcCore plugin, String path, Object badValue, Object fallback) {
        plugin.getLogger().warning("Invalid config value at '" + path + "' (" + badValue
                + ") - falling back to " + fallback + " for this session.");
    }
}
