package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GeneroGUIListener implements Listener {

    private final Main plugin;
    public GeneroGUIListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GeneroGUI.getInventoryTitle())) return;
        e.setCancelled(true);
        if (e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        GeneroGUI.handleClick((org.bukkit.entity.Player) e.getWhoClicked(), e.getCurrentItem(), e.getRawSlot(), plugin);

    }
}
