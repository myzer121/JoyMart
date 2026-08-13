package me.nikl.gamebox.game.impl.minesweeper;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Active Minesweeper session. The board is a 6x9 grid displayed directly in a
 * size-54 chest inventory (one slot per cell). Left-click reveals a cell;
 * right-click toggles a flag. Mines are placed lazily on the first reveal so
 * the opening click is always safe.
 */
public class MinesweeperSession extends AbstractGameSession {

    private final int rows;
    private final int cols;
    private final int mineCount;
    private final int totalCells;

    private final boolean[] mines;
    private final int[] adjacent;
    private final boolean[] revealed;
    private final boolean[] flagged;

    private final Random random = new Random();

    private boolean minesPlaced = false;
    private boolean settled = false;
    private long score = 0;
    private int safeStreak = 0;
    private int revealedNonMine = 0;

    public MinesweeperSession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.rows = game.getConfig().getInt("settings.rows", 6);
        this.cols = game.getConfig().getInt("settings.cols", 9);
        this.mineCount = game.getConfig().getInt("settings.mines", 10);
        this.totalCells = rows * cols;
        this.mines = new boolean[totalCells];
        this.adjacent = new int[totalCells];
        this.revealed = new boolean[totalCells];
        this.flagged = new boolean[totalCells];
    }

    @Override
    protected int getInventorySize() {
        return totalCells;
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    @Override
    public void build() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                inventory.setItem(r * 9 + c, cellItem(r, c));
            }
        }
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (settled) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= totalCells) {
            return;
        }
        int r = raw / 9;
        int c = raw % 9;
        if (r < 0 || r >= rows || c < 0 || c >= cols) {
            return;
        }

        boolean rightClick = event.getClick() != null && event.getClick().isRightClick();
        if (rightClick) {
            toggleFlag(r, c);
        } else {
            reveal(r, c);
        }
        refresh();

        if (!settled && revealedNonMine == totalCells - mineCount) {
            winGame();
        }
    }

    private int idx(int r, int c) {
        return r * cols + c;
    }

    private void toggleFlag(int r, int c) {
        int i = idx(r, c);
        if (revealed[i]) {
            return;
        }
        flagged[i] = !flagged[i];
    }

    private void reveal(int r, int c) {
        int i = idx(r, c);
        if (revealed[i] || flagged[i]) {
            return;
        }

        if (!minesPlaced) {
            placeMines(r, c);
        }

        Player p = players.isEmpty() ? null : players.get(0);

        if (mines[i]) {
            revealed[i] = true;
            if (p != null) {
                game.onGameEvent(p, "mineHit", 0);
            }
            loseGame();
            return;
        }

        // Flood-fill the connected empty region.
        List<Integer> stack = new ArrayList<>();
        stack.add(i);
        while (!stack.isEmpty()) {
            int cur = stack.remove(stack.size() - 1);
            if (revealed[cur]) {
                continue;
            }
            revealed[cur] = true;
            revealedNonMine++;
            score += 10;
            safeStreak++;
            if (p != null) {
                game.onScoreChange(p, score, 10);
                if (safeStreak % 5 == 0) {
                    game.onGameEvent(p, "milestone", 5);
                }
            }
            if (adjacent[cur] == 0) {
                int cr = cur / cols;
                int cc = cur % cols;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) {
                            continue;
                        }
                        int nr = cr + dr;
                        int nc = cc + dc;
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) {
                            continue;
                        }
                        int ni = idx(nr, nc);
                        if (!revealed[ni] && !mines[ni] && !flagged[ni]) {
                            stack.add(ni);
                        }
                    }
                }
            }
        }
    }

    /** Randomly place mines, avoiding the first-clicked cell and its neighbors. */
    private void placeMines(int safeR, int safeC) {
        List<Integer> candidates = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (Math.abs(r - safeR) <= 1 && Math.abs(c - safeC) <= 1) {
                    continue;
                }
                candidates.add(idx(r, c));
            }
        }
        int toPlace = Math.min(mineCount, candidates.size());
        for (int k = 0; k < toPlace; k++) {
            int pick = random.nextInt(candidates.size());
            mines[candidates.remove(pick)] = true;
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mines[idx(r, c)]) {
                    continue;
                }
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) {
                            continue;
                        }
                        int nr = r + dr;
                        int nc = c + dc;
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) {
                            continue;
                        }
                        if (mines[idx(nr, nc)]) {
                            count++;
                        }
                    }
                }
                adjacent[idx(r, c)] = count;
            }
        }
        minesPlaced = true;
    }

    private void winGame() {
        if (settled) {
            return;
        }
        settled = true;
        // Mark all mines as flagged for a tidy finished board.
        for (int i = 0; i < totalCells; i++) {
            if (mines[i]) {
                flagged[i] = true;
            }
        }
        Player p = players.isEmpty() ? null : players.get(0);
        if (p != null) {
            game.onGameWonSingle(p, true, score);
        }
        ((MinesweeperManager) game.getGameManager()).endSession(this);
        end();
    }

    private void loseGame() {
        if (settled) {
            return;
        }
        settled = true;
        for (int i = 0; i < totalCells; i++) {
            if (mines[i]) {
                revealed[i] = true;
            }
        }
        Player p = players.isEmpty() ? null : players.get(0);
        if (p != null) {
            game.onGameWonSingle(p, false, score);
        }
        ((MinesweeperManager) game.getGameManager()).endSession(this);
        end();
    }

    private ItemStack cellItem(int r, int c) {
        int i = idx(r, c);
        if (revealed[i]) {
            if (mines[i]) {
                return Utility.createItem(Material.TNT, "&c&l\u2605", Utility.list("&7" + game.lang("labels.mine")));
            }
            int n = adjacent[i];
            if (n == 0) {
                return Utility.createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", null);
            }
            return Utility.createItem(numberMaterial(n), "&f&l" + n, null);
        }
        if (flagged[i]) {
            return Utility.createItem(Material.RED_WOOL, "&c&l\u2691", Utility.list("&7" + game.lang("labels.flag")));
        }
        return Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, "&7?", Utility.list("&7" + game.lang("labels.hidden")));
    }

    private Material numberMaterial(int n) {
        return switch (n) {
            case 1 -> Material.BLUE_WOOL;
            case 2 -> Material.GREEN_WOOL;
            case 3 -> Material.RED_WOOL;
            case 4 -> Material.PURPLE_WOOL;
            case 5 -> Material.BROWN_WOOL;
            case 6 -> Material.CYAN_WOOL;
            case 7 -> Material.BLACK_WOOL;
            case 8 -> Material.GRAY_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }

    private List<String> list(String line) {
        List<String> l = new ArrayList<>();
        l.add(line);
        return l;
    }
}
