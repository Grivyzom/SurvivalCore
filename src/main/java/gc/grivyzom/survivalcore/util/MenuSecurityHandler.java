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
 * Utilidad para manejar la seguridad de menús personalizados.
 * Previene que los jugadores puedan modificar o extraer items de los menús.
 *
 * @author Brocolitx
 * @version 2.0 - Corregido para permitir interacciones mientras bloquea modificaciones
 */
public class MenuSecurityHandler implements Listener {

    private final JavaPlugin plugin;
    private final Set<String> protectedTitles;
    private final Set<Inventory> protectedInventories;

    // Callbacks opcionales
    private BiConsumer<Player, InventoryClickEvent> onClickHandler;
    private Consumer<Player> onCloseHandler;
    private boolean allowOnlyLeftClick = true;
    private boolean debugMode = false;

    /**
     * Constructor principal
     * @param plugin La instancia del plugin principal
     */
    public MenuSecurityHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.protectedTitles = new HashSet<>();
        this.protectedInventories = new HashSet<>();

        // Registrar los eventos
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Registra un título de inventario para proteger
     * @param title El título del inventario (puede contener códigos de color)
     * @return Esta instancia para encadenamiento
     */
    public MenuSecurityHandler registerTitle(String title) {
        protectedTitles.add(title);
        return this;
    }

    /**
     * Registra múltiples títulos de inventario para proteger
     * @param titles Los títulos de los inventarios
     * @return Esta instancia para encadenamiento
     */
    public MenuSecurityHandler registerTitles(String... titles) {
        for (String title : titles) {
            protectedTitles.add(title);
        }
        return this;
    }

    /**
     * Registra un inventario específico para proteger
     * @param inventory El inventario a proteger
     * @return Esta instancia para encadenamiento
     */
    public MenuSecurityHandler registerInventory(Inventory inventory) {
        protectedInventories.add(inventory);
        return this;
    }

    /**
     * Desregistra un inventario cuando ya no necesita protección
     * @param inventory El inventario a desproteger
     */
    public void unregisterInventory(Inventory inventory) {
        protectedInventories.remove(inventory);
    }

    /**
     * Desregistra un título cuando ya no necesita protección
     * @param title El título a desproteger
     */
    public void unregisterTitle(String title) {
        protectedTitles.remove(title);
    }

    /**
     * Establece si solo se permite click izquierdo (por defecto: true)
     * @param allowOnlyLeftClick true para solo permitir click izquierdo
     * @return Esta instancia para encadenamiento
     */
    public MenuSecurityHandler setAllowOnlyLeftClick(boolean allowOnlyLeftClick) {
        this.allowOnlyLeftClick = allowOnlyLeftClick;
        return this;
    }

    /**
     * Establece el manejador de clicks personalizado
     * @param handler BiConsumer que recibe (Player, InventoryClickEvent)
     * @return Esta instancia para encadenamiento
     */
    public MenuSecurityHandler setClickHandler(BiConsumer<Player, InventoryClickEvent> handler) {
        this.onClickHandler = handler;
        return this;
    }

    /**
     * Establece el manejador de cierre de inventario
     * @param handler Consumer que recibe el Player
     * @return Esta instancia para encadenamiento
     */
    public MenuSecurityHandler setCloseHandler(Consumer<Player> handler) {
        this.onCloseHandler = handler;
        return this;
    }

    /**
     * Activa o desactiva el modo debug
     * @param debug true para activar mensajes de debug
     * @return Esta instancia para encadenamiento
     */
    public MenuSecurityHandler setDebugMode(boolean debug) {
        this.debugMode = debug;
        return this;
    }

    /**
     * Verifica si un inventario está protegido
     * @param title El título del inventario
     * @param inventory El inventario (puede ser null)
     * @return true si está protegido
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

        // Verificar por instancia de inventario
        if (inventory != null && protectedInventories.contains(inventory)) {
            return true;
        }

        return false;
    }

    /**
     * Método helper para verificar si un título necesita una verificación especial
     * Útil para menús con placeholders dinámicos
     * @param title El título a verificar
     * @return true si es un inventario protegido
     */
    public boolean isProtectedTitle(String title) {
        return isProtected(title, null);
    }

    // ================== EVENTOS ==================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // Verificar si es un inventario protegido
        String title = event.getView().getTitle();
        Inventory inventory = event.getInventory();

        if (!isProtected(title, inventory)) return;

        // Solo procesar si es un jugador
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        // Verificar que el click sea en el inventario superior (el GUI)
        int rawSlot = event.getRawSlot();
        int inventorySize = event.getView().getTopInventory().getSize();

        // IMPORTANTE: Permitir procesar el click ANTES de cancelarlo
        boolean shouldProcessClick = false;

        // Si el click es en el inventario del GUI (no en el del jugador)
        if (rawSlot >= 0 && rawSlot < inventorySize) {
            // Obtener el item clickeado
            ItemStack clickedItem = event.getCurrentItem();

            // Si hay un item válido
            if (clickedItem != null && clickedItem.hasItemMeta()) {
                // Verificar el tipo de click
                if (allowOnlyLeftClick) {
                    // Solo procesar click izquierdo con cursor vacío
                    if (event.getClick() == ClickType.LEFT &&
                            (event.getCursor() == null || event.getCursor().getType().isAir())) {
                        shouldProcessClick = true;
                    }
                } else {
                    // Permitir cualquier click (pero seguirá siendo cancelado después)
                    shouldProcessClick = true;
                }
            }
        }

        // PRIMERO: Ejecutar el handler si corresponde
        if (shouldProcessClick && onClickHandler != null) {
            try {
                // Ejecutar el handler personalizado
                onClickHandler.accept(player, event);

                if (debugMode) {
                    plugin.getLogger().info("Click procesado en slot " + rawSlot + " para " + player.getName());
                }
            } catch (Exception e) {
                if (debugMode) {
                    plugin.getLogger().warning("Error procesando click: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // DESPUÉS: Cancelar el evento para prevenir movimiento de items
        event.setCancelled(true);
        event.setResult(InventoryClickEvent.Result.DENY);

        if (debugMode && !shouldProcessClick) {
            plugin.getLogger().info("Click bloqueado sin procesar en slot " + rawSlot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        Inventory inventory = event.getInventory();

        if (!isProtected(title, inventory)) return;

        // Cancelar cualquier intento de arrastrar
        event.setCancelled(true);
        event.setResult(InventoryDragEvent.Result.DENY);

        if (debugMode) {
            plugin.getLogger().info("Drag bloqueado en menú protegido: " + title);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Verificar inventarios involucrados
        boolean shouldCancel = false;

        try {
            // Verificar el inventario fuente
            if (event.getSource().getHolder() instanceof Player sourcePlayer) {
                if (sourcePlayer.getOpenInventory() != null) {
                    String sourceTitle = sourcePlayer.getOpenInventory().getTitle();
                    Inventory sourceInv = sourcePlayer.getOpenInventory().getTopInventory();
                    if (isProtected(sourceTitle, sourceInv)) {
                        shouldCancel = true;
                    }
                }
            }

            // Verificar el inventario destino
            if (!shouldCancel && event.getDestination().getHolder() instanceof Player destPlayer) {
                if (destPlayer.getOpenInventory() != null) {
                    String destTitle = destPlayer.getOpenInventory().getTitle();
                    Inventory destInv = destPlayer.getOpenInventory().getTopInventory();
                    if (isProtected(destTitle, destInv)) {
                        shouldCancel = true;
                    }
                }
            }
        } catch (Exception e) {
            // Si hay algún error, cancelar por seguridad
            shouldCancel = true;
            if (debugMode) {
                plugin.getLogger().warning("Error verificando movimiento de items: " + e.getMessage());
            }
        }

        if (shouldCancel) {
            event.setCancelled(true);
            if (debugMode) {
                plugin.getLogger().info("Movimiento de items bloqueado en menú protegido");
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        Inventory inventory = event.getInventory();

        if (!isProtected(title, inventory)) return;

        // Desregistrar el inventario si estaba registrado por instancia
        protectedInventories.remove(inventory);

        // Ejecutar el handler de cierre si existe
        if (onCloseHandler != null && event.getPlayer() instanceof Player player) {
            try {
                onCloseHandler.accept(player);

                if (debugMode) {
                    plugin.getLogger().info("Menú protegido cerrado: " + title + " por " + player.getName());
                }
            } catch (Exception e) {
                if (debugMode) {
                    plugin.getLogger().warning("Error en handler de cierre: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Método de utilidad para crear un inventario protegido
     * @param title El título del inventario
     * @param size El tamaño del inventario (debe ser múltiplo de 9)
     * @return El inventario creado y ya protegido
     */
    public Inventory createProtectedInventory(String title, int size) {
        Inventory inventory = Bukkit.createInventory(null, size, title);
        registerInventory(inventory);
        return inventory;
    }

    /**
     * Limpia todos los registros (útil para reload del plugin)
     */
    public void cleanup() {
        protectedTitles.clear();
        protectedInventories.clear();
        onClickHandler = null;
        onCloseHandler = null;
    }
}