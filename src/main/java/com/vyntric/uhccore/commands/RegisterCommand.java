package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {

    private final VyntricUhcCore plugin;

    public RegisterCommand(VyntricUhcCore plugin) {
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

        if (plugin.login().isRegistered(player)) {
            Msg.send(player, "&cYou are already registered. Use /login <password>.");
            return true;
        }

        if (args.length < 2) {
            Msg.send(player, "&cUsage: /register <password> <confirmPassword>");
            return true;
        }

        String pass = args[0];
        String confirm = args[1];

        if (!pass.equals(confirm)) {
            Msg.send(player, "&cPasswords do not match.");
            return true;
        }

        if (pass.length() < plugin.login().minPasswordLength()) {
            Msg.send(player, "&cPassword must be at least " + plugin.login().minPasswordLength() + " characters.");
            return true;
        }

        plugin.login().register(player, pass);
        plugin.login().setLoggedIn(player, true);
        Msg.send(player, "&aRegistration successful! You are now logged in.");
        return true;
    }
}
