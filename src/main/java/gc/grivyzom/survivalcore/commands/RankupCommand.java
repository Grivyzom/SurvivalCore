package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.*;
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
 * Comando de rankup modernizado - Versión 2.1
 * Con sistema de paginación integrado y mensajes compactos
 *
 * @author Brocolitx
 * @version 2.1 - Con paginación
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
     * Maneja el comando /rankup con sintaxis simplificada y paginación
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
            case "progress", "p", "progreso" -> {
                // 🆕 NUEVO: Soporte para paginación en progreso
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "❌ Página inválida. Usa un número.");
                        return true;
                    }
                }
                showProgressWithPagination(player, page);
            }
            case "list", "l", "lista" -> {
                // 🆕 NUEVO: Soporte para paginación en lista de rangos
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "❌ Página inválida. Usa un número.");
                        return true;
                    }
                }
                showRanksListWithPagination(player, page);
            }
            case "help", "h", "ayuda" -> showHelp(player);
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
            case "compact" -> {
                // 🆕 NUEVO: Comando para alternar modo compacto
                if (!player.hasPermission("survivalcore.rankup.admin")) {
                    player.sendMessage(ChatColor.RED + "❌ Sin permisos para cambiar modo");
                    return true;
                }
                toggleCompactMode(player);
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
            case "list", "lista" -> {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "❌ Página inválida. Usa un número.");
                        return true;
                    }
                }
                showRanksListWithPagination(player, page);
            }
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

        // 📋 INFO COMPACTA Y CLARA - VERSIÓN MODERNIZADA
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "📋 " + ChatColor.BOLD + "INFORMACIÓN DE RANGO");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "🎯 Rango actual: " + rankData.getDisplayName());
        player.sendMessage(ChatColor.WHITE + "📊 Posición: " + ChatColor.YELLOW + "#" + (rankData.getOrder() + 1));

        if (rankData.hasNextRank()) {
            RankupManager.SimpleRankData nextRankData = rankups.get(rankData.getNextRank());
            String nextDisplay = nextRankData != null ? nextRankData.getDisplayName() : rankData.getNextRank();
            player.sendMessage(ChatColor.WHITE + "⬆️ Siguiente: " + nextDisplay);
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 ¡Rango máximo alcanzado!");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 Comandos útiles:");
        player.sendMessage(ChatColor.GRAY + "  • §e/rankup §7- Intentar rankup");
        player.sendMessage(ChatColor.GRAY + "  • §e/rankup progress §7- Ver progreso detallado");
        player.sendMessage(ChatColor.GRAY + "  • §e/ranks §7- Abrir menú interactivo");
        player.sendMessage("");
    }

    /**
     * 🆕 NUEVO: Muestra progreso con paginación inteligente
     */
    private void showProgressWithPagination(Player player, int page) {
        player.sendMessage(ChatColor.YELLOW + "🔄 Cargando tu progreso...");

        rankupManager.getPlayerProgress(player).thenAccept(progress -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Usar el nuevo sistema de paginación del MessageManager
                    rankupManager.getMessageManager().sendProgressWithPagination(player, progress, page);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error mostrando progreso paginado: " + e.getMessage());
                    player.sendMessage(ChatColor.RED + "❌ Error cargando progreso. Intenta de nuevo.");
                }
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().severe("Error obteniendo progreso: " + throwable.getMessage());
                player.sendMessage(ChatColor.RED + "❌ Error obteniendo progreso. Contacta a un administrador.");
            });
            return null;
        });
    }

    /**
     * 🆕 NUEVO: Muestra lista de rangos con paginación
     */
    private void showRanksListWithPagination(Player player, int page) {
        try {
            Map<String, RankupManager.SimpleRankData> allRanks = rankupManager.getRanks();
            String currentRank = rankupManager.getCurrentRank(player);

            // Usar el nuevo sistema de paginación del MessageManager
            rankupManager.getMessageManager().sendRanksListWithPagination(player, allRanks, currentRank, page);

        } catch (Exception e) {
            plugin.getLogger().severe("Error mostrando lista de rangos: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "❌ Error cargando lista de rangos. Intenta de nuevo.");
        }
    }

    /**
     * Muestra ayuda modernizada
     */
    private void showHelp(Player player) {
        rankupManager.getMessageManager().sendHelpMessage(player);
    }

    /**
     * Muestra top de rangos (placeholder mejorado)
     */
    private void showTopRanks(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "🏆 " + ChatColor.BOLD + "TOP RANGOS");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "🚧 Función en desarrollo...");
        player.sendMessage(ChatColor.GRAY + "Esta función estará disponible próximamente.");
        player.sendMessage(ChatColor.GRAY + "Incluirá ranking de jugadores por tiempo en rangos,");
        player.sendMessage(ChatColor.GRAY + "estadísticas de progreso y logros especiales.");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 Mientras tanto, usa " + ChatColor.WHITE + "/rankup list" +
                ChatColor.GRAY + " para ver todos los rangos");
        player.sendMessage("");
    }

    /**
     * Maneja comandos de debug con información mejorada
     */
    private void handleDebug(Player player, String[] args) {
        if (args.length < 2) {
            // Debug del propio jugador con información extendida
            debugPlayerRankupExtended(player, player);
            return;
        }

        String targetName = args[1];
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            player.sendMessage(ChatColor.RED + "❌ Jugador no encontrado: " + targetName);
            return;
        }

        debugPlayerRankupExtended(target, player);
    }

    /**
     * 🆕 NUEVO: Debug extendido con más información
     */
    private void debugPlayerRankupExtended(Player target, Player admin) {
        admin.sendMessage("");
        admin.sendMessage(ChatColor.GOLD + "🔍 " + ChatColor.BOLD + "DEBUG RANKUP - " + target.getName());
        admin.sendMessage(ChatColor.GRAY + "════════════════════════════════════════");

        // Usar el debug del RankupManager pero con formato mejorado
        rankupManager.debugPlayerRankup(target, admin);

        // Información adicional sobre el sistema de mensajes
        try {
            Map<String, Object> messageStats = rankupManager.getMessageManager().getStats();

            admin.sendMessage("");
            admin.sendMessage(ChatColor.AQUA + "📊 Estadísticas del Sistema de Mensajes:");
            admin.sendMessage(ChatColor.WHITE + "  • Mensajes cargados: " + ChatColor.YELLOW + messageStats.get("total_messages"));
            admin.sendMessage(ChatColor.WHITE + "  • Modo compacto: " +
                    (((Boolean) messageStats.get("compact_mode_enabled")) ? ChatColor.GREEN + "Habilitado" : ChatColor.RED + "Deshabilitado"));
            admin.sendMessage(ChatColor.WHITE + "  • Navegación: " +
                    (((Boolean) messageStats.get("navigation_enabled")) ? ChatColor.GREEN + "Habilitada" : ChatColor.RED + "Deshabilitada"));
            admin.sendMessage(ChatColor.WHITE + "  • Requisitos por página: " + ChatColor.YELLOW + messageStats.get("max_requirements_per_page"));
            admin.sendMessage(ChatColor.WHITE + "  • Rangos por página: " + ChatColor.YELLOW + messageStats.get("max_ranks_per_page"));

        } catch (Exception e) {
            admin.sendMessage(ChatColor.RED + "Error obteniendo estadísticas: " + e.getMessage());
        }

        admin.sendMessage(ChatColor.GRAY + "════════════════════════════════════════");
    }

    /**
     * Recarga la configuración con información mejorada
     */
    private void reloadConfig(Player player) {
        try {
            long startTime = System.currentTimeMillis();

            player.sendMessage(ChatColor.YELLOW + "🔄 Recargando configuración de rankup...");

            rankupManager.reloadConfig();

            long duration = System.currentTimeMillis() - startTime;

            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✅ Configuración recargada exitosamente");
            player.sendMessage(ChatColor.GRAY + "Tiempo: " + duration + "ms");

            // Mostrar estadísticas actualizadas
            int ranksCount = rankupManager.getRanks().size();
            boolean papiEnabled = rankupManager.isPlaceholderAPIEnabled();

            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "📊 Estadísticas actualizadas:");
            player.sendMessage(ChatColor.WHITE + "  • Rangos cargados: " + ChatColor.GREEN + ranksCount);
            player.sendMessage(ChatColor.WHITE + "  • PlaceholderAPI: " +
                    (papiEnabled ? ChatColor.GREEN + "✓ Disponible" : ChatColor.RED + "✗ No disponible"));
            player.sendMessage(ChatColor.WHITE + "  • Cooldown: " + ChatColor.YELLOW + (rankupManager.getCooldownTime() / 1000) + "s");
            player.sendMessage(ChatColor.WHITE + "  • Efectos: " +
                    (rankupManager.areEffectsEnabled() ? ChatColor.GREEN + "Habilitados" : ChatColor.RED + "Deshabilitados"));
            player.sendMessage(ChatColor.WHITE + "  • Broadcast: " +
                    (rankupManager.isBroadcastEnabled() ? ChatColor.GREEN + "Habilitado" : ChatColor.RED + "Deshabilitado"));

            // Verificar sistema de mensajes
            try {
                Map<String, Object> messageStats = rankupManager.getMessageManager().getStats();
                player.sendMessage(ChatColor.WHITE + "  • Mensajes: " + ChatColor.GREEN + messageStats.get("total_messages") + " cargados");
                player.sendMessage(ChatColor.WHITE + "  • Modo compacto: " +
                        (((Boolean) messageStats.get("compact_mode_enabled")) ? ChatColor.GREEN + "Activo" : ChatColor.YELLOW + "Inactivo"));
            } catch (Exception e) {
                player.sendMessage(ChatColor.YELLOW + "  • Sistema de mensajes: Error obteniendo estadísticas");
            }

            player.sendMessage("");

        } catch (Exception e) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ Error recargando configuración:");
            player.sendMessage(ChatColor.RED + e.getMessage());
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "💡 Consejos:");
            player.sendMessage(ChatColor.GRAY + "• Verifica que rankups.yml tenga sintaxis YAML válida");
            player.sendMessage(ChatColor.GRAY + "• Asegúrate de que LuckPerms esté funcionando");
            player.sendMessage(ChatColor.GRAY + "• Usa /score debug rankup para más información");

            plugin.getLogger().severe("Error en reload de rankup solicitado por " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * 🆕 NUEVO: Alterna el modo compacto del sistema de mensajes
     */
    private void toggleCompactMode(Player player) {
        try {
            rankupManager.getMessageManager().toggleCompactMode();

            Map<String, Object> stats = rankupManager.getMessageManager().getStats();
            boolean isCompact = (Boolean) stats.get("compact_mode_enabled");

            player.sendMessage("");
            if (isCompact) {
                player.sendMessage(ChatColor.GREEN + "✅ Modo compacto " + ChatColor.BOLD + "HABILITADO");
                player.sendMessage(ChatColor.WHITE + "   Los mensajes de rankup serán más cortos y concisos");
            } else {
                player.sendMessage(ChatColor.YELLOW + "📜 Modo compacto " + ChatColor.BOLD + "DESHABILITADO");
                player.sendMessage(ChatColor.WHITE + "   Los mensajes de rankup usarán formato extendido");
            }
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "💡 Este cambio afecta a todos los jugadores del servidor");
            player.sendMessage("");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error alternando modo compacto: " + e.getMessage());
        }
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
                        completions.addAll(Arrays.asList("reload", "debug", "compact"));
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

        // Tab completion para páginas en progress y list
        if (args.length == 2 && (args[0].equalsIgnoreCase("progress") || args[0].equalsIgnoreCase("list"))) {
            return Arrays.asList("1", "2", "3", "4", "5").stream()
                    .filter(page -> page.startsWith(args[1]))
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