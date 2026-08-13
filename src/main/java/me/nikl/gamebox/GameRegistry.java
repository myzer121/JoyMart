package me.nikl.gamebox;

import me.nikl.gamebox.game.Game;
import me.nikl.gamebox.game.impl.battleship.GameBattleship;
import me.nikl.gamebox.game.impl.bejeweled.GameBejeweled;
import me.nikl.gamebox.game.impl.chess.GameChess;
import me.nikl.gamebox.game.impl.connect4.GameConnect4;
import me.nikl.gamebox.game.impl.dinorun.GameDinoRun;
import me.nikl.gamebox.game.impl.lottery.GameLottery;
import me.nikl.gamebox.game.impl.maze.GameMaze;
import me.nikl.gamebox.game.impl.minesweeper.GameMinesweeper;
import me.nikl.gamebox.game.impl.monopoly.GameMonopoly;
import me.nikl.gamebox.game.impl.rockpaperscissors.GameRockPaperScissors;
import me.nikl.gamebox.game.impl.snake.GameSnake;
import me.nikl.gamebox.game.impl.slotmachine.GameSlotMachine;
import me.nikl.gamebox.game.impl.tictactoe.GameTicTacToe;
import me.nikl.gamebox.game.impl.twentyfortyeight.Game2048;
import me.nikl.gamebox.game.impl.whackamole.GameWhackAMole;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all built-in games. On {@link #reload()} it reads games.yml and,
 * for every enabled game, reflectively instantiates the matching {@link Game}
 * subclass and runs its enable lifecycle.
 *
 * <p>All games ship inside the main jar — no external modules are loaded.</p>
 */
public class GameRegistry {

    private final GameBox plugin;
    private final Map<String, Class<? extends Game>> gameClasses = new LinkedHashMap<>();
    private final Map<String, Game> loadedGames = new LinkedHashMap<>();
    private final Map<String, List<String>> subCommands = new LinkedHashMap<>();

    public GameRegistry(GameBox plugin) {
        this.plugin = plugin;
        registerBuiltins();
    }

    /** Register all built-in game classes. */
    private void registerBuiltins() {
        gameClasses.put("2048", Game2048.class);
        gameClasses.put("minesweeper", GameMinesweeper.class);
        gameClasses.put("whackamole", GameWhackAMole.class);
        gameClasses.put("bejeweled", GameBejeweled.class);
        gameClasses.put("lottery", GameLottery.class);
        gameClasses.put("slotmachine", GameSlotMachine.class);
        gameClasses.put("maze", GameMaze.class);
        gameClasses.put("battleship", GameBattleship.class);
        gameClasses.put("connect4", GameConnect4.class);
        gameClasses.put("tictactoe", GameTicTacToe.class);
        gameClasses.put("rockpaperscissors", GameRockPaperScissors.class);
        gameClasses.put("snake", GameSnake.class);
        gameClasses.put("dinorun", GameDinoRun.class);
        gameClasses.put("chess", GameChess.class);
        gameClasses.put("monopoly", GameMonopoly.class);
    }

    /** Load enabled games per games.yml and start them. */
    public void reload() {
        // Disable previously loaded games
        for (Game game : loadedGames.values()) {
            try {
                game.onDisable();
            } catch (Exception e) {
                plugin.getLogger().warning("Error disabling game " + game.getGameId() + ": " + e.getMessage());
            }
        }
        loadedGames.clear();
        subCommands.clear();

        ConfigurationSection games = plugin.getGamesConfig().getConfigurationSection("games");
        if (games == null) {
            plugin.getLogger().warning("No 'games' section found in games.yml");
            return;
        }

        for (String gameId : gameClasses.keySet()) {
            ConfigurationSection sec = games.getConfigurationSection(gameId);
            boolean enabled = sec == null || sec.getBoolean("enabled", true);
            if (!enabled) {
                plugin.getLogger().info("Game disabled: " + gameId);
                continue;
            }
            List<String> subs = sec != null ? sec.getStringList("subCommands") : new ArrayList<>();
            if (subs.isEmpty()) subs.add(gameId);
            subCommands.put(gameId, subs);

            try {
                Class<? extends Game> clazz = gameClasses.get(gameId);
                Constructor<? extends Game> ctor = clazz.getConstructor(GameBox.class);
                Game game = ctor.newInstance(plugin);
                game.onEnable();
                loadedGames.put(gameId, game);
                registerGamePermissions(gameId);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load game " + gameId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /** Game sub-commands are handled dynamically by GeneralCommand via /gamebox <arg>. */

    /** Register the dynamic per-game permissions (default true so anyone can play). */
    private void registerGamePermissions(String gameId) {
        registerPerm("gamebox.play." + gameId);
        registerPerm("gamebox.gamegui." + gameId);
    }

    private void registerPerm(String name) {
        if (Bukkit.getPluginManager().getPermission(name) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(name, PermissionDefault.TRUE));
        }
    }

    public Game getGame(String gameId) {
        return loadedGames.get(gameId);
    }

    public Collection<Game> getEnabledGames() {
        return loadedGames.values();
    }

    public boolean isGameEnabled(String gameId) {
        return loadedGames.containsKey(gameId);
    }

    /** All game ids of loaded (enabled) games. */
    public java.util.Set<String> getGameIds() {
        return new java.util.HashSet<>(loadedGames.keySet());
    }

    /** Resolve a sub-command string to a game id, or null. */
    public String resolveSubCommand(String sub) {
        if (sub == null) return null;
        String lower = sub.toLowerCase();
        for (Map.Entry<String, List<String>> entry : subCommands.entrySet()) {
            for (String s : entry.getValue()) {
                if (s.equalsIgnoreCase(lower)) return entry.getKey();
            }
            if (entry.getKey().equalsIgnoreCase(lower)) return entry.getKey();
        }
        return null;
    }

    /** Toggle a game on/off at runtime (admin command). */
    public boolean setEnabled(String gameId, boolean enabled) {
        Game game = loadedGames.get(gameId);
        if (game == null && !enabled) return false;
        if (enabled && game == null) {
            // load on demand
            try {
                Class<? extends Game> clazz = gameClasses.get(gameId);
                if (clazz == null) return false;
                Constructor<? extends Game> ctor = clazz.getConstructor(GameBox.class);
                Game g = ctor.newInstance(plugin);
                g.onEnable();
                loadedGames.put(gameId, g);
            } catch (Exception e) {
                return false;
            }
        } else if (!enabled && game != null) {
            game.onDisable();
            loadedGames.remove(gameId);
        }
        return true;
    }
}
