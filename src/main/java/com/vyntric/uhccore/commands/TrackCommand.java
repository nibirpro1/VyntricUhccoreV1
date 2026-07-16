package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Standalone /track <team> command, kept so servers used to the old
 * Vyntric_Cross_Team_Tracker plugin don't need to change muscle memory.
 * Same data/logic as "/vuhc track <team>" — see CrossTeamManager.
 */
public class TrackCommand implements CommandExecutor {

    private final VyntricUhcCore plugin;

    public TrackCommand(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vyntricuhc.crossteam")) {
            Msg.send(sender, "&cYou don't have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            Msg.send(sender, "&cUsage: /track <team>");
            return true;
        }
        for (String line : plugin.crossTeam().report(args[0])) {
            Msg.sendRaw(sender, line);
        }
        return true;
    }
}
