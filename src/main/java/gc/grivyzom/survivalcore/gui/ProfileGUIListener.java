package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Listener para el GUI de perfil configurable
 * Actualizado para trabajar con el sistema de páginas
 *
 * @author Brocolitx
 * @version 3.0
 */
public class ProfileGUIListener implements Listener {

    private final Main plugin;

    public ProfileGUIListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String inventoryTitle = event.getView().getTitle();

        // Verificar si es un inventario del sistema de perfil
        if (!ProfileGUI.isProfileInventory(inventoryTitle)) return;

        // Cancelar el evento para evitar manipulación de items
        event.setCancelled(true);

        // Ignorar clicks fuera del inventario superior
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        // Manejar el click
        ProfileGUI.handleClick(player, event.getCurrentItem(), event.getRawSlot(), plugin);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String inventoryTitle = event.getView().getTitle();

        // Si es un inventario de perfil, limpiar cache del jugador
        if (ProfileGUI.isProfileInventory(inventoryTitle)) {
            ProfileGUI.cleanupPlayer(player);
        }
    }
}