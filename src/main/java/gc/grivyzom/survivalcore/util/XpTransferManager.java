package gc.grivyzom.survivalcore.util;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Gestor de transferencias de experiencia entre jugadores.
 * Maneja límites diarios por rango y transferencias desde banco/barra personal.
 */
public class XpTransferManager {

    private final Main plugin;
    private final Map<String, Integer> rankLimits;
    private final TransferConfirmationManager confirmationManager;

    // SQL para la tabla de transferencias
    private static final String SQL_CREATE_TRANSFERS =
            "CREATE TABLE IF NOT EXISTS xp_transfers (" +
                    "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "sender_uuid CHAR(36) NOT NULL, " +
                    "receiver_uuid CHAR(36) NOT NULL, " +
                    "amount BIGINT NOT NULL, " +
                    "transfer_type ENUM('PLAYER', 'BANK') NOT NULL, " +
                    "transfer_date DATE NOT NULL, " +
                    "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "sender_name VARCHAR(16), " +
                    "receiver_name VARCHAR(16), " +
                    "INDEX idx_sender_date (sender_uuid, transfer_date), " +
                    "INDEX idx_receiver_date (receiver_uuid, transfer_date)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

    public XpTransferManager(Main plugin) {
        this.plugin = plugin;
        this.rankLimits = new HashMap<>();
        loadConfig();
        createTable();
        registerPermissions();
        this.confirmationManager = new TransferConfirmationManager(plugin);
    }

    /**
     * Carga la configuración de límites por rango
     */
    private void loadConfig() {
        rankLimits.clear();
        var section = plugin.getConfig().getConfigurationSection("transfer_limits");
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                rankLimits.put(rank, section.getInt(rank));
            }
        }

        // Valores por defecto si no están en config
        rankLimits.putIfAbsent("default", 30);
        rankLimits.putIfAbsent("vip", 100);
        rankLimits.putIfAbsent("elite", 200);
        rankLimits.putIfAbsent("admin", -1); // Ilimitado
    }

    /**
     * Registra permisos dinámicamente para cada rango
     */
    private void registerPermissions() {
        for (String rank : rankLimits.keySet()) {
            String permName = "survivalcore.transfer.rank." + rank;
            Permission perm = new Permission(permName,
                    "Permite transferir con límites del rango " + rank,
                    PermissionDefault.FALSE);

            try {
                plugin.getServer().getPluginManager().addPermission(perm);
            } catch (IllegalArgumentException ignored) {
                // El permiso ya existe
            }
        }
    }

    /**
     * Crea la tabla de transferencias en la base de datos
     */
    private void createTable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = DriverManager.getConnection(
                    getConnectionString(),
                    plugin.getConfig().getString("database.user"),
                    plugin.getConfig().getString("database.password"));
                 Statement stmt = conn.createStatement()) {

                stmt.execute(SQL_CREATE_TRANSFERS);
                plugin.getLogger().info("Tabla xp_transfers creada/verificada correctamente.");

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error creando tabla xp_transfers", e);
            }
        });
    }

    /**
     * Transfiere experiencia directa de la barra de un jugador a otro
     */
    public void transferFromPlayer(Player sender, String targetName, int levels) {
        // Validaciones síncronas primero
        if (!validateBasicTransfer(sender, targetName, levels)) return;

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "El jugador " + targetName + " no está online.");
            return;
        }

        if (sender.getLevel() < levels) {
            sender.sendMessage(ChatColor.RED + "No tienes suficientes niveles. Tienes: " + sender.getLevel());
            return;
        }

        // Verificar si requiere confirmación
        if (requiresConfirmation(TransferType.PLAYER_XP, levels)) {
            confirmationManager.requestConfirmation(sender, targetName, levels,
                    TransferConfirmationManager.TransferType.PLAYER_XP);
            return;
        }

        // Verificar límite diario de forma asíncrona
        CompletableFuture.supplyAsync(() -> {
            int dailyLimit = getDailyLimit(sender);
            int usedToday = getDailyTransferred(sender.getUniqueId().toString());
            return dailyLimit == -1 || (usedToday + levels) <= dailyLimit;
        }).thenAccept(canTransfer -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!canTransfer) {
                    int dailyLimit = getDailyLimit(sender);
                    int usedToday = getDailyTransferred(sender.getUniqueId().toString());
                    int remaining = dailyLimit == -1 ? Integer.MAX_VALUE : dailyLimit - usedToday;

                    sender.sendMessage(ChatColor.RED + "╔════════════════════════════════╗");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "¡LÍMITE DIARIO EXCEDIDO!" + ChatColor.RED + "       ║");
                    sender.sendMessage(ChatColor.RED + "╠════════════════════════════════╣");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Límite diario: " +
                            (dailyLimit == -1 ? "∞" : dailyLimit) + " niveles" + ChatColor.RED + " ║");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Usado hoy: " + usedToday + " niveles" + ChatColor.RED + "      ║");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Intentas transferir: " + levels + " niveles" + ChatColor.RED + " ║");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Disponible: " +
                            (remaining == Integer.MAX_VALUE ? "∞" : Math.max(0, remaining)) + " niveles" + ChatColor.RED + " ║");
                    sender.sendMessage(ChatColor.RED + "║                                ║");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "El límite se reinicia a las 00:00" + ChatColor.RED + " ║");
                    sender.sendMessage(ChatColor.RED + "╚════════════════════════════════╝");
                    return;
                }

                // Realizar transferencia
                executePlayerTransfer(sender, target, levels);
            });
        });
    }

    /**
     * Transfiere experiencia del banco de un jugador a otro (CORREGIDO)
     */

    public void transferFromBank(Player sender, String targetName, int levels) {
        // Validaciones básicas
        if (!validateBasicTransfer(sender, targetName, levels)) return;

        // Convertir niveles a XP (aproximadamente 68 XP por nivel)
        long amount = levels * 68L;

        UUID targetUUID = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        if (targetUUID == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado: " + targetName);
            return;
        }

        // Verificar si requiere confirmación (usando niveles para el threshold)
        if (requiresConfirmation(TransferType.BANK_XP, levels)) {
            confirmationManager.requestConfirmation(sender, targetName, levels,
                    TransferConfirmationManager.TransferType.BANK_XP);
            return;
        }

        // Resto del método permanece igual...
        CompletableFuture.supplyAsync(() -> {
            long bankBalance = plugin.getDatabaseManager().getBankedXp(sender.getUniqueId().toString());
            int dailyLimit = getDailyLimit(sender);
            int usedToday = getDailyTransferred(sender.getUniqueId().toString());

            return new Object[]{
                    bankBalance >= amount,
                    dailyLimit == -1 || (usedToday + (int)(amount / 68L)) <= dailyLimit, // XP convertido a niveles
                    bankBalance,
                    dailyLimit,
                    usedToday
            };
        }).thenAccept(results -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean hasBalance = (Boolean) results[0];
                boolean withinLimit = (Boolean) results[1];
                long bankBalance = (Long) results[2];
                int dailyLimit = (Integer) results[3];
                int usedToday = (Integer) results[4];

                if (!hasBalance) {
                    sender.sendMessage(ChatColor.RED + "Saldo insuficiente en tu banco. " +
                            "Saldo: " + bankBalance + ", Necesitas: " + amount);
                    return;
                }

                if (!withinLimit) {
                    int levelsToTransfer = (int)(amount / 68L); // Convertir XP a niveles para el límite
                    int remaining = dailyLimit == -1 ? Integer.MAX_VALUE : dailyLimit - usedToday;

                    sender.sendMessage(ChatColor.RED + "╔════════════════════════════════╗");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "¡LÍMITE DIARIO EXCEDIDO!" + ChatColor.RED + "       ║");
                    sender.sendMessage(ChatColor.RED + "╠════════════════════════════════╣");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Límite diario: " +
                            (dailyLimit == -1 ? "∞" : dailyLimit) + " niveles" + ChatColor.RED + "    ║");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Usado hoy: " + usedToday + " niveles" + ChatColor.RED + "       ║");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Intentas transferir: " + levelsToTransfer + " niveles" + ChatColor.RED + " ║");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "Disponible: " +
                            (remaining == Integer.MAX_VALUE ? "∞" : Math.max(0, remaining)) + " niveles" + ChatColor.RED + "    ║");
                    sender.sendMessage(ChatColor.RED + "║                                ║");
                    sender.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "El límite se reinicia a las 00:00" + ChatColor.RED + " ║");
                    sender.sendMessage(ChatColor.RED + "╚════════════════════════════════╝");
                    return;
                }

                // Realizar transferencia desde banco (CORREGIDO)
                executeBankTransferFixed(sender, targetName, targetUUID, amount);
            });
        });
    }

    /**
     * Ejecuta la transferencia desde la barra de experiencia del jugador
     */
    private void executePlayerTransfer(Player sender, Player target, int levels) {
        // Restar XP del remitente
        sender.setLevel(sender.getLevel() - levels);
        sender.setExp(0); // Reset de la barra fraccionaria

        // Dar XP al destinatario
        target.giveExpLevels(levels);

        // Registrar la transferencia
        recordTransfer(sender, target, levels, "PLAYER");

        // Mensajes y efectos
        sender.sendMessage(ChatColor.GREEN + "Has transferido " + ChatColor.YELLOW + levels +
                ChatColor.GREEN + " niveles a " + ChatColor.AQUA + target.getName() + ChatColor.GREEN + ".");
        target.sendMessage(ChatColor.GREEN + "Has recibido " + ChatColor.YELLOW + levels +
                ChatColor.GREEN + " niveles de " + ChatColor.AQUA + sender.getName() + ChatColor.GREEN + ".");

        // Efectos visuales y sonoros
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

        plugin.getLogger().info(String.format("XP Transfer: %s -> %s (%d levels from player)",
                sender.getName(), target.getName(), levels));
    }

    /**
     * Ejecuta la transferencia desde el banco de experiencia (NUEVA VERSIÓN CORREGIDA)
     */
    private void executeBankTransferFixed(Player sender, String targetName, UUID targetUUID, long amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 1. Restar del banco del remitente
                boolean withdrawSuccess = plugin.getDatabaseManager().withdrawBankedXp(
                        sender.getUniqueId().toString(), amount);

                if (!withdrawSuccess) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(ChatColor.RED + "Error al retirar XP del banco. Intenta de nuevo."));
                    return;
                }

                // 2. Añadir XP al destinatario
                Player targetPlayer = Bukkit.getPlayer(targetUUID);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    // Jugador está online - dar XP directamente
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        long levels = amount / 68L; // Aproximación: 68 XP = 1 nivel en promedio
                        int leftover = (int)(amount % 68L);

                        if (levels > 0) targetPlayer.giveExpLevels((int)levels);
                        if (leftover > 0) targetPlayer.giveExp(leftover);

                        // Efectos para el destinatario online
                        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                        targetPlayer.sendMessage(ChatColor.GREEN + "Has recibido " + ChatColor.YELLOW + amount +
                                " XP" + ChatColor.GREEN + " (" + levels + " niveles) del banco de " +
                                ChatColor.AQUA + sender.getName() + ChatColor.GREEN + ".");
                    });
                } else {
                    // Jugador está offline - añadir al banco del destinatario
                    long addedAmount = plugin.getDatabaseManager().addXpCapped(targetUUID.toString(), amount);

                    if (addedAmount < amount) {
                        // El banco del destinatario se llenó, devolver la diferencia
                        long difference = amount - addedAmount;
                        plugin.getDatabaseManager().updateBankedXp(sender.getUniqueId().toString(), difference);

                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(ChatColor.YELLOW + "El banco de " + targetName +
                                        " está lleno. Solo se transfirieron " + addedAmount + " XP. " +
                                        "Se devolvieron " + difference + " XP a tu banco."));
                    }
                }

                // 3. Registrar la transferencia
                recordTransferAsync(sender.getUniqueId(), targetUUID, amount, "BANK",
                        sender.getName(), targetName);

                // 4. Mensajes y efectos para el remitente
                Bukkit.getScheduler().runTask(plugin, () -> {
                    long levels = amount / 68L;
                    sender.sendMessage(ChatColor.GREEN + "Has transferido " + ChatColor.YELLOW + amount +
                            " XP" + ChatColor.GREEN + " (" + levels + " niveles) desde tu banco a " +
                            ChatColor.AQUA + targetName + ChatColor.GREEN + ".");

                    // Efectos para el remitente
                    sender.playSound(sender.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 0.8f);
                });

                plugin.getLogger().info(String.format("XP Transfer: %s -> %s (%d XP from bank)",
                        sender.getName(), targetName, amount));

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error en transferencia de banco", e);
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Error durante la transferencia. Contacta a un administrador."));
            }
        });
    }

    /**
     * Validaciones básicas para cualquier transferencia
     */
    private boolean validateBasicTransfer(Player sender, String targetName, int amount) {
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "La cantidad debe ser mayor a 0.");
            return false;
        }

        if (sender.getName().equalsIgnoreCase(targetName)) {
            sender.sendMessage(ChatColor.RED + "No puedes transferir experiencia a ti mismo.");
            return false;
        }

        return true;
    }

    /**
     * Obtiene el límite diario de transferencia para un jugador según su rango
     */
    public int getDailyLimit(Player player) {
        // Buscar el rango con mayor límite que tenga el jugador
        int maxLimit = rankLimits.get("default");

        for (Map.Entry<String, Integer> entry : rankLimits.entrySet()) {
            String rank = entry.getKey();
            int limit = entry.getValue();

            if (player.hasPermission("survivalcore.transfer.rank." + rank)) {
                if (limit == -1) return -1; // Ilimitado
                maxLimit = Math.max(maxLimit, limit);
            }
        }

        return maxLimit;
    }

    /**
     * Obtiene cuánto ha transferido un jugador hoy
     */
    public int getDailyTransferred(String uuid) {
        try (Connection conn = DriverManager.getConnection(
                getConnectionString(),
                plugin.getConfig().getString("database.user"),
                plugin.getConfig().getString("database.password"));
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(SUM(CASE " +
                             "WHEN transfer_type = 'PLAYER' THEN amount " +
                             "WHEN transfer_type = 'BANK' THEN FLOOR(amount / 68) " +
                             "END), 0) FROM xp_transfers " +
                             "WHERE sender_uuid = ? AND transfer_date = CURDATE()")) {

            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error consultando transferencias diarias", e);
        }

        return 0;
    }

    /**
     * Registra una transferencia en la base de datos
     */
    private void recordTransfer(Player sender, Player target, long amount, String type) {
        recordTransferAsync(sender.getUniqueId(), target.getUniqueId(), amount, type,
                sender.getName(), target.getName());
    }

    /**
     * Registra una transferencia en la base de datos (versión asíncrona)
     */
    private void recordTransferAsync(UUID senderUUID, UUID receiverUUID, long amount, String type,
                                     String senderName, String receiverName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = DriverManager.getConnection(
                    getConnectionString(),
                    plugin.getConfig().getString("database.user"),
                    plugin.getConfig().getString("database.password"));
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO xp_transfers (sender_uuid, receiver_uuid, amount, transfer_type, " +
                                 "transfer_date, sender_name, receiver_name) VALUES (?, ?, ?, ?, CURDATE(), ?, ?)")) {

                ps.setString(1, senderUUID.toString());
                ps.setString(2, receiverUUID.toString());
                ps.setLong(3, amount);
                ps.setString(4, type);
                ps.setString(5, senderName);
                ps.setString(6, receiverName);

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error registrando transferencia", e);
            }
        });
    }

    /**
     * Obtiene información de transferencias para un jugador (comando /xptransfers)
     */
    public void showTransferInfo(Player player) {
        CompletableFuture.supplyAsync(() -> {
            int dailyLimit = getDailyLimit(player);
            int usedToday = getDailyTransferred(player.getUniqueId().toString());
            int remaining = dailyLimit == -1 ? -1 : Math.max(0, dailyLimit - usedToday);

            return new Object[]{dailyLimit, usedToday, remaining};
        }).thenAccept(results -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int dailyLimit = (Integer) results[0];
                int usedToday = (Integer) results[1];
                int remaining = (Integer) results[2];

                player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════╗");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Información de Transferencias" + ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "╠════════════════════════════════╣");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Límite diario: " +
                        ChatColor.WHITE + (dailyLimit == -1 ? "Ilimitado" : String.format("%,d", dailyLimit)) +
                        ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Transferido hoy: " +
                        ChatColor.WHITE + String.format("%,d", usedToday) + ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Disponible: " +
                        ChatColor.WHITE + (remaining == -1 ? "Ilimitado" : String.format("%,d", remaining)) +
                        ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════╝");
            });
        });
    }

    /**
     * Recarga la configuración de límites
     */
    public void reloadConfig() {
        loadConfig();
        registerPermissions();
        plugin.getLogger().info("Configuración de transferencias recargada.");
    }

    /**
     * Obtiene el manager de confirmaciones
     */
    public TransferConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    /**
     * Determina si una transferencia requiere confirmación - CORREGIDO
     */
    private boolean requiresConfirmation(TransferType type, long amount) {
        String configKey = type == TransferType.PLAYER_XP ?
                "transfer_settings.confirmation_threshold" :
                "transfer_settings.bank_confirmation_threshold";

        int threshold = plugin.getConfig().getInt(configKey,
                type == TransferType.PLAYER_XP ? 50 : 15);

        // CORRECCIÓN: Para transferencias de banco, amount ya viene en niveles, no en XP
        if (type == TransferType.BANK_XP) {
            return amount >= threshold; // amount ya es en niveles
        } else {
            return amount >= threshold; // amount es en niveles
        }
    }

    /**
     * Enum para tipos de transferencia
     */
    public enum TransferType {
        PLAYER_XP, BANK_XP
    }

    /**
     * Obtiene la cadena de conexión a la base de datos
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
}