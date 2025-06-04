package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.util.XpTransferManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comandos para el sistema de transferencia de experiencia.
 * Maneja /xpgive, /xpbank transfer, /xptransfers y /xptransferlog
 */
public class XpTransferCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final XpTransferManager transferManager;

    public XpTransferCommand(Main plugin) {
        this.plugin = plugin;
        this.transferManager = plugin.getXpTransferManager();
    }

    /**
     * Maneja transferencias desde el banco (para integrar con ScoreCommand)
     */
    public boolean handleBankTransfer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        if (args.length != 3) {
            player.sendMessage(ChatColor.RED + "Uso: /score xpbank transfer <jugador> <cantidad>");
            player.sendMessage(ChatColor.GRAY + "Transfiere experiencia de tu banco a otro jugador.");
            return true;
        }

        String targetName = args[1];
        long amount;

        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "La cantidad debe ser un número válido.");
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "La cantidad debe ser mayor a 0.");
            return true;
        }

        // Confirmación para transferencias grandes
        if (amount >= 1000) {
            player.sendMessage(ChatColor.YELLOW + "¿Estás seguro de transferir " + amount +
                    " XP desde tu banco a " + targetName + "? Escribe el comando nuevamente para confirmar.");
        }

        transferManager.transferFromBank(player, targetName, amount);
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "xpgive" -> {
                return handleXpGive(sender, args);
            }
            case "xptransfers" -> {
                return handleXpTransfers(sender, args);
            }
            case "xptransferlog" -> {
                return handleXpTransferLog(sender, args);
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Maneja el comando /xpgive <jugador> <cantidad>
     */
    private boolean handleXpGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Uso: /xpgive <jugador> <cantidad>");
            player.sendMessage(ChatColor.GRAY + "Transfiere experiencia de tu barra a otro jugador.");
            return true;
        }

        String targetName = args[0];
        int levels;

        try {
            levels = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "La cantidad debe ser un número válido.");
            return true;
        }

        if (levels <= 0) {
            player.sendMessage(ChatColor.RED + "La cantidad debe ser mayor a 0.");
            return true;
        }

        // Confirmación para transferencias grandes
        if (levels >= 50) {
            player.sendMessage(ChatColor.YELLOW + "¿Estás seguro de transferir " + levels +
                    " niveles a " + targetName + "? Escribe el comando nuevamente para confirmar.");
            // Aquí podrías implementar un sistema de confirmación más sofisticado
        }

        transferManager.transferFromPlayer(player, targetName, levels);
        return true;
    }

    /**
     * Maneja el comando /xptransfers
     */
    private boolean handleXpTransfers(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        transferManager.showTransferInfo(player);
        return true;
    }

    /**
     * Maneja el comando /xptransferlog (solo staff)
     */
    private boolean handleXpTransferLog(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.transferlog")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Uso: /xptransferlog <jugador> [días]");
            sender.sendMessage(ChatColor.GRAY + "Muestra el historial de transferencias de un jugador.");
            return true;
        }

        String targetName = args[0];
        int days = args.length > 1 ? parseIntSafe(args[1], 7) : 7;

        showTransferLog(sender, targetName, days);
        return true;
    }

    /**
     * Muestra el historial de transferencias de un jugador
     */
    private void showTransferLog(CommandSender sender, String targetName, int days) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> logs = new ArrayList<>();
            String uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId().toString();

            try (Connection conn = DriverManager.getConnection(
                    getConnectionString(),
                    plugin.getConfig().getString("database.user"),
                    plugin.getConfig().getString("database.password"));
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT sender_name, receiver_name, amount, transfer_type, timestamp " +
                                 "FROM xp_transfers " +
                                 "WHERE (sender_uuid = ? OR receiver_uuid = ?) " +
                                 "AND transfer_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                                 "ORDER BY timestamp DESC LIMIT 50")) {

                ps.setString(1, uuid);
                ps.setString(2, uuid);
                ps.setInt(3, days);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String senderName = rs.getString("sender_name");
                        String receiverName = rs.getString("receiver_name");
                        long amount = rs.getLong("amount");
                        String type = rs.getString("transfer_type");
                        Timestamp timestamp = rs.getTimestamp("timestamp");

                        String direction = senderName.equalsIgnoreCase(targetName) ? "→" : "←";
                        String otherPlayer = senderName.equalsIgnoreCase(targetName) ? receiverName : senderName;
                        String typeStr = type.equals("PLAYER") ? "Barra" : "Banco";

                        logs.add(String.format("%s %s %s %d XP (%s) - %s",
                                direction, otherPlayer, direction.equals("→") ? "Enviado" : "Recibido",
                                amount, typeStr, timestamp.toString()));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error consultando historial de transferencias: " + e.getMessage());
                logs.add("Error al consultar la base de datos.");
            }

            // Enviar resultados en el hilo principal
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.GOLD + "=== Historial de Transferencias: " + targetName + " ===");
                sender.sendMessage(ChatColor.GRAY + "Últimos " + days + " días (máximo 50 registros)");

                if (logs.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No se encontraron transferencias.");
                } else {
                    for (String log : logs) {
                        sender.sendMessage(ChatColor.WHITE + log);
                    }
                }

                sender.sendMessage(ChatColor.GOLD + "=== Fin del historial ===");
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "xpgive" -> {
                if (args.length == 1) {
                    // Autocompletar nombres de jugadores online
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 2) {
                    // Sugerencias de cantidades comunes
                    return Arrays.asList("1", "5", "10", "25", "50", "100")
                            .stream()
                            .filter(amount -> amount.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
            }
            case "xptransferlog" -> {
                if (args.length == 1) {
                    // Autocompletar nombres de jugadores (online y offline)
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args.length == 2) {
                    // Sugerencias de días
                    return Arrays.asList("1", "7", "30", "90")
                            .stream()
                            .filter(days -> days.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Parsea un entero de forma segura, devolviendo un valor por defecto si falla
     */
    private int parseIntSafe(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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