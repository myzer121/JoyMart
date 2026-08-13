package me.nikl.gamebox.inventory;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.data.GBPlayer;
import me.nikl.gamebox.nms.NmsUtility;
import me.nikl.gamebox.utility.Utility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Token shop: lists purchasable items from tokenShop.yml. Buying deducts tokens
 * and runs the configured console commands.
 *
 * <p>Both the top-level category list and each category's item list support
 * pagination. When a page is full, a "next page" button appears on the right
 * side of the bottom row; a "previous page" button appears on the left side.
 * The current page is tracked per-player so multiple players can browse
 * independently.</p>
 *
 * <p>Per-item fields supported (see tokenShop.yml header for full docs):</p>
 * <ul>
 *   <li>{@code permission} — visibility gate (player must have to see the item)</li>
 *   <li>{@code buyPermission} — purchase gate (player must have to buy)</li>
 *   <li>{@code closeAfter} — close GUI after purchase (default true)</li>
 *   <li>{@code confirm} — require a confirmation click (default false)</li>
 * </ul>
 */
public class Shop extends AGui {

    private ConfigurationSection categories;
    /** Tracks players who have clicked an item requiring confirmation, awaiting second click. */
    private final Map<UUID, String> pendingConfirm = new HashMap<>();
    /** Current page in the top-level category list, per player. */
    private final Map<UUID, Integer> categoryPage = new HashMap<>();

    /** Slots reserved for navigation buttons in the bottom row. */
    private static final int PREV_SLOT = 0;       // bottom-left of last row
    private static final int NEXT_SLOT_OFFSET = 1; // right of prev
    private static final int BACK_SLOT_OFFSET = 8; // bottom-right
    private static final int ADMIN_SLOT_OFFSET = 7; // left of back

    public Shop(GameBox plugin) {
        super(plugin, plugin.getShopConfig().getString("shop.title", plugin.lang("gui.tokenShop")),
                plugin.getShopConfig().getInt("shop.size", 27));
        refreshCategories();
    }

    @Override
    protected String getDynamicTitle() {
        return plugin.getShopConfig().getString("shop.title", plugin.lang("gui.tokenShop"));
    }

    /** Re-read the categories section from the (possibly reloaded) shop config. */
    public void refreshCategories() {
        this.categories = plugin.getShopConfig().getConfigurationSection("shop.categories");
    }

    /** Number of item/category slots available (everything except the bottom navigation row). */
    private int contentSlotCount() {
        return getSize() - 9;
    }

    @Override
    public void build(Player player) {
        clear();
        refreshCategories();
        if (categories == null) {
            setButton(13, Button.display(Utility.createItem(Material.BARRIER, plugin.lang("gui.shopDisabled"), null)));
            return;
        }

        // Collect visible categories for this player
        List<String> visibleKeys = new ArrayList<>();
        for (String key : categories.getKeys(false)) {
            ConfigurationSection cat = categories.getConfigurationSection(key);
            if (cat == null) continue;
            String catPerm = cat.getString("permission", "");
            if (!catPerm.isEmpty() && !player.hasPermission(catPerm)) continue;
            visibleKeys.add(key);
        }

        if (visibleKeys.isEmpty()) {
            setButton(13, Button.display(Utility.createItem(Material.BARRIER, plugin.lang("gui.shopDisabled"), null)));
        } else {
            int perPage = contentSlotCount();
            int rawPage = categoryPage.getOrDefault(player.getUniqueId(), 0);
            int maxPage = Math.max(0, (visibleKeys.size() - 1) / perPage);
            final int page = Math.min(rawPage, maxPage);
            int start = page * perPage;
            int end = Math.min(start + perPage, visibleKeys.size());

            int slot = 0;
            for (int i = start; i < end; i++) {
                String key = visibleKeys.get(i);
                ConfigurationSection cat = categories.getConfigurationSection(key);
                if (cat == null) continue;
                if (slot >= contentSlotCount()) break;

                Material mat = Utility.matchMaterial(cat.getString("material", "CHEST"), Material.CHEST);
                ItemStack item = Utility.createItem(mat, cat.getString("name", key),
                        cat.getStringList("lore"));
                setButton(slot, Button.action("cat_" + key, item,
                        p -> openCategory(p, key)));
                slot++;
            }

            // Navigation row (bottom row)
            int base = getSize() - 9;
            if (page > 0) {
                setButton(base + PREV_SLOT, Button.action("prevpage",
                        Utility.createItem(Material.ARROW, "&a\u2190 " + plugin.lang("gui.prevPage"),
                                Utility.list("&7" + plugin.lang("gui.prevPageLore"))),
                        p -> {
                            categoryPage.put(p.getUniqueId(), page - 1);
                            build(p);
                        }));
            }
            if (page < maxPage) {
                setButton(base + NEXT_SLOT_OFFSET, Button.action("nextpage",
                        Utility.createItem(Material.ARROW, "&a" + plugin.lang("gui.nextPage") + " \u2192",
                                Utility.list("&7" + plugin.lang("gui.nextPageLore"))),
                        p -> {
                            categoryPage.put(p.getUniqueId(), page + 1);
                            build(p);
                        }));
            }
            // Page indicator
            setButton(base + 4, Button.display(Utility.createItem(Material.PAPER,
                    "&e" + (page + 1) + " / " + (maxPage + 1), null)));
        }

        setButton(getSize() - 1, Button.action("back",
                Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                p -> plugin.getGuiManager().openMain(p)));

        // Admin "manage shop" button — only visible to shop admins.
        if (player.hasPermission(ShopAdmin.PERM)) {
            setButton(getSize() - 2, Button.action("admin",
                    Utility.createItem(Material.PAPER,
                            plugin.lang("gui.shopAdminButton"),
                            Utility.list("&7" + plugin.lang("gui.shopAdminLore"))),
                    p -> plugin.getGuiManager().openShopAdmin(p)));
        }
    }

    /** Open a shop category page. Package-private so {@link ShopQuantityGui}
     *  can return the player to the category after a cancelled purchase. */
    void openCategory(Player player, String categoryKey) {
        ConfigurationSection cat = categories.getConfigurationSection(categoryKey);
        if (cat == null) return;
        int size = cat.getInt("size", 27);
        ShopCategory category = new ShopCategory(plugin, this, categoryKey,
                Utility.color(cat.getString("title", "&8Shop")), size);
        category.open(player);
        plugin.getGuiManager().track(player.getUniqueId(), category);
    }

    /**
     * Attempt to buy an item. Honors {@code buyPermission} and {@code confirm}.
     * Returns true only when the purchase actually completes.
     */
    public boolean buy(Player player, String categoryKey, String itemKey) {
        ConfigurationSection item = plugin.getShopConfig()
                .getConfigurationSection("shop.categories." + categoryKey + ".items." + itemKey);
        if (item == null) return false;

        GBPlayer gb = plugin.getPluginManager().getPlayer(player.getUniqueId());
        if (gb == null) return false;

        // Buy permission check (before deducting anything)
        String buyPerm = item.getString("buyPermission",
                item.getString("permission", ""));
        if (!buyPerm.isEmpty() && !player.hasPermission(buyPerm)) {
            player.sendMessage(plugin.langPrefixed("messages.noPermission"));
            return false;
        }

        // Confirmation step
        boolean requireConfirm = item.getBoolean("confirm", false);
        if (requireConfirm) {
            String pendingKey = categoryKey + ":" + itemKey;
            String previous = pendingConfirm.get(player.getUniqueId());
            if (!pendingKey.equals(previous)) {
                pendingConfirm.put(player.getUniqueId(), pendingKey);
                player.sendMessage(plugin.langPrefixed("messages.confirmPurchase")
                        .replace("%item%", item.getString("name", itemKey)));
                return false;
            }
            pendingConfirm.remove(player.getUniqueId());
        }

        int cost = item.getInt("cost", 0);
        if (cost > 0 && !gb.removeTokens(cost)) {
            player.sendMessage(plugin.langPrefixed("messages.notEnoughTokens")
                    .replace("%tokens%", String.valueOf(cost))
                    .replace("%balance%", String.valueOf(gb.getTokens())));
            return false;
        }

        // Give the item by storing it in the delivery box when giveItem is true.
        if (item.getBoolean("giveItem", false)) {
            String matName = item.getString("material", "");
            if (matName == null || matName.isEmpty()) {
                plugin.getLogger().warning("Shop item " + categoryKey + ":" + itemKey
                        + " has giveItem=true but no material set; skipping item give.");
            } else {
                Material mat = Utility.matchMaterial(matName, null);
                if (mat == null) {
                    plugin.getLogger().warning("Shop item " + categoryKey + ":" + itemKey
                            + " has unrecognized material '" + matName + "'; skipping item give.");
                } else if (mat.isAir()) {
                    plugin.getLogger().warning("Shop item " + categoryKey + ":" + itemKey
                            + " has AIR material; skipping item give.");
                } else {
                    int amount = Math.max(1, item.getInt("amount", 1));
                    ItemStack stack = new ItemStack(mat, amount);
                    plugin.getDeliveryBoxManager().addItem(player, stack);
                }
            }
        }

        for (String cmd : item.getStringList("commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }
        player.sendMessage(plugin.langPrefixed("messages.bought")
                .replace("%item%", item.getString("name", itemKey))
                .replace("%cost%", String.valueOf(cost)));
        if (item.getBoolean("giveItem", false)) {
            player.sendMessage(plugin.langPrefixed("messages.deliveryStored")
                    .replace("%item%", item.getString("name", itemKey)));
        }

        boolean closeAfter = item.getBoolean("closeAfter", true);
        if (closeAfter) {
            player.closeInventory();
        }
        return true;
    }

    /** Clear any pending confirmation when the player leaves the shop. */
    public void clearPending(UUID uuid) {
        pendingConfirm.remove(uuid);
    }

    /** One category page within the shop, with per-player pagination. */
    public static class ShopCategory extends AGui {
        private final Shop shop;
        private final String categoryKey;
        private final Map<UUID, Integer> playerPage = new HashMap<>();

        ShopCategory(GameBox plugin, Shop shop, String categoryKey, String title, int size) {
            super(plugin, title, size);
            this.shop = shop;
            this.categoryKey = categoryKey;
        }

        private int contentSlotCount() {
            return getSize() - 9;
        }

        @Override
        public void build(Player player) {
            clear();
            ConfigurationSection items = plugin.getShopConfig()
                    .getConfigurationSection("shop.categories." + categoryKey + ".items");

            if (items == null || items.getKeys(false).isEmpty()) {
                setButton(13, Button.display(Utility.createItem(Material.BARRIER,
                        plugin.lang("gui.shopEmpty"), null)));
            } else {
                // Collect visible items for this player
                List<String> visibleKeys = new ArrayList<>();
                for (String key : items.getKeys(false)) {
                    ConfigurationSection item = items.getConfigurationSection(key);
                    if (item == null) continue;
                    String perm = item.getString("permission", "");
                    if (!perm.isEmpty() && !player.hasPermission(perm)) continue;
                    visibleKeys.add(key);
                }

                int perPage = contentSlotCount();
                int rawPage = playerPage.getOrDefault(player.getUniqueId(), 0);
                int maxPage = Math.max(0, (visibleKeys.size() - 1) / perPage);
                final int page = Math.min(rawPage, maxPage);
                int start = page * perPage;
                int end = Math.min(start + perPage, visibleKeys.size());

                int slot = 0;
                for (int i = start; i < end; i++) {
                    if (slot >= contentSlotCount()) break;
                    String key = visibleKeys.get(i);
                    ConfigurationSection item = items.getConfigurationSection(key);
                    if (item == null) continue;

                    final Material mat = Utility.matchMaterial(item.getString("material", "PAPER"), Material.PAPER);
                    List<String> lore = new ArrayList<>(item.getStringList("lore"));
                    lore.add(plugin.lang("gui.shopCost").replace("%cost%", String.valueOf(item.getInt("cost", 0))));
                    if (item.getBoolean("confirm", false)) {
                        lore.add(plugin.lang("gui.shopClickAgainConfirm"));
                    }
                    ItemStack stack = Utility.createItem(mat, item.getString("name", key), lore);
                    NmsUtility.getInstance().setTag(stack, NmsUtility.Keys.SHOP_ITEM,
                            categoryKey + ":" + key);
                    final boolean giveItem = item.getBoolean("giveItem", false);
                    final int unitCost = item.getInt("cost", 0);
                    final String displayName = item.getString("name", key);
                    setButton(slot, Button.action("buy_" + key, stack,
                            p -> {
                                if (giveItem && mat != null && !mat.isAir()) {
                                    // Open the quantity selector so the player can
                                    // choose how many to buy; items stack to the
                                    // delivery box on confirm.
                                    ShopQuantityGui qtyGui = new ShopQuantityGui(
                                            plugin, shop, categoryKey, key,
                                            displayName, mat, unitCost);
                                    qtyGui.open(p);
                                    plugin.getGuiManager().track(p.getUniqueId(), qtyGui);
                                } else {
                                    // Non-item purchases (commands only) use the
                                    // original single-buy flow.
                                    shop.buy(p, categoryKey, key);
                                }
                            }));
                    slot++;
                }

                // Navigation row
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
                setButton(base + 4, Button.display(Utility.createItem(Material.PAPER,
                        "&e" + (page + 1) + " / " + (maxPage + 1), null)));
            }

            setButton(getSize() - 1, Button.action("back",
                    Utility.createItem(Material.ARROW, plugin.lang("gui.backButton"), null),
                    p -> shop.open(p)));
        }
    }
}
