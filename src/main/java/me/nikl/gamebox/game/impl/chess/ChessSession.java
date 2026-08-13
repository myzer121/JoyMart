package me.nikl.gamebox.game.impl.chess;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Active Chess session. Plays the 6x6 Los Alamos chess variant (no bishops,
 * no castling, pawns move only one square) either against another player or
 * a simple two-ply minimax AI. The board occupies the left 6 columns of a
 * size-54 chest inventory; the right three columns hold captured pieces,
 * status, and control buttons.
 *
 * <p>White = player 1 (always starts). Black = player 2 (or AI in vsAi mode).
 * Click a piece to select it (legal destinations are highlighted), then click
 * a destination to move. Pawns auto-promote to queens on the last rank. The
 * game ends on checkmate, stalemate, or a player giving up via the Resign
 * button.</p>
 */
public class ChessSession extends AbstractGameSession {

    private static final int INVENTORY_SIZE = 54;
    private static final int BOARD_SIZE = 6;
    private static final int BOARD_COLS = 6;

    // Right-column control slots.
    private static final int SLOT_TURN = 8;
    private static final int SLOT_STATUS = 17;
    private static final int SLOT_MOVE_COUNT = 26;
    private static final int SLOT_RESTART = 35;
    private static final int SLOT_RESIGN = 44;
    private static final int SLOT_LEGEND = 53;

    // Captured-piece display column (col 7).
    private static final int[] CAPTURED_SLOTS = {7, 16, 25, 34, 43, 52};

    // Piece letters: uppercase = white, lowercase = black. ' ' = empty.
    private static final char EMPTY = ' ';
    private static final char WK = 'K', WQ = 'Q', WR = 'R', WN = 'N', WP = 'P';
    private static final char BK = 'k', BQ = 'q', BR = 'r', BN = 'n', BP = 'p';

    private final GameChess game;
    private final Random random = new Random();
    private final UUID p1Id;
    private final UUID p2Id;

    private char[][] board;
    private boolean whiteToMove = true;
    private int selectedR = -1;
    private int selectedC = -1;
    private List<int[]> legalFromSelected = new ArrayList<>();
    private int[] lastMove = null; // [fromR, fromC, toR, toC]
    private List<Character> capturedByWhite = new ArrayList<>();
    private List<Character> capturedByBlack = new ArrayList<>();
    private int moveCount = 0;
    private boolean settled = false;
    private boolean aiThinking = false;
    private BukkitTask aiTask;

    public ChessSession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.game = (GameChess) game;
        this.p1Id = players.get(0).getUniqueId();
        this.p2Id = players.size() > 1 ? players.get(1).getUniqueId() : AI_ID;
        this.vsAi = players.size() == 1;
        initBoard();
    }

    private void initBoard() {
        board = new char[BOARD_SIZE][BOARD_SIZE];
        for (char[] row : board) Arrays.fill(row, EMPTY);
        // Los Alamos setup: R N Q K N R on the back ranks, pawns on rank 2/5.
        char[] backRank = {WR, WN, WQ, WK, WN, WR};
        for (int c = 0; c < BOARD_COLS; c++) {
            board[0][c] = backRank[c];           // white back rank (row 0)
            board[1][c] = WP;                     // white pawns
            board[BOARD_SIZE - 2][c] = BP;        // black pawns
            board[BOARD_SIZE - 1][c] = Character.toLowerCase(backRank[c]); // black back rank
        }
        whiteToMove = true;
        selectedR = -1;
        selectedC = -1;
        legalFromSelected.clear();
        lastMove = null;
        capturedByWhite.clear();
        capturedByBlack.clear();
        moveCount = 0;
        settled = false;
        aiThinking = false;
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
        ItemStack border = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < INVENTORY_SIZE; i++) inventory.setItem(i, border);

        // Render the board
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_COLS; c++) {
                inventory.setItem(r * 9 + c, cellItem(r, c));
            }
        }

        // Captured pieces column (white's captures on top, black's on bottom)
        renderCaptured();

        // Right-column controls
        inventory.setItem(SLOT_TURN, turnItem());
        inventory.setItem(SLOT_STATUS, statusItem());
        inventory.setItem(SLOT_MOVE_COUNT,
                Utility.createItem(Material.PAPER, "&e" + game.lang("labels.moves"),
                        Utility.list("&f" + moveCount)));
        inventory.setItem(SLOT_RESTART,
                Utility.createItem(Material.REDSTONE_BLOCK, "&c&l\u21BB " + game.lang("buttons.restart"),
                        Utility.list("&7" + game.lang("buttons.restartHint"))));
        inventory.setItem(SLOT_RESIGN,
                Utility.createItem(Material.BARRIER, "&c" + game.lang("buttons.resign"),
                        Utility.list("&7" + game.lang("buttons.resignHint"))));
        inventory.setItem(SLOT_LEGEND,
                Utility.createItem(Material.BOOK, "&e" + game.lang("labels.legend"),
                        Utility.list(
                                "&fK &7= " + game.lang("labels.king"),
                                "&fQ &7= " + game.lang("labels.queen"),
                                "&fR &7= " + game.lang("labels.rook"),
                                "&fN &7= " + game.lang("labels.knight"),
                                "&fP &7= " + game.lang("labels.pawn")
                        )));
    }

    private void renderCaptured() {
        // Top half: pieces white has captured (black pieces).
        for (int i = 0; i < 3; i++) {
            int slot = CAPTURED_SLOTS[i];
            if (i < capturedByWhite.size()) {
                inventory.setItem(slot, pieceItem(capturedByWhite.get(i)));
            } else {
                inventory.setItem(slot, Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
            }
        }
        // Bottom half: pieces black has captured (white pieces).
        for (int i = 0; i < 3; i++) {
            int slot = CAPTURED_SLOTS[i + 3];
            if (i < capturedByBlack.size()) {
                inventory.setItem(slot, pieceItem(capturedByBlack.get(i)));
            } else {
                inventory.setItem(slot, Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
            }
        }
    }

    private ItemStack cellItem(int r, int c) {
        char piece = board[r][c];
        boolean isLight = (r + c) % 2 == 0;
        boolean isSelected = (r == selectedR && c == selectedC);
        boolean isLegalTarget = false;
        for (int[] m : legalFromSelected) {
            if (m[2] == r && m[3] == c) { isLegalTarget = true; break; }
        }
        boolean isLastMove = lastMove != null
                && ((lastMove[0] == r && lastMove[1] == c) || (lastMove[2] == r && lastMove[3] == c));

        // Background material when cell is empty or highlights underneath
        if (piece == EMPTY) {
            Material mat;
            if (isLegalTarget) mat = Material.LIME_STAINED_GLASS_PANE;
            else if (isLastMove) mat = Material.ORANGE_STAINED_GLASS_PANE;
            else mat = isLight ? Material.LIGHT_GRAY_CONCRETE : Material.BROWN_CONCRETE;
            String label = isLegalTarget ? "&a\u2022" : " ";
            return Utility.createItem(mat, label, null);
        }

        // Cell with a piece: show the piece, with a hint in the lore.
        List<String> lore = new ArrayList<>();
        if (isSelected) {
            lore.add("&e&l" + game.lang("labels.selected"));
        } else if (isLegalTarget) {
            lore.add("&c&l" + game.lang("labels.capture"));
        } else if (isLastMove) {
            lore.add("&6" + game.lang("labels.lastMove"));
        }
        return pieceItem(piece, lore);
    }

    /** Build the item representing a single piece (no lore). */
    private ItemStack pieceItem(char piece) {
        return pieceItem(piece, null);
    }

    private ItemStack pieceItem(char piece, List<String> lore) {
        Material mat;
        String name;
        boolean white = Character.isUpperCase(piece);
        char p = Character.toUpperCase(piece);
        switch (p) {
            case 'K':
                mat = white ? Material.GOLD_BLOCK : Material.REDSTONE_BLOCK;
                name = (white ? "&f&l" : "&8&l") + game.lang("labels.king");
                break;
            case 'Q':
                mat = white ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE;
                name = (white ? "&f&l" : "&8&l") + game.lang("labels.queen");
                break;
            case 'R':
                mat = white ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE;
                name = (white ? "&f&l" : "&8&l") + game.lang("labels.rook");
                break;
            case 'N':
                mat = white ? Material.BONE : Material.COAL_BLOCK;
                name = (white ? "&f&l" : "&8&l") + game.lang("labels.knight");
                break;
            case 'P':
                mat = white ? Material.WHITE_WOOL : Material.BLACK_WOOL;
                name = (white ? "&f&l" : "&8&l") + game.lang("labels.pawn");
                break;
            default:
                mat = Material.BARRIER;
                name = "?";
        }
        return Utility.createItem(mat, name, lore);
    }

    private ItemStack turnItem() {
        String color = whiteToMove ? game.lang("labels.white") : game.lang("labels.black");
        UUID currentId = whiteToMove ? p1Id : p2Id;
        String who = color + " &7(" + playerName(currentId) + ")";
        Material mat = whiteToMove ? Material.WHITE_WOOL : Material.BLACK_WOOL;
        List<String> lore = new ArrayList<>();
        lore.add("&f" + game.lang("labels.toMove").replace("%p%", who));
        if (aiThinking) lore.add("&7" + game.lang("labels.aiThinking"));
        boolean inCheck = isKingInCheck(whiteToMove);
        if (inCheck) lore.add("&c&l" + game.lang("labels.check"));
        return Utility.createItem(mat, "&e" + game.lang("labels.turn"), lore);
    }

    private ItemStack statusItem() {
        Material mat = Material.PAPER;
        String status;
        if (settled) {
            status = game.lang("labels.gameOver");
            mat = Material.BARRIER;
        } else if (isKingInCheck(whiteToMove)) {
            status = game.lang("labels.check");
            mat = Material.RED_WOOL;
        } else {
            status = game.lang("labels.normal");
        }
        return Utility.createItem(mat, "&e" + game.lang("labels.status"),
                Utility.list("&f" + status));
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        UUID id = player.getUniqueId();

        if (settled) {
            // Allow restart even after the game is over
            int raw = event.getRawSlot();
            if (raw == SLOT_RESTART) {
                restart();
            }
            return;
        }
        if (aiThinking) return; // ignore clicks while the AI is moving

        int raw = event.getRawSlot();
        if (raw == SLOT_RESTART) {
            restart();
            return;
        }
        if (raw == SLOT_RESIGN) {
            resign(player);
            return;
        }

        // Only the current player can move pieces
        UUID currentTurnId = whiteToMove ? p1Id : p2Id;
        if (!id.equals(currentTurnId)) {
            if (!vsAi || !id.equals(p2Id)) {
                player.sendMessage(Utility.color(plugin.lang("prefix")
                        + game.lang("messages.notYourTurn")));
            }
            return;
        }

        if (raw < 0 || raw >= INVENTORY_SIZE) return;

        int r = raw / 9;
        int c = raw % 9;
        if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_COLS) return;

        handleClick(r, c, player);
    }

    private void handleClick(int r, int c, Player player) {
        char piece = board[r][c];

        if (selectedR == -1) {
            // Selecting a piece: must be the current player's own color.
            if (piece != EMPTY && Character.isUpperCase(piece) == whiteToMove) {
                selectedR = r;
                selectedC = c;
                legalFromSelected = legalMovesFrom(r, c);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                refresh();
            }
            return;
        }

        // Already have a selection. Check if (r, c) is a legal destination.
        int[] chosen = null;
        for (int[] m : legalFromSelected) {
            if (m[2] == r && m[3] == c) { chosen = m; break; }
        }
        if (chosen != null) {
            // Execute the move
            makeMove(selectedR, selectedC, r, c, true);
            selectedR = -1;
            selectedC = -1;
            legalFromSelected.clear();
            whiteToMove = !whiteToMove;
            moveCount++;
            refresh();

            if (checkGameEnd()) return;

            // Schedule AI move only in vsAi mode when it's now the AI's turn
            if (vsAi && !whiteToMove) {
                aiThinking = true;
                refresh();
                aiTask = Bukkit.getScheduler().runTaskLater(plugin, this::aiMove, 40L);
            }
            return;
        }

        // Not a legal target. If the player clicked another own piece, reselect.
        if (piece != EMPTY && Character.isUpperCase(piece) == whiteToMove) {
            selectedR = r;
            selectedC = c;
            legalFromSelected = legalMovesFrom(r, c);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            refresh();
            return;
        }
        // Otherwise deselect.
        selectedR = -1;
        selectedC = -1;
        legalFromSelected.clear();
        refresh();
    }

    /** Apply a move on the board, handling captures and pawn promotion. */
    private void makeMove(int fromR, int fromC, int toR, int toC, boolean recordCapture) {
        char moving = board[fromR][fromC];
        char captured = board[toR][toC];
        boolean moverWhite = Character.isUpperCase(moving);
        if (recordCapture && captured != EMPTY) {
            if (moverWhite) {
                capturedByWhite.add(captured);
            } else {
                capturedByBlack.add(captured);
            }
            Player p = findPlayer(moverWhite ? p1Id : p2Id);
            if (p != null) {
                game.onGameEvent(p, "capture", pieceValue(captured));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.2f);
            }
        }
        // Pawn promotion: white pawn reaching row 0, black pawn reaching last row.
        boolean promoted = false;
        if (moving == WP && toR == 0) { moving = WQ; promoted = true; }
        else if (moving == BP && toR == BOARD_SIZE - 1) { moving = BQ; promoted = true; }
        board[toR][toC] = moving;
        board[fromR][fromC] = EMPTY;
        lastMove = new int[]{fromR, fromC, toR, toC};
        if (promoted) {
            Player p = findPlayer(moverWhite ? p1Id : p2Id);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.5f);
            }
        }
    }

    /** Find a Player in the session by UUID, or null (e.g. for AI_ID). */
    private Player findPlayer(UUID id) {
        for (Player p : players) {
            if (p != null && p.getUniqueId().equals(id)) return p;
        }
        return null;
    }

    /** Undo a move previously applied by makeMove (used by AI search). */
    private void undoMove(int fromR, int fromC, int toR, int toC, char captured, char movingBefore) {
        board[fromR][fromC] = movingBefore;
        board[toR][toC] = captured;
    }

    // ---- Move generation ----

    /** All legal moves from the given square for the current board state. */
    private List<int[]> legalMovesFrom(int r, int c) {
        List<int[]> pseudo = pseudoMovesFrom(r, c);
        List<int[]> legal = new ArrayList<>();
        char moving = board[r][c];
        boolean moverWhite = Character.isUpperCase(moving);
        for (int[] m : pseudo) {
            char captured = board[m[2]][m[3]];
            char saved = board[r][c];
            // Apply move temporarily (no promotion tracking for legality check)
            board[m[2]][m[3]] = moving;
            board[r][c] = EMPTY;
            if (!isKingInCheck(moverWhite)) {
                legal.add(m);
            }
            // Undo
            board[r][c] = saved;
            board[m[2]][m[3]] = captured;
        }
        return legal;
    }

    /** All pseudo-legal moves (ignoring check) for the piece at (r, c). */
    private List<int[]> pseudoMovesFrom(int r, int c) {
        char piece = board[r][c];
        if (piece == EMPTY) return new ArrayList<>();
        char p = Character.toUpperCase(piece);
        boolean white = Character.isUpperCase(piece);
        return switch (p) {
            case 'P' -> pawnMoves(r, c, white);
            case 'N' -> knightMoves(r, c, white);
            case 'B' -> new ArrayList<>(); // Los Alamos: no bishops
            case 'R' -> slideMoves(r, c, white, new int[][]{{-1,0},{1,0},{0,-1},{0,1}});
            case 'Q' -> slideMoves(r, c, white, new int[][]{
                    {-1,0},{1,0},{0,-1},{0,1},
                    {-1,-1},{-1,1},{1,-1},{1,1}});
            case 'K' -> kingMoves(r, c, white);
            default -> new ArrayList<>();
        };
    }

    private List<int[]> pawnMoves(int r, int c, boolean white) {
        List<int[]> moves = new ArrayList<>();
        int dir = white ? 1 : -1; // white pawns move from row 0 toward row 5
        int nr = r + dir;
        if (nr >= 0 && nr < BOARD_SIZE) {
            // Forward one (must be empty)
            if (board[nr][c] == EMPTY) {
                moves.add(new int[]{r, c, nr, c});
            }
            // Captures diagonally
            for (int dc : new int[]{-1, 1}) {
                int nc = c + dc;
                if (nc >= 0 && nc < BOARD_COLS) {
                    char target = board[nr][nc];
                    if (target != EMPTY && Character.isUpperCase(target) != white) {
                        moves.add(new int[]{r, c, nr, nc});
                    }
                }
            }
        }
        return moves;
    }

    private List<int[]> knightMoves(int r, int c, boolean white) {
        List<int[]> moves = new ArrayList<>();
        int[][] deltas = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : deltas) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr >= BOARD_SIZE || nc < 0 || nc >= BOARD_COLS) continue;
            char target = board[nr][nc];
            if (target == EMPTY || Character.isUpperCase(target) != white) {
                moves.add(new int[]{r, c, nr, nc});
            }
        }
        return moves;
    }

    private List<int[]> slideMoves(int r, int c, boolean white, int[][] dirs) {
        List<int[]> moves = new ArrayList<>();
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_COLS) {
                char target = board[nr][nc];
                if (target == EMPTY) {
                    moves.add(new int[]{r, c, nr, nc});
                } else {
                    if (Character.isUpperCase(target) != white) {
                        moves.add(new int[]{r, c, nr, nc});
                    }
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
        return moves;
    }

    private List<int[]> kingMoves(int r, int c, boolean white) {
        List<int[]> moves = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (nr < 0 || nr >= BOARD_SIZE || nc < 0 || nc >= BOARD_COLS) continue;
                char target = board[nr][nc];
                if (target == EMPTY || Character.isUpperCase(target) != white) {
                    moves.add(new int[]{r, c, nr, nc});
                }
            }
        }
        return moves;
    }

    /** All legal moves for the side to move. */
    private List<int[]> allLegalMoves(boolean white) {
        List<int[]> all = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_COLS; c++) {
                char p = board[r][c];
                if (p != EMPTY && Character.isUpperCase(p) == white) {
                    all.addAll(legalMovesFrom(r, c));
                }
            }
        }
        return all;
    }

    // ---- Check / mate detection ----

    /** True if the king of the given color is currently attacked. */
    private boolean isKingInCheck(boolean white) {
        // Find own king
        int kr = -1, kc = -1;
        char kingChar = white ? WK : BK;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_COLS; c++) {
                if (board[r][c] == kingChar) { kr = r; kc = c; break; }
            }
            if (kr >= 0) break;
        }
        if (kr < 0) return false; // king missing (shouldn't happen in legal play)
        return isSquareAttacked(kr, kc, !white);
    }

    /** True if any enemy piece of the given color attacks (r, c). */
    private boolean isSquareAttacked(int r, int c, boolean byWhite) {
        // Pawn attacks
        int pawnDir = byWhite ? -1 : 1; // a white pawn at (r-1, c±1) attacks (r, c)
        char pawnChar = byWhite ? WP : BP;
        for (int dc : new int[]{-1, 1}) {
            int pr = r + pawnDir;
            int pc = c + dc;
            if (pr >= 0 && pr < BOARD_SIZE && pc >= 0 && pc < BOARD_COLS) {
                if (board[pr][pc] == pawnChar) return true;
            }
        }
        // Knight attacks
        char knightChar = byWhite ? WN : BN;
        int[][] kd = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : kd) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr >= BOARD_SIZE || nc < 0 || nc >= BOARD_COLS) continue;
            if (board[nr][nc] == knightChar) return true;
        }
        // King attacks (adjacent)
        char kingChar = byWhite ? WK : BK;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (nr < 0 || nr >= BOARD_SIZE || nc < 0 || nc >= BOARD_COLS) continue;
                if (board[nr][nc] == kingChar) return true;
            }
        }
        // Sliding: rook/queen (orthogonal)
        int[][] ortho = {{-1,0},{1,0},{0,-1},{0,1}};
        char rookChar = byWhite ? WR : BR;
        char queenChar = byWhite ? WQ : BQ;
        for (int[] d : ortho) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_COLS) {
                char t = board[nr][nc];
                if (t != EMPTY) {
                    if (t == rookChar || t == queenChar) return true;
                    break;
                }
                nr += d[0]; nc += d[1];
            }
        }
        // Sliding: bishop/queen (diagonal) — Los Alamos has no bishops, but
        // queens still attack diagonally.
        int[][] diag = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] d : diag) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_COLS) {
                char t = board[nr][nc];
                if (t != EMPTY) {
                    if (t == queenChar) return true;
                    break;
                }
                nr += d[0]; nc += d[1];
            }
        }
        return false;
    }

    /** True if the side to move has no legal moves. */
    private boolean hasNoLegalMoves(boolean white) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_COLS; c++) {
                char p = board[r][c];
                if (p != EMPTY && Character.isUpperCase(p) == white) {
                    if (!legalMovesFrom(r, c).isEmpty()) return false;
                }
            }
        }
        return true;
    }

    /** Check for checkmate / stalemate after a move. Returns true if game ended.
     *  After a move, whiteToMove has been toggled to the side that must now move;
     *  we check whether THAT side has any legal moves. */
    private boolean checkGameEnd() {
        boolean sideToMoveInCheck = isKingInCheck(whiteToMove);
        if (hasNoLegalMoves(whiteToMove)) {
            if (sideToMoveInCheck) {
                // Checkmate — the side that just moved wins.
                finishGame(!whiteToMove, false);
            } else {
                // Stalemate — draw.
                finishGame(false, true);
            }
            return true;
        }
        if (sideToMoveInCheck) {
            broadcast(plugin.lang("prefix") + game.lang("messages.check"));
            for (Player p : players) {
                if (p != null && p.isOnline()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.8f);
                }
            }
        }
        return false;
    }

    // ---- AI ----

    /** Compute and play the AI's move (black). */
    private void aiMove() {
        aiThinking = false;
        if (settled || finished) return;
        List<int[]> moves = allLegalMoves(false);
        if (moves.isEmpty()) {
            // No moves: either checkmate (white wins) or stalemate (draw).
            if (isKingInCheck(false)) {
                finishGame(true, false);
            } else {
                finishGame(false, true);
            }
            return;
        }
        int[] best = pickBestMove(moves);
        if (best == null) best = moves.get(random.nextInt(moves.size()));
        makeMove(best[0], best[1], best[2], best[3], true);
        whiteToMove = true;
        moveCount++;
        refresh();
        // Check if the AI's move ends the game (player has no moves).
        checkGameEnd();
    }

    /**
     * Two-ply minimax: try every legal AI move, then assume the opponent
     * replies with the move that minimizes the AI's material. Pick the AI
     * move with the best resulting material. Ties are broken randomly.
     */
    private int[] pickBestMove(List<int[]> moves) {
        int bestScore = Integer.MIN_VALUE;
        List<int[]> bestMoves = new ArrayList<>();
        for (int[] m : moves) {
            char captured = board[m[2]][m[3]];
            char moving = board[m[0]][m[1]];
            char savedMoving = moving;
            char undoTarget = captured;
            // Handle promotion in simulation
            char simulated = moving;
            if (moving == BP && m[2] == BOARD_SIZE - 1) simulated = BQ;
            board[m[2]][m[3]] = simulated;
            board[m[0]][m[1]] = EMPTY;
            int score = evaluatePosition(false) - bestOpponentReply(true);
            // Slight bonus for capturing
            if (captured != EMPTY) score += 5;
            // Slight bonus for central squares
            int centerDist = Math.abs(m[2] - 2) + Math.abs(m[3] - 2);
            score += (4 - centerDist);
            // Undo
            board[m[0]][m[1]] = savedMoving;
            board[m[2]][m[3]] = undoTarget;
            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(m);
            } else if (score == bestScore) {
                bestMoves.add(m);
            }
        }
        if (bestMoves.isEmpty()) return null;
        return bestMoves.get(random.nextInt(bestMoves.size()));
    }

    /** Best material outcome the side `white` can achieve in one ply. */
    private int bestOpponentReply(boolean white) {
        List<int[]> moves = allLegalMovesForSearch(white);
        if (moves.isEmpty()) return 0;
        int best = Integer.MIN_VALUE;
        for (int[] m : moves) {
            char captured = board[m[2]][m[3]];
            char moving = board[m[0]][m[1]];
            char savedMoving = moving;
            char undoTarget = captured;
            char simulated = moving;
            if (moving == WP && m[2] == 0) simulated = WQ;
            else if (moving == BP && m[2] == BOARD_SIZE - 1) simulated = BQ;
            board[m[2]][m[3]] = simulated;
            board[m[0]][m[1]] = EMPTY;
            int score = evaluatePosition(white);
            if (captured != EMPTY) score += 10;
            // Undo
            board[m[0]][m[1]] = savedMoving;
            board[m[2]][m[3]] = undoTarget;
            if (score > best) best = score;
        }
        return best;
    }

    /** All pseudo-legal moves filtered for "doesn't leave own king in check" — used by the search. */
    private List<int[]> allLegalMovesForSearch(boolean white) {
        List<int[]> all = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_COLS; c++) {
                char p = board[r][c];
                if (p != EMPTY && Character.isUpperCase(p) == white) {
                    all.addAll(legalMovesFrom(r, c));
                }
            }
        }
        return all;
    }

    /** Material evaluation from the perspective of `white`. */
    private int evaluatePosition(boolean white) {
        int score = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_COLS; c++) {
                char p = board[r][c];
                if (p == EMPTY) continue;
                int v = pieceValue(p);
                if (Character.isUpperCase(p)) score += v;
                else score -= v;
            }
        }
        return white ? score : -score;
    }

    private int pieceValue(char p) {
        return switch (Character.toUpperCase(p)) {
            case 'P' -> 1;
            case 'N' -> 3;
            case 'B' -> 3;
            case 'R' -> 5;
            case 'Q' -> 9;
            case 'K' -> 1000;
            default -> 0;
        };
    }

    // ---- Game-end / lifecycle ----

    private void finishGame(boolean whiteWon, boolean draw) {
        if (settled) return;
        settled = true;

        // Broadcast result message to all players
        if (draw) {
            broadcast(plugin.lang("prefix") + game.lang("messages.draw"));
        } else {
            String winnerName = whiteWon ? game.lang("labels.white") : game.lang("labels.black");
            broadcast(plugin.lang("prefix") + game.lang("messages.win")
                    .replace("%player%", winnerName));
        }

        // Play win/lose sounds
        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;
            if (draw) continue;
            boolean isWinner = (whiteWon && p.getUniqueId().equals(p1Id))
                    || (!whiteWon && p.getUniqueId().equals(p2Id));
            if (isWinner) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            } else {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
            }
        }

        // Settle via two-player reward system (works for both vsAi and PvP)
        UUID winner, loser;
        if (draw) {
            winner = p1Id; // order doesn't matter for draw
            loser = p2Id;
        } else if (whiteWon) {
            winner = p1Id;
            loser = p2Id;
        } else {
            winner = p2Id;
            loser = p1Id;
        }
        game.onGameWonMulti(winner, loser, draw);

        ((ChessManager) game.getGameManager()).endSession(this);
        end();
    }

    private void resign(Player resigner) {
        if (settled) return;
        boolean resignerIsWhite = resigner.getUniqueId().equals(p1Id);
        // If white resigns, black wins (whiteWon=false); if black resigns, white wins
        finishGame(!resignerIsWhite, false);
    }

    private void restart() {
        if (aiTask != null) {
            try { aiTask.cancel(); } catch (IllegalStateException ignored) {}
            aiTask = null;
        }
        initBoard();
        refresh();
    }

    /** Send a colored message to all players in the session. */
    private void broadcast(String msg) {
        String colored = Utility.color(msg);
        for (Player p : players) {
            if (p != null && p.isOnline()) p.sendMessage(colored);
        }
    }

    @Override
    public void end() {
        if (aiTask != null) {
            try { aiTask.cancel(); } catch (IllegalStateException ignored) {}
            aiTask = null;
        }
        super.end();
    }
}
