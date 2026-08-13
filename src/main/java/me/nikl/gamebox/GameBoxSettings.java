package me.nikl.gamebox;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Global, statically-accessible configuration values for GameBox.
 * Populated from config.yml during {@link GameBox#reload()}.
 */
public final class GameBoxSettings {

    // Static fields set at load time
    public static String PREFIX = ChatColor.GOLD + "[GameBox]" + ChatColor.RESET + " ";
    public static boolean economyEnabled = true;
    public static boolean vaultEnabled = true;
    public static boolean tokensEnabled = true;
    public static boolean bstatsEnabled = true;

    public static String storageType = "yaml";
    public static int autoSaveInterval = 120;
    public static boolean bungee = false;

    public static String language = "en";
    /** When true, auto-detect each player's client language via Player.getLocale(). */
    public static boolean autoDetectLanguage = false;

    public static boolean lobbyEnabled = false;
    public static java.util.List<String> lobbyWorlds = new java.util.ArrayList<>();
    public static int lobbySlot = 4;
    public static String lobbyMaterial = "NETHER_STAR";
    public static String lobbyName = "&6&lGameBox";
    public static boolean lobbyLockItem = true;

    public static int inviteExpiry = 30;
    public static String inviteStyle = "json";

    public static int mainMenuSize = 45;
    public static int backButtonSlot = 40;
    public static int closeButtonSlot = 44;

    public static boolean soundsEnabled = true;
    public static String soundClick = "UI_BUTTON_CLICK";
    public static String soundWin = "ENTITY_PLAYER_LEVELUP";
    public static String soundLose = "BLOCK_ANVIL_LAND";
    public static String soundToken = "ENTITY_EXPERIENCE_ORB_PICKUP";

    public static java.util.List<String> commandsOnEnter = new java.util.ArrayList<>();
    public static java.util.List<String> commandsOnLeave = new java.util.ArrayList<>();

    public static boolean shopEnabled = true;
    public static boolean scoreboardEnabled = true;

    private GameBoxSettings() {}

    public static void load(FileConfiguration config, LanguageManager lang) {
        PREFIX = ChatColor.translateAlternateColorCodes('&', lang.get("prefix"));

        economyEnabled = config.getBoolean("economy.tokens", true) || config.getBoolean("economy.vault", true);
        tokensEnabled = config.getBoolean("economy.tokens", true);
        vaultEnabled = config.getBoolean("economy.vault", true);
        bstatsEnabled = config.getBoolean("bstats", true);

        storageType = config.getString("storage.type", "yaml").toLowerCase();
        autoSaveInterval = config.getInt("storage.autoSaveInterval", 120);
        bungee = config.getBoolean("storage.mysql.bungee", false);

        language = config.getString("language.default", "en");
        autoDetectLanguage = config.getBoolean("language.autoDetect", false);

        lobbyEnabled = config.getBoolean("lobby.enabled", false);
        lobbyWorlds = config.getStringList("lobby.worlds");
        lobbySlot = config.getInt("lobby.slot", 4);
        lobbyMaterial = config.getString("lobby.material", "NETHER_STAR");
        lobbyName = config.getString("lobby.name", "&6&lGameBox");
        lobbyLockItem = config.getBoolean("lobby.lockItem", true);

        inviteExpiry = config.getInt("invitations.expiry", 30);
        inviteStyle = config.getString("invitations.style", "json");

        mainMenuSize = config.getInt("navigation.mainMenuSize", 45);
        backButtonSlot = config.getInt("navigation.backButtonSlot", 40);
        closeButtonSlot = config.getInt("navigation.closeButtonSlot", 44);

        soundsEnabled = config.getBoolean("sounds.enabled", true);
        soundClick = config.getString("sounds.click", "UI_BUTTON_CLICK");
        soundWin = config.getString("sounds.win", "ENTITY_PLAYER_LEVELUP");
        soundLose = config.getString("sounds.lose", "BLOCK_ANVIL_LAND");
        soundToken = config.getString("sounds.token", "ENTITY_EXPERIENCE_ORB_PICKUP");

        commandsOnEnter = config.getStringList("commands.onEnter");
        commandsOnLeave = config.getStringList("commands.onLeave");

        shopEnabled = config.getBoolean("shop.enabled", true);

        scoreboardEnabled = config.getBoolean("scoreboard.enabled", true);
    }
}
