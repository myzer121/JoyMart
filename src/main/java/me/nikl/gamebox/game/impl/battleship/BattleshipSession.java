package me.nikl.gamebox.game.impl.battleship;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * A simplified, shared-inventory Battleship session.
 *
 * <p>Each player's fleet is auto-placed randomly at construction time. Players
 * alternate turns firing at a single ocean grid. Because both players share one
 * inventory, the grid always shows the <b>current attacker's</b> view of the
 * <b>defender's</b> board: unknown cells appear as fog, misses as blue wool and
 * hits as red wool. The defender's own ship layout is never leaked to the
 * attacker beyond cells that have already been fired upon.</p>
 */
public class BattleshipSession extends AbstractGameSession {

    private static final int INVENTORY_SIZE = 54;
    private static final int[] SHIP_SIZES = {4, 3, 2, 2};

    private final int gridCols;
    private final int gridRows;

    private final UUID p1Id;
    private final UUID p2Id;
    private UUID currentTurn;

    // 0 = water, shipId (>0) = a ship cell
    private final int[][] ships1;
    private final int[][] ships2;
    // Whether a cell on that player's board has already been fired upon
    private final boolean[][] fired1;
    private final boolean[][] fired2;
    private int remaining1;
    private int remaining2;

    // Per-player score: hit +100, sunk +500. Reported via onScoreChange.
    private int score1 = 0;
    private int score2 = 0;

    private boolean over = false;

    public BattleshipSession(GameBox plugin, GameBattleship game, List<Player> players) {
        super(plugin, game, players);
        this.gridCols = game.getGridCols();
        this.gridRows = game.getGridRows();
        this.p1Id = players.get(0).getUniqueId();
        this.p2Id = players.size() > 1 ? players.get(1).getUniqueId() : AI_ID;
        if (players.size() == 1) this.vsAi = true;
        this.currentTurn = p1Id;
        this.ships1 = placeFleet();
        this.ships2 = placeFleet();
        this.fired1 = new boolean[gridRows][gridCols];
        this.fired2 = new boolean[gridRows][gridCols];
        this.remaining1 = countShipCells(ships1);
        this.remaining2 = countShipCells(ships2);
    }

    @Override
    protected int getInventorySize() {
        return INVENTORY_SIZE;
    }

    @Override
    protected String getInventoryTitle() {
        return game.lang("title");
    }

    /** Randomly place the fleet of {@link #SHIP_SIZES} without overlap or out-of-bounds. */
    private int[][] placeFleet() {
        int[][] board = new int[gridRows][gridCols];
        Random rand = new Random();
        int shipId = 1;
        for (int size : SHIP_SIZES) {
            boolean placed = false;
            int attempts = 0;
            while (!placed && attempts < 2000) {
                attempts++;
                boolean horizontal = rand.nextBoolean();
                int maxR = horizontal ? gridRows : gridRows - size + 1;
                int maxC = horizontal ? gridCols - size + 1 : gridCols;
                if (maxR <= 0 || maxC <= 0) continue;
                int r = rand.nextInt(maxR);
                int c = rand.nextInt(maxC);
                boolean ok = true;
                for (int i = 0; i < size; i++) {
                    int rr = horizontal ? r : r + i;
                    int cc = horizontal ? c + i : c;
                    if (board[rr][cc] != 0) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                for (int i = 0; i < size; i++) {
                    int rr = horizontal ? r : r + i;
                    int cc = horizontal ? c + i : c;
                    board[rr][cc] = shipId;
                }
                placed = true;
            }
            shipId++;
        }
        return board;
    }

    private int countShipCells(int[][] board) {
        int n = 0;
        for (int[] row : board) {
            for (int v : row) {
                if (v > 0) n++;
            }
        }
        return n;
    }

    /** Whether every cell belonging to the given ship id has been fired upon. */
    private boolean isSunk(int[][] ships, boolean[][] fired, int shipId) {
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (ships[r][c] == shipId && !fired[r][c]) return false;
            }
        }
        return true;
    }

    /** Count the number of cells occupied by the given ship id (= ship size). */
    private int countShipSize(int[][] ships, int shipId) {
        int n = 0;
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (ships[r][c] == shipId) n++;
            }
        }
        return n;
    }

    /** Add delta to the attacker's score and return the new score. */
    private int addScore(Player attacker, int delta) {
        if (attacker.getUniqueId().equals(p1Id)) {
            score1 += delta;
            return score1;
        }
        score2 += delta;
        return score2;
    }

    @Override
    public void build() {
        if (inventory == null) return;

        boolean attackerIsP1 = currentTurn.equals(p1Id);
        int[][] defenderShips = attackerIsP1 ? ships2 : ships1;
        boolean[][] defenderFired = attackerIsP1 ? fired2 : fired1;

        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        ItemStack water = Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, game.lang("items.water"), null);
        ItemStack miss = Utility.createItem(Material.BLUE_WOOL, game.lang("items.miss"), null);
        ItemStack hit = Utility.createItem(Material.RED_WOOL, game.lang("items.hit"), null);

        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                int slot = r * 9 + c;
                ItemStack item;
                if (!defenderFired[r][c]) {
                    item = water;
                } else if (defenderShips[r][c] > 0) {
                    item = hit;
                } else {
                    item = miss;
                }
                inventory.setItem(slot, item);
            }
        }

        // Turn indicator (top-right).
        Material turnMat = attackerIsP1 ? Material.RED_WOOL : Material.YELLOW_WOOL;
        ItemStack turn = Utility.createItem(turnMat,
                game.lang("info.turn").replace("%player%", playerName(currentTurn)), null);
        inventory.setItem(8, turn);

        // Fleet status (bottom-right).
        ItemStack status = Utility.createItem(Material.PAPER,
                game.lang("info.status")
                        .replace("%p1%", playerName(p1Id))
                        .replace("%p2%", playerName(p2Id))
                        .replace("%r1%", String.valueOf(remaining1))
                        .replace("%r2%", String.valueOf(remaining2)),
                null);
        inventory.setItem(53, status);
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
        if (c >= gridCols || r >= gridRows) return; // info columns / out of board

        boolean attackerIsP1 = currentTurn.equals(p1Id);
        int[][] defenderShips = attackerIsP1 ? ships2 : ships1;
        boolean[][] defenderFired = attackerIsP1 ? fired2 : fired1;
        Player attacker = attackerIsP1 ? players.get(0) : null;

        if (defenderFired[r][c]) {
            player.sendMessage(Utility.color(game.lang("messages.alreadyFired")));
            return;
        }
        defenderFired[r][c] = true;

        if (defenderShips[r][c] > 0) {
            int shipId = defenderShips[r][c];
            if (attackerIsP1) remaining2--; else remaining1--;
            // Hit scoring: +100 per hit, reported live via onScoreChange.
            int hitScore = addScore(player, 100);
            game.onScoreChange(player, hitScore, 100);
            game.onGameEvent(player, "hit", 1);
            broadcast(game.lang("messages.hit")
                    .replace("%player%", playerName(currentTurn))
                    .replace("%row%", String.valueOf(r + 1))
                    .replace("%col%", String.valueOf(c + 1)));
            if (isSunk(defenderShips, defenderFired, shipId)) {
                int shipSize = countShipSize(defenderShips, shipId);
                // Sunk scoring: +500 per ship, reported live via onScoreChange.
                int sunkScore = addScore(player, 500);
                game.onScoreChange(player, sunkScore, 500);
                game.onGameEvent(player, "sunk", shipSize);
                broadcast(game.lang("messages.sunk").replace("%player%", playerName(currentTurn)));
            }
            int nowRemaining = attackerIsP1 ? remaining2 : remaining1;
            if (nowRemaining <= 0) {
                over = true;
                refresh();
                broadcast(game.lang("messages.win").replace("%player%", playerName(currentTurn)));
                UUID winner = attackerIsP1 ? p1Id : p2Id;
                UUID loser = attackerIsP1 ? p2Id : p1Id;
                settle(winner, loser, false);
                return;
            }
        } else {
            broadcast(game.lang("messages.miss")
                    .replace("%player%", playerName(currentTurn))
                    .replace("%row%", String.valueOf(r + 1))
                    .replace("%col%", String.valueOf(c + 1)));
        }

        currentTurn = attackerIsP1 ? p2Id : p1Id;
        broadcast(game.lang("messages.turn").replace("%player%", playerName(currentTurn)));
        refresh();

        // If it's now the AI's turn, schedule the AI move.
        if (vsAi && currentTurn.equals(p2Id) && !over) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, this::aiMove, 30L);
        }
    }

    /** Simple Battleship AI: fire at a random unfired cell. */
    private void aiMove() {
        if (over || finished) return;
        // AI fires at player 1's board (ships1/fired1)
        java.util.List<int[]> targets = new java.util.ArrayList<>();
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (!fired1[r][c]) targets.add(new int[]{r, c});
            }
        }
        if (targets.isEmpty()) return;
        int[] target = targets.get(new java.util.Random().nextInt(targets.size()));
        int r = target[0], c = target[1];
        fired1[r][c] = true;

        if (ships1[r][c] > 0) {
            int shipId = ships1[r][c];
            remaining1--;
            score2 += 100;
            broadcast(game.lang("messages.hit")
                    .replace("%player%", playerName(p2Id))
                    .replace("%row%", String.valueOf(r + 1))
                    .replace("%col%", String.valueOf(c + 1)));
            if (isSunk(ships1, fired1, shipId)) {
                score2 += 500;
                broadcast(game.lang("messages.sunk").replace("%player%", playerName(p2Id)));
            }
            if (remaining1 <= 0) {
                over = true;
                refresh();
                broadcast(game.lang("messages.win").replace("%player%", playerName(p2Id)));
                settle(p2Id, p1Id, false);
                return;
            }
        } else {
            broadcast(game.lang("messages.miss")
                    .replace("%player%", playerName(p2Id))
                    .replace("%row%", String.valueOf(r + 1))
                    .replace("%col%", String.valueOf(c + 1)));
        }
        currentTurn = p1Id;
        broadcast(game.lang("messages.turn").replace("%player%", playerName(p1Id)));
        refresh();
    }

    @Override
    public void start() {
        super.start();
        broadcast(game.lang("messages.fleetPlaced").replace("%cells%", String.valueOf(remaining1)));
        players.get(0).sendMessage(Utility.color(game.lang("messages.yourFleet")
                .replace("%cells%", String.valueOf(remaining1))));
        broadcast(game.lang("messages.turn").replace("%player%", playerName(p1Id)));
    }

    private void settle(UUID winner, UUID loser, boolean draw) {
        game.onGameWonMulti(winner, loser, draw);
        ((BattleshipManager) game.getGameManager()).endSession(this);
        end();
    }

    private void broadcast(String msg) {
        String colored = Utility.color(msg);
        for (Player p : players) {
            if (p != null && p.isOnline()) p.sendMessage(colored);
        }
    }
}
