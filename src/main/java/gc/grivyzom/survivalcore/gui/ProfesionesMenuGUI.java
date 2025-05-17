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
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfesionesMenuGUI {

    public static final String INVENTORY_TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "    Menú de Profesiones";
    private static final int INVENTORY_SIZE = 54;

    private static final Map<String, ProfessionData> PROFESSIONS = new LinkedHashMap<>() {{
        put("Granjería", new ProfessionData(Material.GOLDEN_HOE, "farming", true));
        put("Minería",   new ProfessionData(Material.DIAMOND_PICKAXE, "mining", true));
        put("Caza",      new ProfessionData(Material.BARRIER, "hunting", false));
        put("Pesca",     new ProfessionData(Material.BARRIER, "fishing", false));
        put("Herrería",  new ProfessionData(Material.BARRIER, "blacksmithing", false));
    }};

    private static final int[] PROFESSION_SLOTS = {19, 20, 21, 22, 23};

    public static void open(Player player, Main plugin) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        fillBackground(inv);
        setupNavigationButtons(inv);
        setupProfessionItems(inv, player, plugin);
        inv.setItem(13, createGuiItem(Material.OAK_SIGN,
                ChatColor.AQUA + "Información",
                ChatColor.WHITE + "Selecciona una profesión para ver sus maestrías.",
                ChatColor.WHITE + "Las profesiones en papel se activarán en futuras actualizaciones."));
        player.openInventory(inv);
        player.sendMessage(ChatColor.YELLOW + "➡ " + ChatColor.WHITE + "Abriendo menú de profesiones." + ChatColor.YELLOW + " ⬅");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.7f, 1f);
    }

    private static void setupProfessionItems(Inventory inv, Player player, Main plugin) {
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        int index = 0;
        for (Map.Entry<String, ProfessionData> entry : PROFESSIONS.entrySet()) {
            if (index >= PROFESSION_SLOTS.length) break;
            String name = entry.getKey();
            ProfessionData pd = entry.getValue();
            int slot = PROFESSION_SLOTS[index++];
            inv.setItem(slot, createProfessionItem(pd, name, data));
        }
    }

    private static ItemStack createProfessionItem(ProfessionData pd, String displayName, UserData data) {
        Material mat = pd.isActive ? pd.material : Material.BARRIER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (pd.isActive) {
            meta.setDisplayName(ChatColor.YELLOW + displayName);
            String profession = pd.id;
            int level = 1;
            long xp = 0;
            if (profession.equals("farming")) {
                level = data.getFarmingLevel();
                xp    = data.getFarmingXP();
            } else if (profession.equals("mining")) {
                level = data.getMiningLevel();
                xp    = data.getMiningXP();
            }
            meta.setLore(Arrays.asList(
                    ChatColor.WHITE + "Nivel: " + ChatColor.AQUA + level,
                    ChatColor.WHITE + "XP: "     + ChatColor.AQUA + xp,
                    "",
                    ChatColor.GRAY + "Haz clic para ver habilidades."
            ));
        } else {
            meta.setDisplayName(ChatColor.GRAY + displayName + " (Próximamente)");
            meta.setLore(Arrays.asList(
                    ChatColor.DARK_GRAY + "Esta profesión estará disponible próximamente.",
                    ChatColor.DARK_GRAY + "¡Mantente atento a futuras actualizaciones!"
            ));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static void fillBackground(Inventory inv) {
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        ItemStack border = createGuiItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(inv.getSize() - 9 + i, border);
        }
    }

    private static void setupNavigationButtons(Inventory inv) {
        inv.setItem(46, createGuiItem(Material.OAK_DOOR,
                ChatColor.YELLOW + "Regresar al Perfil",
                ChatColor.WHITE + "Haz clic para volver a tu perfil."));
        inv.setItem(52, createGuiItem(Material.BARRIER,
                ChatColor.RED + "Cerrar",
                ChatColor.WHITE + "Haz clic para cerrar el menú."));
        inv.setItem(4, createGuiItem(Material.ENCHANTED_BOOK,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Maestrías de Profesiones",
                ChatColor.WHITE + "Selecciona una profesión para ver sus habilidades."));
    }

    private static ItemStack createGuiItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static class ProfessionData {
        final Material material;
        final String id;
        final boolean isActive;
        ProfessionData(Material material, String id, boolean isActive) {
            this.material = material;
            this.id = id;
            this.isActive = isActive;
        }
    }
}
