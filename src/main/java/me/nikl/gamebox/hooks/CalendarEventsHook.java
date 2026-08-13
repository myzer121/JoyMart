package me.nikl.gamebox.hooks;

import me.nikl.gamebox.GameBox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Optional integration with CalendarEvents.
 *
 * <p>CalendarEvents fires real-world holiday events. Because it is an optional
 * soft-dependency whose event API cannot be safely referenced at compile time
 * here, this hook performs only a presence check and exposes a callable bonus
 * method. Other modules (or a future CalendarEvents event listener registered
 * when the API is available) can call {@link #grantHolidayBonus(int)} to reward
 * online players on holidays.</p>
 */
public class CalendarEventsHook {

    private final GameBox plugin;
    private boolean hooked = false;

    public CalendarEventsHook(GameBox plugin) {
        this.plugin = plugin;
    }

    /** Detect CalendarEvents; returns true if present. */
    public boolean hook() {
        Plugin cal = Bukkit.getPluginManager().getPlugin("CalendarEvents");
        if (cal == null) {
            plugin.getLogger().info("CalendarEvents not found; holiday hooks disabled.");
            return false;
        }
        this.hooked = true;
        plugin.getLogger().info("Hooked into CalendarEvents v" + cal.getDescription().getVersion() + ".");
        return true;
    }

    public boolean isHooked() {
        return hooked;
    }

    /** Grant a token bonus to every online player (call on a holiday event). */
    public void grantHolidayBonus(int tokens) {
        if (tokens <= 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            me.nikl.gamebox.data.GBPlayer gb = plugin.getPluginManager().getPlayer(p.getUniqueId());
            if (gb != null) {
                gb.addTokens(tokens);
            }
        }
    }
}
