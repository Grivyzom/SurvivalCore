package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.util.XpChequeManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener para el sistema de cheques de experiencia.
 * Maneja la interacción con cheques y previene exploits.
 */
public class XpChequeListener implements Listener {

    private final Main plugin;
    private final XpChequeManager chequeManager;

    public XpChequeListener(Main plugin, XpChequeManager chequeManager) {
        this.plugin = plugin;
        this.chequeManager = chequeManager;
    }

    /**
     * Maneja el clic derecho para canjear cheques
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !chequeManager.isCheque(item)) {
            return;
        }

        // Cancelar el evento para evitar colocación de bloques u otras acciones
        event.setCancelled(true);

        // Verificar permisos para canjear
        if (!player.hasPermission("survivalcore.cheque.redeem")) {
            player.sendMessage(ChatColor.RED + "No tienes permisos para canjear cheques.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }

        // Verificar cooldown para prevenir spam
        if (isOnCooldown(player)) {
            player.sendMessage(ChatColor.RED + "Espera un momento antes de canjear otro cheque.");
            return;
        }

        // Intentar canjear el cheque
        boolean success = chequeManager.redeemCheque(player, item);

        if (success) {
            setCooldown(player);
        } else {
            player.sendMessage(ChatColor.RED + "No se pudo canjear el cheque. Puede estar dañado o ya canjeado.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
    }

    /**
     * Previene la duplicación de cheques en ciertos inventarios
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Verificar si algún ítem es un cheque
        boolean clickedIsCheque = chequeManager.isCheque(clickedItem);
        boolean cursorIsCheque = chequeManager.isCheque(cursorItem);

        if (!clickedIsCheque && !cursorIsCheque) return;

        // Prevenir ciertas acciones con cheques en inventarios específicos
        InventoryType invType = event.getInventory().getType();

        // Lista de inventarios donde los cheques no deberían poder ser colocados
        if (invType == InventoryType.ANVIL ||
                invType == InventoryType.BEACON ||
                invType == InventoryType.BREWING ||
                invType == InventoryType.ENCHANTING ||
                invType == InventoryType.FURNACE ||
                invType == InventoryType.BLAST_FURNACE ||
                invType == InventoryType.SMOKER ||
                invType == InventoryType.GRINDSTONE ||
                invType == InventoryType.LOOM ||
                invType == InventoryType.STONECUTTER) {

            if (clickedIsCheque || cursorIsCheque) {
                event.setCancelled(true);
                ((Player) event.getWhoClicked()).sendMessage(ChatColor.RED +
                        "Los cheques no pueden ser utilizados en este tipo de inventario.");
                return;
            }
        }

        // Prevenir shift-click con cheques para evitar movimientos masivos accidentales
        if (event.isShiftClick() && clickedIsCheque) {
            // Permitir solo en el inventario del jugador
            if (event.getInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                ((Player) event.getWhoClicked()).sendMessage(ChatColor.YELLOW +
                        "Los cheques deben ser movidos uno por uno fuera de tu inventario personal.");
            }
        }
    }

    /**
     * Mensaje informativo para nuevos jugadores
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Solo mostrar mensaje a jugadores nuevos o que no han usado cheques antes
        if (!player.hasPlayedBefore() && player.hasPermission("survivalcore.cheque.create")) {
            // Enviar mensaje después de 5 segundos
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GOLD + "💰 " + ChatColor.YELLOW + "¡Descubre el sistema de cheques!");
                    player.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/cheque help " +
                            ChatColor.GRAY + "para aprender a crear cheques de experiencia.");
                    player.sendMessage("");
                }
            }, 100L); // 5 segundos
        }
    }

    // Sistema de cooldown simple para prevenir spam de canje
    private final java.util.Map<java.util.UUID, Long> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long COOLDOWN_TIME = 1000; // 1 segundo

    private boolean isOnCooldown(Player player) {
        Long lastUse = cooldowns.get(player.getUniqueId());
        return lastUse != null && System.currentTimeMillis() - lastUse < COOLDOWN_TIME;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Limpiar cooldowns al desconectarse (opcional, para ahorrar memoria)
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }
}