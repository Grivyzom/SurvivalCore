package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.RankupGUI;
import gc.grivyzom.survivalcore.rankup.RankupManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener para el menú de rankup.
 * Maneja los clics en los menús GUI del sistema de rankup.
 *
 * @author Brocolitx
 * @version 1.0
 */
public class RankupMenuListener implements Listener {

    private final Main plugin;

    public RankupMenuListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();

        // Verificar si es uno de nuestros menús
        if (!isRankupMenu(title)) return;

        event.setCancelled(true);

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // Manejar clics según el menú
        switch (title) {
            case "Sistema de Rangos" -> handleMainMenuClick(player, itemName, clickedItem);
            case "Mi Progreso" -> handleProgressMenuClick(player, itemName, clickedItem);
            case "Lista de Rangos" -> handleRanksListClick(player, itemName, clickedItem);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (isRankupMenu(title)) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
        }
    }

    /**
     * Verifica si el título corresponde a un menú de rankup
     */
    private boolean isRankupMenu(String title) {
        return title.equals(ChatColor.DARK_PURPLE + "Sistema de Rangos") ||
                title.equals(ChatColor.BLUE + "Mi Progreso") ||
                title.equals(ChatColor.GREEN + "Lista de Rangos");
    }

    /**
     * Maneja clics en el menú principal
     */
    private void handleMainMenuClick(Player player, String itemName, ItemStack item) {
        RankupManager rankupManager = plugin.getRankupManager();

        switch (itemName.toLowerCase()) {
            case "hacer rankup", "⬆ hacer rankup" -> {
                if (rankupManager.isOnCooldown(player.getUniqueId())) {
                    long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
                    player.sendMessage(ChatColor.RED + "Debes esperar " + (remaining / 1000) + " segundos antes de intentar rankup.");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    return;
                }

                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Procesando rankup...");

                rankupManager.attemptRankup(player).thenAccept(result -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (result.isSuccess()) {
                            player.sendMessage(result.getMessage());
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        } else {
                            player.sendMessage(result.getMessage());
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                        }
                    });
                });
            }
            case "ver mi progreso", "📊 ver mi progreso" -> {
                RankupGUI.openProgressMenu(player, plugin);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            }
            case "lista de rangos", "📋 lista de rangos" -> {
                RankupGUI.openRanksList(player, plugin);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.0f);
            }
            case "sistema prestige", "✨ sistema prestige" -> {
                if (rankupManager.isPrestigeEnabled()) {
                    player.sendMessage(ChatColor.YELLOW + "Sistema de prestige en desarrollo...");
                } else {
                    player.sendMessage(ChatColor.RED + "El sistema de prestige está deshabilitado.");
                }
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            }
            case "mi historial", "📜 mi historial" -> {
                player.sendMessage(ChatColor.YELLOW + "Historial de rankups en desarrollo...");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            }
        }
    }

    /**
     * Maneja clics en el menú de progreso
     */
    private void handleProgressMenuClick(Player player, String itemName, ItemStack item) {
        if (itemName.contains("volver") || itemName.equals("⬅ Volver")) {
            RankupGUI.openMainMenu(player, plugin);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        }
    }

    /**
     * Maneja clics en la lista de rangos
     */
    private void handleRanksListClick(Player player, String itemName, ItemStack item) {
        if (itemName.contains("volver") || itemName.equals("⬅ Volver")) {
            RankupGUI.openMainMenu(player, plugin);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        }
    }
}