package me.nikl.gamebox.game.impl.snake;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Active Snake session. The snake lives on a 5x9 playfield rendered in the top
 * 45 slots of a size-54 chest inventory; the bottom row holds directional
 * buttons, a live score panel, and a restart button. The snake advances one
 * cell per tick on a self-scheduling timer whose interval shrinks as the
 * player eats more food.
 *
 * <p>Direction is buffered so a single click registers consistently even when
 * the next tick is still pending. Reversing onto the snake's own neck is
 * rejected to prevent instant self-collision.</p>
 */
public class SnakeSession extends AbstractGameSession {

    private static final int INVENTORY_SIZE = 54;
    private static final int PLAY_ROWS = 5;
    private static final int PLAY_COLS = 9;

    // Bottom-row control slots.
    private static final int SLOT_LEFT = 45;
    private static final int SLOT_UP = 46;
    private static final int SLOT_DOWN = 47;
    private static final int SLOT_RIGHT = 48;
    private static final int SLOT_SCORE = 50;
    private static final int SLOT_RESTART = 52;
    private static final int SLOT_PAUSE = 53;

    // Direction vectors (dr, dc): 0=up, 1=down, 2=left, 3=right.
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};
    private static final int DIR_UP = 0;
    private static final int DIR_DOWN = 1;
    private static final int DIR_LEFT = 2;
    private static final int DIR_RIGHT = 3;

    private final GameSnake game;
    private final Random random = new Random();

    /** Snake body: head is at the front of the deque (peekFirst). */
    private final Deque<int[]> body = new LinkedList<>();
    /** Lookup grid: true if a cell is currently occupied by the snake. */
    private final boolean[][] occupied = new boolean[PLAY_ROWS][PLAY_COLS];

    private int direction = DIR_RIGHT;
    /** Buffered next direction so quick double-clicks resolve in order. */
    private int pendingDirection = DIR_RIGHT;

    private int foodR = -1;
    private int foodC = -1;

    private long score = 0;
    private int foodEaten = 0;
    private int currentIntervalTicks;
    private boolean settled = false;
    private boolean paused = false;
    private boolean started = false;

    private BukkitTask task;

    public SnakeSession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.game = (GameSnake) game;
        this.currentIntervalTicks = this.game.getStartIntervalTicks();
        resetState();
    }

    private void resetState() {
        body.clear();
        for (boolean[] row : occupied) Arrays.fill(row, false);
        // Start in the middle of the grid, length 3, moving right.
        int startR = PLAY_ROWS / 2;
        int startC = 3;
        for (int i = 2; i >= 0; i--) {
            int r = startR;
            int c = startC - i;
            body.addLast(new int[]{r, c});
            occupied[r][c] = true;
        }
        direction = DIR_RIGHT;
        pendingDirection = DIR_RIGHT;
        score = 0;
        foodEaten = 0;
        settled = false;
        paused = false;
        finished = false;  // MUST reset so the new task doesn't immediately cancel
        currentIntervalTicks = game.getStartIntervalTicks();
        spawnFood();
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
        ItemStack bg = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < INVENTORY_SIZE; i++) inventory.setItem(i, bg);

        // Render playfield
        for (int r = 0; r < PLAY_ROWS; r++) {
            for (int c = 0; c < PLAY_COLS; c++) {
                inventory.setItem(r * 9 + c, emptyCell());
            }
        }
        // Food
        if (foodR >= 0 && foodC >= 0) {
            inventory.setItem(foodR * 9 + foodC, foodItem());
        }
        // Snake body
        int idx = 0;
        for (int[] cell : body) {
            boolean isHead = (idx == 0);
            inventory.setItem(cell[0] * 9 + cell[1], snakeItem(isHead));
            idx++;
        }

        // Controls
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
        String pauseLabel = paused ? game.lang("buttons.resume") : game.lang("buttons.pause");
        String pauseHint = paused ? game.lang("buttons.resumeHint") : game.lang("buttons.pauseHint");
        inventory.setItem(SLOT_PAUSE,
                Utility.createItem(Material.CLOCK, "&e\u23F8 " + pauseLabel, Utility.list("&7" + pauseHint)));

        // Score panel
        List<String> scoreLore = new ArrayList<>();
        scoreLore.add("&e" + game.lang("labels.score") + ": &f" + score);
        scoreLore.add("&a" + game.lang("labels.length") + ": &f" + body.size());
        scoreLore.add("&c" + game.lang("labels.food") + ": &f" + foodEaten);
        if (paused) scoreLore.add("&7" + game.lang("labels.paused"));
        if (settled) scoreLore.add("&c" + game.lang("labels.gameOver"));
        inventory.setItem(SLOT_SCORE,
                Utility.createItem(Material.BOOK, "&e" + game.lang("labels.score"), scoreLore));

        if (!started) {
            started = true;
            startTask();
        }
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= INVENTORY_SIZE) return;

        if (raw == SLOT_RESTART) {
            restart();
            return;
        }
        if (raw == SLOT_PAUSE) {
            paused = !paused;
            refresh();
            return;
        }
        if (settled) return;

        if (raw == SLOT_LEFT) pendingDirection = DIR_LEFT;
        else if (raw == SLOT_UP) pendingDirection = DIR_UP;
        else if (raw == SLOT_DOWN) pendingDirection = DIR_DOWN;
        else if (raw == SLOT_RIGHT) pendingDirection = DIR_RIGHT;
        else return;

        // Try to apply immediately if it's a safe direction; the timer
        // tick will pick up pendingDirection otherwise.
        tryApplyDirection();
    }

    private void tryApplyDirection() {
        if (pendingDirection != opposite(direction)) {
            direction = pendingDirection;
        }
    }

    private int opposite(int dir) {
        return switch (dir) {
            case DIR_UP -> DIR_DOWN;
            case DIR_DOWN -> DIR_UP;
            case DIR_LEFT -> DIR_RIGHT;
            case DIR_RIGHT -> DIR_LEFT;
            default -> dir;
        };
    }

    private void startTask() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (finished || settled) {
                    cancel();
                    return;
                }
                Player p = players.isEmpty() ? null : players.get(0);
                if (p == null || !p.isOnline()) {
                    cancel();
                    return;
                }
                if (paused) return;
                step();
            }
        }.runTaskTimer(plugin, currentIntervalTicks, currentIntervalTicks);
    }

    /** Reschedule the timer with a new interval (after speed-up). */
    private void rescheduleTask() {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
            task = null;
        }
        // Do NOT set started = false here — same fix as DinoRun.
        // Setting it causes build() (via refresh()) to create a duplicate
        // scheduler task, resulting in two step() loops running at once.
        startTask();
    }

    private void step() {
        tryApplyDirection();
        int[] head = body.peekFirst();
        int nr = head[0] + DR[direction];
        int nc = head[1] + DC[direction];

        // Wall collision
        if (nr < 0 || nr >= PLAY_ROWS || nc < 0 || nc >= PLAY_COLS) {
            endGame();
            return;
        }
        // Self collision: the tail will move away unless we eat food, so the
        // current tail cell is safe to step on when not growing.
        int[] tail = body.peekLast();
        boolean willGrow = (nr == foodR && nc == foodC);
        if (occupied[nr][nc]) {
            if (!(tail[0] == nr && tail[1] == nc) || willGrow) {
                endGame();
                return;
            }
        }

        // Move head forward
        body.addFirst(new int[]{nr, nc});
        occupied[nr][nc] = true;

        Player p = players.isEmpty() ? null : players.get(0);
        if (willGrow) {
            score += 10;
            foodEaten++;
            if (p != null) {
                game.onScoreChange(p, score, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
                game.onGameEvent(p, "food", foodEaten);
            }
            spawnFood();
            // Speed up every food eaten.
            int newInterval = Math.max(game.getMinIntervalTicks(),
                    currentIntervalTicks - game.getSpeedupTicks());
            if (newInterval != currentIntervalTicks) {
                currentIntervalTicks = newInterval;
                refresh();
                rescheduleTask();
                return;
            }
        } else {
            int[] removed = body.removeLast();
            occupied[removed[0]][removed[1]] = false;
        }
        refresh();
    }

    private void spawnFood() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < PLAY_ROWS; r++) {
            for (int c = 0; c < PLAY_COLS; c++) {
                if (!occupied[r][c]) empty.add(new int[]{r, c});
            }
        }
        if (empty.isEmpty()) {
            // Board full — player has effectively won.
            foodR = -1;
            foodC = -1;
            endGame();
            return;
        }
        int[] pick = empty.get(random.nextInt(empty.size()));
        foodR = pick[0];
        foodC = pick[1];
    }

    private void restart() {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
            task = null;
        }
        resetState();
        // Do NOT set started = false here. If we do, refresh() → build()
        // will call startTask(), and then we also call startTask() below,
        // creating TWO tasks that each call step() — the snake moves 2 cells
        // per tick and can skip collision detection.
        startTask();
        refresh();
    }

    private void endGame() {
        if (settled) return;
        settled = true;
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
            task = null;
        }
        Player p = players.isEmpty() ? null : players.get(0);
        // Surviving long enough (>=30 food) counts as a "win" for rewards.
        boolean won = foodEaten >= 30;
        if (p != null) {
            game.onGameWonSingle(p, won, score);
        }
        ((SnakeManager) game.getGameManager()).endSession(this);
        end();
    }

    @Override
    public void end() {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
            task = null;
        }
        super.end();
    }

    // ---- Items ----

    private ItemStack emptyCell() {
        return Utility.createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", null);
    }

    private ItemStack snakeItem(boolean isHead) {
        Material mat = isHead ? Material.LIME_WOOL : Material.GREEN_WOOL;
        String name = isHead ? "&a&l" + game.lang("labels.head") : "&2" + game.lang("labels.body");
        return Utility.createItem(mat, name, null);
    }

    private ItemStack foodItem() {
        return Utility.createItem(Material.REDSTONE_BLOCK,
                "&c&l" + game.lang("labels.foodItem"),
                Utility.list("&7" + game.lang("labels.foodHint")));
    }
}
