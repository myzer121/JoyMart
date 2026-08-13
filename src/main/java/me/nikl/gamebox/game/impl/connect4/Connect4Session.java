package me.nikl.gamebox.game.impl.connect4;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * A shared-inventory Connect 4 session.
 *
 * <p>The board is a {@code rows x cols} grid laid out in a size-54 inventory
 * using slot {@code r*9 + c}. Player 1 drops red wool, player 2 drops yellow
 * wool. Clicking any cell in a column drops the current player's piece into the
 * lowest empty row of that column. The session ends when a player connects four
 * in any direction, or when the board fills with no winner (a draw).</p>
 */
public class Connect4Session extends AbstractGameSession {

    private static final int INVENTORY_SIZE = 54;

    private final int cols;
    private final int rows;

    // 0 = empty, 1 = player 1 (red), 2 = player 2 (yellow)
    private final int[][] board;

    private final UUID p1Id;
    private final UUID p2Id;
    private UUID currentTurn;

    private int moves = 0;
    private boolean over = false;

    // Per-player score: each dropped piece is +1 (a 4-in-a-row win lands on 4).
    private int score1 = 0;
    private int score2 = 0;

    public Connect4Session(GameBox plugin, GameConnect4 game, List<Player> players) {
        super(plugin, game, players);
        this.cols = game.getCols();
        this.rows = game.getRows();
        this.board = new int[rows][cols];
        this.p1Id = players.get(0).getUniqueId();
        this.p2Id = players.size() > 1 ? players.get(1).getUniqueId() : AI_ID;
        if (players.size() == 1) this.vsAi = true;
        this.currentTurn = p1Id;
    }

    @Override
    protected int getInventorySize() {
        return INVENTORY_SIZE;
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    @Override
    public void build() {
        if (inventory == null) return;

        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        ItemStack empty = Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, game.lang("items.empty"), null);
        ItemStack red = Utility.createItem(Material.RED_WOOL, game.lang("items.red"), null);
        ItemStack yellow = Utility.createItem(Material.YELLOW_WOOL, game.lang("items.yellow"), null);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int slot = r * 9 + c;
                ItemStack item;
                switch (board[r][c]) {
                    case 1:
                        item = red;
                        break;
                    case 2:
                        item = yellow;
                        break;
                    default:
                        item = empty;
                }
                inventory.setItem(slot, item);
            }
        }

        // Turn indicator (top-right).
        boolean p1Turn = currentTurn.equals(p1Id);
        Material turnMat = p1Turn ? Material.RED_WOOL : Material.YELLOW_WOOL;
        ItemStack turn = Utility.createItem(turnMat,
                game.lang("info.turn").replace("%player%", playerName(currentTurn)), null);
        inventory.setItem(8, turn);

        // Legend (bottom-right).
        ItemStack legend = Utility.createItem(Material.PAPER, game.lang("info.legend"), null);
        inventory.setItem(53, legend);
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (over || finished) return;

        UUID id = player.getUniqueId();
        if (!id.equals(currentTurn)) {
            player.sendMessage(Utility.color(game.lang("messages.notYourTurn")));
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= INVENTORY_SIZE) return;
        int c = raw % 9;
        int r = raw / 9;
        if (c >= cols || r >= rows) return; // info columns / out of board

        int dropRow = -1;
        for (int rr = rows - 1; rr >= 0; rr--) {
            if (board[rr][c] == 0) {
                dropRow = rr;
                break;
            }
        }
        if (dropRow == -1) {
            player.sendMessage(Utility.color(game.lang("messages.columnFull")));
            return;
        }

        boolean p1Turn = currentTurn.equals(p1Id);
        int piece = p1Turn ? 1 : 2;
        board[dropRow][c] = piece;
        moves++;

        // Each dropped piece is +1 score, reported live via onScoreChange.
        int dropScore = addScore(p1Turn, 1);
        if (p1Turn) {
            game.onScoreChange(players.get(0), dropScore, 1);
        }

        // Threat detection: a line of exactly 3 through this piece (with room
        // to extend to 4) reports a "threat" event before any win check.
        if (maxLineThrough(dropRow, c, piece) == 3) {
            if (p1Turn) game.onGameEvent(players.get(0), "threat", 3);
        }

        if (checkWin(dropRow, c, piece)) {
            over = true;
            refresh();
            broadcast(game.lang("messages.win").replace("%player%", playerName(currentTurn)));
            UUID winner = p1Turn ? p1Id : p2Id;
            UUID loser = p1Turn ? p2Id : p1Id;
            settle(winner, loser, false);
            return;
        }

        if (moves >= rows * cols) {
            over = true;
            refresh();
            broadcast(game.lang("messages.draw"));
            settle(p1Id, p2Id, true);
            return;
        }

        currentTurn = p1Turn ? p2Id : p1Id;
        broadcast(game.lang("messages.turn").replace("%player%", playerName(currentTurn)));
        refresh();

        // If it's now the AI's turn, schedule the AI move.
        if (vsAi && currentTurn.equals(p2Id) && !over) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, this::aiMove, 20L);
        }
    }

    /** Simple Connect 4 AI: win if possible, block opponent, otherwise prefer center columns. */
    private void aiMove() {
        if (over || finished) return;
        // 1. Try to win
        int col = findWinningColumn(2);
        // 2. Block opponent
        if (col < 0) col = findWinningColumn(1);
        // 3. Prefer center columns
        if (col < 0) {
            int[] pref = {cols / 2, cols / 2 - 1, cols / 2 + 1, cols / 2 - 2, cols / 2 + 2};
            java.util.Random rand = new java.util.Random();
            for (int c : pref) {
                if (c >= 0 && c < cols && board[0][c] == 0) {
                    col = c;
                    break;
                }
            }
            // 4. Random valid column
            if (col < 0) {
                java.util.List<Integer> valid = new java.util.ArrayList<>();
                for (int c = 0; c < cols; c++) if (board[0][c] == 0) valid.add(c);
                if (!valid.isEmpty()) col = valid.get(rand.nextInt(valid.size()));
            }
        }
        if (col < 0) return;

        // Drop the piece
        int dropRow = -1;
        for (int rr = rows - 1; rr >= 0; rr--) {
            if (board[rr][col] == 0) { dropRow = rr; break; }
        }
        if (dropRow < 0) return;

        board[dropRow][col] = 2;
        moves++;
        score2 += 1;

        if (checkWin(dropRow, col, 2)) {
            over = true;
            refresh();
            broadcast(game.lang("messages.win").replace("%player%", playerName(p2Id)));
            settle(p2Id, p1Id, false);
            return;
        }
        if (moves >= rows * cols) {
            over = true;
            refresh();
            broadcast(game.lang("messages.draw"));
            settle(p1Id, p2Id, true);
            return;
        }
        currentTurn = p1Id;
        broadcast(game.lang("messages.turn").replace("%player%", playerName(p1Id)));
        refresh();
    }

    /** Find a column where dropping `piece` would win, or -1. */
    private int findWinningColumn(int piece) {
        for (int c = 0; c < cols; c++) {
            if (board[0][c] != 0) continue;
            int r = rows - 1;
            while (r >= 0 && board[r][c] != 0) r--;
            if (r < 0) continue;
            board[r][c] = piece;
            boolean win = checkWin(r, c, piece);
            board[r][c] = 0;
            if (win) return c;
        }
        return -1;
    }

    /** Add delta to the dropper's score and return the new score. */
    private int addScore(boolean p1Turn, int delta) {
        if (p1Turn) {
            score1 += delta;
            return score1;
        }
        score2 += delta;
        return score2;
    }

    private boolean checkWin(int r, int c, int piece) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1 + countDir(r, c, d[0], d[1], piece) + countDir(r, c, -d[0], -d[1], piece);
            if (count >= 4) return true;
        }
        return false;
    }

    private int countDir(int r, int c, int dr, int dc, int piece) {
        int count = 0;
        int rr = r + dr;
        int cc = c + dc;
        while (rr >= 0 && rr < rows && cc >= 0 && cc < cols && board[rr][cc] == piece) {
            count++;
            rr += dr;
            cc += dc;
        }
        return count;
    }

    /** Longest connected line (>= 1) of `piece` passing through (r, c). */
    private int maxLineThrough(int r, int c, int piece) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        int maxLen = 1;
        for (int[] d : dirs) {
            int count = 1 + countDir(r, c, d[0], d[1], piece) + countDir(r, c, -d[0], -d[1], piece);
            if (count > maxLen) maxLen = count;
        }
        return maxLen;
    }

    @Override
    public void start() {
        super.start();
        broadcast(game.lang("messages.turn").replace("%player%", playerName(p1Id)));
    }

    private void settle(UUID winner, UUID loser, boolean draw) {
        game.onGameWonMulti(winner, loser, draw);
        ((Connect4Manager) game.getGameManager()).endSession(this);
        end();
    }

    private void broadcast(String msg) {
        String colored = Utility.color(msg);
        for (Player p : players) {
            if (p != null && p.isOnline()) p.sendMessage(colored);
        }
    }
}
