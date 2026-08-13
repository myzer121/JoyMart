package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin GUI for managing the token shop: list categories, add (上架) items by
 * clicking the "add" slot while holding an item on the cursor, edit (修改)
 * existing items' name and price, and remove (下架) items.
 *
 * <p>Every action requires the {@code gamebox.admin.shop} permission, which is
 * checked before this GUI is ever opened (see {@link GuiManager#openShopAdmin}
 * and {@link me.nikl.gamebox.commands.admin.AdminCommand}). Changes are written
 * straight back to {@code tokenShop.yml} via {@link GameBox#saveShopConfig()}.</p>
 */
public class ShopAdmin extends AGui {

    /** Permission required to open / use this GUI. */
    public static final String PERM = "gamebox.admin.shop";

    public ShopAdmin(GameBox plugin) {
        super(plugin, Utility.color(plugin.lang("gui.shopAdminTitle")), 27);
    }

    @Override
    public void build(Player player) {
        clear();
        ConfigurationSection categories = plugin.getShopConfig()
                .getConfigurationSection("shop.categories");
        if (categories == null) {
            setButton(13, Button.display(Utility.createItem(Material.BARRIER,
                    plugin.lang("gui.shopNoCategories"), null)));
        } else {
            int slot = 10;
            for (String key : categories.getKeys(false)) {
                ConfigurationSection cat = categories.getConfigurationSection(key);
                if (cat == null) continue;
                if (slot == 17 || slot == 26) slot += 2;
                if (slot >= getSize() - 1) break;
                int count = cat.isConfigurationSection("items")
                        ? cat.getConfigurationSection("items").getKeys(false).size() : 0;
                List<String> lore = new ArrayList<>();
                lore.add(plugin.lang("gui.shopItemsCount").replace("%count%", String.valueOf(count)));
                lore.add("");
                lore.add(plugin.lang("gui.shopClickToManage"));
                Material mat = Utility.matchMaterial(cat.getString("material", "CHEST"), Material.CHEST);
                ItemStack item = Utility.createItem(mat,
                        cat.getString("name", key) + " &8(" + key + ")", lore);
                final String catKey = key;
                setButton(slot, Button.action("cat_" + key, item,
                        p -> openCategory(p, catKey)));
                slot++;
            }
        }

        // "Add Category" button at fixed slot 18 (bottom-left row)
        setButton(18, Button.action("addcat",
                Utility.createItem(Material.CHEST,
                        plugin.lang("gui.shopAddCategory"),
                        Utility.list(plugin.lang("gui.shopAddCategoryHint"))),
                this::startAddCategory));

        setButton(getSize() - 1, Button.action("back",
                Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                p -> {
                    // Leaving the admin flow — save any inventory changes,
                    // re-clear the temporarily restored inventory, and clear
                    // the admin flow flag so the player returns to normal
                    // GameBox (cleared-inventory) state.
                    plugin.getPluginManager().reClearInventory(p);
                    plugin.getPluginManager().setInAdminFlow(p.getUniqueId(), false);
                    plugin.getGuiManager().openShop(p);
                }));
    }

    /**
     * Begin the two-step chat input flow for creating a new category:
     * first the key (lowercase identifier), then the display name.
     */
    private void startAddCategory(Player player) {
        if (!player.hasPermission(PERM)) {
            player.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return;
        }
        player.closeInventory();
        player.sendMessage(Utility.color(plugin.lang("prefix")
                + plugin.lang("gui.shopCategoryKeyPrompt")));
        plugin.getTextInputHandler().await(player, keyInput -> {
            final String key = keyInput.trim();
            if ("cancel".equalsIgnoreCase(key)) {
                open(player);
                plugin.getGuiManager().track(player.getUniqueId(), this);
                return;
            }
            if (!key.matches("[a-z0-9_]+")) {
                player.sendMessage(plugin.langPrefixed("messages.shopCategoryInvalidKey"));
                open(player);
                plugin.getGuiManager().track(player.getUniqueId(), this);
                return;
            }
            ConfigurationSection categories = plugin.getShopConfig()
                    .getConfigurationSection("shop.categories");
            if (categories != null && categories.contains(key)) {
                player.sendMessage(plugin.langPrefixed("messages.shopCategoryExists")
                        .replace("%key%", key));
                open(player);
                plugin.getGuiManager().track(player.getUniqueId(), this);
                return;
            }
            // Ask for display name
            player.sendMessage(Utility.color(plugin.lang("prefix")
                    + plugin.lang("gui.shopCategoryNamePrompt")));
            plugin.getTextInputHandler().await(player, nameInput -> {
                final String name = nameInput.trim();
                if ("cancel".equalsIgnoreCase(name)) {
                    open(player);
                    plugin.getGuiManager().track(player.getUniqueId(), this);
                    return;
                }
                // Create the category with sensible defaults
                String path = "shop.categories." + key;
                plugin.getShopConfig().set(path + ".name", name);
                plugin.getShopConfig().set(path + ".material", "CHEST");
                plugin.getShopConfig().set(path + ".slot", 10);
                plugin.getShopConfig().set(path + ".title", "&8" + name);
                plugin.getShopConfig().set(path + ".size", 27);
                plugin.getShopConfig().set(path + ".items", new HashMap<>());
                plugin.saveShopConfig();
                player.sendMessage(plugin.langPrefixed("messages.shopCategoryAdded")
                        .replace("%key%", key));
                open(player);
                plugin.getGuiManager().track(player.getUniqueId(), this);
            });
        });
    }

    private void openCategory(Player player, String categoryKey) {
        ShopAdminCategory cat = new ShopAdminCategory(plugin, this, categoryKey);
        cat.open(player);
        plugin.getGuiManager().track(player.getUniqueId(), cat);
    }

    /** One category's management page: list items, add from cursor, edit/remove.
     *  Supports pagination when items exceed one page. */
    public static class ShopAdminCategory extends AGui {

        private final ShopAdmin admin;
        private final String categoryKey;
        private final Map<UUID, Integer> playerPage = new HashMap<>();
        private boolean pendingRemoveCategory = false;

        ShopAdminCategory(GameBox plugin, ShopAdmin admin, String categoryKey) {
            super(plugin, Utility.color(plugin.lang("gui.shopAdminCatTitle")
                    .replace("%cat%", categoryKey)), 54);
            this.admin = admin;
            this.categoryKey = categoryKey;
        }

        @Override
        public boolean allowPlayerInventoryInteraction() {
            // Admins need to pick up items from their own inventory onto the
            // cursor, then click the "add" slot to add the item to the shop.
            return true;
        }

        @Override
        public void open(Player player) {
            // Inventory restore is handled once at the ShopAdmin entry
            // point (GuiManager.openShopAdmin), not per-category. Calling
            // tempRestoreInventory here would overwrite any item movements
            // the player made (e.g. picking up an item onto the cursor),
            // causing items to vanish.
            super.open(player);
        }

        @Override
        public void build(Player player) {
            clear();
            ConfigurationSection items = plugin.getShopConfig()
                    .getConfigurationSection("shop.categories." + categoryKey + ".items");

            int perPage = getSize() - 9; // reserve bottom row for buttons
            int total = (items != null) ? items.getKeys(false).size() : 0;
            int rawPage = playerPage.getOrDefault(player.getUniqueId(), 0);
            int maxPage = Math.max(0, (total - 1) / perPage);
            final int page = Math.min(rawPage, maxPage);

            if (items != null && total > 0) {
                List<String> keys = new ArrayList<>(items.getKeys(false));
                int start = page * perPage;
                int end = Math.min(start + perPage, keys.size());
                int slot = 0;
                for (int i = start; i < end; i++) {
                    if (slot >= perPage) break;
                    String key = keys.get(i);
                    ConfigurationSection item = items.getConfigurationSection(key);
                    if (item == null) continue;
                    Material mat = Utility.matchMaterial(item.getString("material", "PAPER"), Material.PAPER);
                    List<String> lore = new ArrayList<>();
                    lore.add(plugin.lang("gui.shopKey").replace("%key%", key));
                    lore.add(plugin.lang("gui.shopCostTokens").replace("%cost%",
                            String.valueOf(item.getInt("cost", 0))));
                    lore.add("");
                    lore.add(plugin.lang("gui.shopClickToEdit"));
                    ItemStack stack = Utility.createItem(mat,
                            item.getString("name", key), lore);
                    final String itemKey = key;
                    setButton(slot, Button.action("edit_" + key, stack,
                            p -> openItemEditor(p, itemKey)));
                    slot++;
                }
            }

            // Bottom row: prev-page (left), add slot (center), next-page (right), back (far right)
            int base = getSize() - 9;
            if (page > 0) {
                setButton(base, Button.action("prevpage",
                        Utility.createItem(Material.ARROW, "&a\u2190 " + plugin.lang("gui.prevPage"),
                                Utility.list("&7" + plugin.lang("gui.prevPageLore"))),
                        p -> {
                            playerPage.put(p.getUniqueId(), page - 1);
                            build(p);
                        }));
            }
            if (page < maxPage) {
                setButton(base + 1, Button.action("nextpage",
                        Utility.createItem(Material.ARROW, "&a" + plugin.lang("gui.nextPage") + " \u2192",
                                Utility.list("&7" + plugin.lang("gui.nextPageLore"))),
                        p -> {
                            playerPage.put(p.getUniqueId(), page + 1);
                            build(p);
                        }));
            }
            if (total > 0) {
                setButton(base + 4, Button.display(Utility.createItem(Material.PAPER,
                        "&e" + (page + 1) + " / " + (maxPage + 1) + "  &7(" + total + " items)", null)));
            }

            // Add item button (center-right of bottom row)
            int addSlot = base + 5;
            ItemStack addBtn = Utility.createItem(Material.WRITTEN_BOOK,
                    "&a&l+ " + plugin.lang("gui.shopAddItem"),
                    Utility.list(
                            plugin.lang("gui.shopAddHint1"),
                            plugin.lang("gui.shopAddHint2"),
                            plugin.lang("gui.shopAddHint3"),
                            "",
                            plugin.lang("gui.shopDefaultCost")));
            setButton(addSlot, new Button("add", addBtn, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                if (!p.hasPermission(PERM)) {
                    p.sendMessage(plugin.langPrefixed("messages.noPermission"));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) return;
                    ItemStack cursor = p.getItemOnCursor();
                    if (cursor == null || cursor.getType() == Material.AIR) {
                        p.sendMessage(plugin.langPrefixed("messages.shopAddNoItem"));
                        return;
                    }
                    String displayName;
                    ItemMeta cm = cursor.getItemMeta();
                    if (cm != null && cm.hasDisplayName()) {
                        displayName = cm.getDisplayName();
                    } else {
                        displayName = "&f" + cursor.getType().name();
                    }
                    String itemKey = addItem(cursor);
                    if (itemKey == null) {
                        p.sendMessage(plugin.langPrefixed("messages.shopAddFailed"));
                        return;
                    }
                    if (cursor.getAmount() > 1) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        p.setItemOnCursor(cursor);
                    } else {
                        p.setItemOnCursor(null);
                    }
                    p.sendMessage(plugin.langPrefixed("messages.shopAdded")
                            .replace("%item%", displayName)
                            .replace("%key%", itemKey));
                    build(p);
                });
            }));

            // Rename Category button (slot 51)
            setButton(base + 6, Button.action("rename_cat",
                    Utility.createItem(Material.NAME_TAG,
                            "&e" + plugin.lang("gui.shopRenameCategory"),
                            Utility.list(plugin.lang("gui.shopRenameCategoryHint"))),
                    p -> startRenameCategory(p)));

            // Remove Category button (slot 52, with confirmation)
            if (pendingRemoveCategory) {
                setButton(base + 7, Button.action("remove_cat_confirm",
                        Utility.createItem(Material.BARRIER,
                                plugin.lang("gui.shopConfirmRemoveCategory"),
                                Utility.list(plugin.lang("gui.shopConfirmRemoveCategoryHint"))),
                        p -> {
                            plugin.getShopConfig().set(
                                    "shop.categories." + categoryKey, null);
                            plugin.saveShopConfig();
                            p.sendMessage(plugin.langPrefixed("messages.shopCategoryRemoved")
                                    .replace("%key%", categoryKey));
                            // Don't reClearInventory — still within admin flow
                            admin.open(p);
                            plugin.getGuiManager().track(p.getUniqueId(), admin);
                        }));
            } else {
                setButton(base + 7, Button.action("remove_cat",
                        Utility.createItem(Material.BARRIER,
                                plugin.lang("gui.shopRemoveCategory"),
                                Utility.list(plugin.lang("gui.shopRemoveCategoryHint"))),
                        p -> {
                            pendingRemoveCategory = true;
                            build(p);
                        }));
            }

            setButton(getSize() - 1, Button.action("back",
                    Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                    p -> {
                        // Don't reClearInventory here — the player is
                        // navigating within the admin flow (back to the
                        // category list). Inventory is cleared only when
                        // leaving the entire ShopAdmin flow.
                        admin.open(p);
                    }));
        }

        /** Prompt the player to type a new category display name in chat. */
        private void startRenameCategory(Player player) {
            if (!player.hasPermission(PERM)) {
                player.sendMessage(plugin.langPrefixed("messages.noPermission"));
                return;
            }
            player.closeInventory();
            player.sendMessage(Utility.color(plugin.lang("prefix")
                    + plugin.lang("gui.shopCategoryRenamePrompt")));
            plugin.getTextInputHandler().await(player, newNameInput -> {
                final String newName = newNameInput.trim();
                if ("cancel".equalsIgnoreCase(newName)) {
                    open(player);
                    plugin.getGuiManager().track(player.getUniqueId(), this);
                    return;
                }
                plugin.getShopConfig().set(
                        "shop.categories." + categoryKey + ".name", newName);
                plugin.getShopConfig().set(
                        "shop.categories." + categoryKey + ".title", "&8" + newName);
                plugin.saveShopConfig();
                player.sendMessage(plugin.langPrefixed("messages.shopCategoryRenamed")
                        .replace("%name%", Utility.color(newName)));
                open(player);
                plugin.getGuiManager().track(player.getUniqueId(), this);
            });
        }

        private void openItemEditor(Player player, String itemKey) {
            ShopItemEditor editor = new ShopItemEditor(plugin, this, categoryKey, itemKey);
            editor.open(player);
            plugin.getGuiManager().track(player.getUniqueId(), editor);
        }

        /** Delete an item section and persist. */
        private void removeItem(String itemKey) {
            plugin.getShopConfig().set(
                    "shop.categories." + categoryKey + ".items." + itemKey, null);
            plugin.saveShopConfig();
        }

        /**
         * Add a new item derived from the given stack. Returns the generated
         * key, or null on failure. The item is delivered to the buyer via the
         * Bukkit API ({@code giveItem: true}) — no console give command needed.
         */
        private String addItem(ItemStack stack) {
            ConfigurationSection items = plugin.getShopConfig()
                    .getConfigurationSection("shop.categories." + categoryKey + ".items");
            if (items == null) {
                items = plugin.getShopConfig().createSection(
                        "shop.categories." + categoryKey + ".items");
            }
            String key = uniqueKey(items, stack.getType().name());
            String name = "&f" + stack.getType().name();
            List<String> lore = new ArrayList<>();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    name = meta.getDisplayName();
                }
                if (meta.hasLore()) {
                    for (String l : meta.getLore()) lore.add(l);
                }
            }

            String path = "shop.categories." + categoryKey + ".items." + key;
            plugin.getShopConfig().set(path + ".material", stack.getType().name());
            plugin.getShopConfig().set(path + ".giveItem", true);
            plugin.getShopConfig().set(path + ".amount", 1);
            plugin.getShopConfig().set(path + ".name", name);
            plugin.getShopConfig().set(path + ".lore", lore);
            plugin.getShopConfig().set(path + ".cost", 10);
            plugin.getShopConfig().set(path + ".commands", new ArrayList<String>());
            plugin.saveShopConfig();
            return key;
        }

        /** Generate a unique lowercase item key, e.g. diamond_2. */
        private String uniqueKey(ConfigurationSection items, String baseName) {
            String base = baseName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            if (items.getKeys(false).contains(base)) {
                int n = 2;
                while (items.getKeys(false).contains(base + "_" + n)) n++;
                return base + "_" + n;
            }
            return base;
        }
    }

    /**
     * Per-item editor GUI: change display name (via chat), adjust price
     * (+/- buttons), and remove the item (with confirmation).
     */
    public static class ShopItemEditor extends AGui {

        private final ShopAdminCategory category;
        private final String categoryKey;
        private final String itemKey;
        private boolean pendingRemove = false;

        ShopItemEditor(GameBox plugin, ShopAdminCategory category,
                       String categoryKey, String itemKey) {
            super(plugin, Utility.color(plugin.lang("gui.shopEditItemTitle")), 27);
            this.category = category;
            this.categoryKey = categoryKey;
            this.itemKey = itemKey;
        }

        @Override
        public boolean allowPlayerInventoryInteraction() {
            // The editor only uses buttons (rename, price +/-, remove, back).
            // No cursor-item interaction is needed, so block inventory movement.
            return false;
        }

        private ConfigurationSection item() {
            return plugin.getShopConfig()
                    .getConfigurationSection("shop.categories." + categoryKey + ".items." + itemKey);
        }

        @Override
        public void build(Player player) {
            clear();
            ConfigurationSection item = item();
            if (item == null) {
                // Item was removed; go back
                category.open(player);
                return;
            }

            // Slot 4: item preview (display only)
            Material mat = Utility.matchMaterial(item.getString("material", "PAPER"), Material.PAPER);
            List<String> previewLore = new ArrayList<>();
            previewLore.add(plugin.lang("gui.shopKey").replace("%key%", itemKey));
            previewLore.add(plugin.lang("gui.shopCostTokens").replace("%cost%",
                    String.valueOf(item.getInt("cost", 0))));
            previewLore.add("");
            previewLore.add(plugin.lang("gui.shopEditHint2"));
            setButton(4, Button.display(Utility.createItem(mat,
                    item.getString("name", itemKey), previewLore)));

            // Row 2 (slots 10-16): edit name
            setButton(11, Button.action("rename",
                    Utility.createItem(Material.NAME_TAG,
                            "&e" + plugin.lang("gui.shopEditName"),
                            Utility.list(plugin.lang("gui.shopEditNameHint"))),
                    p -> startRename(p)));

            // Row 3 (slots 19-25): price adjustment
            // -10 / -1 / current / +1 / +10
            int cost = item.getInt("cost", 0);
            setButton(20, Button.action("m10",
                    Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-10", null),
                    p -> adjustPrice(p, -10)));
            setButton(21, Button.action("m1",
                    Utility.createItem(Material.RED_STAINED_GLASS_PANE, "&c-1", null),
                    p -> adjustPrice(p, -1)));
            setButton(22, Button.display(
                    Utility.createItem(Material.GOLD_BLOCK,
                            "&e" + plugin.lang("gui.shopCostLabel") + ": &f" + cost, null)));
            setButton(23, Button.action("p1",
                    Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+1", null),
                    p -> adjustPrice(p, +1)));
            setButton(24, Button.action("p10",
                    Utility.createItem(Material.LIME_STAINED_GLASS_PANE, "&a+10", null),
                    p -> adjustPrice(p, +10)));

            // Remove item (with confirmation)
            if (pendingRemove) {
                setButton(13, Button.action("remove_confirm",
                        Utility.createItem(Material.BARRIER,
                                "&c&l" + plugin.lang("gui.shopConfirmRemove"),
                                Utility.list(plugin.lang("gui.shopConfirmRemoveHint"))),
                        p -> {
                            category.removeItem(itemKey);
                            p.sendMessage(plugin.langPrefixed("messages.shopRemoved")
                                    .replace("%item%", item.getString("name", itemKey)));
                            category.open(p);
                            plugin.getGuiManager().track(p.getUniqueId(), category);
                        }));
            } else {
                setButton(13, Button.action("remove",
                        Utility.createItem(Material.BARRIER,
                                "&c" + plugin.lang("gui.shopClickToRemove"),
                                Utility.list(plugin.lang("gui.shopClickToRemoveHint"))),
                        p -> {
                            pendingRemove = true;
                            build(p);
                        }));
            }

            // Back button
            setButton(getSize() - 1, Button.action("back",
                    Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                    p -> category.open(p)));
        }

        /** Prompt the player to type a new name in chat. */
        private void startRename(Player player) {
            player.closeInventory();
            player.sendMessage(Utility.color(plugin.lang("prefix")
                    + plugin.lang("gui.shopRenamePrompt")));
            plugin.getTextInputHandler().await(player, newName -> {
                ConfigurationSection item = item();
                if (item != null) {
                    item.set("name", newName);
                    plugin.saveShopConfig();
                    player.sendMessage(plugin.langPrefixed("messages.shopNameChanged")
                            .replace("%name%", Utility.color(newName)));
                }
                open(player);
                plugin.getGuiManager().track(player.getUniqueId(), this);
            });
        }

        /** Adjust the item's price by delta (clamped to >= 0). */
        private void adjustPrice(Player player, int delta) {
            ConfigurationSection item = item();
            if (item == null) return;
            int newCost = Math.max(0, item.getInt("cost", 0) + delta);
            item.set("cost", newCost);
            plugin.saveShopConfig();
            player.sendMessage(plugin.langPrefixed("messages.shopPriceChanged")
                    .replace("%cost%", String.valueOf(newCost)));
            build(player);
        }
    }
}
