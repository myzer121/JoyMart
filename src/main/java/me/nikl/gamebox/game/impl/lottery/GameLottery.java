package me.nikl.gamebox.game.impl.lottery;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.PrizeGame;
import me.nikl.gamebox.game.PrizePool;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Lottery single-player game: pick {@code pickCount} numbers from a pool, then
 * draw the same count of winning numbers. The match count selects a prize tier
 * from the configurable {@link PrizePool} (index = matches). Admins edit the
 * prize tiers via the in-game {@code Edit Prizes} GUI.
 */
public class GameLottery extends Game implements PrizeGame {

    private LotteryManager manager;
    private PrizePool prizePool;

    private int poolSize = 16;
    private int pickCount = 4;

    public GameLottery(GameBox plugin) {
        super(plugin, "lottery", GameType.SINGLE_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        rule.setGuiSize(config.getInt("guiSize", 45));
        rule.setScorePerToken(config.getInt("scorePerToken", 0));
        rule.setTrackHighScore(config.getBoolean("trackHighScore", true));
        double moneyPerToken = config.getDouble("moneyPerToken", 0.0);
        if (moneyPerToken > 0) rule.setMoneyPerToken(moneyPerToken);
        loadRewards(rewards, config.getConfigurationSection("rewards"));

        poolSize = config.getInt("settings.poolSize", 16);
        pickCount = config.getInt("settings.pickCount", 4);
        if (poolSize < pickCount) poolSize = pickCount;
        if (pickCount < 1) pickCount = 1;

        prizePool = new PrizePool(plugin, this);
        prizePool.load();
    }

    @Override
    public void loadLanguage() {
        // merged by framework
    }

    @Override
    public void init() {
        // no-op
    }

    @Override
    public void loadGameManager() {
        this.manager = new LotteryManager(this);
    }

    @Override
    public LotteryManager getGameManager() {
        return manager;
    }

    public int getPoolSize() { return poolSize; }
    public int getPickCount() { return pickCount; }

    @Override
    public PrizePool getPrizePool() {
        return prizePool;
    }

    @Override
    public void openPrizeEditor(Player player) {
        me.nikl.gamebox.inventory.PrizePoolEditor.open(plugin, player, this);
    }
}
