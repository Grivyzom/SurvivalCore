package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.util.RepairStatistics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comando administrativo para el sistema de reparación
 *
 * @author Brocolitx
 * @version 1.0
 */
public class RepairAdminCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final RepairStatistics repairStats;
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public RepairAdminCommand(Main plugin) {
        this.plugin = plugin;
        this.repairStats = new RepairStatistics(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survivalcore.repair.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ No tienes permisos para usar comandos administrativos de reparación.");
            return true;
        }

        if (args.length == 0) {
            showAdminHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "stats", "estadisticas" -> {
                return handleStats(sender, args);
            }
            case "config", "configuracion" -> {
                return handleConfig(sender, args);
            }
            case "test", "prueba" -> {
                return handleTest(sender, args);
            }
            case "reset" -> {
                return handleReset(sender, args);
            }
            case "reload" -> {
                return handleReload(sender);
            }
            case "help", "ayuda" -> {
                showAdminHelp(sender);
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "❌ Subcomando desconocido. Usa " +
                        ChatColor.YELLOW + "/repairanim help" + ChatColor.RED + " para ver la ayuda.");
                return true;
            }
        }
    }

    /**
     * Maneja comandos de estadísticas
     */
    private boolean handleStats(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Mostrar estadísticas globales
            showGlobalStats(sender);
            return true;
        }

        String subAction = args[1].toLowerCase();

        switch (subAction) {
            case "global", "servidor" -> {
                showGlobalStats(sender);
                return true;
            }
            case "player", "jugador" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "❌ Uso: /repairanim stats player <jugador>");
                    return true;
                }
                return showPlayerStats(sender, args[2]);
            }
            case "top", "ranking" -> {
                showTopPlayers(sender);
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "❌ Opción de estadísticas desconocida: " + subAction);
                return true;
            }
        }
    }

    /**
     * Maneja comandos de configuración
     */
    private boolean handleConfig(CommandSender sender, String[] args) {
        if (args.length == 1) {
            showCurrentConfig(sender);
            return true;
        }

        String subAction = args[1].toLowerCase();

        switch (subAction) {
            case "show", "mostrar" -> {
                showCurrentConfig(sender);
                return true;
            }
            case "set", "establecer" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "❌ Uso: /repairanim config set <clave> <valor>");
                    return true;
                }
                return setConfigValue(sender, args[2], args[3]);
            }
            case "reset" -> {
                return resetConfig(sender);
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "❌ Opción de configuración desconocida: " + subAction);
                return true;
            }
        }
    }

    /**
     * Maneja comandos de prueba
     */
    private boolean handleTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "❌ Solo los jugadores pueden usar comandos de prueba.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ Uso: /repairanim test <costo|algoritmo|multiplicadores>");
            return true;
        }

        String testType = args[1].toLowerCase();

        switch (testType) {
            case "costo", "cost" -> {
                return testCostCalculation(player);
            }
            case "algoritmo", "algorithm" -> {
                return testAlgorithm(player);
            }
            case "multiplicadores", "multipliers" -> {
                return testMultipliers(player);
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "❌ Tipo de prueba desconocido: " + testType);
                return true;
            }
        }
    }

    /**
     * Maneja comandos de reset
     */
    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ Uso: /repairanim reset <player|all> [jugador]");
            return true;
        }

        String resetType = args[1].toLowerCase();

        switch (resetType) {
            case "player", "jugador" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "❌ Uso: /repairanim reset player <jugador>");
                    return true;
                }
                return resetPlayerStats(sender, args[2]);
            }
            case "all", "todos" -> {
                return resetAllStats(sender);
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "❌ Tipo de reset desconocido: " + resetType);
                return true;
            }
        }
    }

    /**
     * Recarga la configuración
     */
    private boolean handleReload(CommandSender sender) {
        try {
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "✅ Configuración del sistema de reparación recargada exitosamente.");

            // Mostrar valores clave actualizados
            sender.sendMessage(ChatColor.GRAY + "• Multiplicador base: " +
                    plugin.getConfig().getDouble("repair.base_cost_multiplier", 0.05));
            sender.sendMessage(ChatColor.GRAY + "• Costo máximo: " +
                    plugin.getConfig().getInt("repair.max_cost_per_item", 75));
            sender.sendMessage(ChatColor.GRAY + "• Cooldown: " +
                    plugin.getConfig().getLong("repair.cooldown", 3000) + "ms");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Error al recargar la configuración: " + e.getMessage());
        }

        return true;
    }

    /**
     * Muestra estadísticas globales del servidor
     */
    private void showGlobalStats(CommandSender sender) {
        RepairStatistics.GlobalRepairStats stats = repairStats.getGlobalStats();

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "📊 ESTADÍSTICAS GLOBALES DE REPARACIÓN" + ChatColor.GOLD + " ║");
        sender.sendMessage(ChatColor.GOLD + "╠════════════════════════════════════════╣");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Jugadores activos: " +
                ChatColor.AQUA + stats.activePlayers);
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Total reparaciones: " +
                ChatColor.GREEN + stats.totalRepairs);
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "XP total gastada: " +
                ChatColor.YELLOW + stats.totalXpSpent + " niveles");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Ítems reparados: " +
                ChatColor.LIGHT_PURPLE + stats.totalItemsRepaired);
        sender.sendMessage(ChatColor.GOLD + "║                                        ║");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "📈 PROMEDIOS:" + ChatColor.GOLD + "                        ║");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Reparaciones/jugador: " +
                ChatColor.AQUA + decimalFormat.format(stats.getAverageRepairsPerPlayer()));
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "XP/reparación: " +
                ChatColor.YELLOW + decimalFormat.format(stats.getAverageXpPerRepair()));
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Ítems/reparación: " +
                ChatColor.LIGHT_PURPLE + decimalFormat.format(stats.getAverageItemsPerRepair()));
        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
    }

    /**
     * Muestra estadísticas de un jugador específico
     */
    private boolean showPlayerStats(CommandSender sender, String playerName) {
        Player target = plugin.getServer().getPlayer(playerName);
        UUID playerId;

        if (target != null) {
            playerId = target.getUniqueId();
        } else {
            // Intentar obtener UUID desde la base de datos o cache
            sender.sendMessage(ChatColor.RED + "❌ Jugador no encontrado o no está online.");
            return true;
        }

        RepairStatistics.PlayerRepairStats stats = repairStats.getPlayerStats(playerId);

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "👤 ESTADÍSTICAS DE " + playerName.toUpperCase() + ChatColor.GOLD + "           ║");
        sender.sendMessage(ChatColor.GOLD + "╠════════════════════════════════════════╣");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Total reparaciones: " +
                ChatColor.GREEN + stats.totalRepairs);
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "XP gastada: " +
                ChatColor.YELLOW + stats.totalXpSpent + " niveles");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Ítems reparados: " +
                ChatColor.LIGHT_PURPLE + stats.itemsRepaired);
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Material favorito: " +
                ChatColor.AQUA + formatMaterialName(stats.favoriteMaterial));

        if (stats.lastRepair > 0) {
            sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Última reparación: " +
                    ChatColor.GRAY + dateFormat.format(new Date(stats.lastRepair)));
        }

        sender.sendMessage(ChatColor.GOLD + "║                                        ║");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "📊 ANÁLISIS:" + ChatColor.GOLD + "                         ║");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "XP promedio/reparación: " +
                ChatColor.YELLOW + decimalFormat.format(stats.getAverageXpPerRepair()));
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Ítems promedio/reparación: " +
                ChatColor.LIGHT_PURPLE + decimalFormat.format(stats.getAverageItemsPerRepair()));
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Reparaciones gratuitas: " +
                ChatColor.GREEN + stats.getFreeRepairPercentage() + "%");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Reparaciones con descuento: " +
                ChatColor.AQUA + stats.getDiscountUsagePercentage() + "%");
        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");

        return true;
    }

    /**
     * Muestra el top de jugadores con más reparaciones
     */
    private void showTopPlayers(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "🏆 TOP REPARADORES DEL SERVIDOR:");
        sender.sendMessage(ChatColor.GRAY + "Esta función requiere implementación de ranking en base de datos.");
        // TODO: Implementar query para obtener top players
    }

    /**
     * Muestra la configuración actual
     */
    private void showCurrentConfig(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "⚙️ CONFIGURACIÓN ACTUAL" + ChatColor.GOLD + "              ║");
        sender.sendMessage(ChatColor.GOLD + "╠════════════════════════════════════════╣");

        // Configuración básica
        double baseCost = plugin.getConfig().getDouble("repair.base_cost_multiplier", 0.05);
        double enchantMult = plugin.getConfig().getDouble("repair.enchantment_multiplier", 0.15);
        double mendingMult = plugin.getConfig().getDouble("repair.mending_extra_multiplier", 0.20);
        int minCost = plugin.getConfig().getInt("repair.min_cost", 1);
        int maxCost = plugin.getConfig().getInt("repair.max_cost_per_item", 75);
        long cooldown = plugin.getConfig().getLong("repair.cooldown", 3000);

        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Multiplicador base: " +
                ChatColor.YELLOW + (baseCost * 100) + "%");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Mult. encantamientos: " +
                ChatColor.YELLOW + (enchantMult * 100) + "%");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Mult. Mending extra: " +
                ChatColor.YELLOW + (mendingMult * 100) + "%");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Costo mínimo: " +
                ChatColor.GREEN + minCost + " niveles");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Costo máximo: " +
                ChatColor.RED + maxCost + " niveles");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Cooldown: " +
                ChatColor.AQUA + (cooldown / 1000.0) + " segundos");

        sender.sendMessage(ChatColor.GOLD + "║                                        ║");
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "🔧 CARACTERÍSTICAS:" + ChatColor.GOLD + "                 ║");

        boolean particlesEnabled = plugin.getConfig().getBoolean("repair.effects.particles.enabled", true);
        boolean statsEnabled = plugin.getConfig().getBoolean("repair_advanced.statistics.track_player_stats", false);
        boolean dbEnabled = plugin.getConfig().getBoolean("repair_advanced.statistics.save_to_database", false);

        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Partículas: " +
                (particlesEnabled ? ChatColor.GREEN + "Habilitadas" : ChatColor.RED + "Deshabilitadas"));
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Estadísticas: " +
                (statsEnabled ? ChatColor.GREEN + "Habilitadas" : ChatColor.RED + "Deshabilitadas"));
        sender.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Base de datos: " +
                (dbEnabled ? ChatColor.GREEN + "Habilitada" : ChatColor.RED + "Deshabilitada"));

        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
    }

    /**
     * Establece un valor de configuración
     */
    private boolean setConfigValue(CommandSender sender, String key, String value) {
        try {
            // Validar clave
            if (!isValidConfigKey(key)) {
                sender.sendMessage(ChatColor.RED + "❌ Clave de configuración inválida: " + key);
                showValidConfigKeys(sender);
                return true;
            }

            // Convertir y establecer valor según el tipo
            Object convertedValue = convertConfigValue(key, value);
            if (convertedValue == null) {
                sender.sendMessage(ChatColor.RED + "❌ Valor inválido para la clave " + key + ": " + value);
                return true;
            }

            plugin.getConfig().set("repair." + key, convertedValue);
            plugin.saveConfig();

            sender.sendMessage(ChatColor.GREEN + "✅ Configuración actualizada:");
            sender.sendMessage(ChatColor.WHITE + "  " + key + " = " + ChatColor.YELLOW + convertedValue);
            sender.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/repairanim reload" +
                    ChatColor.GRAY + " para aplicar los cambios.");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Error estableciendo configuración: " + e.getMessage());
        }

        return true;
    }

    /**
     * Prueba el cálculo de costos para el ítem en mano
     */
    private boolean testCostCalculation(Player player) {
        // Esta sería una implementación simplificada
        // En la implementación real, integrarías con el RepairCommand

        player.sendMessage(ChatColor.YELLOW + "🧪 Ejecutando prueba de cálculo de costos...");
        player.sendMessage(ChatColor.GRAY + "Esta función requiere integración con RepairCommand.");

        return true;
    }

    /**
     * Prueba diferentes algoritmos de cálculo
     */
    private boolean testAlgorithm(Player player) {
        player.sendMessage(ChatColor.YELLOW + "🧪 Probando algoritmos de cálculo...");
        player.sendMessage(ChatColor.GRAY + "Función de prueba para comparar algoritmos.");

        return true;
    }

    /**
     * Prueba los multiplicadores de material
     */
    private boolean testMultipliers(Player player) {
        player.sendMessage(ChatColor.YELLOW + "🧪 Probando multiplicadores de material...");

        var materialMults = plugin.getConfig().getConfigurationSection("repair.material_multipliers");
        if (materialMults != null) {
            player.sendMessage(ChatColor.AQUA + "Multiplicadores configurados:");
            for (String material : materialMults.getKeys(false)) {
                double mult = materialMults.getDouble(material);
                player.sendMessage(ChatColor.WHITE + "  " + material + ": " +
                        ChatColor.YELLOW + "x" + mult);
            }
        }

        return true;
    }

    /**
     * Resetea las estadísticas de un jugador
     */
    private boolean resetPlayerStats(CommandSender sender, String playerName) {
        Player target = plugin.getServer().getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ Jugador no encontrado: " + playerName);
            return true;
        }

        repairStats.resetPlayerStats(target.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "✅ Estadísticas de reparación reseteadas para " + playerName);

        return true;
    }

    /**
     * Resetea todas las estadísticas
     */
    private boolean resetAllStats(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "⚠️ Esta acción reseteará TODAS las estadísticas de reparación.");
        sender.sendMessage(ChatColor.RED + "Esta función requiere confirmación adicional para evitar pérdida de datos.");

        return true;
    }

    /**
     * Muestra la ayuda administrativa
     */
    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "    COMANDOS ADMINISTRATIVOS - REPARACIÓN");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "📊 ESTADÍSTICAS:");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim stats global" + ChatColor.GRAY + " - Estadísticas del servidor");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim stats player <jugador>" + ChatColor.GRAY + " - Stats de un jugador");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim stats top" + ChatColor.GRAY + " - Top de jugadores");

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "⚙️ CONFIGURACIÓN:");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim config show" + ChatColor.GRAY + " - Mostrar configuración actual");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim config set <clave> <valor>" + ChatColor.GRAY + " - Cambiar valor");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim reload" + ChatColor.GRAY + " - Recargar configuración");

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "🧪 PRUEBAS:");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim test costo" + ChatColor.GRAY + " - Probar cálculo de costos");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim test multiplicadores" + ChatColor.GRAY + " - Ver multiplicadores");

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "🗑️ GESTIÓN:");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim reset player <jugador>" + ChatColor.GRAY + " - Reset stats jugador");
        sender.sendMessage(ChatColor.WHITE + "  /repairanim reset all" + ChatColor.GRAY + " - Reset todas las stats");

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
    }

    // =================== MÉTODOS AUXILIARES ===================

    private boolean isValidConfigKey(String key) {
        List<String> validKeys = Arrays.asList(
                "base_cost_multiplier", "enchantment_multiplier", "mending_extra_multiplier",
                "min_cost", "max_cost_per_item", "cooldown"
        );
        return validKeys.contains(key);
    }

    private void showValidConfigKeys(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Claves válidas:");
        sender.sendMessage(ChatColor.GRAY + "base_cost_multiplier, enchantment_multiplier, mending_extra_multiplier");
        sender.sendMessage(ChatColor.GRAY + "min_cost, max_cost_per_item, cooldown");
    }

    private Object convertConfigValue(String key, String value) {
        try {
            switch (key) {
                case "base_cost_multiplier", "enchantment_multiplier", "mending_extra_multiplier":
                    return Double.parseDouble(value);
                case "min_cost", "max_cost_per_item":
                    return Integer.parseInt(value);
                case "cooldown":
                    return Long.parseLong(value);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean resetConfig(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Reset de configuración no implementado por seguridad.");
        return true;
    }

    private String formatMaterialName(String material) {
        if (material == null || material.equals("UNKNOWN")) {
            return "Ninguno";
        }
        return Arrays.stream(material.split("_"))
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("survivalcore.repair.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("stats", "config", "test", "reset", "reload", "help")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "stats":
                    return Arrays.asList("global", "player", "top")
                            .stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "config":
                    return Arrays.asList("show", "set", "reset")
                            .stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "test":
                    return Arrays.asList("costo", "algoritmo", "multiplicadores")
                            .stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "reset":
                    return Arrays.asList("player", "all")
                            .stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("player")) {
                // Tab completion para nombres de jugadores online
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("set")) {
                // Tab completion para claves de configuración
                return Arrays.asList("base_cost_multiplier", "enchantment_multiplier",
                                "mending_extra_multiplier", "min_cost", "max_cost_per_item", "cooldown")
                        .stream()
                        .filter(key -> key.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("player")) {
                // Tab completion para nombres de jugadores online
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}