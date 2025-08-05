package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.RankupGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener para manejar clics en los GUIs de rankup
 * Versión simplificada pero funcional
 *
 * @author Brocolitx
 * @version 2.1 - Con GUIs básicos funcionales
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
        int slot = event.getSlot();

        // Verificar si es un menú de rankup
        if (!isRankupMenu(title)) return;

        // Cancelar el evento para evitar que se muevan ítems
        event.setCancelled(true);

        // Si no hay ítem clickeado, ignorar
        if (clickedItem == null || !clickedItem.hasItemMeta() || clickedItem.getType() == Material.AIR) return;

        // Verificar si el sistema de rankup está disponible
        if (!plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            player.closeInventory();
            return;
        }

        try {
            // Manejar clic según el menú
            String cleanTitle = ChatColor.stripColor(title).toLowerCase();

            if (cleanTitle.contains("sistema de rangos")) {
                handleMainMenuClick(player, slot, clickedItem);
            } else if (cleanTitle.contains("progreso")) {
                handleProgressMenuClick(player, slot, clickedItem);
            } else if (cleanTitle.contains("lista de rangos")) {
                handleRanksListClick(player, slot, clickedItem);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error manejando clic en GUI de rankup: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error procesando acción. Intenta de nuevo.");
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // Solo procesar si es un menú de rankup
        if (!isRankupMenu(title)) return;

        try {
            // Sonido de cierre
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 1.0f);
        } catch (Exception e) {
            // Ignorar errores de sonido
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Limpieza básica - no necesaria para GUIs simples
        // Los inventarios se cierran automáticamente
    }

    /**
     * Verifica si el título corresponde a un menú de rankup
     */
    private boolean isRankupMenu(String title) {
        if (title == null) return false;

        String cleanTitle = ChatColor.stripColor(title).toLowerCase();

        return cleanTitle.contains("sistema de rangos") ||
                cleanTitle.contains("mi progreso") ||
                cleanTitle.contains("progreso detallado") ||
                cleanTitle.contains("lista de rangos") ||
                cleanTitle.contains("configuración");
    }

    /**
     * Maneja clics en el menú principal
     */
    private void handleMainMenuClick(Player player, int slot, ItemStack clickedItem) {
        Material material = clickedItem.getType();
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).toLowerCase();

        // Sonido de clic
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);

        switch (slot) {
            case 20 -> { // Botón de Rankup
                if (material == Material.CLOCK) {
                    // En cooldown
                    player.sendMessage(ChatColor.RED + "⏰ Rankup en cooldown. Espera un momento.");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                } else if (material == Material.DIAMOND) {
                    // Hacer rankup
                    handleRankupAttempt(player);
                }
            }
            case 22 -> { // Ver Progreso
                player.closeInventory();
                RankupGUI.openProgressMenu(player, plugin);
            }
            case 24 -> { // Lista de Rangos
                player.closeInventory();
                RankupGUI.openRanksList(player, plugin);
            }
            case 40 -> { // Información del Sistema
                player.closeInventory();
                RankupGUI.showClientInfo(player, plugin);
            }
            case 13 -> { // Info del jugador
                player.sendMessage(ChatColor.YELLOW + "📋 Esta es tu información actual de rangos");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
            }
            default -> {
                // Slot de borde o sin acción
                if (isBorderSlot(slot, 45)) {
                    // No hacer nada en bordes
                    return;
                }
                player.sendMessage(ChatColor.GRAY + "Esta opción no tiene función asignada");
            }
        }
    }

    /**
     * Maneja clics en el menú de progreso
     */
    private void handleProgressMenuClick(Player player, int slot, ItemStack clickedItem) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);

        if (slot == 49) { // Botón volver
            player.closeInventory();
            RankupGUI.openMainMenu(player, plugin);
        } else if (slot == 13) { // Info de progreso
            player.sendMessage(ChatColor.BLUE + "📊 Esta es tu información de progreso general");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else if (clickedItem.getType() == Material.LIME_STAINED_GLASS_PANE) {
            player.sendMessage(ChatColor.GREEN + "✅ Este requisito ya está completado");
        } else if (clickedItem.getType() == Material.RED_STAINED_GLASS_PANE) {
            player.sendMessage(ChatColor.RED + "❌ Este requisito aún no está completado");
            player.sendMessage(ChatColor.YELLOW + "💡 Usa /rankup progress para ver detalles específicos");
        }
    }

    /**
     * Maneja clics en la lista de rangos
     */
    private void handleRanksListClick(Player player, int slot, ItemStack clickedItem) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);

        if (slot == 49) { // Botón volver
            player.closeInventory();
            RankupGUI.openMainMenu(player, plugin);
        } else if (clickedItem.getType() == Material.EMERALD) {
            player.sendMessage(ChatColor.GREEN + "⭐ Este es tu rango actual");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
        } else if (clickedItem.getType() == Material.IRON_INGOT) {
            player.sendMessage(ChatColor.GRAY + "ℹ️ Este es un rango del servidor");
            player.sendMessage(ChatColor.YELLOW + "💡 Usa /rankup progress para ver cómo llegar aquí");
        }
    }

    /**
     * Maneja intentos de rankup desde el GUI
     */
    private void handleRankupAttempt(Player player) {
        if (!plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            return;
        }

        try {
            var rankupManager = plugin.getRankupManager();

            // Verificar cooldown nuevamente
            if (rankupManager.isOnCooldown(player.getUniqueId())) {
                long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "⏰ Debes esperar " + (remaining / 1000) + " segundos");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }

            // Cerrar inventario y procesar
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "🔄 Verificando requisitos...");

            // Realizar rankup de forma asíncrona
            rankupManager.attemptRankup(player).thenAccept(result -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (result.isSuccess()) {
                        // Éxito
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GREEN + "🎉 " + ChatColor.BOLD + "¡RANKUP EXITOSO!");
                        player.sendMessage(ChatColor.WHITE + result.getMessage());
                        player.sendMessage("");

                        // Efectos de éxito
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

                        // Reabrir menú actualizado después de un momento
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            player.sendMessage(ChatColor.GRAY + "💡 Tu menú de rangos se ha actualizado automáticamente");
                            player.sendMessage(ChatColor.GRAY + "Usa /ranks para ver tu nuevo progreso");
                        }, 60L); // 3 segundos

                    } else {
                        // Fallo
                        player.sendMessage("");
                        player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "RANKUP NO DISPONIBLE");
                        player.sendMessage("");
                        player.sendMessage(result.getMessage());
                        player.sendMessage("");
                        player.sendMessage(ChatColor.YELLOW + "💡 Comandos útiles:");
                        player.sendMessage(ChatColor.GRAY + "  • /rankup progress - Ver progreso detallado");
                        player.sendMessage(ChatColor.GRAY + "  • /ranks - Abrir menú de rangos");
                        player.sendMessage("");

                        // Efectos de fallo
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                    }
                });
            }).exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "❌ Error procesando rankup. Intenta de nuevo.");
                    plugin.getLogger().severe("Error en rankup desde GUI: " + throwable.getMessage());
                });
                return null;
            });

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error iniciando rankup");
            plugin.getLogger().severe("Error manejando rankup desde GUI: " + e.getMessage());
        }
    }

    /**
     * Verifica si un slot es un borde del inventario
     */
    private boolean isBorderSlot(int slot, int inventorySize) {
        int rows = inventorySize / 9;

        // Primera fila
        if (slot < 9) return true;

        // Última fila
        if (slot >= inventorySize - 9) return true;

        // Columnas laterales
        for (int row = 1; row < rows - 1; row++) {
            if (slot == row * 9 || slot == row * 9 + 8) {
                return true;
            }
        }

        return false;
    }
}