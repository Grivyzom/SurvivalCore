package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MagicLecternMenu implements Listener {
    private static final Main plugin = Main.getPlugin(Main.class);

    private static final NamespacedKey keyRecipes = new NamespacedKey(plugin, "lectern_menu_recipes");
    private static final NamespacedKey keyInfo    = new NamespacedKey(plugin, "lectern_menu_info");
    private static final NamespacedKey keyUpgrade = new NamespacedKey(plugin, "lectern_menu_upgrade");
    private static final NamespacedKey keyLevel   = new NamespacedKey(plugin, "lectern_level");

    private static final String TITLE = ChatColor.DARK_PURPLE + "📜 Atril Mágico 📜";
    private static final Map<UUID, Location> lecternLocations = new HashMap<>();

    /** Getter para que otros menús (p.ej. recetas) recuperen la ubicación del atril. */
    public static Location getLecternLocation(UUID uuid) {
        return lecternLocations.get(uuid);
    }

    /** Abre el menú del Atril Mágico para el jugador. */
    public static void open(Player p, Block lecternBlock) {
        BlockState bs = lecternBlock.getState();
        if (!(bs instanceof TileState ts)) return;
        lecternLocations.put(p.getUniqueId(), lecternBlock.getLocation());

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        // Bordes
        ItemStack border = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        // Botones centrales
        inv.setItem(11, createRecipeBook(p));
        inv.setItem(13, createInfoItem(p, ts));
        inv.setItem(15, createUpgradeButton(p, ts));

        p.openInventory(inv);
    }

    /** Debe ser public para que otros menús (recetas) puedan usarlo. */
    public static ItemStack createGuiItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createRecipeBook(Player p) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "📖 Libro de Recetas");
        meta.setLore(List.of(
                ChatColor.GRAY + "Combina objetos para descubrir nuevas recetas.",
                ChatColor.GRAY + "Haz clic para ver tus recetas desbloqueadas."
        ));
        meta.getPersistentDataContainer().set(keyRecipes, PersistentDataType.BYTE, (byte)1);
        book.setItemMeta(meta);
        return book;
    }

    private static ItemStack createInfoItem(Player p, TileState ts) {
        int level = ts.getPersistentDataContainer().getOrDefault(keyLevel, PersistentDataType.INTEGER, 1);
        int baseCost = plugin.getConfig().getInt("lectern.baseUpgradeCost", 100);
        int cost = baseCost * level;

        ItemStack info = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "ℹ Información Atril Mágico");
        meta.setLore(List.of(
                ChatColor.WHITE + "Nivel actual: "         + ChatColor.YELLOW + level,
                ChatColor.WHITE + "Coste siguiente nivel: " + ChatColor.GOLD   + cost + " XP"
        ));
        meta.getPersistentDataContainer().set(keyInfo, PersistentDataType.BYTE, (byte)1);
        info.setItemMeta(meta);
        return info;
    }

    private static ItemStack createUpgradeButton(Player p, TileState ts) {
        int level = ts.getPersistentDataContainer().getOrDefault(keyLevel, PersistentDataType.INTEGER, 1);
        int maxLevel = plugin.getConfig().getInt("lectern.maxLevel", 10);

        ItemStack up = new ItemStack(level < maxLevel
                ? Material.ENCHANTED_GOLDEN_APPLE
                : Material.NETHER_STAR);
        ItemMeta meta = up.getItemMeta();
        meta.setDisplayName(level < maxLevel
                ? ChatColor.GREEN + "⬆ Mejorar Atril Mágico"
                : ChatColor.LIGHT_PURPLE + "✔ Nivel Máximo alcanzado");

        if (level < maxLevel) {
            int baseCost = plugin.getConfig().getInt("lectern.baseUpgradeCost", 100);
            int cost = baseCost * level;
            meta.setLore(List.of(
                    ChatColor.GRAY + "Haz clic para subir de nivel.",
                    "",
                    ChatColor.WHITE + "Coste: " + ChatColor.GOLD + cost + " XP"
            ));
            meta.getPersistentDataContainer().set(keyUpgrade, PersistentDataType.BYTE, (byte)1);
        } else {
            meta.setLore(List.of(
                    ChatColor.GRAY + "¡Ya has llegado al nivel máximo!"
            ));
        }

        up.setItemMeta(meta);
        return up;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();

        // — Abrir submenú de recetas —
        if (pdc.has(keyRecipes, PersistentDataType.BYTE)) {
            MagicLecternRecipesMenu.open(p);
            return;
        }

        // — Refrescar info —
        if (pdc.has(keyInfo, PersistentDataType.BYTE)) {
            Location loc = lecternLocations.get(p.getUniqueId());
            BlockState bs = loc.getBlock().getState();
            if (bs instanceof TileState ts) {
                e.getInventory().setItem(13, createInfoItem(p, ts));
            }
            return;
        }

        // — Mejorar, con validación asíncrona de XP en banco —
        if (pdc.has(keyUpgrade, PersistentDataType.BYTE)) {
            Location loc = lecternLocations.get(p.getUniqueId());
            BlockState bs = loc.getBlock().getState();
            if (!(bs instanceof TileState ts)) return;

            int level    = ts.getPersistentDataContainer().getOrDefault(keyLevel, PersistentDataType.INTEGER, 1);
            int maxLevel = plugin.getConfig().getInt("lectern.maxLevel", 10);
            if (level >= maxLevel) return;

            int cost     = plugin.getConfig().getInt("lectern.baseUpgradeCost", 100) * level;
            Inventory inv = e.getInventory();

            // Ejecutamos la validación y retirada en hilo async
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean ok = plugin.getDatabaseManager()
                        .withdrawBankedXp(p.getUniqueId().toString(), cost);  // :contentReference[oaicite:2]{index=2}:contentReference[oaicite:3]{index=3}
                // Volver al hilo principal para actualizar GUI / TileState
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) {
                        ts.getPersistentDataContainer()
                                .set(keyLevel, PersistentDataType.INTEGER, level + 1);
                        ts.update(true);
                        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
                        p.spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0,1,0),
                                30, 0.5,0.5,0.5,0.1);
                        inv.setItem(15, createUpgradeButton(p, ts));
                        inv.setItem(13, createInfoItem(p, ts));
                        p.sendMessage(ChatColor.GREEN + "Atril mejorado al nivel " + (level+1) + "!");
                    } else {
                        p.sendMessage(ChatColor.RED + "No tienes suficiente XP en el banco para mejorar el Atril.");
                    }
                });
            });
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        lecternLocations.remove(e.getPlayer().getUniqueId());
    }
}
