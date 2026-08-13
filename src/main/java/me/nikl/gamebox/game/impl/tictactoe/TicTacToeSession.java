package me.nikl.gamebox.game.impl.tictactoe;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * A shared two-player Tic-Tac-Toe session. Both players view the same 3x3
 * board and alternate turns. Player 1 plays X (red), player 2 plays O (blue).
 *
 * <p>The 3x3 board is laid out as a centered grid on a 4-row inventory, leaving
 * the top row for a turn / result info item.</p>
 */
public class TicTacToeSession extends AbstractGameSession {

    /** Board cell index -> inventory slot mapping (centered 3x3 grid). */
    private static final int[] SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};

    /** The 8 winning lines as cell-index triples. */
    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // rows
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // columns
            {0, 4, 8}, {2, 4, 6}             // diagonals
    };

    private final UUID p1;
    private final UUID p2;

    /** Board state: 0 = empty, 1 = X (player 1), 2 = O (player 2). */
    private final int[] board = new int[9];

    private UUID currentTurn;
    private UUID winner = null;

    // Per-player score: outcome-based (win +3, draw +1). Reported via onScoreChange.
    private int score1 = 0;
    private int score2 = 0;

    public TicTacToeSession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.p1 = players.get(0).getUniqueId();
        this.p2 = players.size() > 1 ? players.get(1).getUniqueId() : AI_ID;
        if (players.size() == 1) this.vsAi = true;
        this.currentTurn = p1; // X starts
    }

    @Override
    protected int getInventorySize() {
        return 36;
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    @Override
    public void build() {
        ItemStack glass = Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getInventorySize(); i++) {
            inventory.setItem(i, glass);
        }
        inventory.setItem(4, infoItem());
        for (int cell = 0; cell < 9; cell++) {
            inventory.setItem(SLOTS[cell], cellItem(cell));
        }
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (finished) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= getInventorySize()) {
            return;
        }
        int cell = cellForSlot(slot);
        if (cell < 0) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!id.equals(currentTurn)) {
            return; // not this player's turn
        }
        if (board[cell] != 0) {
            return; // cell already occupied
        }

        board[cell] = id.equals(p1) ? 1 : 2;

        // Threat detection: a line with exactly 2 of this player's marks and 1
        // empty cell signals a "threat" event (a win is available next move).
        int placedPiece = board[cell];
        if (hasThreat(placedPiece)) {
            game.onGameEvent(player, "threat", 2);
        }
        // Live score update (score is 0 during play; the win/draw bonus is
        // applied in finish()). Fires the hook for any custom override.
        int placerScore = id.equals(p1) ? score1 : score2;
        game.onScoreChange(player, placerScore, 0);

        int winMark = checkWin();
        if (winMark != 0) {
            boolean p1Won = winMark == 1;
            finish(p1Won ? p1 : p2, p1Won ? p2 : p1, false);
            return;
        }
        if (isFull()) {
            finish(p1, p2, true); // draw
            return;
        }

        currentTurn = currentTurn.equals(p1) ? p2 : p1;
        refresh();

        // If it's now the AI's turn, schedule the AI move.
        if (vsAi && currentTurn.equals(p2) && !finished) {
            Bukkit.getScheduler().runTaskLater(plugin, this::aiMove, 20L);
        }
    }

    private ItemStack infoItem() {
        if (finished) {
            String name;
            Material mat;
            if (winner != null) {
                name = Utility.replace(game.lang("win"), new String[]{"%player%", playerName(winner)});
                mat = Material.NETHER_STAR;
            } else {
                name = game.lang("draw");
                mat = Material.NETHER_STAR;
            }
            return Utility.createItem(mat, name, null);
        }
        String name = Utility.replace(game.lang("turn"), new String[]{"%player%", playerName(currentTurn)});
        return Utility.createItem(Material.NAME_TAG, name, null);
    }

    private ItemStack cellItem(int cell) {
        int mark = board[cell];
        if (mark == 1) {
            return Utility.createItem(Material.RED_CONCRETE, game.lang("x"), null);
        }
        if (mark == 2) {
            return Utility.createItem(Material.BLUE_CONCRETE, game.lang("o"), null);
        }
        return Utility.createItem(Material.WHITE_STAINED_GLASS_PANE, game.lang("empty"), null);
    }

    private int cellForSlot(int slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private int checkWin() {
        for (int[] line : LINES) {
            int a = board[line[0]];
            int b = board[line[1]];
            int c = board[line[2]];
            if (a != 0 && a == b && b == c) {
                return a;
            }
        }
        return 0;
    }

    /** Whether `piece` currently has a 2-in-a-row with the third cell open. */
    private boolean hasThreat(int piece) {
        for (int[] line : LINES) {
            int mine = 0;
            int empty = 0;
            for (int cell : line) {
                if (board[cell] == piece) mine++;
                else if (board[cell] == 0) empty++;
            }
            if (mine == 2 && empty == 1) return true;
        }
        return false;
    }

    private boolean isFull() {
        for (int v : board) {
            if (v == 0) {
                return false;
            }
        }
        return true;
    }

    private void finish(UUID win, UUID lose, boolean draw) {
        finished = true;
        this.winner = draw ? null : win;
        // Apply outcome scoring (win +3, draw +1 each) and report via onScoreChange.
        // onGameWonMulti handles the final scoreboard; this only fires the hook.
        Player p1Player = Bukkit.getPlayer(p1);
        Player p2Player = Bukkit.getPlayer(p2);
        if (draw) {
            score1 = 1;
            score2 = 1;
            if (p1Player != null) game.onScoreChange(p1Player, score1, 1);
            if (p2Player != null) game.onScoreChange(p2Player, score2, 1);
        } else {
            if (win.equals(p1)) {
                score1 = 3;
                if (p1Player != null) game.onScoreChange(p1Player, score1, 3);
            } else {
                score2 = 3;
                if (p2Player != null) game.onScoreChange(p2Player, score2, 3);
            }
        }
        game.onGameWonMulti(win, lose, draw);
        ((TicTacToeManager) game.getGameManager()).endSession(this);
        refresh();
        if (draw) {
            String msg = Utility.color(game.lang("draw"));
            for (Player p : players) {
                if (p.isOnline()) {
                    p.sendTitle(msg, "", 10, 50, 10);
                    p.sendMessage(msg);
                }
            }
        } else {
            String msg = Utility.color(Utility.replace(game.lang("win"),
                    new String[]{"%player%", playerName(win)}));
            for (Player p : players) {
                if (p.isOnline()) {
                    p.sendMessage(msg);
                }
            }
        }
        end();
    }

    /** Simple Tic-Tac-Toe AI: win if possible, block if needed, center, corner, random. */
    private void aiMove() {
        if (finished) return;
        // 1. Try to win
        int cell = findWinningMove(2);
        // 2. Block opponent's win
        if (cell < 0) cell = findWinningMove(1);
        // 3. Take center
        if (cell < 0 && board[4] == 0) cell = 4;
        // 4. Take a corner
        if (cell < 0) {
            int[] corners = {0, 2, 6, 8};
            java.util.List<Integer> open = new java.util.ArrayList<>();
            for (int c : corners) if (board[c] == 0) open.add(c);
            if (!open.isEmpty()) cell = open.get(new java.util.Random().nextInt(open.size()));
        }
        // 5. Take any open cell
        if (cell < 0) {
            java.util.List<Integer> open = new java.util.ArrayList<>();
            for (int i = 0; i < 9; i++) if (board[i] == 0) open.add(i);
            if (!open.isEmpty()) cell = open.get(new java.util.Random().nextInt(open.size()));
        }
        if (cell < 0) return;

        board[cell] = 2;
        if (hasThreat(2)) {
            Player human = Bukkit.getPlayer(p1);
            if (human != null) game.onGameEvent(human, "threat", 2);
        }
        int winMark = checkWin();
        if (winMark != 0) {
            finish(p2, p1, false);
            return;
        }
        if (isFull()) {
            finish(p1, p2, true);
            return;
        }
        currentTurn = p1;
        refresh();
    }

    /** Find a cell where `piece` would complete a line, or -1. */
    private int findWinningMove(int piece) {
        for (int[] line : LINES) {
            int mine = 0, empty = -1;
            for (int cell : line) {
                if (board[cell] == piece) mine++;
                else if (board[cell] == 0) empty = cell;
            }
            if (mine == 2 && empty >= 0) return empty;
        }
        return -1;
    }
}
