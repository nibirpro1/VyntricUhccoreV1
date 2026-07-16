package com.vyntric.uhccore.integration;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.managers.StatsManager;
import com.vyntric.uhccore.utils.Msg;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Registers %vyntric_*% placeholders for use in other plugins' scoreboards, tablists,
 * chat formats, etc. Only loaded/registered if PlaceholderAPI is actually installed -
 * see VyntricUhcCore#onEnable, which checks for the plugin before touching this class.
 *
 * Available placeholders:
 *   %vyntric_phase%              -> waiting | meetup | pvp
 *   %vyntric_meetup_time%        -> seconds left on the meetup timer, raw number
 *   %vyntric_meetup_time_formatted% -> mm:ss / hh:mm:ss
 *   %vyntric_pvp_time%           -> seconds left before pvp, raw number (0 once enabled)
 *   %vyntric_pvp_time_formatted% -> mm:ss / hh:mm:ss, or "Enabled" once pvp is on
 *   %vyntric_pvp_status%         -> "Enabled" or "Disabled"
 *   %vyntric_kills%              -> the placeholder-target player's saved kill count
 *   %vyntric_deaths%             -> the placeholder-target player's saved death count
 *   %vyntric_kdr%                -> the placeholder-target player's K/D ratio, 2dp
 *   %vyntric_bounty%             -> item summary of the target player's bounty, or "" if none
 *   %vyntric_has_bounty%         -> "Yes" or "No"
 */
public class VyntricPlaceholders extends PlaceholderExpansion {

    private final VyntricUhcCore plugin;

    public VyntricPlaceholders(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "vyntric";
    }

    @Override
    public String getAuthor() {
        return "VyntricUhc";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Keeps the expansion registered across /papi reload without needing to re-add it manually. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(org.bukkit.entity.Player player, String params) {
        switch (params.toLowerCase()) {
            case "phase":
                return phase();
            case "meetup_time":
                return String.valueOf(plugin.timers().getMeetupSecondsLeft());
            case "meetup_time_formatted":
                return Msg.formatTime(plugin.timers().getMeetupSecondsLeft());
            case "pvp_time":
                return String.valueOf(plugin.timers().getPvpSecondsLeft());
            case "pvp_time_formatted":
                return plugin.timers().isPvpEnabled() ? "Enabled" : Msg.formatTime(plugin.timers().getPvpSecondsLeft());
            case "pvp_status":
                return plugin.timers().isPvpEnabled() ? "Enabled" : "Disabled";
            case "kills":
                return statOf(player, "kills");
            case "deaths":
                return statOf(player, "deaths");
            case "kdr":
                return statOf(player, "kdr");
            case "bounty":
                return player == null ? "" : plugin.bounty().describeBounty(player.getUniqueId());
            case "has_bounty":
                return player != null && plugin.bounty().hasBounty(player.getUniqueId()) ? "Yes" : "No";
            default:
                return null;
        }
    }

    private String phase() {
        if (!plugin.timers().isRunning()) return "waiting";
        return plugin.timers().isPvpEnabled() ? "pvp" : "meetup";
    }

    private String statOf(OfflinePlayer player, String field) {
        if (player == null || player.getName() == null || !plugin.stats().isAvailable()) return "0";
        StatsManager.PlayerStats stats = plugin.stats().getStats(player.getName());
        if (stats == null) return field.equals("kdr") ? "0.00" : "0";
        switch (field) {
            case "kills":
                return String.valueOf(stats.kills);
            case "deaths":
                return String.valueOf(stats.deaths);
            case "kdr":
                return String.format("%.2f", stats.kdr());
            default:
                return "0";
        }
    }
}
