package com.vyntric.uhccore.utils;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import com.vyntric.uhccore.VyntricUhcCore;

public class Msg {

    public static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(c(VyntricUhcCore.get().prefix() + msg));
    }

    /** Like send(), but without the branding prefix — for extra lines of a multi-line report. */
    public static void sendRaw(CommandSender to, String msg) {
        to.sendMessage(c(msg));
    }

    public static void broadcast(String msg) {
        Bukkit.broadcastMessage(c(VyntricUhcCore.get().prefix() + msg));
    }

    /** Formats seconds as mm:ss or hh:mm:ss */
    public static String formatTime(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) {
            return String.format("%02d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    /** Parses "10m", "90s", "1h", "500" (plain seconds) into seconds */
    public static long parseTime(String input) throws NumberFormatException {
        input = input.trim().toLowerCase();
        if (input.matches("\\d+")) {
            return Long.parseLong(input);
        }
        long total = 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)([hms])")
                .matcher(input);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            switch (matcher.group(2)) {
                case "h": total += value * 3600; break;
                case "m": total += value * 60; break;
                case "s": total += value; break;
            }
        }
        if (!found) throw new NumberFormatException("Invalid time format: " + input);
        return total;
    }
}
