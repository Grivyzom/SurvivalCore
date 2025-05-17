package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class ProfesionesMenuListener implements Listener {

    private final Main plugin;

    public ProfesionesMenuListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Verificar que el clic se realice en el menú de Profesiones
        if (!event.getView().getTitle().equals(ProfesionesMenuGUI.INVENTORY_TITLE)) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        Player player = (Player) event.getWhoClicked();

        // Si se hace clic en "Cerrar"
        if (displayName.equalsIgnoreCase("Cerrar")) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            return;
        }

        // Si se hace clic en "Regresar al Perfil"
        if (displayName.equalsIgnoreCase("Regresar al Perfil")) {
            ProfileGUI.open(player, plugin);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            return;
        }

        // Verificar si el ítem clickeado es una profesión activa
        boolean isActiveProfession = false;
        String internalProfession = null;

        // Verificar si es una profesión activa
        if (displayName.equalsIgnoreCase("Granjería")) {
            isActiveProfession = true;
            internalProfession = "farming";
        } else if (displayName.equalsIgnoreCase("Minería")) {
            isActiveProfession = true;
            internalProfession = "mining";
        } else if (displayName.toLowerCase().contains("próximamente")) {
            // Es una profesión no implementada
            player.sendMessage(ChatColor.RED + "¡Esta profesión no está disponible aún!");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 1f);
            return;
        }

        // Solo abrir el menú de habilidades si es una profesión activa
        if (isActiveProfession && internalProfession != null) {
            player.sendMessage(ChatColor.AQUA + "Abriendo habilidades de " + displayName + "...");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.7f, 1f);
            SkillsMenuGUI.open(player, plugin, 1, internalProfession);
        }
    }
}