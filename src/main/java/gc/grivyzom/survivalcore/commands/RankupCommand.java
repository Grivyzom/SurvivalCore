package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.*;
import gc.grivyzom.survivalcore.rankup.RankupManager.*;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comando principal del sistema de rankup.
 * Maneja /rankup, /prestige y /ranks con soporte completo para PlaceholderAPI
 *
 * @author Brocolitx
 * @version 1.1
 */
public class RankupCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final RankupManager rankupManager;

    public RankupCommand(Main plugin, RankupManager rankupManager) {
        this.plugin = plugin;
        this.rankupManager = rankupManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este comando.");
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        return switch (cmdName) {
            case "rankup" -> handleRankup(player, args);
            case "prestige" -> handlePrestige(player, args);
            case "ranks" -> handleRanks(player, args);
            default -> false;
        };
    }

    /**
     * Maneja el comando /rankup
     */
    private boolean handleRankup(Player player, String[] args) {
        if (args.length == 0) {
            // Intento de rankup
            attemptRankup(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "info", "information" -> showRankupInfo(player);
            case "progress", "progreso" -> showProgress(player);
            case "help", "ayuda" -> showRankupHelp(player);
            case "list", "lista" -> showRankList(player);
            case "placeholders", "ph" -> showPlaceholderInfo(player);
            case "debug" -> {
                if (!player.hasPermission("survivalcore.rankup.admin")) {
                    player.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
                    return true;
                }
                debugPlaceholders(player, args);
            }
            case "reload" -> {
                if (!player.hasPermission("survivalcore.rankup.admin")) {
                    player.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
                    return true;
                }
                reloadConfig(player);
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /rankup help para ver la ayuda.");
            }
        }
        return true;
    }

    /**
     * Maneja el comando /prestige
     */
    private boolean handlePrestige(Player player, String[] args) {
        if (!rankupManager.isPrestigeEnabled()) {
            player.sendMessage(ChatColor.RED + "El sistema de prestige está deshabilitado.");
            return true;
        }

        if (args.length == 0) {
            // Intento de prestige
            attemptPrestige(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "info", "information" -> showPrestigeInfo(player);
            case "help", "ayuda" -> showPrestigeHelp(player);
            case "list", "lista" -> showPrestigeList(player);
            default -> {
                player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /prestige help para ver la ayuda.");
            }
        }
        return true;
    }

    /**
     * Maneja el comando /ranks
     */
    private boolean handleRanks(Player player, String[] args) {
        if (args.length == 0) {
            RankupGUI.openMainMenu(player, plugin);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "gui", "menu" -> RankupGUI.openMainMenu(player, plugin);
            case "list", "lista" -> showRankList(player);
            case "top", "leaderboard" -> showTopRanks(player);
            case "history", "historial" -> showRankupHistory(player);
            default -> {
                player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /ranks help para ver la ayuda.");
            }
        }
        return true;
    }

    /**
     * Intenta hacer rankup al jugador
     */
    private void attemptRankup(Player player) {
        rankupManager.attemptRankup(player).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    // ✅ MENSAJE DE ÉXITO MEJORADO
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "✅ " + ChatColor.BOLD + "¡RANKUP EXITOSO!");
                    player.sendMessage(ChatColor.GRAY + "▶ " + result.getMessage());
                    player.sendMessage(ChatColor.YELLOW + "💡 Usa /rankup progress para ver tu siguiente objetivo");
                    player.sendMessage("");
                } else {
                    // ❌ MENSAJE DE ERROR MEJORADO
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "RANKUP NO DISPONIBLE");
                    player.sendMessage(ChatColor.GRAY + "▶ " + result.getMessage());
                    player.sendMessage(ChatColor.YELLOW + "💡 Usa /rankup progress para ver qué te falta");
                    player.sendMessage("");
                }
            });
        });
    }

    /**
     * Intenta hacer prestige al jugador
     */
    private void attemptPrestige(Player player) {
        // TODO: Implementar lógica de prestige
        player.sendMessage(ChatColor.YELLOW + "Sistema de prestige en desarrollo...");
    }

    /**
     * Muestra información del rankup actual
     */
    private void showRankupInfo(Player player) {
        String currentRank = rankupManager.getCurrentRank(player);
        if (currentRank == null) {
            player.sendMessage(ChatColor.RED + "❌ No se pudo determinar tu rango actual");
            return;
        }

        Map<String, RankupData> rankups = rankupManager.getRankups();
        RankupData rankupData = rankups.get(currentRank);

        if (rankupData == null) {
            player.sendMessage(ChatColor.RED + "❌ No hay información para tu rango actual");
            return;
        }

        // ℹ️ INFORMACIÓN MEJORADA
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "ℹ️ " + ChatColor.BOLD + "INFORMACIÓN DE RANGO");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "🎯 Rango actual: " + rankupData.getDisplayName());

        if (rankupData.hasNextRank()) {
            RankupData nextRankData = rankups.get(rankupData.getNextRank());
            String nextRankDisplay = nextRankData != null ? nextRankData.getDisplayName() : rankupData.getNextRank();
            player.sendMessage(ChatColor.WHITE + "⬆️ Siguiente rango: " + nextRankDisplay);
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 ¡Has alcanzado el rango máximo!");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 Comandos útiles:");
        player.sendMessage(ChatColor.GRAY + "  • /rankup progress - Ver tu progreso");
        player.sendMessage(ChatColor.GRAY + "  • /ranks - Abrir menú de rangos");
        player.sendMessage("");
    }

    /**
     * Muestra el progreso del jugador con soporte mejorado para placeholders
     */
    private void showProgress(Player player) {
        rankupManager.getPlayerProgress(player).thenAccept(progress -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (progress.getCurrentRank() == null) {
                    player.sendMessage(ChatColor.RED + "❌ No se pudo obtener tu información de rango");
                    return;
                }

                // 📊 PROGRESO MEJORADO
                player.sendMessage("");
                player.sendMessage(ChatColor.AQUA + "📊 " + ChatColor.BOLD + "TU PROGRESO DE RANKUP");
                player.sendMessage("");

                if (progress.getNextRank() == null) {
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 ¡Has alcanzado el rango máximo!");
                    player.sendMessage("");
                    return;
                }

                // Progreso general más limpio
                String progressBar = createProgressBar(progress.getOverallProgress(), 15);
                player.sendMessage(ChatColor.WHITE + "Progreso: " + ChatColor.YELLOW +
                        String.format("%.1f%%", progress.getOverallProgress()));
                player.sendMessage(progressBar);
                player.sendMessage("");

                // Requisitos más compactos
                player.sendMessage(ChatColor.GRAY + "Requisitos:");
                for (Map.Entry<String, RequirementProgress> entry : progress.getRequirements().entrySet()) {
                    RequirementProgress reqProgress = entry.getValue();
                    String status = reqProgress.isCompleted() ?
                            ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                    String reqName = formatRequirementName(reqProgress.getType());

                    player.sendMessage(ChatColor.GRAY + "  " + status + " " +
                            ChatColor.WHITE + reqName + ": " +
                            ChatColor.YELLOW + formatRequirementValue(reqProgress));
                }
                player.sendMessage("");
            });
        });
    }

    /**
     * Muestra información sobre placeholders disponibles
     */
    private void showPlaceholderInfo(Player player) {
        if (!rankupManager.isPlaceholderAPIEnabled()) {
            player.sendMessage(ChatColor.RED + "PlaceholderAPI no está disponible en este servidor.");
            player.sendMessage(ChatColor.GRAY + "Los requisitos de placeholder no funcionarán sin PlaceholderAPI.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "═══ Información de Placeholders ═══");
        player.sendMessage(ChatColor.WHITE + "PlaceholderAPI está " + ChatColor.GREEN + "ACTIVO" + ChatColor.WHITE + " en este servidor.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Formato de placeholders en rankups:");
        player.sendMessage(ChatColor.GRAY + "placeholder_nombre: \"placeholder:valor_requerido\"");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Ejemplos comunes:");
        player.sendMessage(ChatColor.GRAY + "• placeholder_mob_kills: \"%statistic_mob_kills%:100\"");
        player.sendMessage(ChatColor.GRAY + "• placeholder_blocks_mined: \"%statistic_mine_block%:500\"");
        player.sendMessage(ChatColor.GRAY + "• placeholder_player_level: \"%player_level%:30\"");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Para debug: " + ChatColor.WHITE + "/rankup debug <placeholder>");
        player.sendMessage(ChatColor.GRAY + "Ejemplo: /rankup debug %statistic_mob_kills%");
    }

    /**
     * Función de debug para placeholders (solo admins) - MEJORADA
     */
    private void debugPlaceholders(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /rankup debug <placeholder|player>");
            player.sendMessage(ChatColor.GRAY + "Ejemplos:");
            player.sendMessage(ChatColor.GRAY + "  /rankup debug %statistic_mob_kills%");
            player.sendMessage(ChatColor.GRAY + "  /rankup debug player <nombre> - Debug completo del jugador");
            return;
        }

        // Debug de jugador específico
        if (args[1].equalsIgnoreCase("player")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Uso: /rankup debug player <nombre>");
                return;
            }

            Player targetPlayer = plugin.getServer().getPlayer(args[2]);
            if (targetPlayer == null) {
                player.sendMessage(ChatColor.RED + "Jugador no encontrado: " + args[2]);
                return;
            }

            rankupManager.debugPlayerRankup(targetPlayer, player);
            return;
        }

        // Debug de placeholder
        if (!rankupManager.isPlaceholderAPIEnabled()) {
            player.sendMessage(ChatColor.RED + "PlaceholderAPI no está disponible.");
            return;
        }

        String placeholder = args[1];
        if (!placeholder.startsWith("%") || !placeholder.endsWith("%")) {
            placeholder = "%" + placeholder + "%";
        }

        try {
            String result = PlaceholderAPI.setPlaceholders(player, placeholder);

            player.sendMessage(ChatColor.AQUA + "═══ Debug de Placeholder ═══");
            player.sendMessage(ChatColor.WHITE + "Placeholder: " + ChatColor.YELLOW + placeholder);
            player.sendMessage(ChatColor.WHITE + "Resultado: " + ChatColor.GREEN + result);

            // Intentar parsearlo como número
            try {
                double numericValue = Double.parseDouble(result.replaceAll("[^0-9.-]", ""));
                player.sendMessage(ChatColor.WHITE + "Valor numérico: " + ChatColor.YELLOW + numericValue);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "No se pudo convertir a número.");
            }

            // Verificar si el placeholder se resolvió
            if (result.equals(placeholder)) {
                player.sendMessage(ChatColor.RED + "⚠ El placeholder no se resolvió (devolvió el mismo texto)");
            } else {
                player.sendMessage(ChatColor.GREEN + "✓ Placeholder resuelto correctamente");
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error procesando placeholder: " + e.getMessage());
        }
    }

    /**
     * Crea una barra de progreso visual
     */
    private String createProgressBar(double percentage, int length) {
        int filled = (int) Math.ceil(percentage / 100.0 * length);
        StringBuilder bar = new StringBuilder();

        bar.append(ChatColor.GREEN);
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        bar.append(ChatColor.GRAY);
        for (int i = filled; i < length; i++) {
            bar.append("█");
        }

        return bar.toString();
    }

    /**
     * Formatea el nombre de un requisito - MEJORADO para placeholders
     */
    private String formatRequirementName(String type) {
        if (type.startsWith("placeholder_")) {
            // Convertir "placeholder_mob_kills" a "Mob Kills"
            String name = type.replace("placeholder_", "").replace("_", " ");
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }

        return switch (type.toLowerCase()) {
            case "money", "eco", "economy" -> "Dinero";
            case "xp", "experience" -> "Experiencia";
            case "level", "levels" -> "Nivel";
            case "playtime", "time_played" -> "Tiempo jugado";
            case "farming_level" -> "Nivel de granjería";
            case "mining_level" -> "Nivel de minería";
            case "kills", "mob_kills" -> "Kills";
            case "blocks_broken" -> "Bloques rotos";
            case "permission" -> "Permiso";
            default -> type;
        };
    }

    /**
     * Formatea el valor de un requisito - MEJORADO para placeholders
     */
    private String formatRequirementValue(RequirementProgress progress) {
        String type = progress.getType().toLowerCase();
        double current = progress.getCurrent();
        double required = progress.getRequired();

        // Manejar placeholders
        if (type.startsWith("placeholder_")) {
            return String.format("%.0f/%.0f", current, required);
        }

        // Requisitos tradicionales
        return switch (type) {
            case "money", "eco", "economy" -> String.format("$%,.0f/$%,.0f", current, required);
            case "xp", "experience" -> String.format("%,.0f/%,.0f XP", current, required);
            case "level", "levels" -> String.format("%.0f/%.0f", current, required);
            case "playtime", "time_played" -> String.format("%.0f/%.0f horas", current, required);
            case "farming_level", "mining_level" -> String.format("%.0f/%.0f", current, required);
            case "kills", "mob_kills", "blocks_broken" -> String.format("%,.0f/%,.0f", current, required);
            default -> String.format("%.0f/%.0f", current, required);
        };
    }

    /**
     * Muestra ayuda del comando rankup - ACTUALIZADA
     */
    private void showRankupHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "📖 " + ChatColor.BOLD + "AYUDA DE RANKUP");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "📌 Comandos básicos:");
        player.sendMessage(ChatColor.WHITE + "  /rankup " + ChatColor.GRAY + "- Subir de rango");
        player.sendMessage(ChatColor.WHITE + "  /rankup info " + ChatColor.GRAY + "- Info de tu rango");
        player.sendMessage(ChatColor.WHITE + "  /rankup progress " + ChatColor.GRAY + "- Ver progreso");
        player.sendMessage(ChatColor.WHITE + "  /ranks " + ChatColor.GRAY + "- Menú interactivo");
        player.sendMessage("");

        if (player.hasPermission("survivalcore.rankup.admin")) {
            player.sendMessage(ChatColor.RED + "🔧 Comandos de Admin:");
            player.sendMessage(ChatColor.WHITE + "  /rankup debug <placeholder> " + ChatColor.GRAY + "- Debug");
            player.sendMessage(ChatColor.WHITE + "  /rankup reload " + ChatColor.GRAY + "- Recargar config");
            player.sendMessage("");
        }
    }

    /**
     * Muestra información de prestige
     */
    private void showPrestigeInfo(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Sistema de prestige en desarrollo...");
    }

    /**
     * Muestra ayuda de prestige
     */
    private void showPrestigeHelp(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Ayuda de prestige en desarrollo...");
    }

    /**
     * Muestra lista de prestiges
     */
    private void showPrestigeList(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Lista de prestiges en desarrollo...");
    }

    /**
     * Muestra lista de rangos con información de placeholders
     */
    private void showRankList(Player player) {
        Map<String, RankupData> rankups = rankupManager.getRankups();
        if (rankups.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No hay rangos configurados.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "═══ Lista de Rangos ═══");

        List<RankupData> sortedRanks = rankups.values().stream()
                .sorted(Comparator.comparingInt(RankupData::getOrder))
                .collect(Collectors.toList());

        String currentRank = rankupManager.getCurrentRank(player);
        for (RankupData rank : sortedRanks) {
            String marker = rank.getRankId().equals(currentRank) ? ChatColor.GREEN + "► " : ChatColor.GRAY + "  ";

            // Contar placeholders en requisitos
            long placeholderCount = rank.getRequirements().entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("placeholder_"))
                    .count();

            String placeholderInfo = placeholderCount > 0 ?
                    ChatColor.AQUA + " [" + placeholderCount + " placeholders]" : "";

            player.sendMessage(marker + rank.getDisplayName() + ChatColor.GRAY + " (Orden: " +
                    rank.getOrder() + ")" + placeholderInfo);
        }

        if (rankupManager.isPlaceholderAPIEnabled()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.YELLOW + "/rankup placeholders" +
                    ChatColor.GRAY + " para más información sobre placeholders.");
        }
    }

    /**
     * Muestra top de rangos
     */
    private void showTopRanks(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Top de rangos en desarrollo...");
    }

    /**
     * Muestra historial de rankups
     */
    private void showRankupHistory(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Historial de rankups en desarrollo...");
    }

    /**
     * Recarga la configuración
     */
    private void reloadConfig(Player player) {
        try {
            rankupManager.reloadConfig();
            player.sendMessage(ChatColor.GREEN + "Configuración de rankup recargada correctamente.");

            if (rankupManager.isPlaceholderAPIEnabled()) {
                player.sendMessage(ChatColor.GREEN + "PlaceholderAPI está disponible y activo.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "PlaceholderAPI no está disponible - Los placeholders no funcionarán.");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error al recargar la configuración: " + e.getMessage());
            plugin.getLogger().severe("Error recargando configuración de rankup: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String cmdName = command.getName().toLowerCase();
            List<String> completions = new ArrayList<>();

            switch (cmdName) {
                case "rankup" -> {
                    completions.addAll(Arrays.asList("info", "progress", "help", "list", "placeholders"));
                    if (sender.hasPermission("survivalcore.rankup.admin")) {
                        completions.addAll(Arrays.asList("reload", "debug"));
                    }
                }
                case "prestige" -> completions.addAll(Arrays.asList("info", "help", "list"));
                case "ranks" -> completions.addAll(Arrays.asList("gui", "list", "top", "history"));
            }

            return completions.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para debug
        if (args.length == 2 && args[0].equalsIgnoreCase("debug") &&
                sender.hasPermission("survivalcore.rankup.admin")) {

            List<String> options = new ArrayList<>();
            options.add("player");

            // Añadir placeholders comunes
            options.addAll(Arrays.asList(
                    "%statistic_mob_kills%",
                    "%statistic_mine_block%",
                    "%statistic_player_kills%",
                    "%statistic_deaths%",
                    "%player_level%",
                    "%player_health%",
                    "%statistic_walk_one_cm%",
                    "%statistic_animals_bred%",
                    "%statistic_fish_caught%"
            ));

            return options.stream()
                    .filter(option -> option.toLowerCase().contains(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para nombres de jugadores en debug
        if (args.length == 3 && args[0].equalsIgnoreCase("debug") &&
                args[1].equalsIgnoreCase("player") &&
                sender.hasPermission("survivalcore.rankup.admin")) {

            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}