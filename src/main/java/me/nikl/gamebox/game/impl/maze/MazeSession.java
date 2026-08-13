package me.nikl.gamebox.game.impl.maze;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Maze session: a randomly generated maze rendered in a chest GUI. The player
 * starts at the top-left cell and must reach the bottom-right goal by clicking
 * adjacent open cells. Fewer moves yield a higher score
 * ({@code max(0, 1000 - moves*10)}).
 *
 * <p>The maze is carved with a recursive backtracker: rooms live at even grid
 * indices and the cells between them are knocked down to form corridors. Walls
 * are rendered as black concrete, corridors as light-gray, the player as green
 * wool, and the goal as a redstone block.</p>
 */
public class MazeSession extends me.nikl.gamebox.game.AbstractGameSession {

    private final GameMaze game;
    private final int n;
    private final boolean[][] open;
    private final int goalR;
    private final int goalC;

    private int playerR = 0;
    private int playerC = 0;
    private int moves = 0;
    private boolean settled = false;

    public MazeSession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.game = (GameMaze) game;
        this.n = this.game.getGridSize();
        this.open = new boolean[n][n];
        generateMaze();
        // Goal at the largest even index (a carved room), bottom-right corner.
        int g = (n - 1);
        if (g % 2 != 0) g = n - 2;
        if (g < 0) g = 0;
        this.goalR = g;
        this.goalC = g;
        this.playerR = 0;
        this.playerC = 0;
    }

    /** Recursive-backtracker maze generation carving distance-2 neighbors. */
    private void generateMaze() {
        for (int r = 0; r < n; r++) java.util.Arrays.fill(open[r], false);
        Random rand = ThreadLocalRandom.current();
        boolean[][] visited = new boolean[n][n];
        java.util.Deque<int[]> stack = new java.util.ArrayDeque<>();
        open[0][0] = true;
        visited[0][0] = true;
        stack.push(new int[]{0, 0});
        int[] dr = {-2, 2, 0, 0};
        int[] dc = {0, 0, -2, 2};
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            int r = cur[0], c = cur[1];
            // Collect unvisited distance-2 neighbors
            List<int[]> neigh = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int nr = r + dr[d], nc = c + dc[d];
                if (nr >= 0 && nr < n && nc >= 0 && nc < n && !visited[nr][nc]) {
                    neigh.add(new int[]{nr, nc, r + dr[d] / 2, c + dc[d] / 2});
                }
            }
            if (neigh.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] pick = neigh.get(rand.nextInt(neigh.size()));
            int nr = pick[0], nc = pick[1], wr = pick[2], wc = pick[3];
            open[wr][wc] = true;   // carve the wall between
            open[nr][nc] = true;   // open the room
            visited[nr][nc] = true;
            stack.push(new int[]{nr, nc});
        }
        // Ensure the goal room is open (it is a room at even indices).
        if (goalR >= 0 && goalC >= 0 && goalR < n && goalC < n) {
            open[goalR][goalC] = true;
        }
    }

    @Override
    protected int getInventorySize() {
        return Math.max(27, game.getRule().getGuiSize());
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    @Override
    public void build() {
        if (inventory == null) return;
        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getInventorySize(); i++) inventory.setItem(i, filler);

        ItemStack wall = Utility.createItem(Material.BLACK_CONCRETE, " ", null);
        ItemStack path = Utility.createItem(Material.LIGHT_GRAY_CONCRETE, " ", null);
        ItemStack goal = Utility.createItem(Material.REDSTONE_BLOCK,
                "&c" + game.lang("goal"), Utility.list(game.lang("goalHint")));

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int slot = r * 9 + c;
                if (slot >= getInventorySize()) continue;
                if (r == playerR && c == playerC) {
                    inventory.setItem(slot, Utility.createItem(Material.GREEN_WOOL,
                            "&a" + game.lang("you"), null));
                } else if (r == goalR && c == goalC) {
                    inventory.setItem(slot, goal);
                } else if (open[r][c]) {
                    inventory.setItem(slot, path);
                } else {
                    inventory.setItem(slot, wall);
                }
            }
        }

        // Info panel on the right (column 7 = slot +7 per row)
        int infoSlot = 7;
        if (infoSlot < getInventorySize()) {
            inventory.setItem(infoSlot, Utility.createItem(Material.PAPER,
                    game.lang("title"),
                    Utility.list(
                            game.lang("moves").replace("%m%", String.valueOf(moves)),
                            game.lang("moveHint"))));
        }
        // Close/give up at bottom-right
        int closeSlot = getInventorySize() - 1;
        inventory.setItem(closeSlot, Utility.createItem(Material.BARRIER,
                plugin.lang("gui.closeButton"), null));
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (settled) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= getInventorySize()) return;

        // Close button (bottom-right)
        if (raw == getInventorySize() - 1) {
            settled = true;
            ((MazeManager) game.getGameManager()).endSession(this);
            end();
            return;
        }

        int r = raw / 9;
        int c = raw % 9;
        if (r < 0 || r >= n || c < 0 || c >= n) return;

        // Only move to an adjacent open cell (Manhattan distance 1)
        int dist = Math.abs(r - playerR) + Math.abs(c - playerC);
        if (dist != 1) return;
        if (!open[r][c]) return;

        playerR = r;
        playerC = c;
        moves++;
        refresh();

        if (playerR == goalR && playerC == goalC) {
            win(player);
        }
    }

    private void win(Player player) {
        if (settled) return;
        settled = true;
        long score = Math.max(0, 1000L - moves * 10L);
        player.sendMessage(Utility.color(plugin.lang("prefix") + game.lang("winMessage")
                .replace("%m%", String.valueOf(moves)).replace("%s%", String.valueOf(score))));
        game.onGameWonSingle(player, true, score);
        ((MazeManager) game.getGameManager()).endSession(this);
        Bukkit.getScheduler().runTaskLater(plugin, this::end, 60L);
    }
}
