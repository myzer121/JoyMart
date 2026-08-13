package me.nikl.gamebox.game.impl.twentyfortyeight;

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
 * Active 2048 session. Renders a 4x4 grid inside a size-45 chest inventory with
 * directional slide buttons on the bottom row. Tiles are represented as
 * colored wool whose name is the tile value.
 */
public class GameSession2048 extends AbstractGameSession {

    private static final int GRID = 4;
    private static final int COL_OFFSET = 2; // grid starts at column 2 (centered)
    private static final int SIZE = 45;

    // Button slots on the fifth row (indices 36-44).
    private static final int SLOT_SCORE = 36;
    private static final int SLOT_LEFT = 37;
    private static final int SLOT_UP = 38;
    private static final int SLOT_DOWN = 39;
    private static final int SLOT_RIGHT = 40;
    private static final int SLOT_RESTART = 43;

    // Direction encoding used by the move routine: 0=up, 1=down, 2=left, 3=right.
    private static final int DIR_UP = 0;
    private static final int DIR_DOWN = 1;
    private static final int DIR_LEFT = 2;
    private static final int DIR_RIGHT = 3;

    private final int[] grid = new int[GRID * GRID];
    private final Random random = new Random();

    private long score = 0;
    private boolean reached2048 = false;
    private boolean settled = false;

    public GameSession2048(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        spawnTile();
        spawnTile();
    }

    @Override
    protected int getInventorySize() {
        return SIZE;
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    @Override
    public void build() {
        ItemStack background = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, background);
        }

        for (int r = 0; r < GRID; r++) {
            for (int c = 0; c < GRID; c++) {
                int value = grid[r * GRID + c];
                inventory.setItem(r * 9 + c + COL_OFFSET, tileItem(value));
            }
        }

        inventory.setItem(SLOT_LEFT,
                Utility.createItem(Material.CYAN_WOOL, "&a&l\u25C4 " + game.lang("buttons.left"),
                        Utility.list("&7" + game.lang("buttons.leftHint"))));
        inventory.setItem(SLOT_UP,
                Utility.createItem(Material.LIME_WOOL, "&a&l\u25B2 " + game.lang("buttons.up"),
                        Utility.list("&7" + game.lang("buttons.upHint"))));
        inventory.setItem(SLOT_DOWN,
                Utility.createItem(Material.GREEN_WOOL, "&a&l\u25BC " + game.lang("buttons.down"),
                        Utility.list("&7" + game.lang("buttons.downHint"))));
        inventory.setItem(SLOT_RIGHT,
                Utility.createItem(Material.BLUE_WOOL, "&a&l\u25BA " + game.lang("buttons.right"),
                        Utility.list("&7" + game.lang("buttons.rightHint"))));
        inventory.setItem(SLOT_RESTART,
                Utility.createItem(Material.REDSTONE_BLOCK, "&c&l\u21BB " + game.lang("buttons.restart"),
                        Utility.list("&7" + game.lang("buttons.restartHint"))));

        List<String> scoreLore = new ArrayList<>();
        scoreLore.add("&e" + game.lang("labels.score") + ": &f" + score);
        scoreLore.add("&7" + game.lang("labels.target") + ": &f2048");
        if (reached2048) {
            scoreLore.add("&a" + game.lang("labels.reached"));
        }
        inventory.setItem(SLOT_SCORE,
                Utility.createItem(Material.BOOK, "&e" + game.lang("labels.score"), scoreLore));
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (settled) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= SIZE) {
            return;
        }

        if (raw == SLOT_RESTART) {
            restart();
            refresh();
            return;
        }

        int dir;
        if (raw == SLOT_LEFT) {
            dir = DIR_LEFT;
        } else if (raw == SLOT_UP) {
            dir = DIR_UP;
        } else if (raw == SLOT_DOWN) {
            dir = DIR_DOWN;
        } else if (raw == SLOT_RIGHT) {
            dir = DIR_RIGHT;
        } else {
            return;
        }

        if (move(dir)) {
            if (!canMove()) {
                refresh();
                endGame();
            } else {
                refresh();
            }
        }
    }

    private void restart() {
        for (int i = 0; i < grid.length; i++) {
            grid[i] = 0;
        }
        score = 0;
        reached2048 = false;
        settled = false;
        spawnTile();
        spawnTile();
    }

    /** Slide and merge all lines in the given direction; returns whether anything moved. */
    private boolean move(int dir) {
        boolean moved = false;
        for (int i = 0; i < GRID; i++) {
            int[] line = new int[GRID];
            for (int j = 0; j < GRID; j++) {
                line[j] = cell(dir, i, j);
            }
            int[] merged = slideAndMerge(line);
            for (int j = 0; j < GRID; j++) {
                if (cell(dir, i, j) != merged[j]) {
                    moved = true;
                }
                setCell(dir, i, j, merged[j]);
            }
        }
        if (moved) {
            spawnTile();
        }
        return moved;
    }

    /** Read the j-th cell of the i-th line for the given direction. */
    private int cell(int dir, int i, int j) {
        return switch (dir) {
            case DIR_UP -> grid[j * GRID + i];
            case DIR_DOWN -> grid[(GRID - 1 - j) * GRID + i];
            case DIR_LEFT -> grid[i * GRID + j];
            case DIR_RIGHT -> grid[i * GRID + (GRID - 1 - j)];
            default -> 0;
        };
    }

    private void setCell(int dir, int i, int j, int v) {
        switch (dir) {
            case DIR_UP -> grid[j * GRID + i] = v;
            case DIR_DOWN -> grid[(GRID - 1 - j) * GRID + i] = v;
            case DIR_LEFT -> grid[i * GRID + j] = v;
            case DIR_RIGHT -> grid[i * GRID + (GRID - 1 - j)] = v;
            default -> { }
        }
    }

    /** Compress non-zero tiles toward index 0 and merge equal adjacent pairs once. */
    private int[] slideAndMerge(int[] line) {
        int[] tmp = new int[GRID];
        int n = 0;
        for (int v : line) {
            if (v != 0) {
                tmp[n++] = v;
            }
        }
        int[] res = new int[GRID];
        int pos = 0;
        Player p = players.isEmpty() ? null : players.get(0);
        for (int i = 0; i < n; i++) {
            if (i + 1 < n && tmp[i] == tmp[i + 1]) {
                int merged = tmp[i] * 2;
                res[pos++] = merged;
                score += merged;
                if (merged >= 2048) {
                    reached2048 = true;
                }
                if (p != null) {
                    game.onScoreChange(p, score, merged);
                    if (isMilestone(merged)) {
                        game.onGameEvent(p, "milestone", merged);
                    }
                    if (merged >= 2048) {
                        game.onGameEvent(p, "won2048", 2048);
                    }
                }
                i++;
            } else {
                res[pos++] = tmp[i];
            }
        }
        return res;
    }

    /** True for the tracked merge milestones: 128, 256, 512, 1024, 2048. */
    private boolean isMilestone(int value) {
        return value >= 128 && value <= 2048 && (value & (value - 1)) == 0;
    }

    private void spawnTile() {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == 0) {
                empty.add(i);
            }
        }
        if (empty.isEmpty()) {
            return;
        }
        int idx = empty.get(random.nextInt(empty.size()));
        grid[idx] = random.nextDouble() < 0.9 ? 2 : 4;
    }

    private boolean canMove() {
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == 0) {
                return true;
            }
            int r = i / GRID;
            int c = i % GRID;
            if (c + 1 < GRID && grid[i] == grid[r * GRID + c + 1]) {
                return true;
            }
            if (r + 1 < GRID && grid[i] == grid[(r + 1) * GRID + c]) {
                return true;
            }
        }
        return false;
    }

    private void endGame() {
        if (settled) {
            return;
        }
        settled = true;
        Player p = players.isEmpty() ? null : players.get(0);
        if (p != null) {
            game.onGameWonSingle(p, reached2048, score);
        }
        ((GameManager2048) game.getGameManager()).endSession(this);
        end();
    }

    private ItemStack tileItem(int value) {
        if (value == 0) {
            return Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        }
        List<String> lore = new ArrayList<>();
        lore.add("&e" + game.lang("labels.score") + ": &f" + score);
        return Utility.createItem(materialFor(value), "&f&l" + value, lore);
    }

    private Material materialFor(int value) {
        return switch (value) {
            case 2 -> Material.WHITE_WOOL;
            case 4 -> Material.LIGHT_GRAY_WOOL;
            case 8 -> Material.ORANGE_WOOL;
            case 16 -> Material.YELLOW_WOOL;
            case 32 -> Material.LIME_WOOL;
            case 64 -> Material.PINK_WOOL;
            case 128 -> Material.LIGHT_BLUE_WOOL;
            case 256 -> Material.MAGENTA_WOOL;
            case 512 -> Material.CYAN_WOOL;
            case 1024 -> Material.PURPLE_WOOL;
            case 2048 -> Material.RED_WOOL;
            default -> value > 2048 ? Material.GOLD_BLOCK : Material.BLACK_WOOL;
        };
    }

    private List<String> list(String line) {
        List<String> l = new ArrayList<>();
        l.add(line);
        return l;
    }
}
