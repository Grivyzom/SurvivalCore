package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class GeneroGUI {

    public static final String INVENTORY_TITLE = ChatColor.LIGHT_PURPLE + "Selecciona tu Género";
    private static final int SIZE = 27;

    private static final int SLOT_MASC = 11;
    private static final int SLOT_FEM  = 13;
    private static final int SLOT_OTRO = 15;
    private static final int SLOT_CLOSE = 22;

    public static void open(Player player, Main plugin) {
        Inventory inv = Bukkit.createInventory(null, SIZE, INVENTORY_TITLE);
        fillBorders(inv);

        inv.setItem(SLOT_MASC,  createGuiItem(Material.BLUE_WOOL,   ChatColor.AQUA   + "Masculino"));
        inv.setItem(SLOT_FEM,   createGuiItem(Material.PINK_WOOL,   ChatColor.LIGHT_PURPLE + "Femenino"));
        inv.setItem(SLOT_OTRO,  createGuiItem(Material.LIME_WOOL,   ChatColor.YELLOW + "Otro / Prefiero no decir"));
        inv.setItem(SLOT_CLOSE, createGuiItem(Material.BARRIER,     ChatColor.RED + "Cerrar"));

        player.openInventory(inv);
    }

    /* ========== LISTENER ========== */
    public static void handleClick(Player player, ItemStack item, Main plugin) {
        if (item == null || !item.hasItemMeta()) return;

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        String genero;
        switch (name.toLowerCase()) {
            case "masculino" -> genero = "Masculino";
            case "femenino"  -> genero = "Femenino";
            case "otro / prefiero no decir" -> genero = "Otro";
            case "cerrar"    -> { player.closeInventory(); return; }
            default          -> { return; }
        }

        // Actualizar en memoria y BD
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        data.setGenero(genero);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabaseManager().saveUserData(data));

        player.sendMessage(ChatColor.GREEN + "¡Género actualizado a " + ChatColor.WHITE + genero + ChatColor.GREEN + "!");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1f);
        player.closeInventory();
    }

    /* ========== UTILIDADES ========== */
    private static void fillBorders(Inventory inv) {
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, pane);
        }
    }

    private static ItemStack createGuiItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore.length > 0) im.setLore(Arrays.asList(lore));
        it.setItemMeta(im);
        return it;
    }
}
