package com.vyntric.uhccore;

import com.vyntric.uhccore.commands.VuhcCommand;
import com.vyntric.uhccore.commands.LoginCommand;
import com.vyntric.uhccore.commands.RegisterCommand;
import com.vyntric.uhccore.commands.TrackCommand;
import com.vyntric.uhccore.integration.VyntricPlaceholders;
import com.vyntric.uhccore.listeners.BountyListener;
import com.vyntric.uhccore.listeners.ChunkPreloadListener;
import com.vyntric.uhccore.listeners.CrossTeamListener;
import com.vyntric.uhccore.listeners.DeathmatchCampListener;
import com.vyntric.uhccore.listeners.KitListener;
import com.vyntric.uhccore.listeners.LeaveZombieListener;
import com.vyntric.uhccore.listeners.LobbyListener;
import com.vyntric.uhccore.listeners.LoginListener;
import com.vyntric.uhccore.listeners.ScatterListener;
import com.vyntric.uhccore.listeners.ScoreboardListener;
import com.vyntric.uhccore.listeners.SpectatorListener;
import com.vyntric.uhccore.listeners.StatsListener;
import com.vyntric.uhccore.managers.AltsManager;
import com.vyntric.uhccore.managers.BountyManager;
import com.vyntric.uhccore.managers.ChunkPreloadManager;
import com.vyntric.uhccore.managers.CrossTeamManager;
import com.vyntric.uhccore.managers.DeathmatchCampManager;
import com.vyntric.uhccore.managers.KitManager;
import com.vyntric.uhccore.managers.LeaveZombieManager;
import com.vyntric.uhccore.managers.LobbyManager;
import com.vyntric.uhccore.managers.LoginManager;
import com.vyntric.uhccore.managers.ScatterManager;
import com.vyntric.uhccore.managers.ScoreboardManager;
import com.vyntric.uhccore.managers.SpectatorManager;
import com.vyntric.uhccore.managers.StatsManager;
import com.vyntric.uhccore.managers.TimerManager;
import com.vyntric.uhccore.utils.ConfigValidator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * VyntricUhccoreV1
 * Branding: VyntricUhc
 *
 * All-in-one add-on for UHC-Core style servers. Adds:
 *  - /vuhc meetup <time>   -> set/change the meetup (deathmatch) timer, even mid-game
 *  - /vuhc pvp force       -> force-start PvP immediately, skipping the normal 5 min wait
 *  - /vuhc timer start|stop-> starts/stops this plugin's own timer engine
 *  - Alt account detector (IP based, alerts staff)
 *  - Simple register/login system
 *  - Cross-team ("teaming"/alliance) detector — /vuhc track <team> or /track <team>
 *    (this absorbs and replaces the old standalone Vyntric_Cross_Team_Tracker plugin;
 *    see CrossTeamManager for what changed and why)
 *  - Leave-becomes-zombie: once the game has started, a player who disconnects is
 *    replaced by a real zombie at their spot. Get killed while offline -> eliminated
 *    (dropped into spectator on rejoin). Rejoin before it's killed -> safe, teleported
 *    back. See LeaveZombieManager.
 *
 * NOTE: This runs its own independent timer + broadcast engine (it does not require
 * reflection into UHC-Core's internal classes, since those aren't guaranteed stable
 * across versions/forks). It reads/writes its own data files under the plugin folder.
 */
public class VyntricUhcCore extends JavaPlugin {

    private static VyntricUhcCore instance;

    private String prefix;
    private TimerManager timerManager;
    private AltsManager altsManager;
    private LoginManager loginManager;
    private ScoreboardManager scoreboardManager;
    private CrossTeamManager crossTeamManager;
    private LeaveZombieManager leaveZombieManager;
    private SpectatorManager spectatorManager;
    private StatsManager statsManager;
    private BountyManager bountyManager;
    private ChunkPreloadManager chunkPreloadManager;
    private ScatterManager scatterManager;
    private DeathmatchCampManager deathmatchCampManager;
    private LobbyManager lobbyManager;
    private KitManager kitManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ConfigValidator.validate(this);

        this.prefix = getConfig().getString("branding.prefix", "&d[&fVyntric&5Uhc&d] &r");

        // Register + start this first so joins are blocked from the very first tick.
        this.chunkPreloadManager = new ChunkPreloadManager(this);
        getServer().getPluginManager().registerEvents(new ChunkPreloadListener(this), this);
        this.chunkPreloadManager.start();

        this.timerManager = new TimerManager(this);
        this.altsManager = new AltsManager(this);
        this.loginManager = new LoginManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.crossTeamManager = new CrossTeamManager(this);
        this.leaveZombieManager = new LeaveZombieManager(this);
        this.spectatorManager = new SpectatorManager(this);
        this.statsManager = new StatsManager(this);
        this.bountyManager = new BountyManager(this);
        this.scatterManager = new ScatterManager(this);
        this.deathmatchCampManager = new DeathmatchCampManager(this);
        this.lobbyManager = new LobbyManager(this);
        this.kitManager = new KitManager(this);

        VuhcCommand vuhcCommand = new VuhcCommand(this);
        getCommand("vuhc").setExecutor(vuhcCommand);
        getCommand("vuhc").setTabCompleter(vuhcCommand);
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("track").setExecutor(new TrackCommand(this));

        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        getServer().getPluginManager().registerEvents(new ScoreboardListener(this), this);
        getServer().getPluginManager().registerEvents(new CrossTeamListener(this), this);
        getServer().getPluginManager().registerEvents(new LeaveZombieListener(this), this);
        getServer().getPluginManager().registerEvents(new SpectatorListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsListener(this), this);
        getServer().getPluginManager().registerEvents(new BountyListener(this), this);
        getServer().getPluginManager().registerEvents(new ScatterListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathmatchCampListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new KitListener(this), this);

        if (lobbyManager.isEnabled() && lobbyManager.isAutoBuild()) {
            lobbyManager.build();
        }

        this.scoreboardManager.start();
        this.crossTeamManager.start();
        this.deathmatchCampManager.start();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new VyntricPlaceholders(this).register();
            getLogger().info("PlaceholderAPI found - %vyntric_*% placeholders registered.");
        }

        getLogger().info("VyntricUhccoreV1 enabled — branding: VyntricUhc");
    }

    @Override
    public void onDisable() {
        if (altsManager != null) altsManager.save();
        if (loginManager != null) loginManager.save();
        if (timerManager != null) timerManager.stop();
        if (chunkPreloadManager != null) chunkPreloadManager.stop();
        if (scoreboardManager != null) scoreboardManager.stop();
        if (crossTeamManager != null) crossTeamManager.stop();
        if (deathmatchCampManager != null) deathmatchCampManager.stop();
        if (leaveZombieManager != null) leaveZombieManager.save();
        if (statsManager != null) statsManager.close();
        getLogger().info("VyntricUhccoreV1 disabled.");
    }

    public static VyntricUhcCore get() {
        return instance;
    }

    public String prefix() {
        return prefix;
    }

    public TimerManager timers() {
        return timerManager;
    }

    public AltsManager alts() {
        return altsManager;
    }

    public LoginManager login() {
        return loginManager;
    }

    public ScoreboardManager scoreboard() {
        return scoreboardManager;
    }

    public CrossTeamManager crossTeam() {
        return crossTeamManager;
    }

    public LeaveZombieManager leaveZombies() {
        return leaveZombieManager;
    }

    public SpectatorManager spectator() {
        return spectatorManager;
    }

    public StatsManager stats() {
        return statsManager;
    }

    public BountyManager bounty() {
        return bountyManager;
    }

    public ChunkPreloadManager chunkPreload() {
        return chunkPreloadManager;
    }

    public ScatterManager scatter() {
        return scatterManager;
    }

    public DeathmatchCampManager deathmatchCamp() {
        return deathmatchCampManager;
    }

    public LobbyManager lobby() {
        return lobbyManager;
    }

    public KitManager kits() {
        return kitManager;
    }
}
