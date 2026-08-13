package me.nikl.gamebox.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * A single clickable element in a GameBox GUI: an item plus the action to run
 * when a player clicks it.
 */
public class Button {

    private final String id;
    private final ItemStack item;
    private final Consumer<InventoryClickEvent> action;

    public Button(String id, ItemStack item, Consumer<InventoryClickEvent> action) {
        this.id = id;
        this.item = item;
        this.action = action;
    }

    /** A button whose only purpose is to be displayed (no action). */
    public static Button display(ItemStack item) {
        return new Button("display", item, event -> event.setCancelled(true));
    }

    /** A button that cancels the click and runs a player action. */
    public static Button action(String id, ItemStack item, Consumer<Player> action) {
        return new Button(id, item, event -> {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                action.accept((Player) event.getWhoClicked());
            }
        });
    }

    public String getId() { return id; }
    public ItemStack getItem() { return item; }

    public void handleClick(InventoryClickEvent event) {
        if (action != null) {
            action.accept(event);
        } else {
            event.setCancelled(true);
        }
    }
}
