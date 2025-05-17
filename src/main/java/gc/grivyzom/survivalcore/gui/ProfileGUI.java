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

public class ProfileGUI {

    public static final String INVENTORY_TITLE = ChatColor.GREEN + "Perfil de Jugador";
    private static final int INVENTORY_SIZE = 54;
    private static final int SLOT_HEAD = 22;
    private static final int SLOT_SKILLS = 29;
    private static final int SLOT_MASTERY = 33;
    private static final int SLOT_CLOSE = 52;

    public static void open(Player player, Main plugin) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        fillBorders(inv);

        // Obtener datos del jugador desde la nueva estructura de tablas
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

        // Formatear valores por defecto si están ausentes
        String genero = (data.getGenero() == null || data.getGenero().isEmpty()) ? "N/D" : data.getGenero();
        String cumple = (data.getCumpleaños() == null || data.getCumpleaños().isEmpty()) ? "N/D" : data.getCumpleaños();
        String pais = (data.getPais() == null || data.getPais().isEmpty()) ? "N/D" : data.getPais();

        // Cabeza del jugador con info básica y niveles desde user_stats
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(player);
        headMeta.setDisplayName(ChatColor.YELLOW + player.getName());
        headMeta.setLore(Arrays.asList(
                ChatColor.WHITE + "Género: " + genero,
                ChatColor.WHITE + "Cumpleaños: " + cumple,
                ChatColor.WHITE + "País: " + pais,
                "",
                ChatColor.AQUA + "Granjería: " + ChatColor.WHITE + data.getFarmingLevel() + " (XP: " + data.getFarmingXP() + ")",
                ChatColor.AQUA + "Minería: " + ChatColor.WHITE + data.getMiningLevel() + " (XP: " + data.getMiningXP() + ")"
        ));
        head.setItemMeta(headMeta);
        inv.setItem(SLOT_HEAD, head);

        // Botón HABILIDADES
        ItemStack skillsItem = createGuiItem(Material.ENCHANTED_BOOK,
                ChatColor.AQUA + "HABILIDADES",
                ChatColor.WHITE + "Click para ver tus habilidades.");
        inv.setItem(SLOT_SKILLS, skillsItem);

        // Botón MAESTRÍAS
        ItemStack masteryItem = createGuiItem(Material.BOOK,
                ChatColor.LIGHT_PURPLE + "MAESTRÍAS",
                ChatColor.WHITE + "Click para ver tus maestrías.");
        inv.setItem(SLOT_MASTERY, masteryItem);

        // Botón CERRAR
        ItemStack close = createGuiItem(Material.BARRIER,
                ChatColor.RED + "Cerrar",
                ChatColor.WHITE + "Cierra este menú.");
        inv.setItem(SLOT_CLOSE, close);

        player.openInventory(inv);
    }

    private static void fillBorders(Inventory inv) {
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        int size = inv.getSize();
        int rows = size / 9;
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
            inv.setItem(size - 1 - i, filler);
        }
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9, filler);
            inv.setItem(row * 9 + 8, filler);
        }
    }

    private static ItemStack createGuiItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
