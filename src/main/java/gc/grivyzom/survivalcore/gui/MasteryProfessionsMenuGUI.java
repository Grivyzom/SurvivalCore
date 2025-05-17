package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MasteryProfessionsMenuGUI {

    public static final String INVENTORY_TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Maestrías de Profesiones";
    private static final int INVENTORY_SIZE = 54;

    // Mapeamos 5 profesiones: Minería y Granjería activas; Caza, Pesca y Herrería como placeholders.
    private static final Map<String, ProfessionData> PROFESSIONS = new LinkedHashMap<String, ProfessionData>() {{
        put("Minería", new ProfessionData(Material.DIAMOND_PICKAXE, "mining", true));
        put("Granjería", new ProfessionData(Material.GOLDEN_HOE, "farming", true));
        put("Caza", new ProfessionData(Material.BARRIER, "hunting", false));
        put("Pesca", new ProfessionData(Material.BARRIER, "fishing", false));
        put("Herrería", new ProfessionData(Material.BARRIER, "blacksmithing", false));
    }};

    // Se usará un arreglo de slots para ubicar los ítems de profesión
    private static final int[] PROFESSION_SLOTS = {20, 21, 22, 23, 24};

    public static void open(Player player, Main plugin) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        setupInventory(inv, player, plugin);
        player.openInventory(inv);
        // Mensaje informativo al abrir el menú:
        player.sendMessage(ChatColor.YELLOW + "➡ " + ChatColor.WHITE + "Abriendo menú de maestrias." + ChatColor.YELLOW + " ⬅") ;
        player.playSound(player.getLocation(), Material.BELL == null ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_HARP : org.bukkit.Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 1f);
    }

    private static void setupInventory(Inventory inv, Player player, Main plugin) {
        fillBackground(inv);
        setupNavigationButtons(inv);
        setupProfessionItems(inv, player, plugin);
        // Botón de información (opcional): en el centro, por ejemplo en el slot 13, para mostrar instrucciones.
        inv.setItem(13, createGuiItem(Material.OAK_SIGN, ChatColor.AQUA + "Información",
                ChatColor.WHITE + "Selecciona una profesión para ver sus maestrías.",
                ChatColor.WHITE + "Las profesiones en papel están en desarrollo."));
    }

    private static void fillBackground(Inventory inv) {
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        // Crear borde con GOLD_BLOCK en la primera y última fila
        ItemStack border = createGuiItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(inv.getSize() - 9 + i, border);
        }
    }

    private static void setupNavigationButtons(Inventory inv) {
        inv.setItem(46, createGuiItem(Material.OAK_DOOR, ChatColor.YELLOW + "Regresar al Perfil",
                ChatColor.GRAY + "Haz clic para volver a tu perfil."));
        inv.setItem(52, createGuiItem(Material.BARRIER, ChatColor.RED + "Cerrar",
                ChatColor.GRAY + "Haz clic para cerrar el menú."));
        inv.setItem(4, createGuiItem(Material.ENCHANTED_BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "Maestrías de Profesiones",
                ChatColor.GRAY + "Selecciona una profesión para ver sus maestrías."));
    }

    private static void setupProfessionItems(Inventory inv, Player player, Main plugin) {
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        if (data == null) return;

        int index = 0;
        for (Map.Entry<String, ProfessionData> entry : PROFESSIONS.entrySet()) {
            if (index >= PROFESSION_SLOTS.length) break;

            String professionName = entry.getKey();
            ProfessionData profession = entry.getValue();
            int slot = PROFESSION_SLOTS[index++];

            // Si la profesión es activa, se muestra normalmente; de lo contrario, se muestra como "Próximamente".
            ItemStack item;
            if (profession.isActive) {
                item = createGuiItem(profession.material, ChatColor.YELLOW + professionName,
                        ChatColor.GRAY + "Haz clic para ver las maestrías.");
            } else {
                item = createGuiItem(Material.PAPER, ChatColor.GRAY + professionName + " (Próximamente)",
                        ChatColor.DARK_GRAY + "Esta profesión aún no ha sido implementada.",
                        ChatColor.DARK_GRAY + "¡Mantente atento para futuras actualizaciones!");
            }
            inv.setItem(slot, item);
        }
    }

    private static ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static class ProfessionData {
        final Material material;
        final String id;
        final boolean isActive; // Si la profesión está creada
        ProfessionData(Material material, String id, boolean isActive) {
            this.material = material;
            this.id = id;
            this.isActive = isActive;
        }
    }
}
