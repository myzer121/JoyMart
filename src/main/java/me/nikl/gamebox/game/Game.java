package me.nikl.gamebox.game;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.game.rules.GameRule;
import me.nikl.gamebox.game.rules.GameRuleMultiRewards;
import me.nikl.gamebox.game.rules.GameRuleRewards;
import me.nikl.gamebox.game.rules.GameType;
import me.nikl.gamebox.inventory.GameGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Abstract base for every GameBox game. Subclasses implement the four lifecycle
 * methods ({@link #loadSettings}, {@link #loadLanguage}, {@link #init},
 * {@link #loadGameManager}); the framework drives them via {@link #onEnable}.
 *
 * <p>Reward settlement ({@link #onGameWonSingle} / {@link #onGameWonMulti}) is
 * handled here so all games share consistent token/money/high-score behavior.</p>
 */
public abstract class Game {

    protected final GameBox plugin;
    protected final String gameId;
    protected final GameType type;
    protected GameRule rule;
    protected GameRuleRewards rewards;
    protected GameRuleMultiRewards multiRewards;
    protected GameGui gameGui;
    protected FileConfiguration config;
    protected FileConfiguration language;

    private boolean enabled = true;

    /**
     * Pending token bets for two-player games, keyed by player UUID. When a
     * game starts with a bet, both players' UUIDs are mapped to the bet
     * amount. The bet is settled in {@link #onGameWonMulti} (winner takes the
     * pot) and refunded in {@link #refundBets} (forfeit / early exit).
     */
    private final java.util.Map<UUID, Integer> sessionBets = new java.util.concurrent.ConcurrentHashMap<>();

    public Game(GameBox plugin, String gameId, GameType type) {
        this.plugin = plugin;
        this.gameId = gameId;
        this.type = type;
    }

    /** Full enable lifecycle: config, language, settings, init, manager, gui. */
    public final void onEnable() {
        this.config = loadGameConfig();
        this.language = loadGameLanguage();
        if (!enabled) return;

        this.rule = new GameRule(type);
        if (type == GameType.SINGLE_PLAYER) {
            this.rewards = new GameRuleRewards();
        } else {
            // TWO_PLAYER and MULTI_PLAYER both use multi-rewards
            this.multiRewards = new GameRuleMultiRewards();
        }

        loadSettings();
        loadLanguage();
        init();
        loadGameManager();

        // Hook into the GUI system
        this.gameGui = new GameGui(plugin, this);
        plugin.getGuiManager().registerGameGui(gameId, gameGui);

        plugin.getLogger().info("Enabled game: " + gameId + " (" + type + ")");
    }

    /** Disable hook (e.g. when toggled off at runtime). */
    public void onDisable() {
        plugin.getGuiManager().unregisterGameGui(gameId);
    }

    /** Copy the bundled config template to the data folder if absent, then load it. */
    protected FileConfiguration loadGameConfig() {
        String resource = "games/" + gameId + "/config.yml";
        File file = new File(plugin.getDataFolder(), "games/" + gameId + "/config.yml");
        if (!file.exists() && plugin.getResource(resource) != null) {
            plugin.saveResource(resource, false);
        }
        if (file.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            // merge jar defaults
            if (plugin.getResource(resource) != null) {
                try (InputStreamReader r = new InputStreamReader(plugin.getResource(resource), StandardCharsets.UTF_8)) {
                    cfg.setDefaults(YamlConfiguration.loadConfiguration(r));
                } catch (Exception ignored) {}
            }
            return cfg;
        }
        return new YamlConfiguration();
    }

    /** Load the game-specific language file, merging into the main language cache.
     *  Loads ALL available languages and merges each into its respective cache
     *  so per-player language switching works for game strings too. */
    protected FileConfiguration loadGameLanguage() {
        FileConfiguration result = null;
        // Load game language files for ALL supported languages
        for (String lang : me.nikl.gamebox.LanguageManager.getSupportedLanguages()) {
            String resource = "language/game_" + gameId + "/language_" + lang + ".yml";
            File file = new File(plugin.getDataFolder(), "language/game_" + gameId + "/language_" + lang + ".yml");
            if (!file.exists() && plugin.getResource(resource) != null) {
                plugin.saveResource(resource, false);
            }
            FileConfiguration cfg;
            if (file.exists()) {
                cfg = YamlConfiguration.loadConfiguration(file);
                // Merge JAR defaults for missing keys
                if (plugin.getResource(resource) != null) {
                    try (InputStreamReader r = new InputStreamReader(plugin.getResource(resource), StandardCharsets.UTF_8)) {
                        cfg.setDefaults(YamlConfiguration.loadConfiguration(r));
                    } catch (Exception ignored) {}
                }
            } else if (plugin.getResource(resource) != null) {
                try (InputStreamReader r = new InputStreamReader(plugin.getResource(resource), StandardCharsets.UTF_8)) {
                    cfg = YamlConfiguration.loadConfiguration(r);
                } catch (Exception e) {
                    cfg = new YamlConfiguration();
                }
            } else {
                cfg = new YamlConfiguration();
            }
            // Merge into the language-specific cache
            plugin.getLanguageManager().mergeGameLanguage(gameId, cfg, lang);
            // Keep the default-language config as the return value
            if (lang.equals(me.nikl.gamebox.GameBoxSettings.language) ||
                    (result == null && lang.equals("en"))) {
                result = cfg;
            }
        }
        // Fallback: if no config was loaded, use an empty one
        if (result == null) {
            result = new YamlConfiguration();
        }
        return result;
    }

    /**
     * Persist the in-memory game config back to {@code games/<id>/config.yml}.
     * Used by the prize-pool editor GUIs to save admin edits.
     */
    public void saveGameConfig() {
        File file = new File(plugin.getDataFolder(), "games/" + gameId + "/config.yml");
        if (!(config instanceof YamlConfiguration)) return;
        try {
            ((YamlConfiguration) config).save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not save games/" + gameId + "/config.yml: " + e.getMessage());
        }
    }

    // ---- Lifecycle hooks ----
    public abstract void loadSettings();
    public abstract void loadLanguage();
    public abstract void init();
    public abstract void loadGameManager();

    // ---- Accessors ----
    public String getGameId() { return gameId; }
    public GameType getType() { return type; }
    public GameRule getRule() { return rule; }
    public GameRuleRewards getRewards() { return rewards; }
    public GameRuleMultiRewards getMultiRewards() { return multiRewards; }
    public GameGui getGameGui() { return gameGui; }
    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getLanguage() { return language; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public abstract GameManager<?> getGameManager();

    /** Language helper scoped to this game. Uses the active language context. */
    public String lang(String key) {
        return plugin.getLanguageManager().get("games." + gameId + "." + key);
    }

    /** Language helper scoped to this game for a specific player. */
    public String lang(org.bukkit.entity.Player player, String key) {
        return plugin.getLanguageManager().get(player, "games." + gameId + "." + key);
    }

    // ---- Special event hooks (overridable by subclasses) ----

    /**
     * Fired when a milestone is reached inside a game (e.g. clearing a row in
     * 2048, sinking a ship, hitting a 4-gem cascade). Subclasses override to
     * grant bonus tokens, play effects, or trigger custom logic.
     *
     * @param player  the player who triggered the event
     * @param event   a stable event id (e.g. "milestone", "cascade", "sunk")
     * @param value   a numeric magnitude (score gained, ship size, etc.)
     */
    public void onGameEvent(Player player, String event, long value) {
        // default: small bonus token for any milestone
        if ("milestone".equals(event) && value > 0) {
            GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
            if (gb != null) {
                int bonus = (int) Math.min(5, Math.max(1, value / 100));
                gb.addTokens(bonus);
            }
        }
    }

    /**
     * Fired when a single-player game produces a notable in-game score change
     * (e.g. +4 in 2048, clearing 5 cells in minesweeper). Override to update a
     * live scoreboard or play sounds.
     */
    public void onScoreChange(Player player, long newScore, long delta) {
        // default: no-op; scoreboard updates are handled by ScoreboardManager
    }

    // ---- Settlement ----

    /** Settle a single-player game result. */
    public void onGameWonSingle(Player player, boolean won, long score) {
        GBPlayer gbPlayer = plugin.getPluginManager().getPlayer(player.getUniqueId());
        if (gbPlayer == null) return;

        int tokens = won ? rewards.getTokensOnWin() : rewards.getTokensOnLose();
        if (rule.getScorePerToken() > 0) {
            tokens += (int) (score / Math.max(1, rule.getScorePerToken()));
        }
        if (tokens > 0) gbPlayer.addTokens(tokens);

        double money = won ? rewards.getMoneyOnWin() : rewards.getMoneyOnLose();
        if (rule.getMoneyPerToken() > 0 && tokens > 0) {
            money += tokens * rule.getMoneyPerToken();
        }
        if (money > 0 && plugin.getEconomyManager().isVaultEnabled()) {
            plugin.getEconomyManager().deposit(player, money);
        }

        boolean newRecord = false;
        if (won || rule.isTrackHighScore()) {
            newRecord = gbPlayer.setHighScore(gameId, score);
            plugin.getDataBase().addScore(gameId, player.getUniqueId(), player.getName(), score);
            if (newRecord) {
                me.nikl.gamebox.inventory.TopListPage.invalidate(gameId);
            }
        }

        if (won) {
            runCommands(rewards.getCommandsOnWin(), player);
            plugin.getPluginManager().playWinEffects(player);
            if (newRecord) {
                onGameEvent(player, "newRecord", score);
            }
        } else {
            plugin.getPluginManager().playLoseEffects(player);
        }

        // Update scoreboard with final result
        plugin.getPluginManager().updateGameScoreboard(player, gameId, score, won);
        plugin.getPluginManager().clearGameScoreboardLater(player, 100L);
    }

    /** Settle a two-player game result. draw=true distributes draw rewards. */
    public void onGameWonMulti(UUID winner, UUID loser, boolean draw) {
        Player winnerPlayer = Bukkit.getPlayer(winner);
        Player loserPlayer = Bukkit.getPlayer(loser);
        GBPlayer gbWinner = plugin.getPluginManager().getPlayer(winner);
        GBPlayer gbLoser = plugin.getPluginManager().getPlayer(loser);

        // --- Settle token bets (escrow already deducted on accept) ---
        // Use Integer (not int) to safely handle null returns: vsAi games and
        // friendly matches have no bets in the map, so remove() returns null.
        Integer winnerBetBox = sessionBets.remove(winner);
        Integer loserBetBox = sessionBets.remove(loser);
        int winnerBet = winnerBetBox != null ? winnerBetBox : 0;
        int loserBet = loserBetBox != null ? loserBetBox : 0;
        if (winnerBet > 0 && loserBet > 0) {
            if (draw) {
                // Refund both bets
                if (gbWinner != null) gbWinner.addTokens(winnerBet);
                if (gbLoser != null) gbLoser.addTokens(loserBet);
            } else {
                // Winner takes the full pot (both bets)
                int pot = winnerBet + loserBet;
                if (gbWinner != null) {
                    gbWinner.addTokens(pot);
                    if (winnerPlayer != null) {
                        winnerPlayer.sendMessage(plugin.langPrefixed("messages.betWon")
                                .replace("%tokens%", String.valueOf(pot)));
                    }
                }
                if (loserPlayer != null) {
                    loserPlayer.sendMessage(plugin.langPrefixed("messages.betLost")
                            .replace("%tokens%", String.valueOf(loserBet)));
                }
            }
        }

        if (draw) {
            if (gbWinner != null) gbWinner.addTokens(multiRewards.getTokensDraw());
            if (gbLoser != null) gbLoser.addTokens(multiRewards.getTokensDraw());
            if (plugin.getEconomyManager().isVaultEnabled()) {
                if (winnerPlayer != null) plugin.getEconomyManager().deposit(winnerPlayer, multiRewards.getMoneyDraw());
                if (loserPlayer != null) plugin.getEconomyManager().deposit(loserPlayer, multiRewards.getMoneyDraw());
            }
            if (winnerPlayer != null) {
                plugin.getPluginManager().updateGameScoreboard(winnerPlayer, gameId, 0, true);
                plugin.getPluginManager().clearGameScoreboardLater(winnerPlayer, 100L);
            }
            if (loserPlayer != null) {
                plugin.getPluginManager().updateGameScoreboard(loserPlayer, gameId, 0, true);
                plugin.getPluginManager().clearGameScoreboardLater(loserPlayer, 100L);
            }
            return;
        }

        if (gbWinner != null) {
            gbWinner.addTokens(multiRewards.getTokensWinner());
            if (winnerPlayer != null) runCommands(multiRewards.getCommandsWinner(), winnerPlayer);
            if (multiRewards.getMoneyWinner() > 0 && plugin.getEconomyManager().isVaultEnabled() && winnerPlayer != null) {
                plugin.getEconomyManager().deposit(winnerPlayer, multiRewards.getMoneyWinner());
            }
            gbWinner.setHighScore(gameId, gbWinner.getHighScore(gameId) + 1);
            plugin.getDataBase().addScore(gameId, winner, winnerPlayer != null ? winnerPlayer.getName() : "?", gbWinner.getHighScore(gameId));
        }
        if (gbLoser != null) {
            gbLoser.addTokens(multiRewards.getTokensLoser());
            if (loserPlayer != null) runCommands(multiRewards.getCommandsLoser(), loserPlayer);
            if (multiRewards.getMoneyLoser() > 0 && plugin.getEconomyManager().isVaultEnabled() && loserPlayer != null) {
                plugin.getEconomyManager().deposit(loserPlayer, multiRewards.getMoneyLoser());
            }
        }
        if (winnerPlayer != null) plugin.getPluginManager().playWinEffects(winnerPlayer);
        if (loserPlayer != null) plugin.getPluginManager().playLoseEffects(loserPlayer);

        if (winnerPlayer != null) {
            plugin.getPluginManager().updateGameScoreboard(winnerPlayer, gameId, 1, true);
            plugin.getPluginManager().clearGameScoreboardLater(winnerPlayer, 100L);
        }
        if (loserPlayer != null) {
            plugin.getPluginManager().updateGameScoreboard(loserPlayer, gameId, 0, false);
            plugin.getPluginManager().clearGameScoreboardLater(loserPlayer, 100L);
        }
    }

    /** Run a list of console commands with %player% replaced. */
    protected void runCommands(java.util.List<String> commands, Player player) {
        for (String cmd : commands) {
            String formatted = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
        }
    }

    /** Helper to read a rewards section from config into a GameRuleRewards. */
    protected void loadRewards(GameRuleRewards r, ConfigurationSection section) {
        if (section == null) return;
        r.setTokensOnWin(section.getInt("win.tokens", 0));
        r.setMoneyOnWin(section.getDouble("win.money", 0));
        r.getCommandsOnWin().addAll(section.getStringList("win.commands"));
        r.setTokensOnLose(section.getInt("lose.tokens", 0));
        r.setMoneyOnLose(section.getDouble("lose.money", 0));
    }

    /** Helper to read a multi-rewards section from config. */
    protected void loadMultiRewards(GameRuleMultiRewards r, ConfigurationSection section) {
        if (section == null) return;
        r.setTokensWinner(section.getInt("winner.tokens", 0));
        r.setMoneyWinner(section.getDouble("winner.money", 0));
        r.getCommandsWinner().addAll(section.getStringList("winner.commands"));
        r.setTokensLoser(section.getInt("loser.tokens", 0));
        r.setMoneyLoser(section.getDouble("loser.money", 0));
        r.getCommandsLoser().addAll(section.getStringList("loser.commands"));
        r.setTokensDraw(section.getInt("draw.tokens", 0));
        r.setMoneyDraw(section.getDouble("draw.money", 0));
    }

    /**
     * Register an escrowed token bet for a player in a two-player session.
     * The bet (already deducted by {@link me.nikl.gamebox.input.InvitationHandler})
     * is settled in {@link #onGameWonMulti} (winner takes the pot) or refunded
     * in {@link #refundBets} (forfeit / early exit before a result).
     */
    public void setBet(UUID player, int bet) {
        if (bet > 0) {
            sessionBets.put(player, bet);
        }
    }

    /**
     * Refund any unsettled bets for the players of a session. Called from
     * {@link AbstractGameSession#end()} when a session ends without a winner
     * being declared via {@link #onGameWonMulti} (e.g. forfeit, disconnect).
     */
    public void refundBets(UUID... players) {
        for (UUID id : players) {
            Integer bet = sessionBets.remove(id);
            if (bet != null && bet > 0) {
                GBPlayer gb = plugin.getPluginManager().getPlayer(id);
                if (gb != null) {
                    gb.addTokens(bet);
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        p.sendMessage(plugin.langPrefixed("messages.betRefunded")
                                .replace("%tokens%", String.valueOf(bet)));
                    }
                }
            }
        }
    }

    /** Convenience check: can the player afford the entry cost? */
    public boolean canAfford(Player player) {
        if (rule.getCost() <= 0) return true;
        if (rule.getCost() > 0 && plugin.getEconomyManager().isVaultEnabled()) {
            return plugin.getEconomyManager().has((OfflinePlayer) player, rule.getCost());
        }
        return true;
    }

    /** Deduct the entry cost from the player. */
    public boolean chargeCost(Player player) {
        if (rule.getCost() <= 0) return true;
        if (plugin.getEconomyManager().isVaultEnabled()) {
            return plugin.getEconomyManager().withdraw((OfflinePlayer) player, rule.getCost());
        }
        return true;
    }

    /** Refund the entry cost to the player (e.g. when cancelling a multi-invite). */
    public boolean refundCost(Player player) {
        if (rule.getCost() <= 0) return true;
        if (plugin.getEconomyManager().isVaultEnabled()) {
            return plugin.getEconomyManager().deposit((OfflinePlayer) player, rule.getCost());
        }
        return true;
    }
}
