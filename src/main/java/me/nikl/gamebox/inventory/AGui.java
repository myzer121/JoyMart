package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * Abstract base for all GameBox GUIs. Implements {@link InventoryHolder} so the
 * framework can identify GameBox inventories, and routes clicks to per-slot
 * {@link Button}s.
 *
 * <p>Subclasses populate the inventory with buttons via {@link #setButton} and
 * implement {@link #build(Player)} to (re)construct the contents.</p>
 *
 * <p>Buttons are stored in a fixed-size array indexed by raw slot number
 * rather than a {@code HashMap}. Since inventory slots are contiguous
 * integers in {@code [0, size)}, an array gives O(1) lookup with no hashing
 * or boxing overhead — important because {@link #handleClick} fires on every
 * click event.</p>
 */
public abstract class AGui implements InventoryHolder {

    protected final GameBox plugin;
    protected String title;
    protected final int size;
    protected final Button[] buttons;
    protected Inventory inventory;

    public AGui(GameBox plugin, String title, int size) {
        this.plugin = plugin;
        this.title = title;
        this.size = size;
        this.buttons = new Button[size];
    }

    /** Lazily create the inventory using this gui as its holder. */
    protected Inventory createInventory() {
        return Bukkit.createInventory(this, size, me.nikl.gamebox.utility.Utility.color(title));
    }

    /** Recreate the inventory with a new title (e.g. when language changes).
     *  Copies existing buttons to the new inventory. */
    protected void recreateInventory(String newTitle) {
        this.title = newTitle;
        Inventory old = this.inventory;
        this.inventory = createInventory();
        if (old != null) {
            // Preserve existing items
            for (int i = 0; i < size && i < old.getSize(); i++) {
                ItemStack item = old.getItem(i);
                if (item != null) this.inventory.setItem(i, item);
            }
        }
    }

    public Inventory getInventory() {
        if (inventory == null) {
            inventory = createInventory();
        }
        return inventory;
    }

    /** Place a button at the given slot and update the displayed item. */
    public void setButton(int slot, Button button) {
        if (slot >= 0 && slot < buttons.length) {
            buttons[slot] = button;
        }
        if (button != null && button.getItem() != null) {
            getInventory().setItem(slot, button.getItem());
        }
    }

    public void setItem(int slot, ItemStack item) {
        getInventory().setItem(slot, item);
    }

    public Button getButton(int slot) {
        if (slot < 0 || slot >= buttons.length) return null;
        return buttons[slot];
    }

    /** Rebuild the gui contents for the given viewer (e.g. fresh pagination). */
    public abstract void build(Player player);

    /** Get the title for the current language context. Subclasses can override
     *  to provide a dynamic title that changes with the active language.
     *  The default returns the static title set at construction time. */
    protected String getDynamicTitle() {
        return title;
    }

    /** Open this gui for a player. Sets the active language to the player's
     *  detected language before building, then resets it. This ensures all
     *  language lookups during build() use the player's language. */
    public void open(Player player) {
        plugin.getLanguageManager().setActiveLanguage(player);
        try {
            // Recreate the inventory so the title matches the active language.
            // The title may have been set at construction time with a different
            // language context (e.g. during plugin startup before any player
            // joins, or when the config language was changed after startup).
            recreateInventory(getDynamicTitle());
            build(player);
        } finally {
            plugin.getLanguageManager().resetActiveLanguage();
        }
        player.openInventory(getInventory());
    }

    /**
     * Whether this GUI allows the player to freely interact with their own
     * inventory (picking up items onto the cursor, shift-clicking, etc.).
     * Admin GUIs that need items placed on the cursor (ShopAdmin add-item,
     * PrizePoolEditor add-prize) override this to return true.
     */
    public boolean allowPlayerInventoryInteraction() {
        return false;
    }

    /** Handle a click: route to the button at the clicked slot. */
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        boolean clickedOwnInventory = (slot < 0 || slot >= size);
        boolean shiftClick = event.isShiftClick();

        if (clickedOwnInventory) {
            if (allowPlayerInventoryInteraction() && !shiftClick) {
                // Admin GUIs (ShopAdmin, PrizePoolEditor) need the player to
                // be able to pick up items onto the cursor (regular left/right
                // click) so they can then click the "add" slot. Let it proceed.
                return;
            }
            // Normal GUIs, or shift-click in admin GUIs (would move items
            // into the GUI): cancel to prevent any item movement.
            event.setCancelled(true);
            return;
        }

        // Click inside this GUI's inventory. Always cancel to prevent item
        // movement into/out of GUI slots; buttons handle their own logic.
        event.setCancelled(true);
        Button button = getButton(slot);
        if (button != null) {
            button.handleClick(event);
        }
    }

    public int getSize() { return size; }
    public String getTitle() { return title; }

    /** Clear all buttons and items. */
    public void clear() {
        Arrays.fill(buttons, null);
        getInventory().clear();
    }
}
