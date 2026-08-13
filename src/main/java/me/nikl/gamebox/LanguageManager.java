package me.nikl.gamebox;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches language strings from the language directory.
 * Missing keys fall back to the bundled English resource.
 *
 * <p>Both scalar and list lookups are cached in a {@link ConcurrentHashMap},
 * so repeated {@code get}/{@code getList} calls are O(1) and allocation-free
 * after the first resolution. This matters because these methods are on the
 * hot path of every GUI open and every chat message.</p>
 */
public class LanguageManager {

    private final GameBox plugin;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    /** Dedicated cache for already-parsed list values (immutable). */
    private final Map<String, List<String>> listCache = new ConcurrentHashMap<>();
    private FileConfiguration defaultConfig;

    public LanguageManager(GameBox plugin) {
        this.plugin = plugin;
        loadDefaults();
        reload();
    }

    private void loadDefaults() {
        try (Reader reader = new InputStreamReader(plugin.getResource("language/language_en.yml"), StandardCharsets.UTF_8)) {
            this.defaultConfig = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default language file: " + e.getMessage());
            this.defaultConfig = new YamlConfiguration();
        }
    }

    public void reload() {
        cache.clear();
        listCache.clear();
        String lang = GameBoxSettings.language.equalsIgnoreCase("zh") ? "zh"
                : GameBoxSettings.language.equalsIgnoreCase("de") ? "de"
                : GameBoxSettings.language.equalsIgnoreCase("es") ? "es"
                : "en";
        File file = new File(plugin.getDataFolder(), "language/language_" + lang + ".yml");
        if (!file.exists() && plugin.getResource("language/language_" + lang + ".yml") != null) {
            plugin.saveResource("language/language_" + lang + ".yml", false);
        }
        FileConfiguration user = YamlConfiguration.loadConfiguration(file);
        for (String key : user.getKeys(true)) {
            cache.put(key, user.get(key));
        }
        // Fill missing from defaults
        for (String key : defaultConfig.getKeys(true)) {
            cache.putIfAbsent(key, defaultConfig.get(key));
        }
    }

    public String get(String path) {
        Object o = cache.get(path);
        if (o == null) return path;
        return o.toString();
    }

    public String getPrefixed(String path) {
        // PREFIX is already colorized (uses § codes). The message body from
        // get(path) may contain raw & codes — translate them here so all
        // chat messages display colors correctly.
        return ChatColor.translateAlternateColorCodes('&', GameBoxSettings.PREFIX + get(path));
    }

    public List<String> getList(String path) {
        // Fast path: return the cached immutable list (no allocation).
        List<String> cached = listCache.get(path);
        if (cached != null) return cached;

        Object o = cache.get(path);
        List<String> result;
        if (o instanceof List) {
            result = new ArrayList<>();
            for (Object item : (List<?>) o) {
                result.add(item.toString());
            }
        } else if (o != null) {
            result = new ArrayList<>();
            result.add(o.toString());
        } else {
            result = new ArrayList<>();
        }
        // Cache as unmodifiable so callers can't corrupt the shared instance.
        List<String> immutable = Collections.unmodifiableList(result);
        listCache.put(path, immutable);
        return immutable;
    }

    public void set(String path, Object value) {
        cache.put(path, value);
    }

    /** Merge a game-specific language file into the cache (keys are namespaced by game id). */
    public void mergeGameLanguage(String gameId, FileConfiguration config) {
        String prefix = "games." + gameId + ".";
        for (String key : config.getKeys(true)) {
            String fullKey = prefix + key;
            cache.put(fullKey, config.get(key));
            // Invalidate any previously cached list under this key.
            listCache.remove(fullKey);
        }
    }
}
