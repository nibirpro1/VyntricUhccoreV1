package com.vyntric.uhccore.managers;

import com.vyntric.uhccore.VyntricUhcCore;
import com.vyntric.uhccore.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Tracks which IP addresses each player UUID has joined from, stored in
 * alts.yml under the plugin data folder. On join, if the IP matches a
 * different known UUID, staff (permission vyntricuhc.altalert) get notified.
 */
public class AltsManager {

    private final VyntricUhcCore plugin;
    private final File file;
    private final YamlConfiguration data;

    // uuid(string) -> set of ip's seen
    private final Map<String, Set<String>> uuidToIps = new HashMap<>();
    // ip -> set of uuid's seen (and last known name)
    private final Map<String, Set<String>> ipToUuids = new HashMap<>();
    private final Map<String, String> uuidToLastName = new HashMap<>();

    public AltsManager(VyntricUhcCore plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "alts.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        for (String uuid : data.getKeys(false)) {
            List<String> ips = data.getStringList(uuid + ".ips");
            uuidToIps.put(uuid, new HashSet<>(ips));
            uuidToLastName.put(uuid, data.getString(uuid + ".name", "?"));
            for (String ip : ips) {
                ipToUuids.computeIfAbsent(ip, k -> new HashSet<>()).add(uuid);
            }
        }
    }

    public void save() {
        for (Map.Entry<String, Set<String>> e : uuidToIps.entrySet()) {
            data.set(e.getKey() + ".ips", new ArrayList<>(e.getValue()));
            data.set(e.getKey() + ".name", uuidToLastName.getOrDefault(e.getKey(), "?"));
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save alts.yml: " + e.getMessage());
        }
    }

    /** Call on PlayerJoinEvent. Returns list of alt player names sharing this IP, empty if none. */
    public List<String> checkAndRecord(Player player, String ip) {
        if (!plugin.getConfig().getBoolean("alts-detector.enabled", true)) return Collections.emptyList();

        String uuid = player.getUniqueId().toString();
        uuidToLastName.put(uuid, player.getName());
        uuidToIps.computeIfAbsent(uuid, k -> new HashSet<>()).add(ip);

        Set<String> sharing = ipToUuids.computeIfAbsent(ip, k -> new HashSet<>());
        List<String> altNames = new ArrayList<>();
        for (String otherUuid : sharing) {
            if (!otherUuid.equals(uuid)) {
                altNames.add(uuidToLastName.getOrDefault(otherUuid, otherUuid));
            }
        }
        sharing.add(uuid);

        if (!altNames.isEmpty() && plugin.getConfig().getBoolean("alts-detector.alert-staff", true)) {
            String names = String.join(", ", altNames);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("vyntricuhc.altalert")) {
                    Msg.send(staff, "&c&l[Alts] &f" + player.getName() + " &7shares an IP with: &f" + names);
                }
            }
        }

        return altNames;
    }

    public String listAltsOf(String playerName) {
        for (Map.Entry<String, String> e : uuidToLastName.entrySet()) {
            if (e.getValue().equalsIgnoreCase(playerName)) {
                Set<String> ips = uuidToIps.getOrDefault(e.getKey(), Collections.emptySet());
                Set<String> related = new HashSet<>();
                for (String ip : ips) {
                    for (String otherUuid : ipToUuids.getOrDefault(ip, Collections.emptySet())) {
                        if (!otherUuid.equals(e.getKey())) {
                            related.add(uuidToLastName.getOrDefault(otherUuid, otherUuid));
                        }
                    }
                }
                return related.isEmpty()
                        ? "&7No known alts for &f" + playerName
                        : "&7Known alts of &f" + playerName + "&7: &f" + String.join(", ", related);
            }
        }
        return "&7No data for &f" + playerName;
    }
}
