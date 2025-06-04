package gc.grivyzom.survivalcore.util;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestor de confirmaciones para transferencias de experiencia grandes.
 * Maneja el sistema de "escribe 'confirm' para continuar" para transferencias importantes.
 */
public class TransferConfirmationManager implements Listener {

    private final Main plugin;
    private final Map<UUID, PendingTransfer> pendingTransfers = new HashMap<>();

    public TransferConfirmationManager(Main plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Representa una transferencia pendiente de confirmación
     */
    private static class PendingTransfer {
        final String targetName;
        final long amount;
        final TransferType type;
        final long timestamp;

        PendingTransfer(String targetName, long amount, TransferType type) {
            this.targetName = targetName;
            this.amount = amount;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30000; // 30 segundos
        }
    }

    public enum TransferType {
        PLAYER_XP, BANK_XP
    }

    /**
     * Solicita confirmación para una transferencia grande
     */
    public void requestConfirmation(Player player, String targetName, long amount, TransferType type) {
        UUID uuid = player.getUniqueId();

        // Limpiar transferencias expiradas
        cleanupExpired();

        // Guardar la transferencia pendiente
        pendingTransfers.put(uuid, new PendingTransfer(targetName, amount, type));

        // Enviar mensaje de confirmación
        String amountStr = type == TransferType.PLAYER_XP ? amount + " niveles" : amount + " XP";
        String sourceStr = type == TransferType.PLAYER_XP ? "tu barra de experiencia" : "tu banco de experiencia";

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "CONFIRMACIÓN REQUERIDA");
        player.sendMessage(ChatColor.WHITE + "Vas a transferir " + ChatColor.GOLD + amountStr +
                ChatColor.WHITE + " desde " + sourceStr);
        player.sendMessage(ChatColor.WHITE + "al jugador " + ChatColor.AQUA + targetName + ChatColor.WHITE + ".");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Escribe " + ChatColor.BOLD + "confirm" +
                ChatColor.GREEN + " para continuar o " + ChatColor.RED + ChatColor.BOLD + "cancel" +
                ChatColor.RED + " para cancelar.");
        player.sendMessage(ChatColor.GRAY + "Esta confirmación expira en 30 segundos.");
        player.sendMessage("");

        // Programar limpieza automática
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingTransfers.containsKey(uuid)) {
                pendingTransfers.remove(uuid);
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Transferencia cancelada por timeout.");
                }
            }
        }, 600L); // 30 segundos
    }

    /**
     * Verifica si un jugador tiene una transferencia pendiente de confirmación
     */
    public boolean hasPendingTransfer(UUID uuid) {
        PendingTransfer pending = pendingTransfers.get(uuid);
        if (pending != null && pending.isExpired()) {
            pendingTransfers.remove(uuid);
            return false;
        }
        return pending != null;
    }

    /**
     * Obtiene la transferencia pendiente de un jugador
     */
    public PendingTransfer getPendingTransfer(UUID uuid) {
        return pendingTransfers.get(uuid);
    }

    /**
     * Cancela una transferencia pendiente
     */
    public void cancelTransfer(UUID uuid) {
        pendingTransfers.remove(uuid);
    }

    /**
     * Confirma y ejecuta una transferencia pendiente
     */
    public void confirmTransfer(Player player) {
        UUID uuid = player.getUniqueId();
        PendingTransfer pending = pendingTransfers.remove(uuid);

        if (pending == null) {
            player.sendMessage(ChatColor.RED + "No tienes transferencias pendientes.");
            return;
        }

        if (pending.isExpired()) {
            player.sendMessage(ChatColor.RED + "La transferencia ha expirado. Inténtalo de nuevo.");
            return;
        }

        // Ejecutar la transferencia
        XpTransferManager transferManager = plugin.getXpTransferManager();

        switch (pending.type) {
            case PLAYER_XP -> transferManager.transferFromPlayer(player, pending.targetName, (int)pending.amount);
            case BANK_XP -> transferManager.transferFromBank(player, pending.targetName, pending.amount);
        }

        player.sendMessage(ChatColor.GREEN + "Transferencia confirmada y ejecutada.");
    }

    /**
     * Limpia transferencias expiradas
     */
    private void cleanupExpired() {
        pendingTransfers.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!hasPendingTransfer(uuid)) return;

        String message = event.getMessage().toLowerCase().trim();

        if (message.equals("confirm") || message.equals("confirmar") || message.equals("si")) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> confirmTransfer(player));
        } else if (message.equals("cancel") || message.equals("cancelar") || message.equals("no")) {
            event.setCancelled(true);
            cancelTransfer(uuid);
            player.sendMessage(ChatColor.RED + "Transferencia cancelada.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Limpiar transferencias pendientes cuando el jugador se desconecta
        pendingTransfers.remove(event.getPlayer().getUniqueId());
    }
}