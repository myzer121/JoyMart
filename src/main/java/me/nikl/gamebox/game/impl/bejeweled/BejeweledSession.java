package me.nikl.gamebox.game.impl.bejeweled;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.nms.NmsUtility;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A single Bejeweled round. Holds a 2D grid of gem colours and renders it into
 * a size-(rows*9) inventory, using column 8 of each row for an info item and
 * background panes. Click a gem to select it, then click an adjacent gem to
 * swap. Swaps that form a line of three or more clear, gravity pulls gems down,
 * new gems spawn, and cascades resolve. Each cleared gem scores one point. The
 * game ends when the move limit is reached.
 */
public class BejeweledSession extends AbstractGameSession {

    private static final int NUM_COLORS = 6;
    private static final Material[] GEM_MATERIALS = {
            Material.RED_CONCRETE,
            Material.BLUE_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.PURPLE_CONCRETE,
            Material.ORANGE_CONCRETE
    };
    private static final String[] GEM_LANG_KEYS = {"red", "blue", "green", "yellow", "purple", "orange"};

    private final int rows;
    private final int cols;
    private final int moveLimit;

    private final int[][] grid;
    private int selectedSlot = -1;
    private int movesLeft;
    private long score = 0;
    private final Random random = new Random();

    public BejeweledSession(GameBox plugin, GameBejeweled game, List<Player> players) {
        super(plugin, game, players);
        this.rows = game.getRows();
        this.cols = game.getCols();
        this.moveLimit = game.getMoveLimit();
        this.movesLeft = moveLimit;
        this.grid = new int[rows][cols];
        initGrid();
    }

    @Override
    protected int getInventorySize() {
        return rows * 9;
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    /** Build the initial grid with no pre-existing three-in-a-row. */
    private void initGrid() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int color;
                int guard = 0;
                do {
                    color = random.nextInt(NUM_COLORS);
                    guard++;
                } while (guard < 50 && (
                        (c >= 2 && grid[r][c - 1] == color && grid[r][c - 2] == color)
                                || (r >= 2 && grid[r - 1][c] == color && grid[r - 2][c] == color)));
                grid[r][c] = color;
            }
        }
        // Safety net: clear any accidental match (the guard above should prevent them).
        resolveCascades(false);
    }

    @Override
    public void build() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int slot = r * 9 + c;
                ItemStack gem = makeGem(grid[r][c]);
                if (slot == selectedSlot) {
                    gem = NmsUtility.getInstance().addGlow(gem);
                }
                inventory.setItem(slot, gem);
            }
        }
        // Column 8: info item on the first row, background elsewhere.
        for (int r = 0; r < rows; r++) {
            int slot = r * 9 + 8;
            inventory.setItem(slot, r == 0 ? infoItem() : backgroundPane());
        }
    }

    private boolean isGemCell(int slot) {
        if (slot < 0 || slot >= getInventorySize()) return false;
        return (slot % 9) < cols;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (finished) return;
        int slot = event.getRawSlot();
        if (!isGemCell(slot)) return;

        if (selectedSlot == -1) {
            selectedSlot = slot;
            refresh();
            return;
        }
        if (slot == selectedSlot) {
            selectedSlot = -1;
            refresh();
            return;
        }
        int r1 = selectedSlot / 9, c1 = selectedSlot % 9;
        int r2 = slot / 9, c2 = slot % 9;
        if (Math.abs(r1 - r2) + Math.abs(c1 - c2) != 1) {
            // Not adjacent: reselect the newly clicked gem.
            selectedSlot = slot;
            refresh();
            return;
        }
        // Adjacent: attempt the swap.
        swap(r1, c1, r2, c2);
        if (findMatches().isEmpty()) {
            // No match formed: revert the swap.
            swap(r1, c1, r2, c2);
        } else {
            movesLeft--;
            resolveCascades();
        }
        selectedSlot = -1;
        refresh();
        if (movesLeft <= 0 && !finished) {
            finishGame();
        }
    }

    private void swap(int r1, int c1, int r2, int c2) {
        int tmp = grid[r1][c1];
        grid[r1][c1] = grid[r2][c2];
        grid[r2][c2] = tmp;
    }

    /** Repeatedly clear matches, apply gravity, and refill until stable. */
    private void resolveCascades() {
        resolveCascades(true);
    }

    /**
     * Repeatedly clear matches, apply gravity, and refill until stable.
     *
     * @param scoreAndHook when true (real play), add N*10*cascade-level to the
     *                     score and fire the score-change / cascade hooks; when
     *                     false (board initialization) just stabilize the grid.
     */
    private void resolveCascades(boolean scoreAndHook) {
        int cascadeLevel = 0;
        while (true) {
            Set<Long> matches = findMatches();
            if (matches.isEmpty()) break;
            cascadeLevel++;
            int n = matches.size();
            if (scoreAndHook) {
                long delta = (long) n * 10L * cascadeLevel;
                score += delta;
                Player p = players.isEmpty() ? null : players.get(0);
                if (p != null) {
                    game.onScoreChange(p, score, delta);
                    if (cascadeLevel >= 4) {
                        game.onGameEvent(p, "cascade", cascadeLevel);
                    }
                }
            }
            for (long key : matches) {
                int r = (int) (key >>> 16);
                int c = (int) (key & 0xFFFF);
                grid[r][c] = -1;
            }
            applyGravity();
        }
    }

    /** Find every cell that is part of a horizontal or vertical run of length >= 3. */
    private Set<Long> findMatches() {
        Set<Long> matches = new HashSet<>();
        // Horizontal runs.
        for (int r = 0; r < rows; r++) {
            int c = 0;
            while (c < cols) {
                int color = grid[r][c];
                if (color == -1) {
                    c++;
                    continue;
                }
                int start = c;
                while (c < cols && grid[r][c] == color) c++;
                if (c - start >= 3) {
                    for (int k = start; k < c; k++) matches.add(cellKey(r, k));
                }
            }
        }
        // Vertical runs.
        for (int c = 0; c < cols; c++) {
            int r = 0;
            while (r < rows) {
                int color = grid[r][c];
                if (color == -1) {
                    r++;
                    continue;
                }
                int start = r;
                while (r < rows && grid[r][c] == color) r++;
                if (r - start >= 3) {
                    for (int k = start; k < r; k++) matches.add(cellKey(k, c));
                }
            }
        }
        return matches;
    }

    private long cellKey(int r, int c) {
        return ((long) r << 16) | (c & 0xFFFF);
    }

    /** Drop existing gems down each column, then refill empties from the top. */
    private void applyGravity() {
        for (int c = 0; c < cols; c++) {
            int writeRow = rows - 1;
            for (int r = rows - 1; r >= 0; r--) {
                if (grid[r][c] != -1) {
                    grid[writeRow][c] = grid[r][c];
                    if (writeRow != r) grid[r][c] = -1;
                    writeRow--;
                }
            }
            for (int r = writeRow; r >= 0; r--) {
                grid[r][c] = random.nextInt(NUM_COLORS);
            }
        }
    }

    private void finishGame() {
        if (finished) return;
        Player p = players.isEmpty() ? null : players.get(0);
        if (p != null && p.isOnline()) {
            game.onGameWonSingle(p, true, score);
        }
        ((BejeweledManager) game.getGameManager()).endSession(this);
        end();
    }

    // ---- Items ----

    private ItemStack makeGem(int color) {
        if (color < 0 || color >= NUM_COLORS) {
            return backgroundPane();
        }
        String name = game.lang("gems." + GEM_LANG_KEYS[color]);
        return Utility.createItem(GEM_MATERIALS[color], name, null);
    }

    private ItemStack backgroundPane() {
        return Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
    }

    private ItemStack infoItem() {
        List<String> lore = new ArrayList<>();
        lore.add(Utility.replace(game.lang("infoScore"),
                new String[]{"%score%", String.valueOf(score)}));
        lore.add(Utility.replace(game.lang("infoMoves"),
                new String[]{"%moves%", String.valueOf(Math.max(0, movesLeft))}));
        return Utility.createItem(Material.CLOCK, game.lang("infoName"), lore);
    }
}
