package me.nikl.gamebox.game.impl.monopoly;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.game.AbstractGameSession;
import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.music.MusicPlayer;
import me.nikl.gamebox.music.NbsSong;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Monopoly game session shared by 2-3 players (or 1 human + AI). Renders the
 * 26-space board as the perimeter of a 6×9 inventory, with dice, player info,
 * and action buttons in the center.
 *
 * <h3>Turn flow</h3>
 * <ol>
 *   <li><b>ROLL</b> — current player clicks "Roll Dice"; a single die is thrown
 *       and the token advances that many spaces.</li>
 *   <li><b>ACTION</b> — if the landing space is an unowned property the player
 *       can buy it; if owned by another player, rent is auto-deducted; chance /
 *       chest spaces draw a random card. The player then clicks "End Turn".</li>
 * </ol>
 *
 * <p>A player whose balance drops to zero is bankrupt and eliminated. The last
 * non-bankrupt player wins.</p>
 */
public class MonopolySession extends AbstractGameSession {

    // ---- Game phases ----
    private static final int PHASE_ROLL = 0;
    private static final int PHASE_ACTION = 1;
    private static final int PHASE_GAME_OVER = 2;

    // ---- Center-button slots ----
    private static final int SLOT_P1_INFO = 10;
    private static final int SLOT_P2_INFO = 12;
    private static final int SLOT_P3_INFO = 14;
    private static final int SLOT_DICE = 22;
    private static final int SLOT_ROLL_BTN = 20;
    private static final int SLOT_ACTION_BTN = 24;
    private static final int SLOT_MESSAGE = 30;
    private static final int SLOT_MUSIC = 40;
    private static final int SLOT_FORFEIT = 43;
    // Betting slots (3-player mode only): pick a winner to bet on.
    private static final int SLOT_BET_P1 = 31;
    private static final int SLOT_BET_P2 = 32;
    private static final int SLOT_BET_P3 = 33;
    private static final int SLOT_BET_INFO = 34;

    // ---- Per-player display colors ----
    private static final String[] COLORS = {"&c", "&9", "&a"};
    private static final Material[] TOKEN_MATS = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.GREEN_CONCRETE
    };

    // ---- Game state ----
    private final int numPlayers;
    private final int[] positions;        // board position per player
    private final int[] balances;          // in-game money per player
    private final boolean[] bankrupt;      // bankrupt flag per player
    private final int[] jailTurns;         // turns remaining in jail (0 = not in jail)
    private final boolean[] isAi;          // AI flag per player
    private final UUID[] playerIds;        // UUID per player (AI_ID for AI)

    private int currentPlayer = 0;
    private int phase = PHASE_ROLL;
    private int lastDice = 0;
    private String lastMessage = "";

    /** Whether betting is still open (only before the first roll in 3-player mode). */
    private boolean bettingOpen = false;
    /** Bet amount each player has placed (0 = no bet yet). */
    private final int[] betAmount = new int[3];
    /** Which player index each player has bet on (-1 = no bet). */
    private final int[] betTarget = new int[]{-1, -1, -1};

    private final java.util.Random random = new java.util.Random();

    public MonopolySession(GameBox plugin, Game game, List<Player> players) {
        super(plugin, game, players);
        this.numPlayers = players.size() == 1 ? 2 : players.size(); // 1 human + AI = 2

        this.positions = new int[numPlayers];
        this.balances = new int[numPlayers];
        this.bankrupt = new boolean[numPlayers];
        this.jailTurns = new int[numPlayers];
        this.isAi = new boolean[numPlayers];
        this.playerIds = new UUID[numPlayers];

        GameMonopoly mg = (GameMonopoly) game;
        for (int i = 0; i < numPlayers; i++) {
            balances[i] = mg.getStartMoney();
            if (i < players.size()) {
                playerIds[i] = players.get(i).getUniqueId();
                isAi[i] = false;
            } else {
                playerIds[i] = AI_ID;
                isAi[i] = true;
            }
        }
        // vsAi mode: player 1 is the AI
        if (players.size() == 1) {
            isAi[1] = true;
            playerIds[1] = AI_ID;
            this.vsAi = true;
        }
        // Betting is only available in 3-player mode (not vsAi).
        this.bettingOpen = (numPlayers == 3 && !this.vsAi);
    }

    @Override
    protected int getInventorySize() { return 54; }

    @Override
    protected String getInventoryTitle() { return game.lang("title"); }

    // ---- Build / render ----

    @Override
    public void build() {
        ItemStack filler = Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < getInventorySize(); i++) {
            inventory.setItem(i, filler);
        }

        renderBoard();
        renderCenter();
    }

    private void renderBoard() {
        MonopolyProperty[] props = ((GameMonopoly) game).getProperties();
        for (int space = 0; space < MonopolyBoard.SIZE; space++) {
            int slot = MonopolyBoard.slotFor(space);
            inventory.setItem(slot, spaceItem(space, props));
        }
    }

    private ItemStack spaceItem(int space, MonopolyProperty[] props) {
        int type = MonopolyBoard.typeOf(space);
        switch (type) {
            case MonopolyBoard.TYPE_GO:
                return loreItem(Material.EMERALD_BLOCK, game.lang("go"),
                        buildLore("&a经过获得 &f" + ((GameMonopoly) game).getPassGoBonus() + "元", space));
            case MonopolyBoard.TYPE_JAIL:
                return loreItem(Material.IRON_BARS, game.lang("jail"),
                        buildLore("&7仅仅是路过", space));
            case MonopolyBoard.TYPE_FREE_PARKING:
                return loreItem(Material.CYAN_CONCRETE, game.lang("freeParking"),
                        buildLore("&7无事发生", space));
            case MonopolyBoard.TYPE_GO_TO_JAIL:
                return loreItem(Material.REDSTONE_BLOCK, game.lang("goToJail"),
                        buildLore("&c直接送进监狱!", space));
            case MonopolyBoard.TYPE_CHANCE:
                return loreItem(Material.PURPLE_SHULKER_BOX, game.lang("chance"),
                        buildLore("&7抽取机会卡", space));
            case MonopolyBoard.TYPE_CHEST:
                return loreItem(Material.CYAN_SHULKER_BOX, game.lang("communityChest"),
                        buildLore("&7抽取命运卡", space));
            default:
                return propertyItem(space, props);
        }
    }

    /** Build a lore list from a description line plus the players on the space. */
    private List<String> buildLore(String desc, int space) {
        List<String> lore = new ArrayList<>();
        lore.add(desc);
        lore.addAll(playersOnSpace(space));
        return lore;
    }

    private ItemStack propertyItem(int space, MonopolyProperty[] props) {
        int idx = MonopolyBoard.propertyIndex(space);
        if (idx < 0 || idx >= props.length) {
            return Utility.createItem(Material.BARRIER, "&c?", null);
        }
        MonopolyProperty prop = props[idx];
        List<String> lore = new ArrayList<>();
        if (prop.isOwned()) {
            lore.add(Utility.replace(game.lang("propertyOwned"),
                    new String[]{"%owner%", coloredName(prop.getOwner())}));
        } else {
            lore.add(Utility.replace(Utility.replace(game.lang("propertyUnowned"),
                    new String[]{"%price%", String.valueOf(prop.getPrice())}),
                    new String[]{"%rent%", String.valueOf(prop.getRent())}));
        }
        lore.addAll(playersOnSpace(space));
        Material mat = prop.isOwned() ? TOKEN_MATS[prop.getOwner()] : Material.GRAY_CONCRETE;
        return Utility.createItem(mat, prop.getName(), lore);
    }

    /** Returns a list of colored player names currently on the given space. */
    private List<String> playersOnSpace(int space) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            if (bankrupt[i]) continue;
            if (positions[i] == space) {
                lines.add(COLORS[i] + "● " + playerName(playerIds[i]));
            }
        }
        return lines;
    }

    private void renderCenter() {
        // Use the first online real player for music display state
        Player playerForRender = null;
        for (Player p : players) {
            if (p.isOnline() && !isAi[playerIndex(p)]) { playerForRender = p; break; }
        }
        if (playerForRender == null && !players.isEmpty()) playerForRender = players.get(0);

        // Player info panels
        int[] infoSlots = {SLOT_P1_INFO, SLOT_P2_INFO, SLOT_P3_INFO};
        for (int i = 0; i < numPlayers && i < 3; i++) {
            inventory.setItem(infoSlots[i], playerInfoItem(i));
        }
        // Fill unused info slots with filler
        for (int i = numPlayers; i < 3; i++) {
            inventory.setItem(infoSlots[i],
                    Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }

        // Dice display
        inventory.setItem(SLOT_DICE, diceItem());

        // Roll / Action button depending on phase
        if (phase == PHASE_ROLL) {
            setActionButton(SLOT_ROLL_BTN, Material.EMERALD_BLOCK,
                    "&a" + game.lang("rollDice"), null, this::handleRoll);
            inventory.setItem(SLOT_ACTION_BTN,
                    Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        } else if (phase == PHASE_ACTION) {
            inventory.setItem(SLOT_ROLL_BTN,
                    Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
            renderActionButton();
        } else {
            inventory.setItem(SLOT_ROLL_BTN,
                    Utility.createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null));
            inventory.setItem(SLOT_ACTION_BTN,
                    Utility.createItem(Material.NETHER_STAR, "&6" + game.lang("gameOver")
                            .replace("%player%", coloredName(currentPlayer)), null));
        }

        // Message area
        if (lastMessage != null && !lastMessage.isEmpty()) {
            inventory.setItem(SLOT_MESSAGE,
                    Utility.createItem(Material.WRITABLE_BOOK, "&e" + lastMessage, null));
        } else {
            inventory.setItem(SLOT_MESSAGE,
                    Utility.createItem(Material.WRITABLE_BOOK, "&e大富翁", null));
        }

        // Betting area (3-player mode only, while betting is open)
        renderBettingArea();

        // Music player toggle button (toggles play/pause without leaving game)
        if (plugin.getMusicPlayer() != null) {
            boolean playing = plugin.getMusicPlayer().isPlaying(playerForRender);
            Material musicMat = playing ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK;
            String musicName = playing ? "&c暂停音乐" : "&a播放音乐";
            NbsSong cur = plugin.getMusicPlayer().getCurrentSong(playerForRender);
            List<String> musicLore = new ArrayList<>();
            if (cur != null) {
                musicLore.add("&7当前: &f" + cur.name);
            }
            musicLore.add("&7点击切换播放/暂停");
            musicLore.add("&7右键: 下一首");
            inventory.setItem(SLOT_MUSIC,
                    Utility.createItem(musicMat, musicName, musicLore));
        }

        // Forfeit button
        inventory.setItem(SLOT_FORFEIT,
                Utility.createItem(Material.BARRIER, "&c" + game.lang("forfeit"),
                        Utility.list(game.lang("forfeitLore"))));
    }

    /**
     * Render the betting area in 3-player mode. While betting is open (before
     * the first dice roll), each player can click another player's head to
     * place a bet predicting them as the winner. Left-click adds 50 to the
     * bet, shift+left-click adds 500, right-click removes the bet entirely.
     * Bet amounts are deducted from the player's in-game balance and held in
     * the pot until game-over settlement.
     */
    private void renderBettingArea() {
        if (numPlayers != 3 || vsAi) {
            // Hide betting slots when not applicable
            for (int s : new int[]{SLOT_BET_P1, SLOT_BET_P2, SLOT_BET_P3, SLOT_BET_INFO}) {
                inventory.setItem(s, Utility.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
            }
            return;
        }

        int[] betSlots = {SLOT_BET_P1, SLOT_BET_P2, SLOT_BET_P3};
        Material[] betMats = {Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.GREEN_CONCRETE};
        for (int i = 0; i < 3; i++) {
            List<String> lore = new ArrayList<>();
            if (bettingOpen) {
                lore.add("&7左键: &a+50 押注此人");
                lore.add("&7Shift+左键: &a+500");
                lore.add("&7右键: &c取消押注");
            }
            // Show bets placed on this player
            int totalOnMe = 0;
            for (int b = 0; b < 3; b++) {
                if (betTarget[b] == i) totalOnMe += betAmount[b];
            }
            if (totalOnMe > 0) lore.add("&e总押注: &f" + totalOnMe);
            // Show which player this bettor has chosen
            for (int b = 0; b < 3; b++) {
                if (betTarget[b] == i && betAmount[b] > 0) {
                    lore.add(COLORS[b] + playerName(playerIds[b]) + " &7押 &f" + betAmount[b]);
                }
            }
            String label = (bettingOpen ? "&a押 " : "&7") + coloredName(i);
            inventory.setItem(betSlots[i], Utility.createItem(betMats[i], label, lore));
        }

        // Info / pot display
        int pot = 0;
        for (int b = 0; b < 3; b++) pot += betAmount[b];
        List<String> infoLore = new ArrayList<>();
        if (bettingOpen) {
            infoLore.add("&a押注阶段 (首次掷骰前)");
            infoLore.add("&7押对赢家: &f2倍返还");
            infoLore.add("&7押错: &c损失押注");
        } else {
            infoLore.add("&7押注已关闭");
        }
        infoLore.add("&6奖池: &f" + pot);
        inventory.setItem(SLOT_BET_INFO,
                Utility.createItem(Material.GOLD_BLOCK, "&e&l押注", infoLore));
    }

    /** Handle a click in the betting area. */
    private void handleBetClick(Player player, int targetIdx, boolean rightClick, boolean shift) {
        if (!bettingOpen) return;
        if (numPlayers != 3 || vsAi) return;
        int bettor = playerIndex(player);
        if (bettor < 0 || isAi[bettor]) return;
        if (bankrupt[bettor]) return;
        if (targetIdx < 0 || targetIdx >= numPlayers) return;
        if (targetIdx == bettor) return; // can't bet on yourself

        if (rightClick) {
            // Cancel / refund
            if (betAmount[bettor] > 0) {
                balances[bettor] += betAmount[bettor];
                msg(Utility.replace("&e%player% &7取消了 &f%amount% &7的押注",
                        new String[]{"%player%", coloredName(bettor)},
                        new String[]{"%amount%", String.valueOf(betAmount[bettor])}));
                betAmount[bettor] = 0;
                betTarget[bettor] = -1;
            }
        } else {
            int amt = shift ? 500 : 50;
            if (balances[bettor] < amt) {
                player.sendMessage(Utility.color(plugin.lang("prefix") + "&c余额不足以下注"));
                return;
            }
            balances[bettor] -= amt;
            betAmount[bettor] += amt;
            betTarget[bettor] = targetIdx;
            msg(Utility.replace("&e%player% &7押注 &f%amount% &7于 &f%target%",
                    new String[]{"%player%", coloredName(bettor)},
                    new String[]{"%amount%", String.valueOf(amt)},
                    new String[]{"%target%", coloredName(targetIdx)}));
        }
        refresh();
    }

    /** Settle all bets at game-over: winners get 2x their bet back. */
    private void settleBets(int winnerIdx) {
        if (numPlayers != 3 || vsAi) return;
        for (int b = 0; b < 3; b++) {
            if (betAmount[b] <= 0) continue;
            if (betTarget[b] == winnerIdx) {
                // Winner: refund bet + equal winnings (2x total)
                balances[b] += betAmount[b] * 2;
                msg(Utility.replace("&e%player% &a押中赢家! 获得 &f%amount%",
                        new String[]{"%player%", coloredName(b)},
                        new String[]{"%amount%", String.valueOf(betAmount[b] * 2)}));
            } else {
                msg(Utility.replace("&e%player% &c押注失败, 损失 &f%amount%",
                        new String[]{"%player%", coloredName(b)},
                        new String[]{"%amount%", String.valueOf(betAmount[b])}));
            }
        }
    }

    private void renderActionButton() {
        int space = positions[currentPlayer];
        int type = MonopolyBoard.typeOf(space);
        if (type == MonopolyBoard.TYPE_PROPERTY) {
            MonopolyProperty prop = ((GameMonopoly) game).propertyAt(space);
            if (prop != null && !prop.isOwned() && balances[currentPlayer] >= prop.getPrice()) {
                setActionButton(SLOT_ACTION_BTN, Material.GOLD_BLOCK,
                        Utility.replace(game.lang("buyProperty"),
                                new String[]{"%price%", String.valueOf(prop.getPrice())}),
                        null, this::handleBuy);
                return;
            }
        }
        // Default: End Turn
        setActionButton(SLOT_ACTION_BTN, Material.ARROW,
                "&e" + game.lang("endTurn"), null, this::handleEndTurn);
    }

    private ItemStack playerInfoItem(int playerIdx) {
        String name = coloredName(playerIdx);
        List<String> lore = new ArrayList<>();
        lore.add(Utility.replace(game.lang("balance"),
                new String[]{"%amount%", String.valueOf(balances[playerIdx])}));
        lore.add(Utility.replace(game.lang("position"),
                new String[]{"%pos%", String.valueOf(positions[playerIdx])}));
        int propCount = countProperties(playerIdx);
        lore.add(Utility.replace(game.lang("properties"),
                new String[]{"%count%", String.valueOf(propCount)}));
        if (bankrupt[playerIdx]) lore.add(game.lang("bankruptStatus"));
        if (jailTurns[playerIdx] > 0) lore.add("&7在监狱中 (" + jailTurns[playerIdx] + "回合)");
        if (playerIdx == currentPlayer && phase != PHASE_GAME_OVER) {
            lore.add("&e⟵ 当前回合");
        }

        // Use player skull for real players, skeleton skull for AI
        if (isAi[playerIdx]) {
            return Utility.createItem(Material.SKELETON_SKULL, name, lore);
        }
        Player p = Bukkit.getPlayer(playerIds[playerIdx]);
        if (p != null) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.setDisplayName(Utility.color(name));
                meta.setLore(Utility.color(lore));
                skull.setItemMeta(meta);
            }
            return skull;
        }
        return Utility.createItem(Material.PLAYER_HEAD, name, lore);
    }

    private ItemStack diceItem() {
        Material mat;
        switch (lastDice) {
            case 1: mat = Material.WHITE_CONCRETE; break;
            case 2: mat = Material.LIGHT_GRAY_CONCRETE; break;
            case 3: mat = Material.GRAY_CONCRETE; break;
            case 4: mat = Material.YELLOW_CONCRETE; break;
            case 5: mat = Material.ORANGE_CONCRETE; break;
            case 6: mat = Material.RED_CONCRETE; break;
            default: mat = Material.BARRIER; break;
        }
        String name = lastDice > 0 ? "&6&l骰子: &f" + lastDice : "&6&l掷骰子";
        return Utility.createItem(mat, name,
                lastDice > 0 ? Utility.list("&7上次点数: " + lastDice) : null);
    }

    // ---- Click handling ----

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        // Music button: left-click toggles pause, right-click plays next song.
        // This avoids opening a separate GUI (which would close and forfeit
        // the session).
        if (slot == SLOT_MUSIC) {
            if (plugin.getMusicPlayer() != null) {
                if (event.isRightClick()) {
                    plugin.getMusicPlayer().playNext(player);
                } else {
                    plugin.getMusicPlayer().togglePause(player);
                }
                refresh();
            }
            return;
        }

        if (finished || phase == PHASE_GAME_OVER) {
            if (slot == SLOT_FORFEIT) {
                handleForfeit(player);
            }
            return;
        }

        if (slot < 0 || slot >= getInventorySize()) return;

        if (slot == SLOT_FORFEIT) {
            handleForfeit(player);
            return;
        }

        // Betting area (3-player mode, while betting is open)
        if (slot == SLOT_BET_P1 || slot == SLOT_BET_P2 || slot == SLOT_BET_P3) {
            int targetIdx = slot == SLOT_BET_P1 ? 0 : (slot == SLOT_BET_P2 ? 1 : 2);
            handleBetClick(player, targetIdx, event.isRightClick(), event.isShiftClick());
            return;
        }

        // The first dice roll closes the betting window.
        if (slot == SLOT_ROLL_BTN && phase == PHASE_ROLL && bettingOpen) {
            bettingOpen = false;
            if (numPlayers == 3 && !vsAi) {
                msg("&7押注阶段结束，游戏正式开始!");
            }
        }

        // Only the current player can roll / act
        UUID clickerId = player.getUniqueId();
        if (!clickerId.equals(playerIds[currentPlayer]) && !isAi[currentPlayer]) {
            return;
        }

        // Route to the button at this slot
        try {
            if (slot == SLOT_ROLL_BTN && phase == PHASE_ROLL) {
                handleRoll(player);
            } else if (slot == SLOT_ACTION_BTN && phase == PHASE_ACTION) {
                handleActionClick(player);
            }
        } catch (Exception ignored) {}
    }

    private void handleActionClick(Player player) {
        int space = positions[currentPlayer];
        int type = MonopolyBoard.typeOf(space);
        if (type == MonopolyBoard.TYPE_PROPERTY) {
            MonopolyProperty prop = ((GameMonopoly) game).propertyAt(space);
            if (prop != null && !prop.isOwned() && balances[currentPlayer] >= prop.getPrice()) {
                handleBuy(player);
                return;
            }
        }
        handleEndTurn(player);
    }

    // ---- Game actions ----

    private void handleRoll(Player player) {
        rollAndMove();
    }

    private void rollAndMove() {
        if (finished) return;  // Session ended (player quit during AI turn)
        lastDice = random.nextInt(6) + 1;
        int oldPos = positions[currentPlayer];

        // If in jail, check for "doubles" (even = free, odd = stay, simplified)
        if (jailTurns[currentPlayer] > 0) {
            // Simplified jail: 50% chance to get out each roll
            if (lastDice % 2 == 0 || jailTurns[currentPlayer] <= 1) {
                jailTurns[currentPlayer] = 0;
                msg(Utility.replace(game.lang("paidJailFee"),
                        new String[]{"%player%", coloredName(currentPlayer)},
                        new String[]{"%fee%", "0"}));
                // Move normally
            } else {
                jailTurns[currentPlayer]--;
                msg(Utility.replace(game.lang("jailStay"),
                        new String[]{"%player%", coloredName(currentPlayer)}));
                advanceTurn();
                return;
            }
        }

        int newPos = (oldPos + lastDice) % MonopolyBoard.SIZE;
        // Check if passed GO
        if (newPos < oldPos || (oldPos + lastDice) >= MonopolyBoard.SIZE) {
            int bonus = ((GameMonopoly) game).getPassGoBonus();
            balances[currentPlayer] += bonus;
            msg(Utility.replace(game.lang("passedGo"),
                    new String[]{"%player%", coloredName(currentPlayer)},
                    new String[]{"%bonus%", String.valueOf(bonus)}));
        }
        positions[currentPlayer] = newPos;

        msg(Utility.replace(game.lang("rolled"),
                new String[]{"%player%", coloredName(currentPlayer)},
                new String[]{"%dice%", String.valueOf(lastDice)}));

        // Handle landing
        handleLanding();
        refresh();

        // If AI, schedule action
        if (phase == PHASE_ACTION && isAi[currentPlayer]) {
            Bukkit.getScheduler().runTaskLater(plugin, this::aiAction, 30L);
        }
    }

    private void handleLanding() {
        int space = positions[currentPlayer];
        int type = MonopolyBoard.typeOf(space);

        switch (type) {
            case MonopolyBoard.TYPE_GO_TO_JAIL:
                positions[currentPlayer] = 8; // Jail position
                jailTurns[currentPlayer] = 3;
                msg(Utility.replace(game.lang("wentToJail"),
                        new String[]{"%player%", coloredName(currentPlayer)}));
                phase = PHASE_ACTION;
                break;
            case MonopolyBoard.TYPE_CHANCE:
                drawChanceCard();
                phase = PHASE_ACTION;
                break;
            case MonopolyBoard.TYPE_CHEST:
                drawChestCard();
                phase = PHASE_ACTION;
                break;
            case MonopolyBoard.TYPE_PROPERTY:
                MonopolyProperty prop = ((GameMonopoly) game).propertyAt(space);
                if (prop != null && prop.isOwned() && prop.getOwner() != currentPlayer) {
                    // Pay rent
                    int rent = prop.getRent();
                    balances[currentPlayer] -= rent;
                    balances[prop.getOwner()] += rent;
                    msg(Utility.replace(game.lang("paidRent"),
                            new String[]{"%player%", coloredName(currentPlayer)},
                            new String[]{"%owner%", coloredName(prop.getOwner())},
                            new String[]{"%rent%", String.valueOf(rent)}));
                    checkBankruptcy();
                }
                phase = PHASE_ACTION;
                break;
            default:
                // GO, Jail, Free Parking — no action needed
                phase = PHASE_ACTION;
                break;
        }
    }

    private void handleBuy(Player player) {
        int space = positions[currentPlayer];
        MonopolyProperty prop = ((GameMonopoly) game).propertyAt(space);
        if (prop == null || prop.isOwned()) {
            return;
        }
        if (balances[currentPlayer] < prop.getPrice()) {
            msg(game.lang("cannotAfford"));
            refresh();
            return;
        }
        balances[currentPlayer] -= prop.getPrice();
        prop.setOwner(currentPlayer);
        msg(Utility.replace(game.lang("boughtProperty"),
                new String[]{"%player%", coloredName(currentPlayer)},
                new String[]{"%property%", Utility.color(prop.getName())},
                new String[]{"%price%", String.valueOf(prop.getPrice())}));
        refresh();
    }

    private void handleEndTurn(Player player) {
        advanceTurn();
    }

    private void handleForfeit(Player player) {
        // Find the player index
        for (int i = 0; i < numPlayers; i++) {
            if (playerIds[i].equals(player.getUniqueId())) {
                bankrupt[i] = true;
                msg(Utility.replace(game.lang("bankrupt"),
                        new String[]{"%player%", coloredName(i)}));
                break;
            }
        }
        if (checkGameOver()) return;
        // If the forfeiting player was current, advance turn
        if (playerIds[currentPlayer].equals(player.getUniqueId())) {
            advanceTurn();
        } else {
            refresh();
        }
    }

    /** Find the player index for a given Bukkit Player, or -1. */
    private int playerIndex(Player p) {
        for (int i = 0; i < numPlayers; i++) {
            if (playerIds[i].equals(p.getUniqueId())) return i;
        }
        return -1;
    }

    // ---- Card system ----

    private void drawChanceCard() {
        List<String> cards = game.getLanguage().getStringList(
                "games.monopoly.chanceCards");
        if (cards == null || cards.isEmpty()) {
            phase = PHASE_ACTION;
            return;
        }
        String card = cards.get(random.nextInt(cards.size()));
        msg(Utility.replace(game.lang("drawCard"),
                new String[]{"%player%", coloredName(currentPlayer)},
                new String[]{"%card%", Utility.color(card)}));
        applyCardEffect(card);
    }

    private void drawChestCard() {
        List<String> cards = game.getLanguage().getStringList(
                "games.monopoly.communityChest");
        if (cards == null || cards.isEmpty()) {
            phase = PHASE_ACTION;
            return;
        }
        String card = cards.get(random.nextInt(cards.size()));
        msg(Utility.replace(game.lang("drawCard"),
                new String[]{"%player%", coloredName(currentPlayer)},
                new String[]{"%card%", Utility.color(card)}));
        applyCardEffect(card);
    }

    private void applyCardEffect(String card) {
        // Parse the card text for money effects
        if (card.contains("获得") || card.contains("Receive")) {
            int amount = extractAmount(card);
            if (amount > 0) balances[currentPlayer] += amount;
        } else if (card.contains("支付") || card.contains("Pay")) {
            int amount = extractAmount(card);
            if (amount > 0) {
                balances[currentPlayer] -= amount;
                checkBankruptcy();
            }
        } else if (card.contains("GO") && (card.contains("前进") || card.contains("Advance"))) {
            positions[currentPlayer] = 0;
            balances[currentPlayer] += ((GameMonopoly) game).getPassGoBonus();
        } else if (card.contains("入狱") || card.contains("jail")) {
            positions[currentPlayer] = 8;
            jailTurns[currentPlayer] = 3;
        }
    }

    private int extractAmount(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 0;
    }

    // ---- Turn management ----

    private void advanceTurn() {
        if (checkGameOver()) return;
        phase = PHASE_ROLL;
        lastDice = 0;
        currentPlayer = nextActivePlayer(currentPlayer);
        msg(Utility.replace(game.lang("yourTurn"),
                new String[]{"%player%", coloredName(currentPlayer)}));
        refresh();

        // If it's the AI's turn, auto-roll
        if (isAi[currentPlayer]) {
            Bukkit.getScheduler().runTaskLater(plugin, this::rollAndMove, 30L);
        }
    }

    private int nextActivePlayer(int from) {
        for (int i = 1; i <= numPlayers; i++) {
            int candidate = (from + i) % numPlayers;
            if (!bankrupt[candidate]) return candidate;
        }
        return from;
    }

    private void aiAction() {
        if (finished || phase != PHASE_ACTION) return;
        int space = positions[currentPlayer];
        int type = MonopolyBoard.typeOf(space);
        if (type == MonopolyBoard.TYPE_PROPERTY) {
            MonopolyProperty prop = ((GameMonopoly) game).propertyAt(space);
            // AI buys if affordable and with 80% probability
            if (prop != null && !prop.isOwned() && balances[currentPlayer] >= prop.getPrice()
                    && random.nextDouble() < 0.8) {
                balances[currentPlayer] -= prop.getPrice();
                prop.setOwner(currentPlayer);
                msg(Utility.replace(game.lang("boughtProperty"),
                        new String[]{"%player%", coloredName(currentPlayer)},
                        new String[]{"%property%", Utility.color(prop.getName())},
                        new String[]{"%price%", String.valueOf(prop.getPrice())}));
            }
        }
        advanceTurn();
    }

    // ---- Bankruptcy & game-over ----

    private void checkBankruptcy() {
        if (balances[currentPlayer] < 0) {
            balances[currentPlayer] = 0;
            bankrupt[currentPlayer] = true;
            // Release properties
            for (MonopolyProperty prop : ((GameMonopoly) game).getProperties()) {
                if (prop.getOwner() == currentPlayer) prop.setOwner(-1);
            }
            msg(Utility.replace(game.lang("bankrupt"),
                    new String[]{"%player%", coloredName(currentPlayer)}));
        }
    }

    private boolean checkGameOver() {
        int active = 0;
        int lastActive = -1;
        for (int i = 0; i < numPlayers; i++) {
            if (!bankrupt[i]) {
                active++;
                lastActive = i;
            }
        }
        if (active <= 1) {
            phase = PHASE_GAME_OVER;
            finished = true;
            if (lastActive >= 0) {
                String winName = coloredName(lastActive);
                msg(Utility.replace(game.lang("gameOver"),
                        new String[]{"%player%", winName}));
                // Settle bets (3-player mode only) — winners get 2x payout.
                settleBets(lastActive);
                // Settle rewards — call onGameWonMulti only ONCE to avoid
                // duplicating winner rewards (tokens, money, high score,
                // effects) when there are multiple non-AI losers.
                UUID winnerId = playerIds[lastActive];
                boolean firstNonAiLoser = true;
                for (int i = 0; i < numPlayers; i++) {
                    if (i == lastActive) continue;
                    UUID loserId = playerIds[i];
                    if (!isAi[i]) {
                        if (firstNonAiLoser) {
                            game.onGameWonMulti(winnerId, loserId, false);
                            firstNonAiLoser = false;
                        } else {
                            // Additional losers: give only loser-side rewards
                            // (winner rewards already given with first call).
                            Player loserPlayer = Bukkit.getPlayer(loserId);
                            me.nikl.gamebox.data.GBPlayer gbLoser =
                                    plugin.getPluginManager().getPlayer(loserId);
                            if (gbLoser != null) {
                                gbLoser.addTokens(game.getMultiRewards().getTokensLoser());
                            }
                            if (loserPlayer != null) {
                                plugin.getPluginManager().playLoseEffects(loserPlayer);
                                plugin.getPluginManager().updateGameScoreboard(
                                        loserPlayer, game.getGameId(), 0, false);
                                plugin.getPluginManager().clearGameScoreboardLater(
                                        loserPlayer, 100L);
                            }
                        }
                    }
                }
            }
            refresh();
            // Schedule return to game GUI
            for (Player p : players) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) {
                        game.getGameGui().open(p);
                    }
                }, 80L);
            }
            ((MonopolyManager) game.getGameManager()).endSession(this);
            return true;
        }
        return false;
    }

    // ---- Helpers ----

    private int countProperties(int playerIdx) {
        int count = 0;
        for (MonopolyProperty prop : ((GameMonopoly) game).getProperties()) {
            if (prop.getOwner() == playerIdx) count++;
        }
        return count;
    }

    private String coloredName(int playerIdx) {
        if (isAi[playerIdx]) return COLORS[playerIdx] + "AI&r";
        return COLORS[playerIdx] + playerName(playerIds[playerIdx]) + "&r";
    }

    private void msg(String message) {
        lastMessage = Utility.color(message);
        for (Player p : players) {
            if (p.isOnline()) {
                p.sendMessage(Utility.color(message));
            }
        }
    }

    private void setActionButton(int slot, Material mat, String name,
                                  List<String> lore, java.util.function.Consumer<Player> action) {
        ItemStack item = Utility.createItem(mat, name, lore);
        // We store the action in a simple map keyed by slot for the manual
        // routing in onClick. But since we route by slot directly in onClick,
        // we just need to place the item.
        inventory.setItem(slot, item);
    }

    private ItemStack loreItem(Material mat, String name, List<String> lore) {
        return Utility.createItem(mat, name, lore);
    }
}
