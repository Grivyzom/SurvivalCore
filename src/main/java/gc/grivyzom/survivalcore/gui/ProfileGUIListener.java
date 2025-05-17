package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ProfileGUIListener implements Listener {

    private final Main plugin;

    public ProfileGUIListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ProfileGUI.INVENTORY_TITLE)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)
            return;
        Player player = (Player) event.getWhoClicked();
        Material type = event.getCurrentItem().getType();
        String displayName = event.getCurrentItem().getItemMeta().getDisplayName();

        if (type == Material.ENCHANTED_BOOK) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            ProfesionesMenuGUI.open(player, plugin);
            return;
        }
        if (type == Material.BOOK && ChatColor.stripColor(displayName).equalsIgnoreCase("MAESTRÍAS")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            MasteryProfessionsMenuGUI.open(player, plugin);
            return;
        }
        if (type == Material.BARRIER) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
            return;
        }
    }
}
