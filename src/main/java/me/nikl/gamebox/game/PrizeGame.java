package me.nikl.gamebox.game;

import org.bukkit.entity.Player;

/**
 * Marker interface for games whose rewards come from a configurable prize pool
 * (e.g. lottery, slot machine). The {@link me.nikl.gamebox.inventory.GameGui}
 * shows an "Edit Prizes" button for admins when the game implements this.
 */
public interface PrizeGame {

    /** The editable prize pool backing this game. */
    PrizePool getPrizePool();

    /** Open the prize-pool editor GUI for the given admin player. */
    void openPrizeEditor(Player player);
}
