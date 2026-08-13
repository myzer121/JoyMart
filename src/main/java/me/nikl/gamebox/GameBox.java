package me.nikl.gamebox;

import me.nikl.gamebox.commands.admin.AdminCommand;
import me.nikl.gamebox.commands.general.GeneralCommand;
import me.nikl.gamebox.data.DataBase;
import me.nikl.gamebox.data.FileDB;
import me.nikl.gamebox.data.MysqlDB;
import me.nikl.gamebox.economy.EconomyManager;
import me.nikl.gamebox.inventory.GuiManager;
import me.nikl.gamebox.input.InvitationHandler;
import me.nikl.gamebox.input.TextInputHandler;
import me.nikl.gamebox.listeners.GameBoxListener;
import me.nikl.gamebox.nms.NmsUtility;
import me.nikl.gamebox.common.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * GameBox main plugin class.
 *
 * <p>A self-contained game-lobby plugin: all games are built in and require no
 * extra modules. Players pick games from a chest GUI, earn tokens, and appear
 * on cross-server high-score tables.</p>
 */
public class GameBox extends JavaPlugin {

    private static GameBox instance;

    private LanguageManager languageManager;
    private DataBase dataBase;
    private EconomyManager economyManager;
    private GameRegistry gameRegistry;
    private GuiManager guiManager;
    private PluginManager pluginManager;
    private InvitationHandler invitationHandler;
    private TextInputHandler textInputHandler;
    private GeneralCommand generalCommand;
    private AdminCommand adminCommand;
    private Metrics metrics;
    private GameBoxAPI api;
    private boolean listenersRegistered = false;
    private me.nikl.gamebox.inventory.DeliveryBoxManager deliveryBoxManager;
    private me.nikl.gamebox.music.MusicPlayer musicPlayer;

    /** Public API accessor. */
    public GameBoxAPI getApi() {
        return api;
    }

    private FileConfiguration gamesConfig;
    private FileConfiguration shopConfig;

    @Override
    public void onEnable() {
        instance = this;
        NmsUtility.init(this);

        // 1. Load configs
        saveDefaultConfig();
        loadGamesConfig();
        loadShopConfig();

        // 2. Init language + settings
        this.languageManager = new LanguageManager(this);
        GameBoxSettings.load(getConfig(), languageManager);

        // Public API instance + services registration
        this.api = new GameBoxAPI(this);
        getServer().getServicesManager().register(
                GameBoxAPI.class, api, this, org.bukkit.plugin.ServicePriority.Normal);

        // 3. Reload wires database, manager, gui, commands, games
        reload();

        // Hook optional integrations
        hookPlaceholderAPI();
        new me.nikl.gamebox.hooks.CalendarEventsHook(this).hook();
        startBStats();

        getLogger().info("GameBox v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (pluginManager != null) {
            pluginManager.shutdown();
        }
        if (dataBase != null) {
            dataBase.shutdown();
        }
        getServer().getServicesManager().unregisterAll(this);
        instance = null;
    }

    /** Reload all configuration, storage, language, managers, commands and games. */
    public void reload() {
        // Reload config.yml from disk (merges jar defaults automatically)
        try {
            super.reloadConfig();
            loadGamesConfig();
            loadShopConfig();
            GameBoxSettings.load(getConfig(), languageManager);
            languageManager.reload();
        } catch (Exception e) {
            getLogger().severe("Error loading configuration: " + e.getMessage());
            e.printStackTrace();
        }

        // Storage
        try {
            if (dataBase != null) {
                dataBase.shutdown();
            }
            if ("mysql".equalsIgnoreCase(GameBoxSettings.storageType)) {
                dataBase = new MysqlDB(this);
                getLogger().info("Using MySQL storage.");
            } else {
                dataBase = new FileDB(this);
                getLogger().info("Using YAML file storage.");
            }
        } catch (Exception e) {
            getLogger().severe("Error initializing storage, falling back to YAML: " + e.getMessage());
            e.printStackTrace();
            try {
                dataBase = new FileDB(this);
            } catch (Exception e2) {
                getLogger().severe("YAML storage also failed: " + e2.getMessage());
            }
        }

        // Economy
        if (economyManager == null) {
            try {
                economyManager = new EconomyManager(this);
            } catch (Exception e) {
                getLogger().warning("Error initializing economy: " + e.getMessage());
            }
        }

        // Plugin manager (player + inventory + listeners)
        try {
            if (pluginManager != null) {
                pluginManager.shutdown();
            }
            pluginManager = new PluginManager(this);
        } catch (Exception e) {
            getLogger().severe("Error initializing plugin manager: " + e.getMessage());
            e.printStackTrace();
        }

        // Invitation handler
        try {
            invitationHandler = new InvitationHandler(this);
            if (pluginManager != null) {
                pluginManager.setInvitationHandler(invitationHandler);
            }
        } catch (Exception e) {
            getLogger().warning("Error initializing invitation handler: " + e.getMessage());
            e.printStackTrace();
        }

        // Text input handler (for shop item renaming, etc.)
        if (textInputHandler == null) {
            try {
                textInputHandler = new TextInputHandler(this);
            } catch (Exception e) {
                getLogger().warning("Error initializing text input handler: " + e.getMessage());
            }
        }

        // GUI manager
        try {
            guiManager = new GuiManager(this);
        } catch (Exception e) {
            getLogger().severe("Error initializing GUI manager: " + e.getMessage());
            e.printStackTrace();
        }

        // Delivery box manager (persistent item storage for shop purchases)
        if (deliveryBoxManager == null) {
            try {
                deliveryBoxManager = new me.nikl.gamebox.inventory.DeliveryBoxManager(this);
            } catch (Exception e) {
                getLogger().warning("Error initializing delivery box manager: " + e.getMessage());
            }
        }

        // Music player (background OGG/record playback)
        if (musicPlayer == null) {
            try {
                musicPlayer = new me.nikl.gamebox.music.MusicPlayer(this);
            } catch (Exception e) {
                getLogger().warning("Error initializing music player: " + e.getMessage());
            }
        }

        // Registry (reuses the instance so previously loaded games are disabled first)
        try {
            if (gameRegistry == null) {
                gameRegistry = new GameRegistry(this);
            }
            gameRegistry.reload();
        } catch (Exception e) {
            getLogger().severe("Error loading games: " + e.getMessage());
            e.printStackTrace();
        }

        // Wire listeners (only once — they delegate to the live managers)
        if (!listenersRegistered) {
            try {
                new GameBoxListener(this).register();
                listenersRegistered = true;
            } catch (Exception e) {
                getLogger().severe("Error registering listeners: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (pluginManager != null) {
            pluginManager.startAutoSave();
        }

        // Commands — always register, even if other steps failed
        registerCommands();
    }

    private void loadGamesConfig() {
        File file = new File(getDataFolder(), "games.yml");
        if (!file.exists()) {
            saveResource("games.yml", false);
        }
        this.gamesConfig = YamlConfiguration.loadConfiguration(file);
    }

    private void loadShopConfig() {
        File file = new File(getDataFolder(), "tokenShop.yml");
        if (!file.exists()) {
            saveResource("tokenShop.yml", false);
        }
        this.shopConfig = YamlConfiguration.loadConfiguration(file);
    }

    private void registerCommands() {
        generalCommand = new GeneralCommand(this);
        adminCommand = new AdminCommand(this);

        PluginCommand gameboxCmd = getCommand("gamebox");
        if (gameboxCmd != null) {
            gameboxCmd.setExecutor(generalCommand);
            gameboxCmd.setTabCompleter(generalCommand);
        } else {
            getLogger().severe("Could not register /gamebox command — missing from plugin.yml!");
        }

        PluginCommand adminCmd = getCommand("gameboxadmin");
        if (adminCmd != null) {
            adminCmd.setExecutor(adminCommand);
            adminCmd.setTabCompleter(adminCommand);
        } else {
            getLogger().severe("Could not register /gameboxadmin command — missing from plugin.yml!");
        }
    }

    private void hookPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GameBoxPlaceholders(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }
    }

    private void startBStats() {
        if (!GameBoxSettings.bstatsEnabled) return;
        if (metrics == null) {
            metrics = new Metrics(this, 4389);
        }
    }

    public static GameBox getInstance() {
        return instance;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public DataBase getDataBase() {
        return dataBase;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public GameRegistry getGameRegistry() {
        return gameRegistry;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public me.nikl.gamebox.inventory.DeliveryBoxManager getDeliveryBoxManager() {
        return deliveryBoxManager;
    }

    public me.nikl.gamebox.music.MusicPlayer getMusicPlayer() {
        return musicPlayer;
    }

    public InvitationHandler getInvitationHandler() {
        return invitationHandler;
    }

    public TextInputHandler getTextInputHandler() {
        return textInputHandler;
    }

    public FileConfiguration getGamesConfig() {
        return gamesConfig;
    }

    public FileConfiguration getShopConfig() {
        return shopConfig;
    }

    /**
     * Persist the in-memory shop config back to {@code tokenShop.yml}.
     * Called by the shop admin GUI after adding / removing items.
     */
    public void saveShopConfig() {
        if (!(shopConfig instanceof YamlConfiguration)) return;
        try {
            ((YamlConfiguration) shopConfig).save(new File(getDataFolder(), "tokenShop.yml"));
        } catch (java.io.IOException e) {
            getLogger().warning("Could not save tokenShop.yml: " + e.getMessage());
        }
    }

    /** Convenience for translating a language key. Uses active language context. */
    public String lang(String path) {
        return languageManager.get(path);
    }

    /** Convenience for translating a language key for a specific player. */
    public String lang(org.bukkit.entity.Player player, String path) {
        return languageManager.get(player, path);
    }

    /** Convenience returning a prefixed language message. */
    public String langPrefixed(String path) {
        return languageManager.getPrefixed(path);
    }

    /** Convenience returning a prefixed language message for a specific player. */
    public String langPrefixed(org.bukkit.entity.Player player, String path) {
        return languageManager.getPrefixed(player, path);
    }
}
