package me.nikl.gamebox.game.impl.chess;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.GameManager;
import me.nikl.gamebox.game.rules.GameType;
import org.bukkit.entity.Player;

/**
 * Chess two-player game: a 6x6 Los Alamos chess variant that can be played
 * against another player (via invite) or against a simple AI. Los Alamos
 * chess uses a 6x6 board with no bishops and no castling; pawns cannot move
 * two squares on their first move (the board is too small to make that
 * meaningful). All other standard chess rules apply: pieces move as usual,
 * kings must not be left in check, and the game ends on checkmate or
 * stalemate.
 *
 * <p>The board is rendered in the left 6 columns of a size-54 chest inventory;
 * the right three columns hold status info, captured pieces, and control
 * buttons. Because Bukkit inventories cap at 54 slots, a full 8x8 board does
 * not fit; Los Alamos chess is the closest playable international-chess-like
 * variant that fits in the available space.</p>
 */
public class GameChess extends Game {

    private ChessManager manager;

    public GameChess(GameBox plugin) {
        super(plugin, "chess", GameType.TWO_PLAYER);
    }

    @Override
    public void loadSettings() {
        rule.setCost(config.getDouble("cost", 0.0));
        rule.setGuiSize(config.getInt("guiSize", 54));
        rule.setScorePerToken(config.getInt("scorePerToken", 0));
        rule.setTrackHighScore(config.getBoolean("trackHighScore", true));
        double moneyPerToken = config.getDouble("moneyPerToken", 0.0);
        if (moneyPerToken > 0) rule.setMoneyPerToken(moneyPerToken);
        loadMultiRewards(multiRewards, config.getConfigurationSection("rewards"));
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
        this.manager = new ChessManager(this);
    }

    @Override
    public GameManager<?> getGameManager() {
        return manager;
    }

    @Override
    public void onGameEvent(Player player, String event, long value) {
        super.onGameEvent(player, event, value);
        // Capturing a piece grants a small bonus on top of the regular rewards.
        if ("capture".equals(event) && value > 0) {
            me.nikl.gamebox.data.GBPlayer gb =
                    plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                gb.addTokens((int) Math.min(3, value));
            }
        }
    }
}
