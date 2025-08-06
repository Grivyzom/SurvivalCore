package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.*;
import gc.grivyzom.survivalcore.rankup.RankupManager.*;
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
 * Comando de rankup simplificado - Versión 2.0
 * Más intuitivo y fácil de usar
 *
 * @author Brocolitx
 * @version 2.0
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
     * Maneja el comando /rankup con sintaxis simplificada
     */
    private boolean handleRankup(Player player, String[] args) {
        if (args.length == 0) {
            // Intento de rankup
            attemptRankup(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "info", "i" -> showRankInfo(player);
            case "progress", "p", "progreso" -> showProgress(player);
            case "help", "h", "ayuda" -> showHelp(player);
            case "list", "l", "lista" -> showRankList(player);
            case "debug", "d" -> {
                if (!player.hasPermission("survivalcore.rankup.admin")) {
                    player.sendMessage(ChatColor.RED + "❌ Sin permisos para debug");
                    return true;
                }
                handleDebug(player, args);
            }
            case "reload", "r" -> {
                if (!player.hasPermission("survivalcore.rankup.admin")) {
                    player.sendMessage(ChatColor.RED + "❌ Sin permisos para reload");
                    return true;
                }
                reloadConfig(player);
            }
            default -> {
                player.sendMessage(ChatColor.RED + "❌ Subcomando desconocido. Usa §e/rankup help");
            }
        }
        return true;
    }

    /**
     * Maneja el comando /prestige (simplificado)
     */
    private boolean handlePrestige(Player player, String[] args) {
        if (!rankupManager.isPrestigeEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ El sistema de prestige está deshabilitado.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "🚧 Sistema de prestige en desarrollo...");
        return true;
    }

    /**
     * Maneja el comando /ranks
     */
    private boolean handleRanks(Player player, String[] args) {
        if (args.length == 0) {
            // Abrir GUI principal
            RankupGUI.openMainMenu(player, plugin);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "gui", "menu" -> RankupGUI.openMainMenu(player, plugin);
            case "list", "lista" -> showRankList(player);
            case "top", "leaderboard" -> showTopRanks(player);
            default -> {
                player.sendMessage(ChatColor.RED + "❌ Subcomando desconocido para /ranks");
            }
        }
        return true;
    }

    /**
     * Intenta hacer rankup con mensajes mejorados
     */
    private void attemptRankup(Player player) {
        // Verificar cooldown inmediatamente para dar feedback rápido
        if (rankupManager.isOnCooldown(player.getUniqueId())) {
            long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
            rankupManager.getMessageManager().sendCooldownMessage(player, remaining / 1000);
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "🔄 Verificando requisitos...");

        // El resto lo maneja RankupManager.attemptRankup() con mensajes personalizables
        rankupManager.attemptRankup(player);
    }

    /**
     * Muestra información del rango actual (simplificada)
     */
    private void showRankInfo(Player player) {
        String currentRank = rankupManager.getCurrentRank(player);
        if (currentRank == null) {
            player.sendMessage(ChatColor.RED + "❌ No se pudo determinar tu rango actual");
            return;
        }

        Map<String, RankupManager.SimpleRankData> rankups = rankupManager.getRanks();
        RankupManager.SimpleRankData rankData = rankups.get(currentRank);

        if (rankData == null) {
            player.sendMessage(ChatColor.RED + "❌ No hay información para tu rango");
            return;
        }

        // 📋 INFO COMPACTA Y CLARA
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "📋 " + ChatColor.BOLD + "INFORMACIÓN DE RANGO");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "🎯 Rango actual: " + rankData.getDisplayName());
        player.sendMessage(ChatColor.WHITE + "📊 Orden: " + ChatColor.YELLOW + rankData.getOrder());

        if (rankData.hasNextRank()) {
            RankupManager.SimpleRankData nextRankData = rankups.get(rankData.getNextRank());
            String nextDisplay = nextRankData != null ? nextRankData.getDisplayName() : rankData.getNextRank();
            player.sendMessage(ChatColor.WHITE + "⬆️ Siguiente: " + nextDisplay);
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 ¡Rango máximo alcanzado!");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 Comandos:");
        player.sendMessage(ChatColor.GRAY + "  • §e/rankup §7- Intentar rankup");
        player.sendMessage(ChatColor.GRAY + "  • §e/rankup progress §7- Ver progreso");
        player.sendMessage("");
    }

    /**
     * Muestra progreso con formato más limpio
     */
    private void showProgress(Player player) {
        player.sendMessage(ChatColor.YELLOW + "🔄 Cargando tu progreso...");
        rankupManager.showPlayerProgress(player);
    }
    /**
     * Muestra ayuda simplificada
     */
    private void showHelp(Player player) {
        rankupManager.getMessageManager().sendHelpMessage(player);
    }

    /**
     * Lista de rangos simplificada
     */
    private void showRankList(Player player) {
        rankupManager.showRanksList(player);
    }

    /**
     * Muestra top de rangos (placeholder)
     */
    private void showTopRanks(Player player) {
        player.sendMessage(ChatColor.YELLOW + "🚧 Top de rangos en desarrollo...");
        player.sendMessage(ChatColor.GRAY + "Esta función estará disponible próximamente.");
    }

    /**
     * Maneja comandos de debug
     */
    private void handleDebug(Player player, String[] args) {
        if (args.length < 2) {
            // Debug del propio jugador
            rankupManager.debugPlayerRankup(player, player);
            return;
        }

        String targetName = args[1];
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            player.sendMessage(ChatColor.RED + "❌ Jugador no encontrado: " + targetName);
            return;
        }

        rankupManager.debugPlayerRankup(target, player);
    }

    /**
     * Recarga la configuración
     */
    private void reloadConfig(Player player) {
        try {
            long startTime = System.currentTimeMillis();

            player.sendMessage(ChatColor.YELLOW + "🔄 Recargando configuración de rankup...");

            rankupManager.reloadConfig();

            long duration = System.currentTimeMillis() - startTime;

            player.sendMessage(ChatColor.GREEN + "✅ Configuración recargada en " + duration + "ms");

            // Mostrar estadísticas
            int ranksCount = rankupManager.getRanks().size();
            boolean papiEnabled = rankupManager.isPlaceholderAPIEnabled();

            player.sendMessage(ChatColor.GRAY + "📊 Estadísticas:");
            player.sendMessage(ChatColor.GRAY + "  • Rangos: " + ranksCount);
            player.sendMessage(ChatColor.GRAY + "  • PlaceholderAPI: " + (papiEnabled ? "✓" : "✗"));

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error recargando: " + e.getMessage());
            plugin.getLogger().severe("Error en reload de rankup: " + e.getMessage());
        }
    }

    /**
     * Crea una barra de progreso visual mejorada
     */
    private String createProgressBar(double percentage, int length) {
        int filled = (int) Math.ceil(percentage / 100.0 * length);
        StringBuilder bar = new StringBuilder();

        // Agregar color basado en porcentaje
        String color = getProgressColor(percentage);

        bar.append(color);
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        bar.append(ChatColor.GRAY);
        for (int i = filled; i < length; i++) {
            bar.append("█");
        }

        // Agregar porcentaje al final
        bar.append(" ").append(ChatColor.WHITE).append(String.format("%.1f%%", percentage));

        return bar.toString();
    }

    /**
     * Obtiene color basado en porcentaje de progreso
     */
    private String getProgressColor(double percentage) {
        if (percentage >= 100.0) return ChatColor.GREEN.toString();
        if (percentage >= 75.0) return ChatColor.YELLOW.toString();
        if (percentage >= 50.0) return ChatColor.GOLD.toString();
        if (percentage >= 25.0) return ChatColor.RED.toString();
        return ChatColor.DARK_RED.toString();
    }

    /**
     * Formatea el nombre de un requisito para mostrar
     */
    private String formatRequirementName(String type) {
        return switch (type.toLowerCase()) {
            case "money" -> "Dinero";
            case "level" -> "Nivel";
            case "playtime_hours" -> "Tiempo jugado";
            case "mob_kills" -> "Mobs matados";
            case "blocks_mined" -> "Bloques minados";
            case "farming_level" -> "Nivel farming";
            case "mining_level" -> "Nivel minería";
            case "animals_bred" -> "Animales criados";
            case "fish_caught" -> "Peces pescados";
            case "ender_dragon_kills" -> "Ender Dragons";
            case "wither_kills" -> "Withers matados";
            default -> type.replace("_", " ");
        };
    }

    /**
     * Formatea el valor de un requisito para mostrar
     */
    private String formatRequirementValue(RequirementProgress progress) {
        String type = progress.getType().toLowerCase();
        double current = progress.getCurrent();
        double required = progress.getRequired();

        return switch (type) {
            case "money" -> String.format("$%,.0f/$%,.0f", current, required);
            case "level" -> String.format("%.0f/%.0f", current, required);
            case "playtime_hours" -> String.format("%.1f/%.1fh", current, required);
            case "mob_kills", "blocks_mined", "animals_bred", "fish_caught",
                 "ender_dragon_kills", "wither_kills" ->
                    String.format("%,.0f/%,.0f", current, required);
            case "farming_level", "mining_level" ->
                    String.format("Lv.%.0f/%.0f", current, required);
            default -> String.format("%.1f/%.1f", current, required);
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String cmdName = command.getName().toLowerCase();
            List<String> completions = new ArrayList<>();

            switch (cmdName) {
                case "rankup" -> {
                    completions.addAll(Arrays.asList("info", "progress", "help", "list"));
                    if (sender.hasPermission("survivalcore.rankup.admin")) {
                        completions.addAll(Arrays.asList("reload", "debug"));
                    }
                }
                case "prestige" -> {
                    if (rankupManager.isPrestigeEnabled()) {
                        completions.addAll(Arrays.asList("info", "help"));
                    }
                }
                case "ranks" -> completions.addAll(Arrays.asList("gui", "list", "top"));
            }

            return completions.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para debug de jugadores
        if (args.length == 2 && args[0].equalsIgnoreCase("debug") &&
                sender.hasPermission("survivalcore.rankup.admin")) {

            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}