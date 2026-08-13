package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the per-player delivery box: a persistent store of items purchased
 * from the token shop (or otherwise earned) that the player can retrieve at
 * any time via the {@link DeliveryBoxGui}.
 *
 * <p>Items are serialized to {@code delivery_box.yml} so they survive server
 * restarts. Each player has a list of ItemStacks; the GUI shows up to 36 of
 * them at a time.</p>
 *
 * <p>This replaces the old {@code PluginManager.addPendingItem} approach, which
 * was unreliable because GameBox saves and clears the player's real inventory
 * on enter — direct {@code addItem} calls wrote to the cleared inventory and
 * were wiped on restore.</p>
 */
public class DeliveryBoxManager {

    private final GameBox plugin;
    private final Map<UUID, List<ItemStack>> boxes = new HashMap<>();
    private File file;
    private FileConfiguration config;

    public DeliveryBoxManager(GameBox plugin) {
        this.plugin = plugin;
        load();
    }

    /** Load all pending items from delivery_box.yml into memory. */
    @SuppressWarnings("unchecked")
    private void load() {
        file = new File(plugin.getDataFolder(), "delivery_box.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create delivery_box.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players != null) {
            for (String uuidStr : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<?> raw = config.getList("players." + uuidStr);
                    List<ItemStack> items = new ArrayList<>();
                    if (raw != null) {
                        for (Object o : raw) {
                            if (o instanceof ItemStack) {
                                items.add((ItemStack) o);
                            }
                        }
                    }
                    if (!items.isEmpty()) {
                        boxes.put(uuid, items);
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip invalid UUID
                }
            }
        }
        plugin.getLogger().info("DeliveryBox: loaded " + boxes.size() + " player box(es).");
    }

    /** Persist all in-memory boxes to delivery_box.yml. */
    public void save() {
        for (Map.Entry<UUID, List<ItemStack>> entry : boxes.entrySet()) {
            config.set("players." + entry.getKey().toString(), entry.getValue());
        }
        // Also remove entries for players with empty boxes
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players != null) {
            for (String uuidStr : new ArrayList<>(players.getKeys(false))) {
                if (!boxes.containsKey(UUID.fromString(uuidStr))) {
                    config.set("players." + uuidStr, null);
                }
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save delivery_box.yml: " + e.getMessage());
        }
    }

    /**
     * Add an item to the player's delivery box.
     * @param player the player
     * @param item   the item to store (cloned internally)
     */
    public void addItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            plugin.getLogger().warning("DeliveryBox: tried to add null/AIR item for " + player.getName());
            return;
        }
        UUID uuid = player.getUniqueId();
        boxes.computeIfAbsent(uuid, k -> new ArrayList<>()).add(item.clone());
        save();
        plugin.getLogger().info("DeliveryBox: added " + item.getType() + " x" + item.getAmount()
                + " to " + player.getName() + "'s box (total: " + boxes.get(uuid).size() + ")");
    }

    /** Get a copy of the player's pending items (modifying the returned list does not affect storage). */
    public List<ItemStack> getItems(Player player) {
        List<ItemStack> items = boxes.get(player.getUniqueId());
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    /** Remove the item at the given index from the player's box. */
    public void removeItem(Player player, int index) {
        List<ItemStack> items = boxes.get(player.getUniqueId());
        if (items == null || index < 0 || index >= items.size()) return;
        items.remove(index);
        if (items.isEmpty()) boxes.remove(player.getUniqueId());
        save();
    }

    /** Replace the player's entire box with the given list (use for partial takes). */
    public void setItems(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            boxes.remove(player.getUniqueId());
        } else {
            boxes.put(player.getUniqueId(), new ArrayList<>(items));
        }
        save();
    }

    /** Number of items waiting in the player's delivery box. */
    public int count(Player player) {
        List<ItemStack> items = boxes.get(player.getUniqueId());
        return items != null ? items.size() : 0;
    }

    /** Whether the player has any items waiting. */
    public boolean hasItems(Player player) {
        return count(player) > 0;
    }
}
