package me.nikl.gamebox.nms;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Abstraction over version-specific NMS operations.
 * The {@link ModernNmsUtility} implementation uses the stable modern API
 * (PersistentDataContainer, available since 1.14) so the same code works
 * across Spigot/Paper 1.21.x.
 */
public abstract class NmsUtility {

    private static NmsUtility instance;
    protected Plugin plugin;

    public static void init(Plugin plugin) {
        if (instance == null) {
            instance = new ModernNmsUtility(plugin);
        }
    }

    public static NmsUtility getInstance() {
        if (instance == null) {
            throw new IllegalStateException("NmsUtility not initialized");
        }
        return instance;
    }

    public abstract ItemStack addGlow(ItemStack item);

    public abstract void setTag(ItemStack item, String key, String value);

    public abstract String getTag(ItemStack item, String key);

    public boolean hasTagValue(ItemStack item, String key, String value) {
        String tag = getTag(item, key);
        return tag != null && tag.equals(value);
    }

    public static final class Keys {
        public static final String GAMEBOX_ITEM = "gamebox_item";
        public static final String GAME_ID = "gamebox_game_id";
        public static final String BUTTON = "gamebox_button";
        public static final String SHOP_ITEM = "gamebox_shop_item";
    }

    static final class ModernNmsUtility extends NmsUtility {

        ModernNmsUtility(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public ItemStack addGlow(ItemStack item) {
            if (item == null) return null;
            ItemStack clone = item.clone();
            ItemMeta meta = clone.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                clone.setItemMeta(meta);
            }
            return clone;
        }

        @Override
        public void setTag(ItemStack item, String key, String value) {
            if (item == null) return;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }

        @Override
        public String getTag(ItemStack item, String key) {
            if (item == null) return null;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            return meta.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, key), PersistentDataType.STRING);
        }
    }
}
