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
 * Comando principal del sistema de rankup.
 * Maneja /rankup, /prestige y /ranks
 *
 * @author Brocolitx
 * @version 1.0
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
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
                    player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "✓ ¡RANKUP EXITOSO!" + ChatColor.GOLD + "                ║");
                    player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════╣");
                    player.sendMessage(ChatColor.GOLD + "║ " + result.getMessage() + ChatColor.GOLD + " ║");
                    player.sendMessage(ChatColor.GOLD + "║                                      ║");
                    player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Usa /rankup progress para ver tu" + ChatColor.GOLD + "     ║");
                    player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "progreso hacia el siguiente rango." + ChatColor.GOLD + "   ║");
                    player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
                    player.sendMessage("");
                } else {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "╔══════════════════════════════════════╗");
                    player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "❌ RANKUP NO DISPONIBLE" + ChatColor.RED + "            ║");
                    player.sendMessage(ChatColor.RED + "╠══════════════════════════════════════╣");
                    player.sendMessage(ChatColor.RED + "║ " + result.getMessage() + ChatColor.RED + " ║");
                    player.sendMessage(ChatColor.RED + "║                                      ║");
                    player.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "Usa /rankup progress para ver qué" + ChatColor.RED + "     ║");
                    player.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "requisitos te faltan." + ChatColor.RED + "               ║");
                    player.sendMessage(ChatColor.RED + "╚══════════════════════════════════════╝");
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
            player.sendMessage(ChatColor.RED + "No se pudo determinar tu rango actual.");
            return;
        }

        Map<String, RankupData> rankups = rankupManager.getRankups();
        RankupData rankupData = rankups.get(currentRank);

        if (rankupData == null) {
            player.sendMessage(ChatColor.RED + "No hay información disponible para tu rango actual.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "INFORMACIÓN DE RANGO" + ChatColor.GOLD + "                ║");
        player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════╣");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Rango actual: " + rankupData.getDisplayName() + ChatColor.GOLD + " ║");

        if (rankupData.hasNextRank()) {
            RankupData nextRankData = rankups.get(rankupData.getNextRank());
            String nextRankDisplay = nextRankData != null ? nextRankData.getDisplayName() : rankupData.getNextRank();
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Siguiente rango: " + nextRankDisplay + ChatColor.GOLD + " ║");
        } else {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.LIGHT_PURPLE + "¡Has alcanzado el rango máximo!" + ChatColor.GOLD + "     ║");
        }

        player.sendMessage(ChatColor.GOLD + "║                                      ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "Usa /rankup progress para ver tu" + ChatColor.GOLD + "     ║");
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "progreso detallado." + ChatColor.GOLD + "                ║");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
    }

    /**
     * Muestra el progreso del jugador
     */
    private void showProgress(Player player) {
        rankupManager.getPlayerProgress(player).thenAccept(progress -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (progress.getCurrentRank() == null) {
                    player.sendMessage(ChatColor.RED + "No se pudo determinar tu rango actual.");
                    return;
                }

                player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "PROGRESO DE RANKUP" + ChatColor.GOLD + "                  ║");
                player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════╣");

                if (progress.getNextRank() == null) {
                    player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.LIGHT_PURPLE + "¡Has alcanzado el rango máximo!" + ChatColor.GOLD + "     ║");
                    player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
                    return;
                }

                // Progreso general
                String progressBar = createProgressBar(progress.getOverallProgress(), 20);
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.WHITE + "Progreso general: " +
                        String.format("%.1f%%", progress.getOverallProgress()) + ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "║ " + progressBar + ChatColor.GOLD + " ║");
                player.sendMessage(ChatColor.GOLD + "║                                      ║");

                // Requisitos individuales
                for (Map.Entry<String, RequirementProgress> entry : progress.getRequirements().entrySet()) {
                    RequirementProgress reqProgress = entry.getValue();
                    String status = reqProgress.isCompleted() ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                    String reqName = formatRequirementName(reqProgress.getType());

                    player.sendMessage(ChatColor.GOLD + "║ " + status + ChatColor.WHITE + " " + reqName + ": " +
                            formatRequirementValue(reqProgress) + ChatColor.GOLD + " ║");
                }

                player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
            });
        });
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
 * Formatea el nombre