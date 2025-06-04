// FarmingGUIListener.java
package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class FarmingGUIListener implements Listener {
    private final Main plugin;

    public FarmingGUIListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(FarmingGUI.INVENTORY_TITLE)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        String name = event.getCurrentItem().getItemMeta().getDisplayName();

        if (name.contains("Cerrar")) {
            player.closeInventory();
            return;
        }
        if (name.contains("Regresar")) {
            ProfesionesMenuGUI.open(player, plugin);
            return;
        }
        if (event.getCurrentItem().getType() == Material.WHEAT) {
            UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            int lvl = data.getFarmingLevel();
            long xp = data.getFarmingXP();  // Cambiado a long
            long cost = (long) lvl * 100;   // Cambiado a long y casting explícito

            if (xp >= cost) {
                data.setFarmingXP(xp - cost);
                data.setFarmingLevel(lvl + 1);
                // Guardar datos asíncrono
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getDatabaseManager().saveUserData(data);
                    }
                }.runTaskAsynchronously(plugin);
                player.sendMessage(ChatColor.GREEN + "¡Granjería nivel " + data.getFarmingLevel() + "! XP restante: " + data.getFarmingXP());
            } else {
                player.sendMessage(ChatColor.RED + "No tienes suficiente XP de granjería.");
            }
            FarmingGUI.open(player, plugin);
        }
    }
}