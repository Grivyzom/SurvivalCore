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

        // Enviar mensaje de confirmación mejorado
        String amountStr = type == TransferType.PLAYER_XP ? amount + " niveles" : amount + " niveles";
        String sourceStr = type == TransferType.PLAYER_XP ? "tu barra de experiencia" : "tu banco de experiencia";

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "⚠ CONFIRMACIÓN REQUERIDA ⚠" + ChatColor.GOLD + "       ║");
        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════╣");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Transferir: " + ChatColor.YELLOW + amountStr + ChatColor.GOLD + " ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Desde: " + ChatColor.AQUA + (type == TransferType.PLAYER_XP ? "Barra XP" : "Banco XP") + ChatColor.GOLD + " ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Para: " + ChatColor.GREEN + targetName + ChatColor.GOLD + " ║");
        player.sendMessage(ChatColor.GOLD + "║                                  ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "Escribe: " + ChatColor.BOLD + "SI" + ChatColor.RESET + ChatColor.GREEN + " para confirmar" + ChatColor.GOLD + " ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.RED + "Escribe: " + ChatColor.BOLD + "NO" + ChatColor.RESET + ChatColor.RED + " para cancelar" + ChatColor.GOLD + " ║");
        player.sendMessage(ChatColor.GOLD + "║                                  ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "Expira en 30 segundos" + ChatColor.GOLD + "            ║");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
        player.sendMessage("");

        // Programar limpieza automática
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingTransfers.containsKey(uuid)) {
                pendingTransfers.remove(uuid);
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "⏰ Transferencia cancelada por tiempo agotado.");
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
     * Confirma y ejecuta una transferencia pendiente - CORREGIDO
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

        // CORRECCIÓN CRÍTICA: Ejecutar transferencia SIN confirmación
        XpTransferManager transferManager = plugin.getXpTransferManager();

        switch (pending.type) {
            case PLAYER_XP -> {
                // Llamar al método interno que NO requiere confirmación
                executePlayerTransferDirect(player, pending.targetName, (int)pending.amount);
            }
            case BANK_XP -> {
                // Llamar al método interno que NO requiere confirmación
                executeBankTransferDirect(player, pending.targetName, (int)pending.amount);
            }
        }

        player.sendMessage(ChatColor.GREEN + "✅ Transferencia confirmada y ejecutada exitosamente.");
    }

    /**
     * Ejecuta transferencia de barra sin confirmación
     */
    private void executePlayerTransferDirect(Player sender, String targetName, int levels) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "El jugador " + targetName + " ya no está online.");
            return;
        }

        if (sender.getLevel() < levels) {
            sender.sendMessage(ChatColor.RED + "Ya no tienes suficientes niveles. Tienes: " + sender.getLevel());
            return;
        }

        // Verificar límite diario nuevamente
        XpTransferManager transferManager = plugin.getXpTransferManager();
        int dailyLimit = transferManager.getDailyLimit(sender);
        int usedToday = transferManager.getDailyTransferred(sender.getUniqueId().toString());

        if (dailyLimit != -1 && (usedToday + levels) > dailyLimit) {
            sender.sendMessage(ChatColor.RED + "Has excedido tu límite diario mientras confirmabas.");
            return;
        }

        // Ejecutar transferencia directa
        sender.setLevel(sender.getLevel() - levels);
        sender.setExp(0);
        target.giveExpLevels(levels);

        // Registrar en BD
        recordTransferDirect(sender.getUniqueId(), target.getUniqueId(), levels, "PLAYER",
                sender.getName(), target.getName());

        // Mensajes y efectos
        sender.sendMessage(ChatColor.GREEN + "Has transferido " + ChatColor.YELLOW + levels +
                ChatColor.GREEN + " niveles a " + ChatColor.AQUA + target.getName() + ChatColor.GREEN + ".");
        target.sendMessage(ChatColor.GREEN + "Has recibido " + ChatColor.YELLOW + levels +
                ChatColor.GREEN + " niveles de " + ChatColor.AQUA + sender.getName() + ChatColor.GREEN + ".");

        plugin.getLogger().info(String.format("Confirmed XP Transfer: %s -> %s (%d levels from player)",
                sender.getName(), target.getName(), levels));
    }

    /**
     * Ejecuta transferencia de banco sin confirmación
     */
    private void executeBankTransferDirect(Player sender, String targetName, int levels) {
        long amount = levels * 68L;
        UUID targetUUID = Bukkit.getOfflinePlayer(targetName).getUniqueId();

        // Verificar saldo del banco
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long bankBalance = plugin.getDatabaseManager().getBankedXp(sender.getUniqueId().toString());

            if (bankBalance < amount) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Ya no tienes suficiente XP en tu banco."));
                return;
            }

            // Restar del banco
            boolean withdrawSuccess = plugin.getDatabaseManager().withdrawBankedXp(
                    sender.getUniqueId().toString(), amount);

            if (!withdrawSuccess) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Error al retirar XP del banco."));
                return;
            }

            // Dar XP al destinatario
            Player targetPlayer = Bukkit.getPlayer(targetUUID);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    targetPlayer.giveExpLevels(levels);
                    targetPlayer.sendMessage(ChatColor.GREEN + "Has recibido " + ChatColor.YELLOW + levels +
                            " niveles" + ChatColor.GREEN + " del banco de " + ChatColor.AQUA + sender.getName() + ChatColor.GREEN + ".");
                });
            } else {
                // Jugador offline - añadir al banco del destinatario
                plugin.getDatabaseManager().addXpCapped(targetUUID.toString(), amount);
            }

            // Registrar transferencia
            recordTransferDirect(sender.getUniqueId(), targetUUID, amount, "BANK",
                    sender.getName(), targetName);

            // Mensaje al remitente
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.GREEN + "Has transferido " + ChatColor.YELLOW + levels +
                        " niveles" + ChatColor.GREEN + " desde tu banco a " + ChatColor.AQUA + targetName + ChatColor.GREEN + ".");
            });

            plugin.getLogger().info(String.format("Confirmed Bank Transfer: %s -> %s (%d levels)",
                    sender.getName(), targetName, levels));
        });
    }

    /**
     * Registra transferencia directamente en BD
     */
    private void recordTransferDirect(UUID senderUUID, UUID receiverUUID, long amount, String type,
                                      String senderName, String receiverName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String connectionString = getConnectionString();
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                        connectionString,
                        plugin.getConfig().getString("database.user"),
                        plugin.getConfig().getString("database.password"));
                     java.sql.PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO xp_transfers (sender_uuid, receiver_uuid, amount, transfer_type, " +
                                     "transfer_date, sender_name, receiver_name) VALUES (?, ?, ?, ?, CURDATE(), ?, ?)")) {

                    ps.setString(1, senderUUID.toString());
                    ps.setString(2, receiverUUID.toString());
                    ps.setLong(3, amount);
                    ps.setString(4, type);
                    ps.setString(5, senderName);
                    ps.setString(6, receiverName);

                    ps.executeUpdate();
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("Error registrando transferencia confirmada: " + e.getMessage());
            }
        });
    }

    /**
     * Obtiene cadena de conexión a BD
     */
    private String getConnectionString() {
        String type = plugin.getConfig().getString("database.type", "mysql");
        if (type.equalsIgnoreCase("mysql")) {
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String database = plugin.getConfig().getString("database.database", "survivalcore");
            return String.format("jdbc:mysql://%s:%d/%s?autoReconnect=true", host, port, database);
        } else {
            return String.format("jdbc:sqlite:%s/userdata.db", plugin.getDataFolder().getAbsolutePath());
        }
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

        // Sistema de confirmación mejorado - más palabras aceptadas
        if (message.equals("si") || message.equals("sí") || message.equals("yes") ||
                message.equals("confirm") || message.equals("confirmar") || message.equals("ok")) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> confirmTransfer(player));
        } else if (message.equals("no") || message.equals("cancel") || message.equals("cancelar") ||
                message.equals("abort") || message.equals("abortar")) {
            event.setCancelled(true);
            cancelTransfer(uuid);
            player.sendMessage(ChatColor.RED + "❌ Transferencia cancelada.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Limpiar transferencias pendientes cuando el jugador se desconecta
        pendingTransfers.remove(event.getPlayer().getUniqueId());
    }
}