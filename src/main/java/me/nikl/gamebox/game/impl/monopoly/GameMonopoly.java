package me.nikl.gamebox.game.impl.monopoly;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Monopoly — a multi-player board game where players buy properties, collect
 * rent, and try to bankrupt all opponents. Supports 2-3 human players or a
 * 1-vs-AI match. Each player starts with 2000 yuan; the last millionaire wins.
 *
 * <p>Property prices and rents are admin-configurable via
 * {@code games/monopoly/config.yml} or the in-game settings editor.</p>
 */
public class GameMonopoly extends Game {

    private MonopolyManager manager;

    /** In-game starting money per player (default 2000). */
    private int startMoney = 2000;
    /** Bonus for passing GO (default 200). */
    private int passGoBonus = 200;
    /** Fee to leave jail (default 50). */
    private int jailFee = 50;

    /** Property definitions read from config (17 properties). */
    private MonopolyProperty[] properties;

    public GameMonopoly(GameBox plugin) {
        super(plugin, "monopoly", GameType.MULTI_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        loadMultiRewards(multiRewards, config.getConfigurationSection("rewards"));
        startMoney = config.getInt("settings.startMoney", 2000);
        passGoBonus = config.getInt("settings.passGoBonus", 200);
        jailFee = config.getInt("settings.jailFee", 50);
        loadProperties();
    }

    /** Load the property list from config into a fixed-size array. */
    private void loadProperties() {
        java.util.List<?> list = config.getList("properties");
        int count = list != null ? list.size() : 0;
        properties = new MonopolyProperty[count];
        for (int i = 0; i < count; i++) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) list.get(i);
            String name = String.valueOf(map.get("name"));
            int price = ((Number) map.get("price")).intValue();
            int rent = ((Number) map.get("rent")).intValue();
            properties[i] = new MonopolyProperty(i, name, price, rent);
        }
    }

    @Override
    public void loadLanguage() {
        // Language file is loaded and merged into the cache by the framework.
    }

    @Override
    public void init() {
        // No additional setup required.
    }

    @Override
    public void loadGameManager() {
        this.manager = new MonopolyManager(this);
    }

    @Override
    public MonopolyManager getGameManager() {
        return manager;
    }

    public int getStartMoney() { return startMoney; }
    public int getPassGoBonus() { return passGoBonus; }
    public int getJailFee() { return jailFee; }
    public MonopolyProperty[] getProperties() { return properties; }

    /** Get a property by board-space index (the 17 property positions map
     *  sequentially to this array). */
    public MonopolyProperty propertyAt(int spaceIndex) {
        int idx = MonopolyBoard.propertyIndex(spaceIndex);
        if (idx < 0 || idx >= properties.length) return null;
        return properties[idx];
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
    }
}
