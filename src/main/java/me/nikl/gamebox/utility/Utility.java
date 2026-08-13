package me.nikl.gamebox.utility;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Common helper methods for items, colors, numbers. */
public final class Utility {

    private Utility() {}

    public static String color(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String s : input) out.add(color(s));
        return out;
    }

    public static Material matchMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        Material m = Material.matchMaterial(name);
        return m != null ? m : fallback;
    }

    public static ItemStack createItem(Material material, String name, List<String> lore, int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null) meta.setLore(color(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItem(Material material, String name, List<String> lore) {
        return createItem(material, name, lore, 1);
    }

    /** Build a mutable lore list from one or more lines (color codes translated later by createItem). */
    public static List<String> list(String... lines) {
        List<String> out = new ArrayList<>();
        if (lines != null) {
            for (String s : lines) out.add(s);
        }
        return out;
    }

    public static String replace(String input, String[]... pairs) {
        if (input == null) return "";
        String result = input;
        for (String[] pair : pairs) {
            if (pair.length >= 2) {
                result = result.replace(pair[0], pair[1]);
            }
        }
        return result;
    }

    public static List<String> replace(List<String> input, String[]... pairs) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String s : input) out.add(replace(s, pairs));
        return out;
    }

    public static boolean isNumber(String input) {
        if (input == null || input.isEmpty()) return false;
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int parseInt(String input, int def) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
