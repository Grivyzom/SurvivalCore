package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Comando para mostrar estadísticas avanzadas de transferencias de experiencia.
 * Proporciona información detallada sobre patrones de transferencia, rankings y tendencias.
 */
public class TransferStatsCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public TransferStatsCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survivalcore.transferstats")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            showGeneralStats(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "top" -> showTopTransfers(sender, args);
            case "player" -> showPlayerStats(sender, args);
            case "daily" -> showDailyStats(sender, args);
            case "summary" -> showSummaryStats(sender, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa: top, player, daily, summary");
                return true;
            }
        }

        return true;
    }

    /**
     * Muestra estadísticas generales del sistema de transferencias
     */
    private void showGeneralStats(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection()) {
                Map<String, Object> stats = new HashMap<>();

                // Total de transferencias
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as total, SUM(amount) as total_xp FROM xp_transfers")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        stats.put("total_transfers", rs.getInt("total"));
                        stats.put("total_xp", rs.getLong("total_xp"));
                    }
                }

                // Transferencias de hoy
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as today, SUM(amount) as today_xp FROM xp_transfers WHERE transfer_date = CURDATE()")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        stats.put("today_transfers", rs.getInt("today"));
                        stats.put("today_xp", rs.getLong("today_xp"));
                    }
                }

                // Jugadores únicos que han transferido
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(DISTINCT sender_uuid) as unique_senders FROM xp_transfers")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        stats.put("unique_senders", rs.getInt("unique_senders"));
                    }
                }

                // Promedio diario (últimos 30 días)
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT AVG(daily_count) as avg_daily, AVG(daily_xp) as avg_daily_xp FROM (" +
                                "SELECT transfer_date, COUNT(*) as daily_count, SUM(amount) as daily_xp " +
                                "FROM xp_transfers WHERE transfer_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                                "GROUP BY transfer_date) as daily_stats")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        stats.put("avg_daily_transfers", rs.getDouble("avg_daily"));
                        stats.put("avg_daily_xp", rs.getDouble("avg_daily_xp"));
                    }
                }

                // Enviar resultados al hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GOLD + "═══ Estadísticas de Transferencias ═══");
                    sender.sendMessage(ChatColor.YELLOW + "Total de transferencias: " +
                            ChatColor.WHITE + String.format("%,d", (Integer)stats.getOrDefault("total_transfers", 0)));
                    sender.sendMessage(ChatColor.YELLOW + "Total XP transferida: " +
                            ChatColor.WHITE + String.format("%,d", (Long)stats.getOrDefault("total_xp", 0L)));
                    sender.sendMessage("");
                    sender.sendMessage(ChatColor.YELLOW + "Transferencias hoy: " +
                            ChatColor.WHITE + String.format("%,d", (Integer)stats.getOrDefault("today_transfers", 0)));
                    sender.sendMessage(ChatColor.YELLOW + "XP transferida hoy: " +
                            ChatColor.WHITE + String.format("%,d", (Long)stats.getOrDefault("today_xp", 0L)));
                    sender.sendMessage("");
                    sender.sendMessage(ChatColor.YELLOW + "Jugadores únicos: " +
                            ChatColor.WHITE + String.format("%,d", (Integer)stats.getOrDefault("unique_senders", 0)));
                    sender.sendMessage(ChatColor.YELLOW + "Promedio diario (30d): " +
                            ChatColor.WHITE + String.format("%.1f transferencias, %,.0f XP",
                            (Double)stats.getOrDefault("avg_daily_transfers", 0.0),
                            (Double)stats.getOrDefault("avg_daily_xp", 0.0)));
                });

            } catch (SQLException e) {
                plugin.getLogger().warning("Error consultando estadísticas generales: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Error al consultar las estadísticas."));
            }
        });
    }

    /**
     * Muestra el ranking de jugadores que más han transferido
     */
    private void showTopTransfers(CommandSender sender, String[] args) {
        int limit = args.length > 1 ? parseIntSafe(args[1], 10) : 10;
        int days = args.length > 2 ? parseIntSafe(args[2], 30) : 30;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT sender_name, COUNT(*) as transfer_count, SUM(amount) as total_xp " +
                                 "FROM xp_transfers " +
                                 "WHERE transfer_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                                 "GROUP BY sender_uuid, sender_name " +
                                 "ORDER BY total_xp DESC LIMIT ?")) {

                ps.setInt(1, days);
                ps.setInt(2, limit);

                List<String> rankings = new ArrayList<>();
                int position = 1;

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("sender_name");
                        int count = rs.getInt("transfer_count");
                        long totalXp = rs.getLong("total_xp");

                        String medal = position <= 3 ? getMedal(position) : ChatColor.GRAY + "#" + position;
                        rankings.add(String.format("%s %s %s- %s%,d XP %s(%d transferencias)",
                                medal, ChatColor.WHITE, name, ChatColor.YELLOW, totalXp,
                                ChatColor.GRAY, count));
                        position++;
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GOLD + "═══ Top " + limit + " Transferidores (" + days + " días) ═══");
                    if (rankings.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "No hay datos para mostrar.");
                    } else {
                        rankings.forEach(sender::sendMessage);
                    }
                });

            } catch (SQLException e) {
                plugin.getLogger().warning("Error consultando top transferencias: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Error al consultar el ranking."));
            }
        });
    }

    /**
     * Muestra estadísticas detalladas de un jugador específico
     */
    private void showPlayerStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /transferstats player <jugador>");
            return;
        }

        String targetName = args[1];
        String uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId().toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection()) {
                Map<String, Object> stats = new HashMap<>();

                // Estadísticas de envío
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as sent_count, COALESCE(SUM(amount), 0) as sent_xp, " +
                                "transfer_type, COUNT(*) as type_count FROM xp_transfers " +
                                "WHERE sender_uuid = ? GROUP BY transfer_type")) {
                    ps.setString(1, uuid);
                    ResultSet rs = ps.executeQuery();
                    long totalSent = 0;
                    int totalSentCount = 0;
                    while (rs.next()) {
                        String type = rs.getString("transfer_type");
                        long amount = rs.getLong("sent_xp");
                        int count = rs.getInt("type_count");
                        stats.put("sent_" + type.toLowerCase(), amount);
                        stats.put("sent_" + type.toLowerCase() + "_count", count);
                        totalSent += amount;
                        totalSentCount += count;
                    }
                    stats.put("total_sent", totalSent);
                    stats.put("total_sent_count", totalSentCount);
                }

                // Estadísticas de recepción
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as received_count, COALESCE(SUM(amount), 0) as received_xp " +
                                "FROM xp_transfers WHERE receiver_uuid = ?")) {
                    ps.setString(1, uuid);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        stats.put("total_received", rs.getLong("received_xp"));
                        stats.put("total_received_count", rs.getInt("received_count"));
                    }
                }

                // Límite diario y uso actual
                int dailyLimit = plugin.getXpTransferManager().getDailyLimit(
                        Bukkit.getPlayer(targetName) != null ? Bukkit.getPlayer(targetName) :
                                Bukkit.getOfflinePlayer(targetName).getPlayer());
                int usedToday = plugin.getXpTransferManager().getDailyTransferred(uuid);

                stats.put("daily_limit", dailyLimit);
                stats.put("used_today", usedToday);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GOLD + "═══ Estadísticas de " + targetName + " ═══");
                    sender.sendMessage(ChatColor.YELLOW + "Límite diario: " + ChatColor.WHITE +
                            (dailyLimit == -1 ? "Ilimitado" : String.format("%,d", dailyLimit)));
                    sender.sendMessage(ChatColor.YELLOW + "Usado hoy: " + ChatColor.WHITE +
                            String.format("%,d", usedToday));
                    sender.sendMessage("");
                    sender.sendMessage(ChatColor.GREEN + "▲ Enviado:");
                    sender.sendMessage(ChatColor.WHITE + "  Total: " + ChatColor.YELLOW +
                            String.format("%,d XP", (Long)stats.getOrDefault("total_sent", 0L)) +
                            ChatColor.GRAY + " (" + stats.getOrDefault("total_sent_count", 0) + " transferencias)");
                    sender.sendMessage(ChatColor.WHITE + "  Desde barra: " + ChatColor.AQUA +
                            String.format("%,d XP", (Long)stats.getOrDefault("sent_player", 0L)) +
                            ChatColor.GRAY + " (" + stats.getOrDefault("sent_player_count", 0) + ")");
                    sender.sendMessage(ChatColor.WHITE + "  Desde banco: " + ChatColor.LIGHT_PURPLE +
                            String.format("%,d XP", (Long)stats.getOrDefault("sent_bank", 0L)) +
                            ChatColor.GRAY + " (" + stats.getOrDefault("sent_bank_count", 0) + ")");
                    sender.sendMessage("");
                    sender.sendMessage(ChatColor.RED + "▼ Recibido:");
                    sender.sendMessage(ChatColor.WHITE + "  Total: " + ChatColor.YELLOW +
                            String.format("%,d XP", (Long)stats.getOrDefault("total_received", 0L)) +
                            ChatColor.GRAY + " (" + stats.getOrDefault("total_received_count", 0) + " transferencias)");
                });

            } catch (SQLException e) {
                plugin.getLogger().warning("Error consultando estadísticas del jugador: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Error al consultar las estadísticas del jugador."));
            }
        });
    }

    /**
     * Muestra estadísticas por día de los últimos días
     */
    private void showDailyStats(CommandSender sender, String[] args) {
        int days = args.length > 1 ? parseIntSafe(args[1], 7) : 7;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT transfer_date, COUNT(*) as daily_count, SUM(amount) as daily_xp " +
                                 "FROM xp_transfers " +
                                 "WHERE transfer_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                                 "GROUP BY transfer_date ORDER BY transfer_date DESC")) {

                ps.setInt(1, days);

                List<String> dailyStats = new ArrayList<>();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Date date = rs.getDate("transfer_date");
                        int count = rs.getInt("daily_count");
                        long xp = rs.getLong("daily_xp");

                        LocalDate localDate = date.toLocalDate();
                        String dateStr = localDate.format(formatter);

                        dailyStats.add(String.format("%s %s- %s%,d transferencias, %s%,d XP",
                                ChatColor.YELLOW, dateStr, ChatColor.WHITE, count, ChatColor.GOLD, xp));
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GOLD + "═══ Estadísticas Diarias (" + days + " días) ═══");
                    if (dailyStats.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "No hay transferencias en los últimos " + days + " días.");
                    } else {
                        dailyStats.forEach(sender::sendMessage);
                    }
                });

            } catch (SQLException e) {
                plugin.getLogger().warning("Error consultando estadísticas diarias: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Error al consultar las estadísticas diarias."));
            }
        });
    }

    /**
     * Muestra un resumen ejecutivo de las transferencias
     */
    private void showSummaryStats(CommandSender sender, String[] args) {
        int days = args.length > 1 ? parseIntSafe(args[1], 30) : 30;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection()) {
                Map<String, Object> summary = new HashMap<>();

                // Resumen por tipo de transferencia
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT transfer_type, COUNT(*) as count, SUM(amount) as total_xp, " +
                                "AVG(amount) as avg_xp, MIN(amount) as min_xp, MAX(amount) as max_xp " +
                                "FROM xp_transfers " +
                                "WHERE transfer_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                                "GROUP BY transfer_type")) {
                    ps.setInt(1, days);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        String type = rs.getString("transfer_type");
                        summary.put(type + "_count", rs.getInt("count"));
                        summary.put(type + "_total", rs.getLong("total_xp"));
                        summary.put(type + "_avg", rs.getDouble("avg_xp"));
                        summary.put(type + "_min", rs.getLong("min_xp"));
                        summary.put(type + "_max", rs.getLong("max_xp"));
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GOLD + "═══ Resumen de Transferencias (" + days + " días) ═══");

                    // Transferencias desde barra de jugador
                    if (summary.containsKey("PLAYER_count")) {
                        sender.sendMessage(ChatColor.GREEN + "Transferencias desde Barra:");
                        sender.sendMessage(String.format("  %sTotal: %s%,d transferencias, %s%,d XP",
                                ChatColor.GRAY, ChatColor.WHITE, (Integer)summary.get("PLAYER_count"),
                                ChatColor.YELLOW, (Long)summary.get("PLAYER_total")));
                        sender.sendMessage(String.format("  %sPromedio: %s%.1f XP %s| Rango: %s%,d - %,d XP",
                                ChatColor.GRAY, ChatColor.AQUA, (Double)summary.get("PLAYER_avg"),
                                ChatColor.GRAY, ChatColor.WHITE, (Long)summary.get("PLAYER_min"),
                                (Long)summary.get("PLAYER_max")));
                        sender.sendMessage("");
                    }

                    // Transferencias desde banco
                    if (summary.containsKey("BANK_count")) {
                        sender.sendMessage(ChatColor.LIGHT_PURPLE + "Transferencias desde Banco:");
                        sender.sendMessage(String.format("  %sTotal: %s%,d transferencias, %s%,d XP",
                                ChatColor.GRAY, ChatColor.WHITE, (Integer)summary.get("BANK_count"),
                                ChatColor.YELLOW, (Long)summary.get("BANK_total")));
                        sender.sendMessage(String.format("  %sPromedio: %s%.1f XP %s| Rango: %s%,d - %,d XP",
                                ChatColor.GRAY, ChatColor.AQUA, (Double)summary.get("BANK_avg"),
                                ChatColor.GRAY, ChatColor.WHITE, (Long)summary.get("BANK_min"),
                                (Long)summary.get("BANK_max")));
                    }
                });

            } catch (SQLException e) {
                plugin.getLogger().warning("Error consultando resumen: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Error al consultar el resumen."));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("top", "player", "daily", "summary").stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }

    private String getMedal(int position) {
        return switch (position) {
            case 1 -> ChatColor.GOLD + "🥇";
            case 2 -> ChatColor.GRAY + "🥈";
            case 3 -> ChatColor.YELLOW + "🥉";
            default -> ChatColor.GRAY + "#" + position;
        };
    }

    private int parseIntSafe(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Connection getConnection() throws SQLException {
        String type = plugin.getConfig().getString("database.type", "mysql");
        if (type.equalsIgnoreCase("mysql")) {
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String database = plugin.getConfig().getString("database.database", "survivalcore");
            String url = String.format("jdbc:mysql://%s:%d/%s?autoReconnect=true", host, port, database);
            return DriverManager.getConnection(url,
                    plugin.getConfig().getString("database.user"),
                    plugin.getConfig().getString("database.password"));
        } else {
            String url = String.format("jdbc:sqlite:%s/userdata.db", plugin.getDataFolder().getAbsolutePath());
            return DriverManager.getConnection(url);
        }
    }
}