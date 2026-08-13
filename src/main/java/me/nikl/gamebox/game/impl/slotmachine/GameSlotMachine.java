package me.nikl.gamebox.game.impl.slotmachine;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.PrizeGame;
import me.nikl.gamebox.game.PrizePool;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Slot machine single-player game: each spin costs tokens, three reels spin
 * and land on a prize drawn weighted-randomly from the configurable
 * {@link PrizePool}. Admins edit the prize pool via the in-game "Edit Prizes"
 * GUI. Accumulated winnings are recorded as the score when the player cashes out.
 */
public class GameSlotMachine extends Game implements PrizeGame {

    private SlotMachineManager manager;
    private PrizePool prizePool;
    private int spinCostTokens = 2;

    public GameSlotMachine(GameBox plugin) {
        super(plugin, "slotmachine", GameType.SINGLE_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        rule.setGuiSize(config.getInt("guiSize", 27));
        rule.setScorePerToken(config.getInt("scorePerToken", 0));
        rule.setTrackHighScore(config.getBoolean("trackHighScore", true));
        double moneyPerToken = config.getDouble("moneyPerToken", 0.0);
        if (moneyPerToken > 0) rule.setMoneyPerToken(moneyPerToken);
        loadRewards(rewards, config.getConfigurationSection("rewards"));

        spinCostTokens = config.getInt("settings.spinCostTokens", 2);
        if (spinCostTokens < 0) spinCostTokens = 0;

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
        this.manager = new SlotMachineManager(this);
    }

    @Override
    public SlotMachineManager getGameManager() {
        return manager;
    }

    public int getSpinCostTokens() { return spinCostTokens; }

    /** Charge the per-spin token cost. Returns true if the player could afford it. */
    public boolean chargeSpin(Player player) {
        if (spinCostTokens <= 0) return true;
        GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        if (gb == null) return false;
        if (gb.getTokens() < spinCostTokens) return false;
        gb.removeTokens(spinCostTokens);
        return true;
    }

    public boolean canAffordSpin(Player player) {
        if (spinCostTokens <= 0) return true;
        GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        return gb != null && gb.getTokens() >= spinCostTokens;
    }

    @Override
    public PrizePool getPrizePool() {
        return prizePool;
    }

    @Override
    public void openPrizeEditor(Player player) {
        me.nikl.gamebox.inventory.PrizePoolEditor.open(plugin, player, this);
    }
}
