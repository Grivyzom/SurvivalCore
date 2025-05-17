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

public class MasteryProfessionsMenuListener implements Listener {

    private final Main plugin;

    public MasteryProfessionsMenuListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Verificar que el clic fue en el inventario superior (el menú)
        if (event.getClickedInventory() == null ||
                !event.getView().getTitle().equals(MasteryProfessionsMenuGUI.INVENTORY_TITLE)) {
            return;
        }

        // Cancelar el evento para evitar que se muevan los ítems
        event.setCancelled(true);

        // Verificar que se hizo clic en un ítem válido
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        // Manejo de botones de navegación
        if (displayName.equalsIgnoreCase("Cerrar")) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
            return;
        }
        if (displayName.equalsIgnoreCase("Volver al Perfil") || displayName.equalsIgnoreCase("Regresar al Perfil")) {
            ProfileGUI.open(player, plugin);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
            return;
        }

        // Al hacer clic en una profesión
        String internalName = getInternalProfessionName(displayName);
        if (internalName != null) {
            MasteryViewGUI.open(player, plugin, internalName);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 1f);
        }
    }

    private String getInternalProfessionName(String displayName) {
        String lower = displayName.toLowerCase();
        if (lower.contains("minería") || lower.contains("mineria")) {
            return "mining";
        } else if (lower.contains("granjería") || lower.contains("granjeria")) {
            return "farming";
        } else if (lower.contains("caza")) {
            return "hunting";
        } else if (lower.contains("pesca")) {
            return "fishing";
        } else if (lower.contains("herrería") || lower.contains("herreria")) {
            return "blacksmithing";
        } else {
            return null;
        }
    }
}