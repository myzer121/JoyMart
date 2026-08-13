package me.nikl.gamebox;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-language manager that loads ALL available language files at startup
 * and resolves strings per-player based on their client locale.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>{@link #langCaches} — one cache per language code ("en", "zh", "de", "es").
 *       Each cache holds both the main language keys AND game-specific keys
 *       (namespaced as {@code games.<gameId>.<key>}).</li>
 *   <li>{@link #playerLanguages} — per-player language override. Populated
 *       by {@link #detectLanguage(Player)} when autoDetect is enabled.</li>
 *   <li>{@link #activeLang} — the language used by the no-arg {@code get(path)}
 *       methods. Set to a player's language before building their GUI, then
 *       restored afterwards. This avoids changing every call site.</li>
 * </ul>
 *
 * <h3>Mixing bug fix</h3>
 * <p>The old single-cache design caused language mixing: when the configured
 * language changed, game-specific keys from the old language remained in the
 * cache because {@code mergeGameLanguage()} only added keys, never removed
 * old ones. The multi-cache design eliminates this — each language has its
 * own isolated cache, so switching is a pointer swap, not a mutation.</p>
 */
public class LanguageManager {

    private final GameBox plugin;

    /** Supported language codes and their display names. */
    private static final String[] SUPPORTED = {"en", "zh", "de", "es"};

    /** Per-language caches: lang code -> (key -> value). */
    private final Map<String, Map<String, Object>> langCaches = new ConcurrentHashMap<>();

    /** Per-language list caches: lang code -> (key -> immutable list). */
    private final Map<String, Map<String, List<String>>> langListCaches = new ConcurrentHashMap<>();

    /** Per-player language override: player UUID -> lang code. */
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();

    /** The default language (from config.yml {@code language.default}). */
    private String defaultLang = "en";

    /** The currently active language for the no-arg get() methods.
     *  Set via {@link #setActiveLanguage(String)} before building a GUI
     *  for a specific player, and restored via {@link #resetActiveLanguage()}. */
    private String activeLang = "en";

    /** English defaults loaded from the JAR (used as the ultimate fallback). */
    private FileConfiguration defaultConfig;

    public LanguageManager(GameBox plugin) {
        this.plugin = plugin;
        loadDefaults();
        reload();
    }

    /** Load the bundled English resource as the ultimate fallback. */
    private void loadDefaults() {
        try (Reader reader = new InputStreamReader(
                plugin.getResource("language/language_en.yml"), StandardCharsets.UTF_8)) {
            this.defaultConfig = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default language file: " + e.getMessage());
            this.defaultConfig = new YamlConfiguration();
        }
    }

    /** Reload all language caches. Loads every supported language. */
    public void reload() {
        langCaches.clear();
        langListCaches.clear();

        defaultLang = normalizeLang(GameBoxSettings.language);
        activeLang = defaultLang;

        // Load each supported language
        for (String lang : SUPPORTED) {
            loadLanguageCache(lang);
        }
    }

    /** Load a single language's main + game-specific strings into its cache. */
    private void loadLanguageCache(String lang) {
        Map<String, Object> cache = new HashMap<>();
        Map<String, List<String>> listCache = new HashMap<>();

        // 1. Load main language file
        FileConfiguration mainCfg = loadLanguageFile("language/language_" + lang + ".yml");
        for (String key : mainCfg.getKeys(true)) {
            cache.put(key, mainCfg.get(key));
        }

        // 2. Fill missing keys from English defaults
        for (String key : defaultConfig.getKeys(true)) {
            cache.putIfAbsent(key, defaultConfig.get(key));
        }

        // 3. Pre-parse all list values into the list cache
        for (Map.Entry<String, Object> entry : cache.entrySet()) {
            if (entry.getValue() instanceof List) {
                List<String> parsed = new ArrayList<>();
                for (Object item : (List<?>) entry.getValue()) {
                    parsed.add(item.toString());
                }
                listCache.put(entry.getKey(), Collections.unmodifiableList(parsed));
            }
        }

        langCaches.put(lang, cache);
        langListCaches.put(lang, listCache);
    }

    /** Load a YAML file from disk, falling back to the JAR resource. */
    private FileConfiguration loadLanguageFile(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists() && plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
        }
        FileConfiguration cfg;
        if (file.exists()) {
            cfg = YamlConfiguration.loadConfiguration(file);
        } else {
            // Try loading from JAR
            try (Reader reader = new InputStreamReader(
                    plugin.getResource(resourcePath), StandardCharsets.UTF_8)) {
                cfg = YamlConfiguration.loadConfiguration(reader);
            } catch (Exception e) {
                cfg = new YamlConfiguration();
            }
        }
        // Merge JAR defaults for missing keys
        try (Reader jarReader = new InputStreamReader(
                plugin.getResource(resourcePath), StandardCharsets.UTF_8)) {
            if (jarReader != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(jarReader));
            }
        } catch (Exception ignored) {}
        return cfg;
    }

    /** Normalize a locale string (e.g., "zh_CN", "en_US") to a supported lang code. */
    public static String normalizeLang(String input) {
        if (input == null || input.isEmpty()) return "en";
        String lower = input.toLowerCase().replace("_", "-");
        // Check exact match first
        for (String supported : SUPPORTED) {
            if (lower.equals(supported)) return supported;
        }
        // Check prefix (e.g., "zh-cn" -> "zh", "en-us" -> "en")
        String prefix = lower.split("-")[0];
        for (String supported : SUPPORTED) {
            if (prefix.equals(supported)) return supported;
        }
        return "en";
    }

    /**
     * Detect a player's language from their client locale.
     * Called on join and when autoDetect is enabled.
     */
    public String detectLanguage(Player player) {
        if (player == null) return defaultLang;
        try {
            String locale = player.getLocale(); // e.g., "en_US", "zh_CN"
            return normalizeLang(locale);
        } catch (Throwable t) {
            // Some older server versions might not support getLocale()
            return defaultLang;
        }
    }

    /** Set a player's language override. */
    public void setPlayerLanguage(UUID uuid, String lang) {
        String normalized = normalizeLang(lang);
        playerLanguages.put(uuid, normalized);
    }

    /** Get a player's language, detecting it if not yet set. */
    public String getPlayerLanguage(UUID uuid) {
        String lang = playerLanguages.get(uuid);
        if (lang != null) return lang;
        return defaultLang;
    }

    /** Get a player's language, detecting from their client if autoDetect is on. */
    public String getPlayerLanguage(Player player) {
        if (player == null) return defaultLang;
        UUID uuid = player.getUniqueId();
        String lang = playerLanguages.get(uuid);
        if (lang != null) return lang;
        // Auto-detect if enabled
        if (GameBoxSettings.autoDetectLanguage) {
            lang = detectLanguage(player);
            playerLanguages.put(uuid, lang);
            return lang;
        }
        return defaultLang;
    }

    /** Remove a player's language entry (on quit). */
    public void removePlayer(UUID uuid) {
        playerLanguages.remove(uuid);
    }

    // ---- Active language context ----

    /** Set the active language for no-arg get() calls. Call before building
     *  a GUI for a specific player, then call {@link #resetActiveLanguage()}. */
    public void setActiveLanguage(String lang) {
        this.activeLang = normalizeLang(lang);
    }

    /** Set the active language to match a specific player. */
    public void setActiveLanguage(Player player) {
        this.activeLang = getPlayerLanguage(player);
    }

    /** Reset the active language back to the configured default. */
    public void resetActiveLanguage() {
        this.activeLang = defaultLang;
    }

    public String getActiveLanguage() {
        return activeLang;
    }

    public String getDefaultLanguage() {
        return defaultLang;
    }

    // ---- String resolution ----

    /** Resolve a key using the currently active language. */
    public String get(String path) {
        return resolve(activeLang, path);
    }

    /** Resolve a key for a specific player. Does NOT change activeLang. */
    public String get(Player player, String path) {
        return resolve(getPlayerLanguage(player), path);
    }

    /** Resolve a key for a specific language code. */
    private String resolve(String lang, String path) {
        Map<String, Object> cache = langCaches.get(lang);
        if (cache == null) cache = langCaches.get("en");
        if (cache == null) return path;
        Object o = cache.get(path);
        if (o == null) {
            // Try English as fallback
            if (!lang.equals("en")) {
                Map<String, Object> enCache = langCaches.get("en");
                if (enCache != null) {
                    o = enCache.get(path);
                }
            }
            if (o == null) return path;
        }
        return o.toString();
    }

    public String getPrefixed(String path) {
        return ChatColor.translateAlternateColorCodes('&',
                GameBoxSettings.PREFIX + get(path));
    }

    public String getPrefixed(Player player, String path) {
        return ChatColor.translateAlternateColorCodes('&',
                GameBoxSettings.PREFIX + get(player, path));
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String path) {
        return resolveList(activeLang, path);
    }

    public List<String> getList(Player player, String path) {
        return resolveList(getPlayerLanguage(player), path);
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveList(String lang, String path) {
        Map<String, List<String>> listCache = langListCaches.get(lang);
        if (listCache == null) listCache = langListCaches.get("en");
        if (listCache == null) return Collections.emptyList();

        List<String> cached = listCache.get(path);
        if (cached != null) return cached;

        // Build and cache the list
        Map<String, Object> cache = langCaches.get(lang);
        if (cache == null) cache = langCaches.get("en");
        if (cache == null) return Collections.emptyList();

        Object o = cache.get(path);
        List<String> result = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List<?>) o) {
                result.add(item.toString());
            }
        } else if (o != null) {
            result.add(o.toString());
        }
        List<String> immutable = Collections.unmodifiableList(result);
        listCache.put(path, immutable);
        return immutable;
    }

    // ---- Game-specific language merging ----

    /**
     * Merge a game-specific language file into the appropriate language cache.
     * Keys are namespaced as {@code games.<gameId>.<key>}.
     *
     * <p>This method is called by {@link me.nikl.gamebox.game.Game#loadGameLanguage()}
     * for each game. The {@code config} parameter contains the game's language
     * strings already loaded from the correct language file.</p>
     *
     * @param gameId  the game identifier (e.g., "monopoly")
     * @param config  the loaded game-specific language configuration
     * @param lang    the language code this config belongs to
     */
    public void mergeGameLanguage(String gameId, FileConfiguration config, String lang) {
        String prefix = "games." + gameId + ".";
        Map<String, Object> cache = langCaches.get(lang);
        if (cache == null) return;

        // Remove old game-specific keys first to prevent mixing
        String removePrefix = prefix;
        cache.entrySet().removeIf(e -> e.getKey().startsWith(removePrefix));

        // Merge new keys
        for (String key : config.getKeys(true)) {
            String fullKey = prefix + key;
            cache.put(fullKey, config.get(key));
            // Invalidate list cache for this key
            Map<String, List<String>> listCache = langListCaches.get(lang);
            if (listCache != null) {
                listCache.remove(fullKey);
            }
        }
    }

    /**
     * Backward-compatible merge: merges into ALL language caches.
     * Used by older game code that doesn't pass a language parameter.
     *
     * @deprecated Use {@link #mergeGameLanguage(String, FileConfiguration, String)} instead.
     */
    @Deprecated
    public void mergeGameLanguage(String gameId, FileConfiguration config) {
        // Merge into the active language cache
        mergeGameLanguage(gameId, config, activeLang);
    }

    /** Old-style set method for backward compatibility. */
    public void set(String path, Object value) {
        Map<String, Object> cache = langCaches.get(activeLang);
        if (cache != null) {
            cache.put(path, value);
            Map<String, List<String>> listCache = langListCaches.get(activeLang);
            if (listCache != null) listCache.remove(path);
        }
    }

    /** Get the list of supported language codes. */
    public static String[] getSupportedLanguages() {
        return SUPPORTED.clone();
    }

    /** Check if a language is supported. */
    public static boolean isSupported(String lang) {
        if (lang == null) return false;
        String normalized = normalizeLang(lang);
        for (String s : SUPPORTED) {
            if (s.equals(normalized)) return true;
        }
        return false;
    }
}
