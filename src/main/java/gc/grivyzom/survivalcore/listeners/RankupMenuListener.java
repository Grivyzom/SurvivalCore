package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.menu.MenuManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener actualizado para el sistema de menús de rankup
 * Ahora utiliza MenuManager configurable
 *
 * @author Brocolitx
 * @version 2.0 - Integrado con MenuManager
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
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        // Verificar si el sistema de rankup está disponible
        if (!plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            player.closeInventory();
            return;
        }

        try {
            // Intentar usar MenuManager
            MenuManager menuManager = plugin.getRankupManager().getMenuManager();
            if (menuManager != null) {
                // Usar el nuevo sistema de menús
                boolean handled = menuManager.handleMenuClick(player, title, slot, clickedItem);

                if (!handled) {
                    // Si MenuManager no pudo manejar el clic, usar método legacy
                    handleClickLegacy(player, title, clickedItem);
                }
            } else {
                // Fallback al sistema legacy
                handleClickLegacy(player, title, clickedItem);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error manejando clic en menú de rankup: " + e.getMessage());
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
            // Reproducir sonido de cierre si está habilitado
            MenuManager menuManager = plugin.getRankupManager() != null ?
                    plugin.getRankupManager().getMenuManager() : null;

            if (menuManager != null) {
                // El MenuManager maneja sus propios sonidos
                return;
            }

            // Sonido legacy
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);

        } catch (Exception e) {
            // Ignorar errores de sonido
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Limpiar datos del jugador en MenuManager
        try {
            if (plugin.isRankupSystemEnabled() && plugin.getRankupManager() != null) {
                plugin.getRankupManager().cleanupPlayerMenuData(player);
            }
        } catch (Exception e) {
            // Ignorar errores durante la desconexión
        }
    }

    /**
     * Verifica si el título corresponde a un menú de rankup
     */
    private boolean isRankupMenu(String title) {
        if (title == null) return false;

        String cleanTitle = ChatColor.stripColor(title).toLowerCase();

        return cleanTitle.contains("sistema de rangos") ||
                cleanTitle.contains("rankup") ||
                cleanTitle.contains("mi progreso") ||
                cleanTitle.contains("progreso") ||
                cleanTitle.contains("lista de rangos") ||
                cleanTitle.contains("rangos") ||
                cleanTitle.contains("configuración") ||
                cleanTitle.contains("settings");
    }

    /**
     * Método legacy para manejar clics (compatibilidad hacia atrás)
     */
    @Deprecated
    private void handleClickLegacy(Player player, String title, ItemStack clickedItem) {
        String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        String cleanTitle = ChatColor.stripColor(title).toLowerCase();

        try {
            // Menú principal legacy
            if (cleanTitle.contains("sistema de rangos")) {
                handleMainMenuClickLegacy(player, itemName);
            }
            // Menú de progreso legacy
            else if (cleanTitle.contains("progreso")) {
                handleProgressMenuClickLegacy(player, itemName);
            }
            // Lista de rangos legacy
            else if (cleanTitle.contains("lista") && cleanTitle.contains("rangos")) {
                handleRanksListClickLegacy(player, itemName);
            }
            // Otros menús
            else {
                player.sendMessage(ChatColor.YELLOW + "⚠ Función no disponible en modo básico");
                player.sendMessage(ChatColor.GRAY + "Usa comandos: /rankup, /rankup progress, /rankup list");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error en manejo legacy de menú: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error procesando acción");
        }
    }

    /**
     * Maneja clics en el menú principal (método legacy)
     */
    @Deprecated
    private void handleMainMenuClickLegacy(Player player, String itemName) {
        switch (itemName.toLowerCase()) {
            case "hacer rankup", "⬆ hacer rankup", "rankup" -> {
                handleRankupAttempt(player);
            }
            case "ver mi progreso", "📊 ver mi progreso", "progreso" -> {
                player.closeInventory();
                player.performCommand("rankup progress");
            }
            case "lista de rangos", "📋 lista de rangos", "rangos" -> {
                player.closeInventory();
                player.performCommand("rankup list");
            }
            case "sistema prestige", "✨ sistema prestige", "prestige" -> {
                player.sendMessage(ChatColor.YELLOW + "🚧 Sistema de prestige en desarrollo...");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            }
            case "mi historial", "📜 mi historial", "historial" -> {
                player.sendMessage(ChatColor.YELLOW + "📜 Historial de rankups en desarrollo...");
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            }
            default -> {
                player.sendMessage(ChatColor.GRAY + "Acción no reconocida: " + itemName);
            }
        }
    }

    /**
     * Maneja clics en el menú de progreso (método legacy)
     */
    @Deprecated
    private void handleProgressMenuClickLegacy(Player player, String itemName) {
        if (itemName.toLowerCase().contains("volver") || itemName.equals("⬅ Volver")) {
            // Intentar abrir menú principal
            try {
                if (plugin.isRankupSystemEnabled()) {
                    plugin.getRankupManager().getMenuManager().openMainMenu(player);
                } else {
                    player.closeInventory();
                    player.sendMessage(ChatColor.GRAY + "Menú cerrado");
                }
            } catch (Exception e) {
                player.closeInventory();
                player.performCommand("ranks");
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        }
    }

    /**
     * Maneja clics en la lista de rangos (método legacy)
     */
    @Deprecated
    private void handleRanksListClickLegacy(Player player, String itemName) {
        if (itemName.toLowerCase().contains("volver") || itemName.equals("⬅ Volver")) {
            // Intentar abrir menú principal
            try {
                if (plugin.isRankupSystemEnabled()) {
                    plugin.getRankupManager().getMenuManager().openMainMenu(player);
                } else {
                    player.closeInventory();
                    player.sendMessage(ChatColor.GRAY + "Menú cerrado");
                }
            } catch (Exception e) {
                player.closeInventory();
                player.performCommand("ranks");
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        }
    }

    /**
     * Maneja intentos de rankup
     */
    private void handleRankupAttempt(Player player) {
        if (!plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            return;
        }

        try {
            var rankupManager = plugin.getRankupManager();

            // Verificar cooldown
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
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GREEN + "🎉 " + ChatColor.BOLD + "¡RANKUP EXITOSO!");
                        player.sendMessage(ChatColor.WHITE + result.getMessage());
                        player.sendMessage("");

                        // Efectos de éxito
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                        // Abrir menú actualizado después de un momento
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            try {
                                if (plugin.getRankupManager().getMenuManager() != null) {
                                    plugin.getRankupManager().getMenuManager().openMainMenu(player);
                                }
                            } catch (Exception e) {
                                // Ignorar errores al reabrir menú
                            }
                        }, 40L); // 2 segundos

                    } else {
                        player.sendMessage("");
                        player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "RANKUP NO DISPONIBLE");
                        player.sendMessage("");
                        player.sendMessage(result.getMessage());
                        player.sendMessage("");

                        // Efectos de fallo
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                    }
                });
            }).exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "❌ Error procesando rankup. Intenta de nuevo.");
                    plugin.getLogger().severe("Error en rankup desde menú: " + throwable.getMessage());
                });
                return null;
            });

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error iniciando rankup");
            plugin.getLogger().severe("Error manejando rankup desde menú: " + e.getMessage());
        }
    }
}