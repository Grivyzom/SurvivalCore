// FarmingGUI.java
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

public class FarmingGUI {
    public static final String INVENTORY_TITLE = ChatColor.GREEN + "Perfil de Granjería";
    private static final int SIZE = 54;
    private static final int SLOT_HEAD = 22;
    private static final int SLOT_UPGRADE = 49;
    private static final int SLOT_BACK = 0;
    private static final int SLOT_CLOSE = 53;

    public static void open(Player player, Main plugin) {
        Inventory inv = Bukkit.createInventory(null, SIZE, INVENTORY_TITLE);

        // Rellenar borde
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Cargar o inicializar datos
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        if (data == null) {
            data = new UserData(player.getUniqueId().toString(), player.getName(), "N/D", "N/D", "N/D");
            UserData finalData = data;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getDatabaseManager().saveUserData(finalData)
            );
        }

        int level = data.getFarmingLevel();
        long xp = data.getFarmingXP();  // Cambiado a long
        long cost = (long) level * 100;  // Cambiado a long y casting explícito

        // Cabeza del jugador
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.YELLOW + player.getName());
        meta.setLore(Arrays.asList(
                ChatColor.WHITE + "Nivel de Granjería: " + level,
                ChatColor.WHITE + "XP Acumulada: " + xp
        ));
        head.setItemMeta(meta);
        inv.setItem(SLOT_HEAD, head);

        // Botón regresar
        inv.setItem(SLOT_BACK, createGuiItem(Material.OAK_DOOR,
                ChatColor.YELLOW + "Regresar"));

        // Botón subir nivel
        inv.setItem(SLOT_UPGRADE, createGuiItem(Material.WHEAT,
                ChatColor.GREEN + "Subir Nivel",
                ChatColor.WHITE + "Costo: " + cost + " XP",
                ChatColor.WHITE + "Click para mejorar tu Granjería."));

        // Botón cerrar
        inv.setItem(SLOT_CLOSE, createGuiItem(Material.BARRIER,
                ChatColor.RED + "Cerrar"));

        player.openInventory(inv);
    }

    private static ItemStack createGuiItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}