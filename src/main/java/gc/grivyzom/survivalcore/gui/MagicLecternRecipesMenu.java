package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.recipes.LecternRecipeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import gc.grivyzom.survivalcore.recipes.LecternRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MagicLecternRecipesMenu implements Listener {
    private static final Main plugin = Main.getPlugin(Main.class);
    private static final String TITLE = ChatColor.DARK_PURPLE + "📜 Recetas Desbloqueadas 📜";
    private static final NamespacedKey keyBack = new NamespacedKey(plugin, "lectern_menu_back");
    private static final LecternRecipeManager recipeMgr = plugin.getLecternRecipeManager();
    /** Abre el menú de recetas para el jugador. */
    public static void open(Player p) {
        Set<String> unlocked = RecipeUnlockManager.getUnlocked(p.getUniqueId());
        int count = unlocked.size();
        int size = Math.max(9, ((count + 8) / 9) * 9);
        if (size > 54) size = 54;

        Inventory inv = Bukkit.createInventory(null, size, TITLE);
        // bordes
        ItemStack border = MagicLecternMenu.createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        // ítems de receta
// ítems de receta
        int slot = 10;
        for (String matName : unlocked) {
            Material mat = Material.matchMaterial(matName);
            if (mat == null) continue;
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            // ——— Aquí va la búsqueda de la receta ———
            LecternRecipe r = recipeMgr.findRecipe(
                    10,
                    new ItemStack(mat),
                    new ItemStack(mat)
            );
            String nombre = (r != null && !r.getId().isEmpty())
                    ? r.getId()
                    : mat.name().replace("_"," ").toLowerCase();
            meta.setDisplayName(ChatColor.GOLD + nombre);
            // ————————————————————————————————

            meta.setLore(List.of(ChatColor.GRAY + "Receta desbloqueada"));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= size - 9) break;
        }


        // botón “Volver”
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.RED + "⬅ Volver");
        bm.getPersistentDataContainer().set(keyBack, PersistentDataType.BYTE, (byte)1);
        back.setItemMeta(bm);
        inv.setItem(size - 5, back);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        if (it.getItemMeta().getPersistentDataContainer()
                .has(keyBack, PersistentDataType.BYTE)) {
            // volver al menú principal
            var loc = MagicLecternMenu.getLecternLocation(p.getUniqueId());
            if (loc != null) MagicLecternMenu.open(p, loc.getBlock());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        // nada más que limpiar si hiciera falta
    }
}
