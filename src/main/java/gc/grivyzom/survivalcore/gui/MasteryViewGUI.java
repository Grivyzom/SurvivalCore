package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.masteries.Mastery;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MasteryViewGUI {

    private static final int INVENTORY_SIZE = 54;
    private static final int[] MASTERY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public static String getInventoryTitle(String profession) {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "Maestrías: " + profession;
    }

    public static void open(Player player, Main plugin, String profession) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, getInventoryTitle(getDisplayName(profession)));
        setupInventory(inv, player, plugin, profession);
        player.openInventory(inv);
    }

    private static void setupInventory(Inventory inv, Player player, Main plugin, String profession) {
        fillBackground(inv);
        setupNavigationButtons(inv);
        setupMasteryItems(inv, player, plugin, profession);
    }

    private static void fillBackground(Inventory inv) {
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        Arrays.fill(inv.getContents(), filler);
    }

    private static void setupNavigationButtons(Inventory inv) {
        inv.setItem(48, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Volver a Profesiones"));
        inv.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "Cerrar"));
        inv.setItem(4, createGuiItem(Material.PLAYER_HEAD,
                ChatColor.AQUA + "Tus Maestrías",
                ChatColor.GRAY + "Selecciona una maestría para mejorarla"));
    }

    private static void setupMasteryItems(Inventory inv, Player player, Main plugin, String profession) {
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        if (data == null) return;

        List<Mastery> masteries = plugin.getMasteryManager().getMasteriesForProfession(profession);
        if (masteries == null) return;

        masteries.sort(Comparator.comparingInt(m -> -data.getMasteryLevels().getOrDefault(m.getId(), 0)));

        int slotIndex = 0;
        for (Mastery mastery : masteries) {
            if (slotIndex >= MASTERY_SLOTS.length) break;
            int currentLevel = data.getMasteryLevels().getOrDefault(mastery.getId(), 0);
            ItemStack item;
            // Si es Excavación Rápida y el jugador está sobre la capa 35, se muestra bloqueado.
            if (mastery.getId().equalsIgnoreCase("excavacion_rapida") &&
                    player.getLocation().getBlockY() >= 35) {
                item = createLockedMasteryItem(mastery, currentLevel);
            } else {
                // Caso normal: se muestra el ítem para mejorar la maestría.
                int requiredXP = mastery.getXPRequiredForLevel(currentLevel + 1);
                int playerLevel = player.getLevel();
                double progress = Math.min(1.0, (double) playerLevel / requiredXP);
                item = createMasteryItem(mastery, currentLevel, requiredXP, progress);
            }
            inv.setItem(MASTERY_SLOTS[slotIndex++], item);
        }
    }
    // Método para generar el ítem normal de maestría
    private static ItemStack createMasteryItem(Mastery mastery, int currentLevel, int requiredXP, double progress) {
        Material material = mastery.isMaxLevel(currentLevel) ? Material.NETHER_STAR : Material.ENCHANTED_BOOK;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Requerido para mejorar: " + ChatColor.AQUA + requiredXP + " niveles");
        lore.add(ChatColor.GRAY + "Progreso: " + getProgressBar(progress));

        if (!mastery.isMaxLevel(currentLevel)) {
            lore.add("");
            if (mastery.getId().equalsIgnoreCase("excavacion_rapida")) {
                // Mostrar las dos opciones
                lore.add(ChatColor.YELLOW + "Click izquierdo para mejorar");
                lore.add(ChatColor.YELLOW + "Click derecho para activar");
            } else {
                lore.add(ChatColor.YELLOW + "Click para mejorar");
            }
        } else {
            lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "¡Nivel máximo alcanzado!");
        }

        lore.add("");
        lore.addAll(mastery.getDescription());

        return createGuiItem(material, ChatColor.LIGHT_PURPLE + mastery.getName(), lore.toArray(new String[0]));
    }


    // Método helper para generar el ítem bloqueado (cuando el jugador esté sobre la capa 35)
    private static ItemStack createLockedMasteryItem(Mastery mastery, int currentLevel) {
        Material material = Material.RED_STAINED_GLASS_PANE; // Material que indica bloqueo
        String name = ChatColor.GRAY + mastery.getName() + " (Bloqueado)";
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.RED + "Bloqueado: Disponible solo bajo la capa 35");
        lore.add("");
        lore.addAll(mastery.getDescription());
        return createGuiItem(material, name, lore.toArray(new String[0]));
    }

    private static String getDisplayName(String internalName) {
        switch (internalName.toLowerCase()) {
            case "mining": return "Minería";
            case "farming": return "Granjería";
            case "hunting": return "Caza";
            case "fishing": return "Pesca";
            case "blacksmithing": return "Herrería";
            default: return internalName;
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

    // Método helper para crear la barra de progreso
    private static String getProgressBar(double progress) {
        progress = Math.max(0, Math.min(1, progress));
        int totalBars = 20;
        int filledBars = (int) (totalBars * progress);
        return ChatColor.GREEN + "" + ChatColor.BOLD +
                new String(new char[filledBars]).replace("\0", "|") +
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD +
                new String(new char[totalBars - filledBars]).replace("\0", "|") +
                ChatColor.GRAY + " " + (int)(progress * 100) + "%";
    }

    // Método helper para calcular la experiencia necesaria para el siguiente nivel
    private static int calculateXPToNextLevel(int currentLevel) {
        return (int)(100 * Math.pow(1.2, currentLevel - 1));
    }
}
