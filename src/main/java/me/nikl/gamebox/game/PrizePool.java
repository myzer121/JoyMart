package me.nikl.gamebox.game;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An ordered, weighted list of prizes for a {@link PrizeGame}. Each prize
 * carries a display item, a weight (used for weighted-random draws), a token
 * reward, a money reward, and console commands to run on win.
 *
 * <p>The pool is loaded from / saved to a {@code prizes} list inside the game's
 * {@code config.yml}. The lottery uses prizes by index (match-count tier),
 * while the slot machine uses {@link #draw()} for a weighted-random pick.</p>
 */
public class PrizePool {

    /** A single configurable prize. */
    public static class Prize {
        public Material material = Material.PAPER;
        public String name = "&fPrize";
        public List<String> lore = new ArrayList<>();
        public int weight = 10;
        public int tokens = 0;
        public double money = 0.0;
        public List<String> commands = new ArrayList<>();

        public ItemStack display() {
            List<String> l = new ArrayList<>(lore);
            return Utility.createItem(material, name, l);
        }
    }

    private final GameBox plugin;
    private final Game game;
    private final List<Prize> prizes = new ArrayList<>();

    public PrizePool(GameBox plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    /** Load prizes from the game config's {@code prizes} list. */
    public void load() {
        prizes.clear();
        FileConfiguration cfg = game.getConfig();
        List<?> raw = cfg.getList("prizes");
        if (raw == null) {
            // Fall back to a ConfigurationSection map form
            ConfigurationSection sec = cfg.getConfigurationSection("prizes");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection p = sec.getConfigurationSection(key);
                    if (p != null) prizes.add(read(p));
                }
            }
            return;
        }
        for (Object o : raw) {
            if (o instanceof ConfigurationSection) {
                prizes.add(read((ConfigurationSection) o));
            } else if (o instanceof java.util.Map) {
                prizes.add(readMap((java.util.Map<?, ?>) o));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Prize readMap(java.util.Map<?, ?> m) {
        Prize p = new Prize();
        Object mat = m.get("material");
        if (mat != null) p.material = Utility.matchMaterial(mat.toString(), Material.PAPER);
        Object n = m.get("name");
        if (n != null) p.name = n.toString();
        Object l = m.get("lore");
        if (l instanceof List) p.lore = new ArrayList<>((List<String>) l);
        Object w = m.get("weight");
        if (w != null) p.weight = parseInt(w, 10);
        Object t = m.get("tokens");
        if (t != null) p.tokens = parseInt(t, 0);
        Object mo = m.get("money");
        if (mo != null) p.money = parseDouble(mo, 0.0);
        Object c = m.get("commands");
        if (c instanceof List) p.commands = new ArrayList<>((List<String>) c);
        return p;
    }

    private Prize read(ConfigurationSection s) {
        Prize p = new Prize();
        p.material = Utility.matchMaterial(s.getString("material", "PAPER"), Material.PAPER);
        p.name = s.getString("name", "&fPrize");
        p.lore = new ArrayList<>(s.getStringList("lore"));
        p.weight = s.getInt("weight", 10);
        p.tokens = s.getInt("tokens", 0);
        p.money = s.getDouble("money", 0.0);
        p.commands = new ArrayList<>(s.getStringList("commands"));
        return p;
    }

    private int parseInt(Object o, int def) {
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return def; }
    }
    private double parseDouble(Object o, double def) {
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return def; }
    }

    /** Persist the current prizes back to the game config and save the file. */
    public void save() {
        FileConfiguration cfg = game.getConfig();
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (Prize p : prizes) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("material", p.material.name());
            m.put("name", p.name);
            m.put("lore", p.lore);
            m.put("weight", p.weight);
            m.put("tokens", p.tokens);
            m.put("money", p.money);
            m.put("commands", p.commands);
            out.add(m);
        }
        cfg.set("prizes", out);
        game.saveGameConfig();
    }

    public List<Prize> getPrizes() { return prizes; }

    public int size() { return prizes.size(); }

    public Prize get(int index) {
        if (index < 0 || index >= prizes.size()) return null;
        return prizes.get(index);
    }

    /** Add a new prize derived from the given item, with sensible defaults. */
    public Prize addFromItem(ItemStack stack) {
        Prize p = new Prize();
        p.material = stack.getType();
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            p.name = meta.getDisplayName();
        } else {
            p.name = "&f" + stack.getType().name();
        }
        if (meta != null && meta.hasLore()) {
            p.lore = new ArrayList<>(meta.getLore());
        }
        p.weight = 10;
        p.tokens = 5;
        p.money = 0.0;
        p.commands = new ArrayList<>();
        prizes.add(p);
        return p;
    }

    public void remove(int index) {
        if (index >= 0 && index < prizes.size()) prizes.remove(index);
    }

    /**
     * Draw a prize using weighted-random selection. Returns null if the pool
     * is empty (caller should treat that as "no prize").
     */
    public Prize draw() {
        if (prizes.isEmpty()) return null;
        int total = 0;
        for (Prize p : prizes) total += Math.max(0, p.weight);
        if (total <= 0) return prizes.get(0);
        Random r = ThreadLocalRandom.current();
        int roll = r.nextInt(total);
        int acc = 0;
        for (Prize p : prizes) {
            acc += Math.max(0, p.weight);
            if (roll < acc) return p;
        }
        return prizes.get(prizes.size() - 1);
    }

    /** Award a prize to the player: tokens + money + console commands. */
    public void award(Prize prize, org.bukkit.entity.Player player) {
        if (prize == null) return;
        me.nikl.gamebox.data.GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        if (gb != null && prize.tokens > 0) gb.addTokens(prize.tokens);
        if (prize.money > 0 && plugin.getEconomyManager().isVaultEnabled()) {
            plugin.getEconomyManager().deposit(player, prize.money);
        }
        for (String cmd : prize.commands) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()));
        }
    }
}
