package gc.grivyzom.survivalcore.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Utilidad BULLETPROOF para manejar la seguridad de menús personalizados.
 * INTERFAZ COMPLETAMENTE PROTEGIDA - Los items NO se pueden modificar, mover o extraer.
 *
 * @author Brocolitx
 * @version 4.0 - BULLETPROOF Protection + Full Requirements Compliance
 */
public class MenuSecurityHandler implements Listener {

    private final JavaPlugin plugin;
    private final Set<String> protectedTitles;
    private final Set<Inventory> protectedInventories;

    // Callbacks
    private BiConsumer<Player, InventoryClickEvent> onClickHandler;
    private Consumer<Player> onCloseHandler;
    private boolean debugMode = false;

    // Configuración de protección BULLETPROOF
    private boolean allowRightClick = false;
    private boolean processActionsOnly = true; // Solo procesar acciones, NUNCA permitir movimiento

    public MenuSecurityHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.protectedTitles = new HashSet<>();
        this.protectedInventories = new HashSet<>();

        // Registrar eventos con MÁXIMA PRIORIDAD
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Registra títulos para proteger
     */
    public MenuSecurityHandler registerTitle(String title) {
        protectedTitles.add(title);
        return this;
    }

    public MenuSecurityHandler registerTitles(String... titles) {
        for (String title : titles) {
            protectedTitles.add(title);
        }
        return this;
    }

    /**
     * Permite right-click además de left-click (solo para procesamiento, no movimiento)
     */
    public MenuSecurityHandler setAllowRightClick(boolean allowRightClick) {
        this.allowRightClick = allowRightClick;
        return this;
    }

    /**
     * Configurar manejadores
     */
    public MenuSecurityHandler setClickHandler(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onClickHandler = handler;
        return this;
    }

    public MenuSecurityHandler setCloseHandler(Consumer<Player> handler) {
        this.onCloseHandler = handler;
        return this;
    }

    public MenuSecurityHandler setDebugMode(boolean debug) {
        this.debugMode = debug;
        return this;
    }

    /**
     * Verifica si un inventario está protegido
     */
    private boolean isProtected(String title, Inventory inventory) {
        // Verificar por título
        if (title != null) {
            for (String protectedTitle : protectedTitles) {
                if (title.equals(protectedTitle) || title.contains(protectedTitle)) {
                    return true;
                }
            }
        }

        // Verificar por instancia
        return inventory != null && protectedInventories.contains(inventory);
    }

    /**
     * Verifica si un click debe ser procesado (pero SIEMPRE será cancelado)
     */
    private boolean shouldProcessClick(InventoryClickEvent event, Player player) {
        // Solo procesar clicks en el GUI (inventario superior)
        int rawSlot = event.getRawSlot();
        int guiSize = event.getView().getTopInventory().getSize();

        if (rawSlot < 0 || rawSlot >= guiSize) {
            return false; // Click en inventario del jugador o fuera
        }

        // Verificar que hay un item clickeado
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return false; // No hay item válido
        }

        // Verificar tipo de click permitido
        ClickType clickType = event.getClick();
        switch (clickType) {
            case LEFT:
                return true; // Siempre permitir left-click
            case RIGHT:
                return allowRightClick; // Solo si está configurado
            default:
                return false; // Todos los demás tipos bloqueados
        }
    }

    // ================== EVENTOS BULLETPROOF ==================

    @EventHandler(priority = EventPriority.LOWEST) // PRIMERA en procesar
    public void onInventoryClickEarly(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Inventory inventory = event.getInventory();

        if (!isProtected(title, inventory)) return;

        if (debugMode) {
            plugin.getLogger().info("[EARLY] Click detectado en menú protegido: " + title +
                    " (slot: " + event.getRawSlot() + ", tipo: " + event.getClick() + ")");
        }

        // CANCELAR INMEDIATAMENTE - PROTECCIÓN ABSOLUTA
        event.setCancelled(true);
        event.setResult(InventoryClickEvent.Result.DENY);
    }

    @EventHandler(priority = EventPriority.MONITOR) // ÚLTIMA en procesar - para ejecutar acciones
    public void onInventoryClickLate(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Inventory inventory = event.getInventory();

        if (!isProtected(title, inventory)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // El evento YA está cancelado por el handler EARLY
        // Aquí solo procesamos acciones si corresponde

        if (shouldProcessClick(event, player) && onClickHandler != null) {
            try {
                if (debugMode) {
                    plugin.getLogger().info("[LATE] Procesando acción para slot: " + event.getRawSlot());
                }

                // Ejecutar handler personalizado
                onClickHandler.accept(player, event);

            } catch (Exception e) {
                if (debugMode) {
                    plugin.getLogger().warning("[ERROR] Error ejecutando handler: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // ASEGURAR que el evento sigue cancelado
        event.setCancelled(true);
        event.setResult(InventoryClickEvent.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        Inventory inventory = event.getInventory();

        if (!isProtected(title, inventory)) return;

        // BLOQUEAR COMPLETAMENTE cualquier drag
        event.setCancelled(true);
        event.setResult(InventoryDragEvent.Result.DENY);

        if (debugMode) {
            plugin.getLogger().info("[DRAG] Bloqueado en menú protegido: " + title);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Verificar ambos inventarios
        boolean isSourceProtected = false;
        boolean isDestProtected = false;

        // Verificar inventarios registrados
        if (protectedInventories.contains(event.getSource()) ||
                protectedInventories.contains(event.getDestination())) {
            isSourceProtected = true;
            isDestProtected = true;
        }

        if (isSourceProtected || isDestProtected) {
            event.setCancelled(true);
            if (debugMode) {
                plugin.getLogger().info("[MOVE] Movimiento de items bloqueado en menú protegido");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (protectedInventories.contains(event.getInventory())) {
            event.setCancelled(true);
            if (debugMode) {
                plugin.getLogger().info("[PICKUP] Pickup bloqueado en menú protegido");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        String title = event.getView().getTitle();
        Inventory inventory = event.getInventory();

        if (isProtected(title, inventory)) {
            // Registrar el inventario específico para protección adicional
            protectedInventories.add(inventory);

            if (debugMode && event.getPlayer() instanceof Player player) {
                plugin.getLogger().info("[OPEN] Menú protegido abierto: " + title + " por " + player.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        Inventory inventory = event.getInventory();

        if (!isProtected(title, inventory)) return;

        // Limpiar registro del inventario específico
        protectedInventories.remove(inventory);

        // Ejecutar handler de cierre
        if (onCloseHandler != null && event.getPlayer() instanceof Player player) {
            try {
                onCloseHandler.accept(player);

                if (debugMode) {
                    plugin.getLogger().info("[CLOSE] Menú protegido cerrado: " + title + " por " + player.getName());
                }
            } catch (Exception e) {
                if (debugMode) {
                    plugin.getLogger().warning("[ERROR] Error en handler de cierre: " + e.getMessage());
                }
            }
        }
    }

    // ================== PROTECCIÓN ADICIONAL ==================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Si el player tiene un menú protegido abierto, bloquear drops
        if (player.getOpenInventory() != null) {
            String title = player.getOpenInventory().getTitle();
            if (isProtected(title, null)) {
                event.setCancelled(true);
                if (debugMode) {
                    plugin.getLogger().info("[DROP] Drop bloqueado mientras menú protegido está abierto");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSwapHandItems(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // Si el player tiene un menú protegido abierto, bloquear swap
        if (player.getOpenInventory() != null) {
            String title = player.getOpenInventory().getTitle();
            if (isProtected(title, null)) {
                event.setCancelled(true);
                if (debugMode) {
                    plugin.getLogger().info("[SWAP] Hand swap bloqueado mientras menú protegido está abierto");
                }
            }
        }
    }

    // ================== MÉTODOS DE UTILIDAD ==================

    /**
     * Registra un inventario específico para protección
     */
    public MenuSecurityHandler registerInventory(Inventory inventory) {
        protectedInventories.add(inventory);
        return this;
    }

    /**
     * Desregistra protecciones
     */
    public void unregisterInventory(Inventory inventory) {
        protectedInventories.remove(inventory);
    }

    public void unregisterTitle(String title) {
        protectedTitles.remove(title);
    }

    /**
     * Verifica si un título está protegido
     */
    public boolean isProtectedTitle(String title) {
        return isProtected(title, null);
    }

    /**
     * Crea un inventario protegido automáticamente
     */
    public Inventory createProtectedInventory(String title, int size) {
        Inventory inventory = Bukkit.createInventory(null, size, title);
        registerInventory(inventory);
        return inventory;
    }

    /**
     * Limpia todos los registros
     */
    public void cleanup() {
        protectedTitles.clear();
        protectedInventories.clear();
        onClickHandler = null;
        onCloseHandler = null;

        if (debugMode) {
            plugin.getLogger().info("[CLEANUP] MenuSecurityHandler limpiado");
        }
    }

    /**
     * Métodos de configuración simplificados para compatibilidad
     */
    public MenuSecurityHandler setAllowOnlyLeftClick(boolean allowOnlyLeftClick) {
        this.allowRightClick = !allowOnlyLeftClick;
        return this;
    }

    public MenuSecurityHandler setAllowShiftClick(boolean allow) {
        // Shift+click SIEMPRE bloqueado en modo bulletproof
        return this;
    }

    public MenuSecurityHandler setAllowDoubleClick(boolean allow) {
        // Double-click SIEMPRE bloqueado en modo bulletproof
        return this;
    }

    public MenuSecurityHandler setAllowDropOutside(boolean allow) {
        // Drop SIEMPRE bloqueado en modo bulletproof
        return this;
    }

    public MenuSecurityHandler setAllowHotbarSwap(boolean allow) {
        // Hotbar swap SIEMPRE bloqueado en modo bulletproof
        return this;
    }
}