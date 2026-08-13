package me.nikl.gamebox.game.impl.dinorun;

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
import java.util.List;
import java.util.Random;

/**
 * Active Dino Run session. Renders a side-scrolling endless runner inside a
 * size-54 chest inventory: 5 rows of playfield plus a bottom control row.
 *
 * <p>The dino occupies column 1 of the playfield and is either on the ground
 * (row 4) or jumping through rows 3/2/1 in a parabolic arc. Cacti scroll in
 * from the right edge (column 8) and move one cell left per scroll tick; if a
 * cactus reaches the dino's column while the dino is on the ground the game
 * ends. The world speed increases every 100 points.</p>
 */
public class DinoRunSession extends AbstractGameSession {

    private static final int INVENTORY_SIZE = 54;
    private static final int PLAY_ROWS = 5;
    private static final int PLAY_COLS = 9;
    private static final int GROUND_ROW = 4;          // row that holds the ground/dino/cacti
    private static final int DINO_COL = 1;            // column the dino runs in
    private static final int SPAWN_COL = 8;           // column where new cacti appear

    // Bottom-row control slots.
    private static final int SLOT_JUMP = 45;
    private static final int SLOT_RESTART = 46;
    private static final int SLOT_SCORE = 50;
    private static final int SLOT_PAUSE = 53;

    /** Jump arc: row offsets per scroll tick (0 = ground, larger = higher). */
    private static final int[] JUMP_ARC = {0, 2, 3, 3, 2, 0};

    private final GameDinoRun game;
    private final Random random = new Random();

    /** True if a cactus currently occupies the cell at (row, col). */
    private final boolean[][] cacti = new boolean[PLAY_ROWS][PLAY_COLS];

    private long score = 0;
    private int obstaclesCleared = 0;
    private int jumpTick = -1;        // -1 means on the ground
    private int nextObstacleIn = 4;   // scroll steps until next cactus spawns
    private int currentIntervalTicks;
    private boolean settled = false;
    private boolean paused = false;
    private boolean started = false;

    private BukkitTask task;

    public DinoRunSession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.game = (GameDinoRun) game;
        this.currentIntervalTicks = this.game.getScrollIntervalTicks();
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
        ItemStack sky = skyItem();
        ItemStack ground = groundItem();
        for (int r = 0; r < PLAY_ROWS; r++) {
            for (int c = 0; c < PLAY_COLS; c++) {
                inventory.setItem(r * 9 + c, r == GROUND_ROW ? ground : sky);
            }
        }
        // Cacti
        for (int r = 0; r < PLAY_ROWS; r++) {
            for (int c = 0; c < PLAY_COLS; c++) {
                if (cacti[r][c]) {
                    inventory.setItem(r * 9 + c, cactusItem());
                }
            }
        }
        // Dino (compute current row)
        int dinoRow = currentDinoRow();
        inventory.setItem(dinoRow * 9 + DINO_COL, dinoItem());

        // Bottom controls
        inventory.setItem(SLOT_JUMP,
                Utility.createItem(Material.EMERALD_BLOCK,
                        "&a&l\u25B2 " + game.lang("buttons.jump"),
                        Utility.list("&7" + game.lang("buttons.jumpHint"))));
        inventory.setItem(SLOT_RESTART,
                Utility.createItem(Material.REDSTONE_BLOCK,
                        "&c&l\u21BB " + game.lang("buttons.restart"),
                        Utility.list("&7" + game.lang("buttons.restartHint"))));
        String pauseLabel = paused ? game.lang("buttons.resume") : game.lang("buttons.pause");
        String pauseHint = paused ? game.lang("buttons.resumeHint") : game.lang("buttons.pauseHint");
        inventory.setItem(SLOT_PAUSE,
                Utility.createItem(Material.CLOCK, "&e\u23F8 " + pauseLabel, Utility.list("&7" + pauseHint)));

        // Score panel
        List<String> scoreLore = new ArrayList<>();
        scoreLore.add("&e" + game.lang("labels.score") + ": &f" + score);
        scoreLore.add("&a" + game.lang("labels.cleared") + ": &f" + obstaclesCleared);
        scoreLore.add("&7" + game.lang("labels.speed") + ": &f" + currentIntervalTicks + "t");
        if (paused) scoreLore.add("&7" + game.lang("labels.paused"));
        if (settled) scoreLore.add("&c" + game.lang("labels.gameOver"));
        inventory.setItem(SLOT_SCORE,
                Utility.createItem(Material.BOOK, "&e" + game.lang("labels.score"), scoreLore));

        if (!started) {
            started = true;
            startTask();
        }
    }

    private int currentDinoRow() {
        if (jumpTick < 0) return GROUND_ROW;
        int offset = JUMP_ARC[jumpTick];
        return GROUND_ROW - offset;
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

        // Jump on either the dedicated button or any cell in the dino's column.
        if (raw == SLOT_JUMP || (raw % 9 == DINO_COL && raw / 9 < PLAY_ROWS)) {
            tryJump();
        }
    }

    private void tryJump() {
        if (jumpTick < 0) {
            jumpTick = 0;
            Player p = players.isEmpty() ? null : players.get(0);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.7f, 1.4f);
            }
            refresh();
        }
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

    private void rescheduleTask() {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
            task = null;
        }
        // Do NOT set started = false here. Doing so would cause build()
        // (called via refresh()) to see started == false and start a
        // DUPLICATE task, resulting in two schedulers running step()
        // simultaneously — collision detection breaks and the dino can
        // pass through cacti.  We simply create a fresh task with the
        // new interval while keeping started == true.
        startTask();
    }

    private void step() {
        // Advance jump arc
        if (jumpTick >= 0) {
            jumpTick++;
            if (jumpTick >= JUMP_ARC.length) {
                jumpTick = -1; // back on the ground
            }
        }

        // Scroll all cacti one cell left
        for (int r = 0; r < PLAY_ROWS; r++) {
            for (int c = 0; c < PLAY_COLS - 1; c++) {
                cacti[r][c] = cacti[r][c + 1];
            }
            cacti[r][PLAY_COLS - 1] = false;
        }

        // Maybe spawn a new cactus at the right edge (on the ground)
        nextObstacleIn--;
        if (nextObstacleIn <= 0) {
            cacti[GROUND_ROW][SPAWN_COL] = true;
            nextObstacleIn = game.getMinObstacleGap()
                    + random.nextInt(Math.max(1, game.getMaxObstacleGap() - game.getMinObstacleGap() + 1));
        }

        // Score: +1 per scroll step survived
        score++;
        Player p = players.isEmpty() ? null : players.get(0);
        if (p != null) {
            game.onScoreChange(p, score, 1);
        }

        // Speed up every 100 points
        if (score > 0 && score % 100 == 0) {
            int newInterval = Math.max(3, currentIntervalTicks - 1);
            if (newInterval != currentIntervalTicks) {
                currentIntervalTicks = newInterval;
                rescheduleTask();
                // Do NOT return here — the collision check below must
                // still run on the same tick, otherwise the dino can
                // pass through a cactus on speed-up ticks.
            }
        }

        // Count obstacles cleared (cactus just left column DINO_COL+1 → passed)
        // Simpler: count when a cactus reaches column 0 (it's about to scroll off)
        // We count it the moment it passes column DINO_COL going left.
        // Because we already shifted, "passed" means it was at DINO_COL before
        // the shift. We approximate by counting when the cell at (GROUND_ROW, 0)
        // has a cactus (it scrolls off next tick) — track via a flag.

        // Collision check: cactus at dino's column while dino is on the ground
        if (cacti[GROUND_ROW][DINO_COL] && currentDinoRow() == GROUND_ROW) {
            refresh();
            endGame();
            return;
        }

        // Count cleared: if a cactus is at column 0, it will scroll off next
        // tick — count it as cleared now.
        if (cacti[GROUND_ROW][0]) {
            obstaclesCleared++;
            if (p != null) {
                game.onGameEvent(p, "cleared", obstaclesCleared);
                score += 5; // bonus for clearing
            }
        }

        refresh();
    }

    private void restart() {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
            task = null;
        }
        for (int r = 0; r < PLAY_ROWS; r++) {
            for (int c = 0; c < PLAY_COLS; c++) cacti[r][c] = false;
        }
        score = 0;
        obstaclesCleared = 0;
        jumpTick = -1;
        nextObstacleIn = 4;
        currentIntervalTicks = game.getScrollIntervalTicks();
        settled = false;
        paused = false;
        // Do NOT set started = false here. If we do, refresh() → build()
        // will call startTask(), and then we also call startTask() below,
        // creating TWO tasks that each call step() — cacti move 2 cells
        // per tick and can skip the dino's column entirely, making
        // collision detection fail.
        // Instead, just start one fresh task directly. started is still
        // true so build() won't create a duplicate.
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
        // Surviving past 200 score counts as a "win" for rewards.
        boolean won = score >= 200;
        if (p != null) {
            game.onGameWonSingle(p, won, score);
        }
        ((DinoRunManager) game.getGameManager()).endSession(this);
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

    private ItemStack skyItem() {
        return Utility.createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", null);
    }

    private ItemStack groundItem() {
        return Utility.createItem(Material.BROWN_CONCRETE, " ", null);
    }

    private ItemStack cactusItem() {
        return Utility.createItem(Material.CACTUS,
                "&2&l" + game.lang("labels.cactus"),
                Utility.list("&c" + game.lang("labels.cactusHint")));
    }

    private ItemStack dinoItem() {
        return Utility.createItem(Material.LIME_WOOL,
                "&a&l" + game.lang("labels.dino"),
                Utility.list("&7" + game.lang("labels.dinoHint")));
    }
}
