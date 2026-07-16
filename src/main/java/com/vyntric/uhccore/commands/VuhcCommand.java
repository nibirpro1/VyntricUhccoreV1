package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VuhcCommand implements CommandExecutor, TabCompleter {

    private final VyntricUhcCore plugin;

    public VuhcCommand(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isAdmin = sender.hasPermission("vyntricuhc.admin");
        boolean statsOnly = args.length > 0
                && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("top"))
                && sender.hasPermission("vyntricuhc.stats");

        if (!isAdmin && !statsOnly) {
            Msg.send(sender, "&cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                return handleStart(sender);
            case "highlimit":
                return handleHighLimit(sender, args);
            case "border":
                return handleBorder(sender, args);
            case "meetup":
                return handleMeetup(sender, args);
            case "pvp":
                return handlePvp(sender, args);
            case "timer":
                return handleTimer(sender, args);
            case "alts":
                return handleAlts(sender, args);
            case "track":
                return handleTrack(sender, args);
            case "revive":
                return handleRevive(sender, args);
            case "bounty":
                return handleBounty(sender, args);
            case "stats":
                return handleStats(sender, args);
            case "top":
                return handleTop(sender, args);
            case "announcement":
            case "announce":
                return handleAnnouncement(sender, args);
            case "reload":
                plugin.reloadConfig();
                com.vyntric.uhccore.utils.ConfigValidator.validate(plugin);
                plugin.scoreboard().loadScoreboardConfig();
                plugin.deathmatchCamp().loadConfig();
                Msg.send(sender, "&aReloaded config.yml and scoreboard.yml.");
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleStart(CommandSender sender) {
        if (plugin.scatter().isScattering()) {
            Msg.send(sender, "&eA scatter/start is already in progress.");
            return true;
        }
        Msg.send(sender, "&aStarting the game — scattering all online players and starting the timers...");
        plugin.scatter().scatterAndStart();
        return true;
    }

    private boolean handleBorder(CommandSender sender, String[] args) {
        // /vuhc border set <amount>                -> instant, e.g. 200 = 200x200
        // /vuhc border set <amount> <timeduration>  -> shrinks/grows over that time
        //    <timeduration> accepts plain seconds (600) or 10m/1h/90s format
        if (args.length < 3 || !args[1].equalsIgnoreCase("set")) {
            Msg.send(sender, "&cUsage: /vuhc border set <amount> [timeduration]  (e.g. /vuhc border set 200, or /vuhc border set 200 10m)");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            Msg.send(sender, "&cInvalid amount - must be a number, e.g. /vuhc border set 200");
            return true;
        }
        if (amount <= 0) {
            Msg.send(sender, "&cAmount must be greater than 0.");
            return true;
        }

        org.bukkit.World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            Msg.send(sender, "&cCould not resolve a world to set the border on.");
            return true;
        }

        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(world.getSpawnLocation());

        if (args.length >= 4) {
            long seconds;
            try {
                seconds = Msg.parseTime(args[3]);
            } catch (NumberFormatException ex) {
                Msg.send(sender, "&cInvalid time value. Examples: 10m, 90s, 1h, or 600 (seconds).");
                return true;
            }
            border.setSize(amount, seconds);
            Msg.broadcast("&6The world border is now moving to &f" + (int) amount + "x" + (int) amount
                    + "&6 over &f" + Msg.formatTime(seconds) + "&6.");
        } else {
            border.setSize(amount);
            Msg.broadcast("&6The world border has been set to &f" + (int) amount + "x" + (int) amount + "&6.");
        }
        return true;
    }

    private boolean handleHighLimit(CommandSender sender, String[] args) {
        // /vuhc highlimit <amount>       -> set directly
        // /vuhc highlimit set <amount>   -> same thing, "set" is optional
        if (args.length < 2) {
            Msg.send(sender, "&7Current deathmatch height limit: &fY=" + plugin.deathmatchCamp().getHeightLimit());
            Msg.send(sender, "&cUsage: /vuhc highlimit <amount>  (or /vuhc highlimit set <amount>)");
            return true;
        }

        String amountArg = args[1].equalsIgnoreCase("set") && args.length >= 3 ? args[2] : args[1];

        int amount;
        try {
            amount = Integer.parseInt(amountArg);
        } catch (NumberFormatException ex) {
            Msg.send(sender, "&cInvalid amount - must be a whole number, e.g. /vuhc highlimit 90");
            return true;
        }

        plugin.deathmatchCamp().setHeightLimit(amount);
        Msg.send(sender, "&aDeathmatch height limit set to &fY=" + amount + "&a.");
        return true;
    }

    private boolean handleMeetup(CommandSender sender, String[] args) {
        // /vuhc meetup <time>          -> set to an exact value
        // /vuhc meetup add <time>      -> add time
        // /vuhc meetup remove <time>   -> subtract time
        if (args.length < 2) {
            Msg.send(sender, "&cUsage: /vuhc meetup <time|add <time>|remove <time>>  (e.g. 10m, 90s, 1h, or plain seconds)");
            return true;
        }

        try {
            if (args[1].equalsIgnoreCase("add") && args.length >= 3) {
                long delta = Msg.parseTime(args[2]);
                plugin.timers().addMeetup(delta);
                Msg.broadcast("&6The meetup timer has been increased by &f" + Msg.formatTime(delta) + "&6. New time: &f"
                        + Msg.formatTime(plugin.timers().getMeetupSecondsLeft()));
            } else if (args[1].equalsIgnoreCase("remove") && args.length >= 3) {
                long delta = Msg.parseTime(args[2]);
                plugin.timers().addMeetup(-delta);
                Msg.broadcast("&6The meetup timer has been decreased by &f" + Msg.formatTime(delta) + "&6. New time: &f"
                        + Msg.formatTime(plugin.timers().getMeetupSecondsLeft()));
            } else {
                long seconds = Msg.parseTime(args[1]);
                plugin.timers().setMeetup(seconds);
                Msg.broadcast("&6The meetup timer has been changed to &f" + Msg.formatTime(seconds) + "&6 by an admin.");
            }
        } catch (NumberFormatException ex) {
            Msg.send(sender, "&cInvalid time value. Examples: 10m, 90s, 1h, or 600 (seconds).");
        }
        return true;
    }

    private boolean handlePvp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "&cUsage: /vuhc pvp force  |  /vuhc pvp reset <time>");
            return true;
        }
        if (args[1].equalsIgnoreCase("force")) {
            if (plugin.timers().isPvpEnabled()) {
                Msg.send(sender, "&ePvP is already enabled.");
            } else {
                plugin.timers().forcePvp();
                Msg.send(sender, "&aPvP force-started.");
            }
        } else if (args[1].equalsIgnoreCase("reset") && args.length >= 3) {
            try {
                long seconds = Msg.parseTime(args[2]);
                plugin.timers().resetPvp(seconds);
                Msg.broadcast("&6The PvP timer has been reset to &f" + Msg.formatTime(seconds) + "&6 by an admin.");
            } catch (NumberFormatException ex) {
                Msg.send(sender, "&cInvalid time value.");
            }
        } else {
            Msg.send(sender, "&cUsage: /vuhc pvp force  |  /vuhc pvp reset <time>");
        }
        return true;
    }

    private boolean handleTimer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "&cUsage: /vuhc timer start|stop|status");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "start":
                plugin.timers().start();
                Msg.broadcast("&aThe UHC timer engine has started!");
                break;
            case "stop":
                plugin.timers().stop();
                Msg.send(sender, "&cTimer engine stopped.");
                break;
            case "status":
                Msg.send(sender, "&7Phase: " + plugin.timers().getPhaseDisplay()
                        + " &7| Meetup: &f" + Msg.formatTime(plugin.timers().getMeetupSecondsLeft())
                        + " &7| PvP: &f" + (plugin.timers().isPvpEnabled() ? "enabled" : Msg.formatTime(plugin.timers().getPvpSecondsLeft()))
                        + " &7| Running: &f" + plugin.timers().isRunning());
                break;
            default:
                Msg.send(sender, "&cUsage: /vuhc timer start|stop|status");
        }
        return true;
    }

    private boolean handleAlts(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Msg.send(sender, plugin.alts().listAltsOf(args[1]));
        } else {
            Msg.send(sender, "&cUsage: /vuhc alts <player>");
        }
        return true;
    }

    private boolean handleTrack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "&cUsage: /vuhc track <team>");
            return true;
        }
        for (String line : plugin.crossTeam().report(args[1])) {
            Msg.sendRaw(sender, line);
        }
        return true;
    }

    private boolean handleRevive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "&cUsage: /vuhc revive <player>");
            return true;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Msg.send(sender, "&cThat player has never joined this server.");
            return true;
        }
        boolean was = plugin.leaveZombies().revive(target.getUniqueId());
        if (was) {
            Msg.send(sender, "&aRevived &f" + args[1] + "&a. If they're online, ask them to reconnect or set their gamemode back yourself.");
        } else {
            Msg.send(sender, "&e" + args[1] + " &7wasn't marked as eliminated.");
        }
        return true;
    }

    private boolean handleBounty(CommandSender sender, String[] args) {
        if (!plugin.bounty().isEnabled()) {
            Msg.send(sender, "&cThe bounty system is disabled in config.yml.");
            return true;
        }
        if (!(sender instanceof Player)) {
            Msg.send(sender, "&cOnly a player can use /vuhc bounty - you need to place items in the chest.");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "&cUsage: /vuhc bounty <player>");
            return true;
        }

        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Msg.send(sender, "&cThat player has never joined this server.");
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : args[1];
        Player opener = (Player) sender;
        plugin.bounty().openSetupGui(opener, target.getUniqueId(), targetName);
        Msg.send(sender, "&7Place the items you want as the bounty reward, then close the chest to confirm.");
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (!plugin.stats().isAvailable()) {
            Msg.send(sender, "&cStats persistence isn't available right now (database connection failed on startup).");
            return true;
        }
        String targetName = args.length >= 2 ? args[1] : sender.getName();
        com.vyntric.uhccore.managers.StatsManager.PlayerStats stats = plugin.stats().getStats(targetName);
        if (stats == null) {
            Msg.send(sender, "&7No recorded stats for &f" + targetName + "&7 yet.");
            return true;
        }
        Msg.send(sender, "&7Stats for &f" + stats.name + "&7: &fKills: " + stats.kills
                + " &7| Deaths: &f" + stats.deaths + " &7| K/D: &f" + String.format("%.2f", stats.kdr()));
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!plugin.stats().isAvailable()) {
            Msg.send(sender, "&cStats persistence isn't available right now (database connection failed on startup).");
            return true;
        }
        String category = args.length >= 2 ? args[1].toLowerCase() : "kills";
        if (!category.equals("kills") && !category.equals("deaths") && !category.equals("kdr")) {
            Msg.send(sender, "&cUsage: /vuhc top <kills|deaths|kdr>");
            return true;
        }

        java.util.List<com.vyntric.uhccore.managers.StatsManager.PlayerStats> top = plugin.stats().getTop(category, 10);
        if (top.isEmpty()) {
            Msg.send(sender, "&7No stats recorded yet.");
            return true;
        }

        Msg.send(sender, "&d&lTop 10 &7by &f" + category + "&7:");
        int rank = 1;
        for (com.vyntric.uhccore.managers.StatsManager.PlayerStats stats : top) {
            String value = category.equals("kdr") ? String.format("%.2f", stats.kdr())
                    : category.equals("deaths") ? String.valueOf(stats.deaths) : String.valueOf(stats.kills);
            Msg.sendRaw(sender, Msg.c("&7#" + rank + " &f" + stats.name + " &7- &f" + value));
            rank++;
        }
        return true;
    }

    private boolean handleAnnouncement(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "&cUsage: /vuhc announcement <message>");
            return true;
        }

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        broadcastAnnouncement(text);
        Msg.send(sender, "&aAnnouncement sent to everyone online.");
        return true;
    }

    private void broadcastAnnouncement(String rawText) {
        String text = Msg.c(rawText);
        String border = Msg.c("&5&m----------------------------------------------------");
        String bannerTop = Msg.c("&d&l✦ &f&lV Y N T R I C &7&lUHC &d&l✦");

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(border);
        Bukkit.broadcastMessage("     " + bannerTop);
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(Msg.c("   &f" + text));
        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(border);
        Bukkit.broadcastMessage(" ");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(Msg.c("&d&lANNOUNCEMENT"), text, 10, 70, 20);
            try {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } catch (Exception ignored) {
                // sound name can differ slightly between server versions, fail silently
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        Msg.send(sender, "&d&lVyntricUhc &7commands:");
        Msg.send(sender, "&f/vuhc start &7- scatter all online players to random spots and start the game (freezes them briefly)");
        Msg.send(sender, "&f/vuhc highlimit <amount> &7- change the deathmatch height limit (Y level) on the fly");
        Msg.send(sender, "&f/vuhc border set <amount> [timeduration] &7- set the world border (centered on spawn), instantly or over time");
        Msg.send(sender, "&f/vuhc meetup <time|add <t>|remove <t>> &7- set/change meetup timer, works mid-game");
        Msg.send(sender, "&f/vuhc pvp force &7- force start PvP immediately");
        Msg.send(sender, "&f/vuhc pvp reset <time> &7- reset the pre-PvP countdown");
        Msg.send(sender, "&f/vuhc timer start|stop|status &7- control the timer engine");
        Msg.send(sender, "&f/vuhc alts <player> &7- list known alt accounts");
        Msg.send(sender, "&f/vuhc track <team> &7- cross-team (alliance) suspicion report");
        Msg.send(sender, "&f/vuhc revive <player> &7- clear an eliminated player's flag (leave-zombie system)");
        Msg.send(sender, "&f/vuhc bounty <player> &7- open a chest to place a bounty reward on a player");
        Msg.send(sender, "&f/vuhc stats [player] &7- show saved kills/deaths/K-D for a player");
        Msg.send(sender, "&f/vuhc top <kills|deaths|kdr> &7- show the leaderboard");
        Msg.send(sender, "&f/vuhc announcement <message> &7- broadcast a big announcement to everyone");
        Msg.send(sender, "&f/vuhc reload &7- reload config.yml and scoreboard.yml");
    }

    // ------------------------------------------------------------ tab complete

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "start", "highlimit", "border", "meetup", "pvp", "timer", "alts", "track", "revive", "bounty", "stats", "top", "announcement", "reload");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vyntricuhc.admin")) return java.util.Collections.emptyList();

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "highlimit":
                    return filter(Arrays.asList("set", "60", "90", "120"), args[1]);
                case "border":
                    return filter(Arrays.asList("set"), args[1]);
                case "meetup":
                    return filter(Arrays.asList("add", "remove", "10m", "5m", "1h"), args[1]);
                case "pvp":
                    return filter(Arrays.asList("force", "reset"), args[1]);
                case "timer":
                    return filter(Arrays.asList("start", "stop", "status"), args[1]);
                case "alts":
                case "revive":
                case "bounty":
                case "stats":
                    return filter(onlinePlayerNames(), args[1]);
                case "top":
                    return filter(Arrays.asList("kills", "deaths", "kdr"), args[1]);
                case "track":
                    return filter(teamNames(), args[1]);
                default:
                    return java.util.Collections.emptyList();
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("highlimit") && args[1].equalsIgnoreCase("set")) {
                return filter(Arrays.asList("60", "90", "120"), args[2]);
            }
            if (args[0].equalsIgnoreCase("border") && args[1].equalsIgnoreCase("set")) {
                return filter(Arrays.asList("100", "200", "500", "1000"), args[2]);
            }
            if (args[0].equalsIgnoreCase("meetup") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                return filter(Arrays.asList("30s", "1m", "5m", "10m"), args[2]);
            }
            if (args[0].equalsIgnoreCase("pvp") && args[1].equalsIgnoreCase("reset")) {
                return filter(Arrays.asList("60s", "5m", "10m"), args[2]);
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("border") && args[1].equalsIgnoreCase("set")) {
                return filter(Arrays.asList("5m", "10m", "30m", "1h"), args[3]);
            }
        }

        return java.util.Collections.emptyList();
    }

    private List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> teamNames() {
        if (Bukkit.getScoreboardManager() == null) return new ArrayList<>();
        return Bukkit.getScoreboardManager().getMainScoreboard().getTeams().stream()
                .map(Team::getName).collect(Collectors.toList());
    }
}
