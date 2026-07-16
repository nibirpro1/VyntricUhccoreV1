package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {

    private final VyntricUhcCore plugin;

    public LoginCommand(VyntricUhcCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;

        if (!plugin.login().isEnabled()) {
            Msg.send(player, "&cThe login system is currently disabled.");
            return true;
        }

        if (!plugin.login().isRegistered(player)) {
            Msg.send(player, "&cYou are not registered yet. Use /register <password> <confirmPassword>.");
            return true;
        }

        if (plugin.login().isLoggedIn(player)) {
            Msg.send(player, "&eYou are already logged in.");
            return true;
        }

        if (args.length < 1) {
            Msg.send(player, "&cUsage: /login <password>");
            return true;
        }

        if (plugin.login().checkPassword(player, args[0])) {
            plugin.login().setLoggedIn(player, true);
            Msg.send(player, "&aLogin successful. Welcome back!");
        } else {
            Msg.send(player, "&cIncorrect password.");
        }
        return true;
    }
}
