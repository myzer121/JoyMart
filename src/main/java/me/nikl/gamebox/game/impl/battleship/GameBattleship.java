package me.nikl.gamebox.game.impl.battleship;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Two-player Battleship. Both players' fleets are auto-placed at session start
 * and players alternate firing at the opponent's ocean grid. The shared
 * inventory always renders the current attacker's view of the defender's board.
 */
public class GameBattleship extends Game {

    private BattleshipManager manager;
    private int gridCols = 7;
    private int gridRows = 6;

    public GameBattleship(GameBox plugin) {
        super(plugin, "battleship", GameType.TWO_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        loadMultiRewards(multiRewards, config.getConfigurationSection("rewards"));
        // Columns are clamped to 7 so two info columns (7,8) remain in a 9-wide
        // inventory; rows are clamped to 6 (a size-54 inventory holds 6 rows).
        this.gridCols = Math.min(7, Math.max(1, config.getInt("settings.gridSize", 7)));
        this.gridRows = Math.min(6, Math.max(1, config.getInt("settings.gridRows", 6)));
    }

    @Override
    public void loadLanguage() {
        // The game-specific language file is loaded and merged into the cache by
        // the framework in onEnable() before this hook is called.
    }

    @Override
    public void init() {
        // No additional initialisation required.
    }

    @Override
    public void loadGameManager() {
        this.manager = new BattleshipManager(this);
    }

    @Override
    public BattleshipManager getGameManager() {
        return manager;
    }

    public int getGridCols() {
        return gridCols;
    }

    public int getGridRows() {
        return gridRows;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        // Sinking any ship awards shipSize * 5 bonus tokens in real time.
        if ("sunk".equals(event) && value > 0) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens((int) (value * 5));
            }
        }
    }
}
