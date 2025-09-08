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

        // 🔧 CORREGIDO: Cálculo mejorado del tamaño del inventario
        // Necesitamos espacio para bordes + items + botón de volver
        int minSize = 27; // Tamaño mínimo para tener bordes y botón de volver
        int itemsPerRow = 7; // 7 items por fila (excluyendo bordes laterales)
        int rowsNeeded = (count + itemsPerRow - 1) / itemsPerRow; // Redondear hacia arriba
        int totalRowsNeeded = rowsNeeded + 2; // +2 para fila superior e inferior (bordes)

        int size = Math.max(minSize, totalRowsNeeded * 9);
        if (size > 54) size = 54; // Máximo permitido por Minecraft

        Inventory inv = Bukkit.createInventory(null, size, TITLE);

        // 🔧 CORREGIDO: Crear bordes correctamente
        ItemStack border = MagicLecternMenu.createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");

        // Llenar bordes (primera fila, última fila, y columnas laterales)
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        // 🔧 CORREGIDO: Colocación de items de recetas con verificación de límites
        int slot = 10; // Empezar en la primera posición disponible (fila 2, columna 2)
        int itemsPlaced = 0;

        for (String matName : unlocked) {
            // Verificar que no excedamos el tamaño del inventario
            if (slot >= size - 9) { // Dejar espacio para la fila inferior
                break;
            }

            // Verificar que no estemos en un borde lateral
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot = getNextValidSlot(slot, size);
                if (slot == -1) break; // No hay más slots disponibles
            }

            Material mat = Material.matchMaterial(matName);
            if (mat == null) continue;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            // Búsqueda de la receta
            LecternRecipe r = recipeMgr.findRecipe(
                    10,
                    new ItemStack(mat),
                    new ItemStack(mat)
            );

            String nombre = (r != null && !r.getId().isEmpty())
                    ? r.getId()
                    : mat.name().replace("_", " ").toLowerCase();

            meta.setDisplayName(ChatColor.GOLD + nombre);
            meta.setLore(List.of(
                    ChatColor.GRAY + "Receta desbloqueada",
                    ChatColor.YELLOW + "Material: " + mat.name()
            ));
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            itemsPlaced++;

            // Mover al siguiente slot
            slot++;

            // Si llegamos al final de una fila, saltar a la siguiente
            if (slot % 9 == 8) {
                slot += 2; // Saltar al inicio de la siguiente fila disponible
            }
        }

        // 🔧 CORREGIDO: Botón "Volver" en posición segura
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.RED + "⬅ Volver");
        bm.setLore(List.of(
                ChatColor.GRAY + "Volver al menú principal del atril",
                ChatColor.YELLOW + "Recetas mostradas: " + itemsPlaced + "/" + count
        ));
        bm.getPersistentDataContainer().set(keyBack, PersistentDataType.BYTE, (byte)1);
        back.setItemMeta(bm);

        // Colocar el botón "Volver" en el centro de la última fila
        int backButtonSlot = size - 5;
        inv.setItem(backButtonSlot, back);

        // 🆕 NUEVO: Mensaje informativo si hay muchas recetas
        if (count > itemsPlaced) {
            p.sendMessage(ChatColor.YELLOW + "📝 Mostrando " + itemsPlaced + " de " + count + " recetas desbloqueadas");
            p.sendMessage(ChatColor.GRAY + "Algunas recetas no se muestran por limitaciones de espacio");
        }

        p.openInventory(inv);
    }

    /**
     * 🆕 NUEVO: Encuentra el siguiente slot válido para colocar un item
     */
    private static int getNextValidSlot(int currentSlot, int inventorySize) {
        int slot = currentSlot;

        while (slot < inventorySize - 9) { // No en la última fila
            if (slot % 9 != 0 && slot % 9 != 8) { // No en bordes laterales
                return slot;
            }
            slot++;
        }

        return -1; // No hay slots disponibles
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        // Manejar clic en el botón "Volver"
        if (it.getItemMeta().getPersistentDataContainer()
                .has(keyBack, PersistentDataType.BYTE)) {
            // Volver al menú principal
            var loc = MagicLecternMenu.getLecternLocation(p.getUniqueId());
            if (loc != null) {
                MagicLecternMenu.open(p, loc.getBlock());
            } else {
                p.closeInventory();
                p.sendMessage(ChatColor.RED + "No se pudo encontrar el atril mágico");
            }
        }

        // 🆕 NUEVO: Información adicional al hacer clic en una receta
        else if (it.getType() != Material.BLACK_STAINED_GLASS_PANE &&
                it.getType() != Material.BARRIER) {
            p.sendMessage(ChatColor.AQUA + "📖 Receta: " + ChatColor.YELLOW +
                    (it.getItemMeta().hasDisplayName() ?
                            it.getItemMeta().getDisplayName() :
                            it.getType().name()));
            p.sendMessage(ChatColor.GRAY + "Esta receta está desbloqueada y lista para usar");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        // Limpieza si es necesaria
    }
}