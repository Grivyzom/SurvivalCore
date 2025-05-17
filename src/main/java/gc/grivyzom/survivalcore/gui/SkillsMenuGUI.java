package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.skills.SkillFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SkillsMenuGUI {

    public static final String INVENTORY_TITLE_PREFIX = ChatColor.DARK_PURPLE + "Habilidades - ";
    private static final ItemStack BACK_BUTTON = createGuiItem(Material.OAK_DOOR, ChatColor.YELLOW + "Regresar a Profesiones", ChatColor.GRAY + "Haz clic para volver.");

    private static ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    public static void open(Player player, Main plugin, int page, String profession) {
        String internalProfession = convertToInternalProfession(profession);
        List<SkillFactory> filteredSkills = new ArrayList<>();
        for (SkillFactory factory : plugin.getSkillManager().getRegisteredSkills()) {
            if (factory.getProfession().equalsIgnoreCase(internalProfession)) {
                filteredSkills.add(factory);
            }
        }
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        int playerLevel = 1;
        if (internalProfession.equalsIgnoreCase("farming")) {
            playerLevel = data.getFarmingLevel();
        } else if (internalProfession.equalsIgnoreCase("mining")) {
            playerLevel = data.getMiningLevel();
        }
        final int finalPlayerLevel = playerLevel;
        filteredSkills.sort((f1, f2) -> {
            int status1 = (finalPlayerLevel >= f1.getRequiredProfessionLevel()) ? 0 : 1;
            int status2 = (finalPlayerLevel >= f2.getRequiredProfessionLevel()) ? 0 : 1;
            if (status1 != status2) return status1 - status2;
            return f1.getName().compareTo(f2.getName());
        });
        int itemsPerPage = 9;
        int totalSkills = filteredSkills.size();
        int totalPages = Math.max(1, (int)Math.ceil(totalSkills / (double) itemsPerPage));
        page = Math.max(1, Math.min(page, totalPages));
        int size = 27;
        String invTitle = INVENTORY_TITLE_PREFIX + profession + " - Página " + page;
        Inventory inv = Bukkit.createInventory(null, size, invTitle);
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalSkills);
        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            SkillFactory factory = filteredSkills.get(i);
            ItemStack item = createSkillItem(factory, playerLevel, plugin, player);
            inv.setItem(slot, item);
            slot++;
        }
        if (page > 1) {
            inv.setItem(18, createGuiItem(Material.ARROW, ChatColor.GOLD + "Página Anterior"));
        }
        if (page < totalPages) {
            inv.setItem(26, createGuiItem(Material.ARROW, ChatColor.GOLD + "Página Siguiente"));
        }
        inv.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "Cerrar Menú"));
        inv.setItem(0, BACK_BUTTON);
        player.openInventory(inv);
    }

    private static String convertToInternalProfession(String displayName) {
        if (displayName.equalsIgnoreCase("Granjería")) return "farming";
        if (displayName.equalsIgnoreCase("Minería")) return "mining";
        return displayName.toLowerCase();
    }

    private static ItemStack createSkillItem(SkillFactory factory, int playerProfessionLevel, Main plugin, Player player) {
        ItemStack item;
        List<String> lore = new ArrayList<>();

        // Si el jugador aún no cumple el nivel mínimo para desbloquear la habilidad, se muestra un ítem bloqueado.
        if (playerProfessionLevel < factory.getRequiredProfessionLevel()) {
            item = new ItemStack(Material.REDSTONE_BLOCK);
            lore.add(ChatColor.RED + "Desbloquea al nivel " + factory.getRequiredProfessionLevel());
        } else {
            item = new ItemStack(factory.getIcon());
            // Instrucciones básicas para interactuar con la habilidad
            lore.add(ChatColor.GRAY + "Click Izquierdo: Mejorar" + ChatColor.YELLOW + "|" + ChatColor.GRAY + " Click Derecho: Activar");
            // Consultamos el nivel actual de la habilidad y el costo para mejorarla.
            int skillLevel = plugin.getSkillManager().getSkillLevel(player, factory.getName());
            int costoMejora = plugin.getSkillManager().getRequiredXPForLevel(skillLevel);
            // Se agrega al lore únicamente el nivel y el costo de mejora.
            lore.add(ChatColor.AQUA + "Nivel: " + ChatColor.WHITE + skillLevel);
            lore.add(ChatColor.AQUA + "Costo de Mejora: " + ChatColor.WHITE + costoMejora + ChatColor.GREEN + " XP");
        }

        // Establece el nombre del ítem y agrega lore adicional si lo hay.
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + factory.getName());
        if (factory.getLore() != null && !factory.getLore().isEmpty()) {
            lore.addAll(factory.getLore());
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static void open(Player player, Main plugin, String profession) {
        open(player, plugin, 1, profession);
    }
}
