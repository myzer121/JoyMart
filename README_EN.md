# JoyMart

[![中文](https://img.shields.io/badge/%E8%AF%AD%E8%A8%80-%E4%B8%AD%E6%96%87-red?style=for-the-badge)](./README.md)
[![English](https://img.shields.io/badge/Lang-English-blue?style=for-the-badge)](./README_EN.md)
[![Spigot](https://img.shields.io/badge/Paper-1.20.1%2B-orange?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-informational?style=for-the-badge)](./LICENSE)

A fully GUI-driven Minecraft mini-game lobby plugin built for **Spigot / Paper 1.20.1+**. All 15 games are bundled inside the main jar — no extra modules needed. Drop it into `plugins/` and you're ready to play.

> Main class: `me.nikl.gamebox.GameBox` · Version: `1.0` · Build: Maven shade (dependencies relocated to `me.nikl.gamebox.common.*`)

---

## Inspiration & Acknowledgements

JoyMart is **entirely independently designed and developed** by the author. All code, game logic, GUI interactions, and data storage in this repository are original implementations.

In terms of product shape, gameplay concepts, and user experience, this project draws inspiration from — and pays tribute to — the following two excellent Spigot community projects:

- [JukeBox - Music Plugin](https://www.spigotmc.org/resources/jukebox-music-plugin.40580/) — Inspired the "music player + NBS song" concept. The built-in `MusicPlayer` of this project (NBS song parsing + note-block sequence playback) is a from-scratch re-implementation inspired by this work.
- [GameBox - Inventory Games Collection](https://www.spigotmc.org/resources/gamebox-inventory-games-collection.37273/) — Inspired the overall "aggregate multiple mini-games via a chest GUI" product shape. The 15 games, token system, high-score table, multiplayer invitations, and AI opponents in this project are all independently rewritten and do not reuse any of their code.

> The links above point to the SpigotMC resource pages — click to view the original works. Thanks to the original authors for their excellent contributions to the community.

---

## Features at a Glance

| Category | Description |
|---|---|
| **Pure GUI** | Pick games through chest inventories; players never need to memorize commands. |
| **15 Built-in Games** | Single-player: 2048, Minesweeper, Whack-a-Mole, Bejeweled, Lottery, Slot Machine, Maze, Snake, Dino Run; Two-player: Battleship, Connect 4, Tic-Tac-Toe, Rock-Paper-Scissors, Chess; Multiplayer: Monopoly (2–3 players, with AI and betting modes). |
| **Token System** | Built-in JoyMart tokens with optional Vault economy hook (coins). |
| **High-Score Table** | Automatically records the top score of every game; supports cross-server MySQL sync. |
| **Two-Player + AI** | Two-player games support inviting a friend or playing against a built-in AI (no waiting for an opponent). |
| **Configurable Prize Pools** | Lottery / Slot Machine share a prize pool. Admins can add/remove prizes, adjust weights, and configure token/money/command rewards via the in-game GUI. |
| **Lobby Mode** | Automatically distributes a hotbar shortcut item in configured worlds; clicking it opens the main menu. |
| **Dual Storage** | YAML files (default) or MySQL (HikariCP connection pool, BungeeCord-ready). |
| **Custom Events** | Run configured console commands when a player enters / leaves JoyMart. |
| **Public API** | Exposed via `GameBoxAPI` or Bukkit ServicesManager for other plugins. |
| **Hook Support** | PlaceholderAPI, bStats, CalendarEvents (auto-detected). |
| **Multi-Language** | Built-in English / Chinese / German / Spanish. |
| **Scoreboard** | During a game, the sidebar shows the current game, score, high score, and tokens. |

---

## Installation & Build

### Option A: Build from source (recommended)

Requires **JDK 17+** and **Maven 3.6+**:

```bash
git clone https://github.com/myzer121/JoyMart.git
cd JoyMart
mvn clean package
```

Build output: `target/JoyMart-1.0.jar`

### Option B: Use a prebuilt jar

If you have a release `JoyMart-1.0.jar`, just drop it into your server's `plugins/` directory.

### Server Requirements

- **Paper / Spigot 1.20.1+** (Paper recommended)
- **Java 17+**
- Optional: [Vault](https://www.spigotmc.org/resources/vault.34315/) + any economy plugin
- Optional: [PlaceholderAPI](https://github.com/HelpChat/PlaceholderAPI)
- Optional: [CalendarEvents](https://github.com/niklasstern/CalendarEvents) (seasonal easter eggs)

---

## First Run

1. Place `JoyMart-1.0.jar` into `plugins/`, start the server once to let it generate the default config.
2. Edit `plugins/JoyMart/config.yml` as needed (economy, lobby, invitations, sounds, scoreboard, etc.).
3. Edit `plugins/JoyMart/games.yml` to toggle each game and configure sub-commands.
4. Edit `plugins/JoyMart/tokenShop.yml` to add/remove shop items.
5. Run `/gba reload` or restart the server.
6. Players run `/gamebox` to open the main menu.

---

## Commands

### Player Commands (default `/gamebox`, alias `/gb`)

| Command | Permission | Description |
|---|---|---|
| `/gamebox` | `gamebox.use` | Open the main menu |
| `/gamebox <subcommand>` | `gamebox.play.<id>` | Jump straight into a game (subcommands are defined in `games.yml`) |
| `/gamebox token` | `gamebox.use` | View your token balance |
| `/gamebox info` | `gamebox.use` | View plugin info |
| `/gamebox help` | `gamebox.use` | View help |
| `/gamebox accept [player]` | `gamebox.use` | Accept a multiplayer invitation |
| `/gamebox decline [player]` | `gamebox.use` | Decline a multiplayer invitation |

### Admin Commands (default `/gameboxadmin`, alias `/gba`)

| Command | Permission | Description |
|---|---|---|
| `/gba reload` | `gamebox.admin.reload` | Reload all configs |
| `/gba tokens <player> get` | `gamebox.admin.tokens` | View a player's tokens |
| `/gba tokens <player> set <amount>` | `gamebox.admin.tokens` | Set a player's tokens |
| `/gba tokens <player> give <amount>` | `gamebox.admin.tokens` | Give tokens to a player |
| `/gba tokens <player> take <amount>` | `gamebox.admin.tokens` | Take tokens from a player |
| `/gba games <game> <enable\|disable>` | `gamebox.admin.games` | Enable / disable a game |
| `/gba games` | `gamebox.admin.games` | Open the game management GUI (toggle games, edit lottery / slot machine prize pools) |
| `/gba language` | `gamebox.admin.language` | Check language file completeness |
| `/gba migrate <file\|mysql>` | `gamebox.admin.migrate` | Migrate data between YAML / MySQL |
| `/gba reset <game>` | `gamebox.admin.reset` | Reset a game's high-score table |
| `/gba shop` | `gamebox.admin.shop` | Open the shop management GUI (list / unlist items) |

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `gamebox.use` | true | Use the `/gamebox` main command |
| `gamebox.play.*` | true | Play all games |
| `gamebox.play.<id>` | true | Play a specific game (e.g. `gamebox.play.2048`) |
| `gamebox.gamegui.<id>` | true | See a specific game's icon in the main menu |
| `gamebox.admin.*` | op | All admin permissions |
| `gamebox.admin.shop` | op | Manage shop items via GUI |
| `gamebox.shop.<custom>` | false | Permission to buy a specific shop item (defined per-item in `tokenShop.yml`) |

Each game auto-registers its corresponding `gamebox.play.<id>` and `gamebox.gamegui.<id>` permissions (default true) when loaded. The `buyPermission` of shop items is **not** auto-registered — assign it with your permission plugin (e.g. LuckPerms).

---

## Configuration Files

### `config.yml` — Global Config

Key nodes (mirrored by `plugins/JoyMart/config.yml`):

```yaml
# ---------------- Economy ----------------
economy:
  tokens: true              # Enable JoyMart tokens (internal currency, stored per player)
  vault: true               # Hook into Vault (coin rewards/costs, optional)

# ---------------- Storage ----------------
storage:
  type: yaml                # yaml = local files, mysql = database (HikariCP pool)
  autoSaveInterval: 120     # Auto-save interval (seconds); 0 = disabled (save only on quit)
  mysql:                    # Only when storage.type=mysql
    host: localhost
    port: 3306
    database: gamebox
    user: root
    password: ''
    poolSize: 10            # HikariCP pool size
    bungee: false           # true = share high-score table across BungeeCord sub-servers

# ---------------- Language ----------------
language:
  default: en               # en / zh / de / es (falls back to en if missing)

# ---------------- Lobby ----------------
lobby:
  enabled: false            # Lobby mode
  worlds: [world]           # Worlds that receive the hotbar shortcut item
  slot: 4                   # Hotbar slot for the shortcut item (0-8)
  material: NETHER_STAR
  name: '&6&lJoyMart'
  lockItem: true            # Prevent players from dropping/moving the shortcut item

# ---------------- Invitations ----------------
invitations:
  expiry: 30                # Invitation expiry (seconds)
  style: json               # json (chat-clickable) / actionbar / title

# ---------------- Navigation ----------------
navigation:
  mainMenuSize: 45          # Main menu size (multiple of 9, max 54)
  backButtonSlot: 40        # Back-button slot in game GUIs (-1 = none)
  closeButtonSlot: 44       # Close-button slot (-1 = none)

# ---------------- Sounds ----------------
sounds:
  enabled: true
  click: UI_BUTTON_CLICK
  win: ENTITY_PLAYER_LEVELUP
  lose: BLOCK_ANVIL_LAND
  token: ENTITY_EXPERIENCE_ORB_PICKUP

# ---------------- Scoreboard ----------------
scoreboard:
  enabled: true             # Sidebar scoreboard during a game (game/score/highscore/tokens)

# ---------------- Custom commands ----------------
commands:
  onEnter: []               # Run as console when a player enters JoyMart
  onLeave: []               # Run as console when a player leaves JoyMart

# ---------------- Shop ----------------
shop:
  enabled: true             # Master switch for the token shop

# ---------------- bStats ----------------
bstats: true
```

### `games.yml` — Per-Game Config

```yaml
games:
  2048:
    enabled: true
    subCommands: [2048, twentyfortyeight]
  minesweeper:
    enabled: true
    subCommands: [ms, minesweeper]
  # ... same pattern for other games
```

Detailed per-game parameters (scores, rewards, costs, combo thresholds, etc.) live in `plugins/JoyMart/games/<id>/config.yml`, auto-copied from the jar on first startup.

### Lottery / Slot Machine Prize Pool Config

Lottery (`lottery`) and Slot Machine (`slotmachine`) share the same prize-pool format. You can edit it in-game via GUI (recommended) or directly edit `plugins/JoyMart/games/<id>/config.yml`:

```yaml
# Token cost per draw / spin (0 = free)
cost: 20

# Prize pool: weighted-random pick of one prize
prizes:
  - weight: 50            # Weight (>= 0; larger = more likely)
    tokens: 10            # Token reward (optional)
    money: 0.0            # Coin reward (requires Vault, optional)
    commands: []          # Console commands (%player% replaced with player name, optional)
    displayName: '&eSmall prize +10 tokens'
  - weight: 5
    tokens: 200
    money: 100.0
    commands:
      - 'say %player% hit the jackpot!'
    displayName: '&6&lJackpot +200 tokens + 100 coins'
  - weight: 45
    tokens: 0
    displayName: '&7No prize'
```

> When edited via the GUI, all changes are written back to this file immediately — no manual editing needed.

### `tokenShop.yml` — Token Shop

Supports categories + items. Per-item fields:

```yaml
items:
  my_item:
    material: DIAMOND
    name: '&bDiamond'
    lore:
      - '&7Cost: &e50 tokens'
    cost: 50
    permission: ''            # Permission to see the item (empty = everyone)
    buyPermission: ''         # Permission to buy (empty = everyone)
    commands:                 # Run as console on purchase
      - 'give %player% diamond 1'
    closeAfter: true          # Close GUI after purchase (default true)
    confirm: false            # Require a second click to confirm (default false)
```

`%player%` is replaced with the buyer's name.

### Shop Management GUI (categories / list / unlist)

You don't need to hand-edit `tokenShop.yml` — admins with permission can manage it via the in-game GUI:

**Two entry points:**
1. `/gba shop` (requires `gamebox.admin.shop`)
2. Open the `/gb` main menu → enter the token shop → bottom-right "Manage Shop" button (visible only to permissioned players)

**Operations:**
- **Add category**: On the shop-management main page, click the "+ Add Category" button at the bottom-left → type the category key in chat (lowercase letters/digits/underscores) → type the display name → the category is created and saved to `tokenShop.yml`.
- **Rename category**: Enter a category's management page → click the "Rename Category" button at the bottom → type the new name in chat → auto-saved.
- **Delete category**: Enter a category's management page → click "Delete Category" at the bottom → click again to confirm → the category and all its items are removed and saved.
- **List item**: Enter a category's management page → **hold the item on your cursor** → click the "+ List Item" slot at the bottom. The item is added to the category at the default price of 10 tokens and saved to `tokenShop.yml`.
- **Unlist**: Click any item in the category-management page → first click asks for confirmation → second click removes it and saves.
- **Reprice / Rename**: Click an item in the category-management page to enter its edit page → use +/- buttons to adjust the price, or click the rename button and type the new name in chat.
- The item name/lore listed will follow the cursor item's display name and lore; the default price is 10, but you can change it on the edit page.

> **Listing / Unlisting fix note**: Older versions modified the cursor item synchronously inside the event callback, causing a desync issue where the item would "disappear from the cursor but not actually be listed/unlisted". This version wraps all cursor modifications and `build()` calls inside `Bukkit.getScheduler().runTask()`, deferring them to the next tick to eliminate client/server state mismatch. All management-UI texts are fully localized (using the `gui.shop*` keys from `language_<lang>.yml`).

> The `gamebox.admin.shop` permission defaults to OP only and is a child of `gamebox.admin.*`. Non-privileged players cannot see the management button or run `/gba shop`.

---

## Game Modes

### Single-Player Games (2048 / Minesweeper / Whack-a-Mole / Bejeweled)

Open `/gb` → pick a game → click **Start Game**. Scores are recorded to the high-score table automatically.

### Lottery

Classic numeric lottery, fully GUI-driven:

1. Open `/gb` → pick **Lottery** → choose 4 digits (each in range 1–9).
2. Click the **Draw** button to take a weighted-random draw from the prize pool.
3. The prize pool supports tokens, coins (requires Vault), and console commands — all can be awarded together.

**Editing Prizes (Admin GUI):**

- Entry: Run `/gba games` in-game, enter the **Lottery** management page → click the **Prize Pool** button; or click the prize-pool icon inside the `lottery` management GUI.
- In the prize-pool editor you can: add new prizes, remove existing ones, adjust each prize's **weight** (larger = more likely), **token reward**, **coin reward**, and **command reward**.
- Click **Save** to write the result to `plugins/JoyMart/games/lottery/config.yml`.

### Slot Machine

Classic 3-reel slot machine; click the lever and the reels stop on random symbols:

- All three symbols match → jackpot (weighted-random pick from the prize pool)
- Two symbols match → small prize (fixed token payout)
- All different → no win
- Prize-pool config is identical to the Lottery (same admin GUI entry).

### Maze

Each round generates a random 9×9 recursive-backtracking maze. Click an adjacent cell to move one step up/down/left/right toward the bottom-right exit:

- Visited cells turn green so you can review your path.
- A timer records your completion time; the shorter the time, the larger the token reward.
- Closing the GUI mid-game counts as giving up — no reward.
- On completion, your best time is recorded to the high-score table.

### Snake

Classic Snake, auto-moving in a 9×5 two-inventory interface:

- Click the direction buttons (up/down/left/right) to steer the snake; you can't reverse directly.
- Eating food (red apple) grows the snake by 1 and gives +10 score; new food spawns automatically.
- Hitting a wall or yourself ends the game; filling the entire board counts as a win.
- Pause/resume buttons supported; higher scores yield more tokens.

### Dino Run

Inspired by the Chrome offline dinosaur, a side-scrolling runner:

- The dino runs forward automatically; cacti scroll in from the right.
- Click the jump button (3 cells wide for easy tapping) to leap over cacti.
- +1 score per tick alive; higher scores shorten obstacle spacing (difficulty ramps up).
- Hitting a cactus ends the game; the score equals your survival time.

### Chess

6×6 Los Alamos variant (no bishops), supports both two-player and AI play:

- White moves first; Player 1 plays White (bottom), Player 2 / AI plays Black (top).
- Pieces: rook, knight, queen, king, knight, rook + 6 pawns (no bishops).
- Click your own piece to select it (legal moves highlighted in green), then click the target cell to move.
- Pawns auto-promote to queen at the last rank; check / checkmate / stalemate are detected.
- A resign button ends the game at any time; checkmating your opponent wins.
- **Two-player**: Invite a friend to share the same board and take turns.
- **AI**: A two-ply minimax AI that prefers captures, blocks threats, and controls the center.

### Monopoly

A GUI recreation of the classic Monopoly board game. Supports **2–3 players** in the same session, plus a **1 human + AI** mode:

- A 26-space looped board is rendered around the perimeter of a 6×9 (54-slot) chest inventory; the center holds dice, player info, and action buttons.
- Space types: GO (collect 200 on pass), Jail, Free Parking, Go To Jail; Chance and Community Chest spaces draw random cards; 17 buyable properties (Old Town, City Hall, Train Station, Airport, Skyscraper, Gold Mine, etc.).
- Each turn: the current player clicks **Roll Dice** → a single die advances the token → landing on unowned land offers purchase, landing on someone else's property auto-deducts rent, landing on Chance/Chest draws a card → click **End Turn**.
- A player whose balance hits zero is bankrupt and eliminated; the last non-bankrupt player wins.
- **3-player-only betting**: before the first roll, each player may bet on who will win; guessing correctly yields an extra reward.
- **AI opponent**: entering solo auto-fills an AI player that auto-buys properties, rolls, and ends its turn.
- Rewards: win +10, draw +3, lose +2 tokens.

### Two-Player Games (Tic-Tac-Toe / Connect 4 / Rock-Paper-Scissors / Battleship / Chess)

Two-player games support two modes:

1. **Invite a friend** — Click **Invite**, type the target player's name in chat, and they'll receive a clickable invitation message (accept/decline). Once accepted, both players share the same game UI and take turns.

2. **Play against AI** — Click **Play AI** to start immediately without waiting for an opponent. The AI moves automatically after 1 second:
   - **Tic-Tac-Toe AI**: Strategic — find a winning move → block the opponent → take the center → take a corner → random.
   - **Connect 4 AI**: Strategic — find a winning column → block the opponent → prefer the middle column → random.
   - **Rock-Paper-Scissors AI**: Random.
   - **Battleship AI**: Randomly fires at un-attacked cells.
   - **Chess AI**: Two-ply minimax — evaluates each move and the opponent's best reply, preferring captures, center control, and blocking threats.

> AI games also count toward the high-score table and token rewards.

---

## Scoring & Special Events

During a game, each game updates the sidebar scoreboard in real time via `onScoreChange`, and triggers `onGameEvent` to award immediate rewards at milestones:

| Game | Scoring Rule | Special-Event Reward |
|---|---|---|
| **2048** | Each merge = sum of merged tile values | Reach 2048 → +50 tokens |
| **Minesweeper** | +10 per safe cell revealed | Plays hurt sound on mine hit |
| **Whack-a-Mole** | +1 per hit, +2 extra on a 3-combo | Hit a golden mole → +5 tokens |
| **Bejeweled** | N gems cleared = N×10×chain multiplier | 4+ chain → +N tokens each |
| **Lottery** | Each draw consumes 1 ticket (or tokens) | Prize-pool random reward (tokens/coins/commands) |
| **Slot Machine** | Each spin consumes tokens | Three-of-a-kind → jackpot pool; two-of-a-kind → fixed tokens |
| **Maze** | Faster completion = higher score | Base 30 tokens; -2 per extra 10s (min 5) |
| **Battleship** | +100 per hit, +500 per sink | Sink reward = ship size × 5 tokens |
| **Connect 4** | +1 per drop, +4 on win | Form a 3-in-a-row threat → +2 tokens |
| **Tic-Tac-Toe** | +3 win, +1 draw | Form a 2-in-a-row threat → +1 token |
| **Rock-Paper-Scissors** | +2 win, +1 draw | 2-streak +2, 3-streak +3 tokens |
| **Snake** | +10 per food eaten | Every 50-score milestone → +1 token |
| **Dino Run** | +1 per alive tick | Every 50-score milestone → +1 token |
| **Chess** | Win 200 + capture value×10, Draw 100 + capture value×5 | — |
| **Monopoly** | Settled by win / draw / lose outcome | Win +10, Draw +3, Lose +2 tokens (extra reward for correct 3-player bet) |

---

## Data Storage

### YAML (default, single server)

- Player data: `plugins/JoyMart/data/players/<uuid>.yml`
- High scores: `plugins/JoyMart/data/scores/<gameId>.yml`

### MySQL (recommended for cross-server)

Enable `mysql.enabled: true` in `config.yml` and fill in the connection details. JoyMart uses a HikariCP connection pool automatically; all BungeeCord sub-servers share the same high-score table and token data.

**Migration**: `/gba migrate mysql` imports existing YAML data into MySQL; `/gba migrate file` does the reverse.

---

## PlaceholderAPI Placeholders

Once PlaceholderAPI is hooked, the following placeholders are available:

| Placeholder | Description |
|---|---|
| `%gamebox_tokens%` | Current player's token balance |
| `%gamebox_highscore_<id>%` | Current player's high score in a game (e.g. `%gamebox_highscore_2048%`) |
| `%gamebox_ingame%` | Whether the current player is in a game (true/false) |

---

## Public API (for other plugins)

JoyMart registers `GameBoxAPI` with the Bukkit ServicesManager on startup. Other plugins can obtain it via three methods:

```java
// Method 1: static singleton (fastest)
GameBoxAPI api = GameBoxAPI.getInstance();

// Method 2: ServicesManager (recommended, decoupled)
GameBoxAPI api = Bukkit.getServicesManager().load(GameBoxAPI.class);

// Method 3: directly from the main class
GameBox plugin = GameBoxAPI.getPlugin();
GameBoxAPI api = plugin.getApi();
```

Common methods:

```java
// Tokens
int balance = api.getTokens(uuid);
api.addTokens(uuid, 100);
api.removeTokens(uuid, 50);
boolean ok = api.payIfCanAfford(uuid, 30);

// High scores
long hs = api.getHighScore(uuid, "2048");
int rank = api.submitScore("2048", uuid, "Steve", 1234L);
List<TopList.Entry> top = api.getTopList("2048", 10);
api.resetHighScores("2048");

// State
boolean inMenu = api.isInGameBox(uuid);
boolean inGame = api.isInGame(uuid);
boolean in2048 = api.isInGame(uuid, "2048");
Set<String> games = api.getEnabledGameIds();

// Operations
api.openMainMenu(player);
api.openTopList(player, "2048");
api.enterGameBox(player);
api.leaveGameBox(player);
api.saveAll();
api.reload();
```

See [GameBoxAPI.java](src/main/java/me/nikl/gamebox/GameBoxAPI.java) for the full method signatures.

---

## Custom Events

JoyMart fires two Bukkit events that other plugins can listen to:

```java
@EventHandler
public void onEnter(EnterGameBoxEvent e) {
    Player p = e.getPlayer();
    // ...
}

@EventHandler
public void onLeave(LeftGameBoxEvent e) {
    Player p = e.getPlayer();
    // ...
}
```

The event classes are in the `me.nikl.gamebox.events` package.

---

## Caching & Performance

- **GBPlayer** is loaded into memory once on join; all subsequent reads/writes go through the cache and only flush to the DB on `autoSaveInterval` (default 300s) or player quit — avoiding per-operation DB lookups.
- **GBPlayer.isDirty()** flag mechanism: only players whose data actually changed get written.
- **TopListPage** high-score GUI has a 30-second TTL cache, automatically invalidated on new records, to avoid repeatedly scanning the full table.
- **MySQL mode** uses a HikariCP connection pool (default 10 connections) — cross-server safe.

---

## FAQ

**Q: A player's inventory was cleared after entering a game?**  
A: This is by design — JoyMart saves and clears the inventory on entry and restores it on leave. If a server crash prevented restoration, restart and have the player enter then leave JoyMart once to recover it.

**Q: A multiplayer invitation was sent but the recipient didn't get it?**  
A: Check `config.yml`'s `invitations.style` setting (`json` / `actionbar` / `title`), and whether the recipient is on the same server (cross-server invitations require MySQL mode + BungeeCord event forwarding — this version does not include built-in cross-server invitation forwarding).

**Q: Shop items don't respond when clicked?**  
A: Check `tokenShop.yml` for correct indentation, and that the item's `buyPermission` is properly assigned by your permission plugin.

**Q: When listing / unlisting, the cursor item disappeared?**  
A: Earlier versions modified the cursor synchronously inside `InventoryClickEvent`, which conflicted with client prediction and caused the item to "vanish". This version wraps all cursor modifications and `build()` calls inside `Bukkit.getScheduler().runTask()`, deferring them to the next tick to fully resolve the issue. If you still hit it, confirm your jar is the latest build.

**Q: How do I edit the lottery / slot machine prizes?**  
A: An admin runs `/gba games` → picks the game → clicks the **Prize Pool** button to visually add/remove prizes, adjust weights and rewards in the GUI. Saving writes the result to the corresponding game's `config.yml` automatically — no manual editing needed.

**Q: The maze is too easy / too hard?**  
A: The maze size is hard-coded to 9×9 (recursive backtracking guarantees a unique path). To change it, modify the `n` constant in `MazeSession.java` and rebuild.

**Q: How do I add my own language?**  
A: Copy `plugins/JoyMart/language/language_en.yml` to `language_xx.yml`, translate all keys, and set `language.default: xx` in `config.yml`. Game-specific language files live in `language/game_<game>/language_xx.yml` — copy and translate the same way.

**Q: I set Chinese but the game still shows English?**  
A: Make sure `language.default: zh` in `config.yml`, then run `/gba reload`. The plugin will copy `language_zh.yml` and each game's `language_zh.yml` to the `plugins/JoyMart/language/` directory automatically.

---

## Build Dependencies

| Type | Dependency |
|---|---|
| provided | PaperAPI, Vault, PlaceholderAPI |
| Soft-depend at runtime (existence detection only, no compile-time needed) | CalendarEvents |
| compile (shade, relocated to `me.nikl.gamebox.common.*`) | bstats-bukkit, HikariCP, slf4j-nop, ACF (Paper), ExpiringMap, jsr305 |

See [pom.xml](pom.xml) for the full dependency list.

---

## License

See [LICENSE](LICENSE).
