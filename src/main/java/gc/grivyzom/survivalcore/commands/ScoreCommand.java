package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.gui.GeneroGUI;
import gc.grivyzom.survivalcore.gui.ProfileGUI;
import gc.grivyzom.survivalcore.rankup.RankupManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Comando principal /score actualizado para Rankup 2.0
 *
 * @author Brocolitx
 * @version 2.1 - Compatible con Rankup 2.0
 */
public class ScoreCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ScoreCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden ver su puntuación.");
                return true;
            }
            showPlayerScore((Player) sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "version", "v" -> showVersion(sender);
            case "reload", "r" -> handleReload(sender);
            case "reloadrankup", "rr", "rankupreload" -> handleRankupReload(sender);
            case "birthday", "cumpleanos" -> handleBirthday(sender, args);
            case "gender", "genero" -> handleGender(sender, args);
            case "country", "pais" -> handleCountry(sender, args);
            case "help", "ayuda" -> showHelp(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "emergency" -> handleEmergencyRestart(sender);
            case "status" -> handleSystemStatus(sender);
            case "reloadguis", "gui", "guis" -> handleGuiReload(sender);

            default -> {
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /score help para ver la ayuda.");
                return true;
            }
        }

        return true;
    }

    /**
     * Maneja el reload completo del plugin - OPTIMIZADO para Rankup 2.0
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.reload")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "🔄 Iniciando recarga completa de SurvivalCore...");

        long startTime = System.currentTimeMillis();
        boolean hasErrors = false;
        StringBuilder report = new StringBuilder();

        try {
            // 1. Recargar configuración principal PRIMERO
            sender.sendMessage(ChatColor.GRAY + "• Recargando configuración principal...");
            plugin.reloadConfig();
            report.append(ChatColor.GREEN + "✓ Configuración principal\n");

            // 2. NUEVA PRIORIDAD: Recargar rankup ANTES que updateInternalConfig
            if (plugin.isRankupSystemEnabled()) {
                try {
                    sender.sendMessage(ChatColor.GRAY + "• Recargando sistema de Rankup 2.0...");
                    RankupManager rankupManager = plugin.getRankupManager();

                    // Recargar configuración del rankup de forma explícita
                    rankupManager.reloadConfig();

                    // Verificar que se cargó correctamente
                    int ranksCount = rankupManager.getRanks().size();
                    int prestigesCount = rankupManager.getPrestiges().size();

                    report.append(ChatColor.GREEN + "✓ Sistema de Rankup 2.0 (" + ranksCount + " rangos, " + prestigesCount + " prestiges)\n");

                    if (rankupManager.isPlaceholderAPIEnabled()) {
                        report.append(ChatColor.GREEN + "✓ PlaceholderAPI integrado con Rankup\n");
                    } else {
                        report.append(ChatColor.YELLOW + "⚠ PlaceholderAPI no disponible para Rankup\n");
                    }

                    // Log detallado para debug
                    sender.sendMessage(ChatColor.GREEN + "  ✓ Rangos recargados: " + ranksCount);
                    if (prestigesCount > 0) {
                        sender.sendMessage(ChatColor.GREEN + "  ✓ Prestiges recargados: " + prestigesCount);
                    }

                } catch (Exception e) {
                    hasErrors = true;
                    report.append(ChatColor.RED + "✗ Sistema de Rankup 2.0: ").append(e.getMessage()).append("\n");
                    plugin.getLogger().severe("Error crítico recargando Rankup 2.0:");
                    plugin.getLogger().severe("Tipo: " + e.getClass().getSimpleName());
                    plugin.getLogger().severe("Mensaje: " + e.getMessage());
                    e.printStackTrace();

                    sender.sendMessage(ChatColor.RED + "  ✗ Error en Rankup: " + e.getMessage());
                }
            } else {
                report.append(ChatColor.YELLOW + "⚠ Sistema de Rankup: No disponible (LuckPerms requerido)\n");
                sender.sendMessage(ChatColor.YELLOW + "  ⚠ Sistema de Rankup no disponible");
            }

            // 3. Actualizar configuración interna (otros sistemas)
            sender.sendMessage(ChatColor.GRAY + "• Actualizando configuración interna...");
            plugin.updateInternalConfig();
            report.append(ChatColor.GREEN + "✓ Configuración interna actualizada\n");

            // 4. Verificar configuraciones específicas
            sender.sendMessage(ChatColor.GRAY + "• Verificando sistemas específicos...");
            try {
                if (plugin.getCropExperienceConfig() != null) {
                    report.append(ChatColor.GREEN + "✓ Configuración de cultivos\n");
                } else {
                    report.append(ChatColor.YELLOW + "⚠ Configuración de cultivos: No cargada\n");
                }

                if (plugin.getMiningConfig() != null) {
                    report.append(ChatColor.GREEN + "✓ Configuración de minería\n");
                } else {
                    report.append(ChatColor.YELLOW + "⚠ Configuración de minería: No cargada\n");
                }
            } catch (Exception e) {
                hasErrors = true;
                report.append(ChatColor.RED + "✗ Configuraciones específicas: ").append(e.getMessage()).append("\n");
            }

            // 5. Verificar managers principales
            sender.sendMessage(ChatColor.GRAY + "• Verificando managers...");
            report.append(verifyManager("XpTransferManager", plugin.getXpTransferManager()));
            report.append(verifyManager("SellWandManager", plugin.getSellWandManager()));
            report.append(verifyManager("XpChequeManager", plugin.getXpChequeCommand()));
            report.append(verifyManager("LecternRecipeManager", plugin.getLecternRecipeManager()));
            report.append(verifyManager("MagicFlowerPotManager", plugin.getMagicFlowerPotManager()));

            // 6. Verificar base de datos
            sender.sendMessage(ChatColor.GRAY + "• Verificando conexión a base de datos...");
            try {
                plugin.getDatabaseManager().testConnection();
                report.append(ChatColor.GREEN + "✓ Conexión a base de datos\n");
            } catch (Exception e) {
                hasErrors = true;
                report.append(ChatColor.RED + "✗ Base de datos: ").append(e.getMessage()).append("\n");
            }

            // 7. Verificar PlaceholderAPI y expansiones
            sender.sendMessage(ChatColor.GRAY + "• Verificando PlaceholderAPI...");
            try {
                if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    report.append(ChatColor.GREEN + "✓ PlaceholderAPI disponible\n");

                    // Verificar expansiones específicas
                    var papiPlugin = (me.clip.placeholderapi.PlaceholderAPIPlugin)
                            plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");

                    if (papiPlugin.getLocalExpansionManager().getExpansion("score") != null) {
                        report.append(ChatColor.GREEN + "✓ Expansión 'score' registrada\n");
                    } else {
                        report.append(ChatColor.YELLOW + "⚠ Expansión 'score' no registrada\n");
                    }
                } else {
                    report.append(ChatColor.YELLOW + "⚠ PlaceholderAPI no instalado\n");
                }
            } catch (Exception e) {
                report.append(ChatColor.YELLOW + "⚠ Error verificando PlaceholderAPI: " + e.getMessage() + "\n");
            }

            // 8. Verificar plugins externos críticos
            sender.sendMessage(ChatColor.GRAY + "• Verificando dependencias externas...");
            report.append(ChatColor.YELLOW + "Plugins externos:\n");
            report.append(ChatColor.WHITE + "  • LuckPerms: " + getPluginStatus("LuckPerms") + "\n");
            report.append(ChatColor.WHITE + "  • PlaceholderAPI: " + getPluginStatus("PlaceholderAPI") + "\n");
            report.append(ChatColor.WHITE + "  • Vault: " + getPluginStatus("Vault") + "\n");

            // 9. NUEVA FUNCIONALIDAD: Verificar archivos de configuración modificados
            sender.sendMessage(ChatColor.GRAY + "• Verificando archivos de configuración...");
            try {
                // Verificar si rankups.yml existe y es válido
                File rankupsFile = new File(plugin.getDataFolder(), "rankups.yml");
                if (rankupsFile.exists()) {
                    long fileSize = rankupsFile.length();
                    long lastModified = rankupsFile.lastModified();
                    report.append(ChatColor.GREEN + "✓ rankups.yml encontrado (" + fileSize + " bytes, modificado: " +
                            new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(lastModified)) + ")\n");
                } else {
                    report.append(ChatColor.YELLOW + "⚠ rankups.yml no encontrado\n");
                }

                // Verificar config.yml
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                if (configFile.exists()) {
                    long lastModified = configFile.lastModified();
                    report.append(ChatColor.GREEN + "✓ config.yml (modificado: " +
                            new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(lastModified)) + ")\n");
                }

            } catch (Exception e) {
                report.append(ChatColor.YELLOW + "⚠ Error verificando archivos: " + e.getMessage() + "\n");
            }

            // 10. Estadísticas finales del servidor
            sender.sendMessage(ChatColor.GRAY + "• Recopilando estadísticas finales...");
            report.append(ChatColor.YELLOW + "Estadísticas del servidor:\n");
            report.append(ChatColor.WHITE + "  • Jugadores online: " + ChatColor.YELLOW +
                    plugin.getServer().getOnlinePlayers().size() + "\n");
            report.append(ChatColor.WHITE + "  • Versión del plugin: " + ChatColor.YELLOW +
                    plugin.getDescription().getVersion() + "\n");

            // Si hay sistema de rankup, mostrar estadísticas específicas
            if (plugin.isRankupSystemEnabled()) {
                try {
                    RankupManager rankupManager = plugin.getRankupManager();
                    report.append(ChatColor.WHITE + "  • Configuración de rankup:\n");
                    report.append(ChatColor.GRAY + "    - Cooldown: " + (rankupManager.getCooldownTime() / 1000) + "s\n");
                    report.append(ChatColor.GRAY + "    - Efectos: " +
                            (rankupManager.areEffectsEnabled() ? "Habilitados" : "Deshabilitados") + "\n");
                    report.append(ChatColor.GRAY + "    - Broadcast: " +
                            (rankupManager.isBroadcastEnabled() ? "Habilitado" : "Deshabilitado") + "\n");
                    report.append(ChatColor.GRAY + "    - Prestige: " +
                            (rankupManager.isPrestigeEnabled() ? "Habilitado" : "Deshabilitado") + "\n");
                } catch (Exception e) {
                    report.append(ChatColor.YELLOW + "    - Error obteniendo estadísticas de rankup\n");
                }
            }

        } catch (Exception e) {
            hasErrors = true;
            report.append(ChatColor.RED + "✗ Error crítico: ").append(e.getMessage()).append("\n");
            plugin.getLogger().severe("Error crítico durante reload: " + e.getMessage());
            e.printStackTrace();
        }

        long duration = System.currentTimeMillis() - startTime;

        // Mostrar reporte final mejorado
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "═══════════ REPORTE DE RECARGA ═══════════");
        sender.sendMessage(report.toString());

        if (hasErrors) {
            sender.sendMessage(ChatColor.RED + "⚠ Recarga completada con errores");
            sender.sendMessage(ChatColor.GRAY + "Revisa la consola para más detalles");
            sender.sendMessage(ChatColor.YELLOW + "💡 Tip: Verifica que los archivos de configuración sean válidos");
        } else {
            sender.sendMessage(ChatColor.GREEN + "✅ Recarga completada exitosamente");
            sender.sendMessage(ChatColor.GRAY + "Todos los sistemas funcionan correctamente");
        }

        sender.sendMessage(ChatColor.GRAY + "Tiempo total: " + duration + "ms");
        sender.sendMessage(ChatColor.AQUA + "═════════════════════════════════════════");

        // Log mejorado en consola
        if (hasErrors) {
            plugin.getLogger().warning("Recarga completada con errores en " + duration + "ms");
            plugin.getLogger().warning("Se recomienda verificar la configuración manualmente");
        } else {
            plugin.getLogger().info("✅ Recarga completada exitosamente en " + duration + "ms");
            if (plugin.isRankupSystemEnabled()) {
                RankupManager rankupManager = plugin.getRankupManager();
                plugin.getLogger().info("Sistema de Rankup 2.0: " + rankupManager.getRanks().size() + " rangos activos");
            }
        }
    }

    /**
     * Verifica el estado de un manager
     */
    private String verifyManager(String name, Object manager) {
        if (manager != null) {
            return ChatColor.GREEN + "✓ " + name + "\n";
        } else {
            return ChatColor.YELLOW + "⚠ " + name + ": No disponible\n";
        }
    }

    /**
     * Maneja comandos de debug del sistema
     */

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivalcore.debug")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para usar comandos de debug.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Debug disponibles:");
            sender.sendMessage(ChatColor.WHITE + "  /score debug rankup - Estado del sistema de rankup");
            sender.sendMessage(ChatColor.WHITE + "  /score debug placeholders - Verificar placeholders");
            sender.sendMessage(ChatColor.WHITE + "  /score debug systems - Estado de todos los sistemas");
            sender.sendMessage(ChatColor.WHITE + "  /score debug menus - Debug del sistema de menús");
            // 🆕 NUEVOS comandos de debug
            sender.sendMessage(ChatColor.WHITE + "  /score debug statistics [jugador] - Debug de estadísticas");
            sender.sendMessage(ChatColor.WHITE + "  /score debug requirements - Debug de requisitos de rankup");
            if (sender instanceof Player) {
                sender.sendMessage(ChatColor.WHITE + "  /score debug player - Debug de tu información");
            }
            return;
        }

        String debugType = args[1].toLowerCase();

        switch (debugType) {
            case "rankup" -> debugRankupSystem(sender);
            case "placeholders" -> debugPlaceholdersCommand(sender);
            case "systems" -> debugAllSystems(sender);
            case "rankup_detection", "rd" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /score debug rankup_detection <jugador>");
                    return;
                }

                String targetName = args[2];
                Player target = plugin.getServer().getPlayer(targetName);

                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "❌ Jugador no encontrado: " + targetName);
                    return;
                }

                if (!plugin.isRankupSystemEnabled()) {
                    sender.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
                    return;
                }

                sender.sendMessage(ChatColor.YELLOW + "🔍 Iniciando debug detallado de detección de rango para " + target.getName());
                sender.sendMessage(ChatColor.GRAY + "Revisa la consola para información completa...");

                // Ejecutar debug detallado
                plugin.getRankupManager().debugPlayerRankDetection(target);

                // Información básica para el admin
                String currentRank = plugin.getRankupManager().getCurrentRank(target);
                sender.sendMessage("");
                sender.sendMessage(ChatColor.AQUA + "📊 Resumen para " + target.getName() + ":");
                sender.sendMessage(ChatColor.WHITE + "  • Rango detectado: " + ChatColor.YELLOW + currentRank);

                if (currentRank != null) {
                    var rankData = plugin.getRankupManager().getRanks().get(currentRank);
                    if (rankData != null) {
                        sender.sendMessage(ChatColor.WHITE + "  • Display: " + rankData.getDisplayName());
                        sender.sendMessage(ChatColor.WHITE + "  • Orden: " + ChatColor.YELLOW + rankData.getOrder());
                        sender.sendMessage(ChatColor.WHITE + "  • Siguiente: " + ChatColor.YELLOW +
                                (rankData.hasNextRank() ? rankData.getNextRank() : "RANGO MÁXIMO"));
                    }
                }

                sender.sendMessage("");
                sender.sendMessage(ChatColor.GREEN + "✅ Debug completo en consola");
            }
            case "menus", "menu" -> {
                plugin.debugMenuSystemDetailed(sender);
            }

            // 🆕 NUEVOS CASOS DE DEBUG
            case "statistics", "stats" -> {
                if (args.length < 3) {
                    // Debug de estadísticas del propio jugador
                    if (sender instanceof Player) {
                        debugPlayerStatistics((Player) sender);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Especifica un jugador: /score debug statistics <jugador>");
                    }
                    return;
                }

                String targetName = args[2];
                Player target = plugin.getServer().getPlayer(targetName);

                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "❌ Jugador no encontrado: " + targetName);
                    return;
                }

                debugPlayerStatistics(target, sender);
            }

            case "requirements", "req" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Solo jugadores pueden probar requisitos");
                    return;
                }

                debugRequirements((Player) sender);
            }

            case "player" -> {
                if (sender instanceof Player) {
                    debugPlayerInfo((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden usar este debug.");
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Tipo de debug desconocido: " + debugType);
        }
    }

    /**
     * Debug del sistema de rankup - ACTUALIZADO para Rankup 2.0
     */
    private void debugRankupSystem(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "═══ DEBUG SISTEMA RANKUP 2.0 ═══");

        if (!plugin.isRankupSystemEnabled()) {
            sender.sendMessage(ChatColor.RED + "❌ Sistema de rankup: DESHABILITADO");
            sender.sendMessage(ChatColor.GRAY + "Motivo: LuckPerms no disponible o error en inicialización");

            // Verificar específicamente LuckPerms
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                sender.sendMessage(ChatColor.RED + "   • LuckPerms no está instalado");
            } else if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
                sender.sendMessage(ChatColor.RED + "   • LuckPerms está instalado pero deshabilitado");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "   • LuckPerms disponible pero fallo en inicialización");
            }
            return;
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();

            sender.sendMessage(ChatColor.GREEN + "✅ Sistema de rankup 2.0: HABILITADO");
            sender.sendMessage(ChatColor.WHITE + "Rangos cargados: " + ChatColor.YELLOW + rankupManager.getRanks().size());
            sender.sendMessage(ChatColor.WHITE + "Prestiges cargados: " + ChatColor.YELLOW + rankupManager.getPrestiges().size());
            sender.sendMessage(ChatColor.WHITE + "PlaceholderAPI: " +
                    (rankupManager.isPlaceholderAPIEnabled() ? ChatColor.GREEN + "DISPONIBLE" : ChatColor.RED + "NO DISPONIBLE"));
            sender.sendMessage(ChatColor.WHITE + "Cooldown: " + ChatColor.YELLOW + (rankupManager.getCooldownTime() / 1000) + "s");
            sender.sendMessage(ChatColor.WHITE + "Efectos habilitados: " +
                    (rankupManager.areEffectsEnabled() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
            sender.sendMessage(ChatColor.WHITE + "Broadcast habilitado: " +
                    (rankupManager.isBroadcastEnabled() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
            sender.sendMessage(ChatColor.WHITE + "Prestige habilitado: " +
                    (rankupManager.isPrestigeEnabled() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));

            // Mostrar algunos rangos como ejemplo - ACTUALIZADO
            if (!rankupManager.getRanks().isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Primeros rangos:");
                rankupManager.getRanks().values().stream()
                        .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                        .limit(3)
                        .forEach(rank -> sender.sendMessage(ChatColor.GRAY + "  • " + rank.getDisplayName() +
                                " (orden: " + rank.getOrder() + ")"));
            }

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Error obteniendo información: " + e.getMessage());
        }
    }



    /**
     *
     * Debug de placeholders - ACTUALIZADO para nuevos placeholders
     */
    private void debugPlaceholders(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "═══ DEBUG PLACEHOLDERS 2.0 ═══");

        // Verificar PlaceholderAPI
        boolean papiAvailable = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        sender.sendMessage(ChatColor.WHITE + "PlaceholderAPI: " +
                (papiAvailable ? ChatColor.GREEN + "INSTALADO" : ChatColor.RED + "NO INSTALADO"));

        if (!papiAvailable) {
            sender.sendMessage(ChatColor.RED + "Los placeholders de SurvivalCore no funcionarán sin PlaceholderAPI");
            return;
        }

        // Verificar si nuestros placeholders están registrados
        try {
            me.clip.placeholderapi.PlaceholderAPIPlugin papiPlugin =
                    (me.clip.placeholderapi.PlaceholderAPIPlugin) plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");

            boolean scoreRegistered = papiPlugin.getLocalExpansionManager().getExpansion("score") != null;

            sender.sendMessage(ChatColor.WHITE + "Expansión 'score': " +
                    (scoreRegistered ? ChatColor.GREEN + "REGISTRADA" : ChatColor.RED + "NO REGISTRADA"));

            if (scoreRegistered) {
                sender.sendMessage(ChatColor.GREEN + "✅ Placeholders disponibles (v2.0):");
                sender.sendMessage(ChatColor.YELLOW + "Sistema de Rankup:");
                sender.sendMessage(ChatColor.WHITE + "  %score_rank% - ID del rango actual");
                sender.sendMessage(ChatColor.WHITE + "  %score_rank_display% - Nombre del rango");
                sender.sendMessage(ChatColor.WHITE + "  %score_next_rank% - Siguiente rango");
                sender.sendMessage(ChatColor.WHITE + "  %score_rankup_progress% - Porcentaje de progreso");
                sender.sendMessage(ChatColor.WHITE + "  %score_rankup_progress_bar% - Barra de progreso");
                sender.sendMessage(ChatColor.WHITE + "  %score_rank_order% - Orden del rango");
                sender.sendMessage(ChatColor.WHITE + "  %score_is_max_rank% - ¿Es rango máximo?");
                sender.sendMessage(ChatColor.WHITE + "  %score_total_ranks% - Total de rangos");

                sender.sendMessage(ChatColor.YELLOW + "Datos del jugador:");
                sender.sendMessage(ChatColor.WHITE + "  %score_farming_level% - Nivel de farming");
                sender.sendMessage(ChatColor.WHITE + "  %score_mining_level% - Nivel de minería");
                sender.sendMessage(ChatColor.WHITE + "  %score_total_score% - Puntuación total");
                sender.sendMessage(ChatColor.WHITE + "  %score_banked_xp% - XP en banco");

                // Test de placeholder si es un jugador
                if (sender instanceof Player player) {
                    sender.sendMessage(ChatColor.YELLOW + "Test en vivo:");
                    sender.sendMessage(ChatColor.WHITE + "  Tu rango: " +
                            me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%score_rank%"));
                    sender.sendMessage(ChatColor.WHITE + "  Tu rango (display): " +
                            me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%score_rank_display%"));
                    sender.sendMessage(ChatColor.WHITE + "  Tu progreso: " +
                            me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%score_rankup_progress%"));
                }
            }

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error verificando placeholders: " + e.getMessage());
        }
    }

    /**
     * Debug de todos los sistemas
     */
    private void debugAllSystems(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "═══ DEBUG SISTEMAS GENERALES ═══");

        // Base de datos
        try {
            plugin.getDatabaseManager().testConnection();
            sender.sendMessage(ChatColor.GREEN + "✅ Base de datos: CONECTADA");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "❌ Base de datos: ERROR - " + e.getMessage());
        }

        // Managers principales
        sender.sendMessage(ChatColor.WHITE + "CooldownManager: " +
                (plugin.getCooldownManager() != null ? ChatColor.GREEN + "OK" : ChatColor.RED + "NULL"));
        sender.sendMessage(ChatColor.WHITE + "CropExperienceConfig: " +
                (plugin.getCropExperienceConfig() != null ? ChatColor.GREEN + "OK" : ChatColor.RED + "NULL"));
        sender.sendMessage(ChatColor.WHITE + "MiningConfig: " +
                (plugin.getMiningConfig() != null ? ChatColor.GREEN + "OK" : ChatColor.RED + "NULL"));
        sender.sendMessage(ChatColor.WHITE + "XpTransferManager: " +
                (plugin.getXpTransferManager() != null ? ChatColor.GREEN + "OK" : ChatColor.RED + "NULL"));
        sender.sendMessage(ChatColor.WHITE + "SellWandManager: " +
                (plugin.getSellWandManager() != null ? ChatColor.GREEN + "OK" : ChatColor.RED + "NULL"));
        sender.sendMessage(ChatColor.WHITE + "RankupManager: " +
                (plugin.getRankupManager() != null ? ChatColor.GREEN + "OK (v2.0)" : ChatColor.RED + "NULL"));

        // Plugins externos
        sender.sendMessage(ChatColor.YELLOW + "Plugins externos:");
        sender.sendMessage(ChatColor.WHITE + "  LuckPerms: " + getPluginStatus("LuckPerms"));
        sender.sendMessage(ChatColor.WHITE + "  PlaceholderAPI: " + getPluginStatus("PlaceholderAPI"));
        sender.sendMessage(ChatColor.WHITE + "  Vault: " + getPluginStatus("Vault"));

        // Estadísticas del servidor
        sender.sendMessage(ChatColor.YELLOW + "Servidor:");
        sender.sendMessage(ChatColor.WHITE + "  Jugadores online: " + ChatColor.YELLOW +
                plugin.getServer().getOnlinePlayers().size());
        sender.sendMessage(ChatColor.WHITE + "  Versión del plugin: " + ChatColor.YELLOW +
                plugin.getDescription().getVersion());
    }

    /**
     * Debug de información del jugador - ACTUALIZADO para Rankup 2.0
     */
    private void debugPlayerInfo(Player player) {
        player.sendMessage(ChatColor.AQUA + "═══ DEBUG INFORMACIÓN DEL JUGADOR ═══");

        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

            if (userData == null) {
                player.sendMessage(ChatColor.RED + "❌ No se pudieron cargar tus datos de la base de datos");
                return;
            }

            player.sendMessage(ChatColor.GREEN + "✅ Datos cargados correctamente");
            player.sendMessage(ChatColor.WHITE + "UUID: " + ChatColor.GRAY + userData.getUuid());
            player.sendMessage(ChatColor.WHITE + "Nombre: " + ChatColor.YELLOW + userData.getNombre());
            player.sendMessage(ChatColor.WHITE + "Farming: " + ChatColor.GREEN + "Nivel " + userData.getFarmingLevel() +
                    " (" + userData.getFarmingXP() + " XP)");
            player.sendMessage(ChatColor.WHITE + "Minería: " + ChatColor.AQUA + "Nivel " + userData.getMiningLevel() +
                    " (" + userData.getMiningXP() + " XP)");
            player.sendMessage(ChatColor.WHITE + "XP bankeada: " + ChatColor.GOLD + userData.getBankedXp());
            player.sendMessage(ChatColor.WHITE + "Banco nivel: " + ChatColor.GOLD + userData.getBankLevel());
            player.sendMessage(ChatColor.WHITE + "Capacidad banco: " + ChatColor.GOLD + userData.getBankCapacity());

            // Información personal
            player.sendMessage(ChatColor.YELLOW + "Información personal:");
            player.sendMessage(ChatColor.WHITE + "  Cumpleaños: " +
                    (userData.getCumpleaños() != null ? userData.getCumpleaños() : "No establecido"));
            player.sendMessage(ChatColor.WHITE + "  Género: " +
                    (userData.getGenero() != null ? userData.getGenero() : "No establecido"));
            player.sendMessage(ChatColor.WHITE + "  País: " +
                    (userData.getPais() != null ? userData.getPais() : "No establecido"));

            // Información de rankup si está disponible - ACTUALIZADO
            if (plugin.isRankupSystemEnabled()) {
                try {
                    RankupManager rankupManager = plugin.getRankupManager();
                    String currentRank = rankupManager.getCurrentRank(player);
                    player.sendMessage(ChatColor.YELLOW + "Sistema de Rankup 2.0:");
                    player.sendMessage(ChatColor.WHITE + "  Rango actual: " +
                            (currentRank != null ? currentRank : "Sin detectar"));

                    if (currentRank != null) {
                        var rankData = rankupManager.getRanks().get(currentRank);
                        if (rankData != null) {
                            player.sendMessage(ChatColor.WHITE + "  Display: " + rankData.getDisplayName());
                            player.sendMessage(ChatColor.WHITE + "  Orden: " + rankData.getOrder());
                            player.sendMessage(ChatColor.WHITE + "  Siguiente: " +
                                    (rankData.hasNextRank() ? rankData.getNextRank() : "Rango máximo"));
                        }
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "  Error obteniendo info de rankup: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error obteniendo información: " + e.getMessage());
            plugin.getLogger().severe("Error en debug de jugador " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Obtiene el estado de un plugin
     */
    private String getPluginStatus(String pluginName) {
        var pluginManager = plugin.getServer().getPluginManager();
        var targetPlugin = pluginManager.getPlugin(pluginName);

        if (targetPlugin == null) {
            return ChatColor.RED + "NO INSTALADO";
        } else if (targetPlugin.isEnabled()) {
            return ChatColor.GREEN + "HABILITADO (" + targetPlugin.getDescription().getVersion() + ")";
        } else {
            return ChatColor.YELLOW + "DESHABILITADO";
        }
    }

    /**
     * Muestra la puntuación del jugador - ACTUALIZADO para Rankup 2.0
     */
    private void showPlayerScore(Player player) {
        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());

            if (userData == null) {
                player.sendMessage(ChatColor.RED + "Error: No se pudieron cargar tus datos.");
                return;
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.YELLOW + "TU PUNTUACIÓN" + ChatColor.GOLD + " ═══════");
            player.sendMessage("");

            // Información básica
            player.sendMessage(ChatColor.WHITE + "👤 Jugador: " + ChatColor.YELLOW + player.getName());

            // Información de rankup si está disponible - ACTUALIZADO
            if (plugin.isRankupSystemEnabled()) {
                try {
                    RankupManager rankupManager = plugin.getRankupManager();
                    String currentRank = rankupManager.getCurrentRank(player);
                    if (currentRank != null) {
                        var rankData = rankupManager.getRanks().get(currentRank);
                        String displayName = rankData != null ? rankData.getDisplayName() : currentRank;
                        player.sendMessage(ChatColor.WHITE + "🏆 Rango: " + displayName);

                        if (rankData != null && rankData.hasNextRank()) {
                            var nextRankData = rankupManager.getRanks().get(rankData.getNextRank());
                            String nextDisplay = nextRankData != null ? nextRankData.getDisplayName() : rankData.getNextRank();
                            player.sendMessage(ChatColor.WHITE + "⬆️ Siguiente: " + nextDisplay);
                        } else {
                            player.sendMessage(ChatColor.LIGHT_PURPLE + "⭐ ¡Rango máximo alcanzado!");
                        }
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Error obteniendo información de rango");
                }
            }

            player.sendMessage("");

            // Niveles y experiencia
            player.sendMessage(ChatColor.WHITE + "🌾 Farming - Nivel " + ChatColor.GREEN + userData.getFarmingLevel() +
                    ChatColor.WHITE + " (" + ChatColor.YELLOW + userData.getFarmingXP() + " XP" + ChatColor.WHITE + ")");
            player.sendMessage(ChatColor.WHITE + "⛏️ Minería - Nivel " + ChatColor.AQUA + userData.getMiningLevel() +
                    ChatColor.WHITE + " (" + ChatColor.YELLOW + userData.getMiningXP() + " XP" + ChatColor.WHITE + ")");

            player.sendMessage("");

            // Banco de XP
            player.sendMessage(ChatColor.WHITE + "🏦 Banco XP: " + ChatColor.GOLD + userData.getBankedXp() +
                    ChatColor.WHITE + "/" + ChatColor.GOLD + userData.getBankCapacity() +
                    ChatColor.WHITE + " (Nivel " + ChatColor.GREEN + userData.getBankLevel() + ChatColor.WHITE + ")");

            // Información personal
            player.sendMessage("");
            player.sendMessage(ChatColor.WHITE + "🎂 Cumpleaños: " + ChatColor.LIGHT_PURPLE +
                    (userData.getCumpleaños() != null ? userData.getCumpleaños() : "No establecido"));
            player.sendMessage(ChatColor.WHITE + "👫 Género: " + ChatColor.LIGHT_PURPLE +
                    (userData.getGenero() != null ? userData.getGenero() : "No establecido"));
            player.sendMessage(ChatColor.WHITE + "🌍 País: " + ChatColor.LIGHT_PURPLE +
                    (userData.getPais() != null ? userData.getPais() : "Detectando..."));

            // Cálculos adicionales
            long totalXp = userData.getFarmingXP() + userData.getMiningXP();
            player.sendMessage("");
            player.sendMessage(ChatColor.WHITE + "📊 Puntuación total: " + ChatColor.GOLD + totalXp + " XP");

            // Próximo cumpleaños
            if (userData.getCumpleaños() != null) {
                try {
                    LocalDate birthday = LocalDate.parse(userData.getCumpleaños(), formatter);
                    LocalDate today = LocalDate.now();
                    LocalDate nextBirthday = birthday.withYear(today.getYear());
                    if (nextBirthday.isBefore(today) || nextBirthday.isEqual(today)) {
                        nextBirthday = nextBirthday.plusYears(1);
                    }
                    long daysUntil = ChronoUnit.DAYS.between(today, nextBirthday);

                    if (daysUntil == 0) {
                        player.sendMessage(ChatColor.GREEN + "🎉 ¡HOY ES TU CUMPLEAÑOS! 🎉");
                    } else {
                        player.sendMessage(ChatColor.WHITE + "⏰ Días hasta tu cumpleaños: " + ChatColor.YELLOW + daysUntil);
                    }
                } catch (Exception ignored) {
                    // Ignorar errores de fecha
                }
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error cargando tu puntuación. Contacta a un administrador.");
            plugin.getLogger().severe("Error mostrando puntuación para " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Muestra la versión del plugin - ACTUALIZADA
     */
    private void showVersion(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "═══════ " + ChatColor.WHITE + "SURVIVALCORE" + ChatColor.AQUA + " ═══════");
        sender.sendMessage(ChatColor.WHITE + "Versión: " + ChatColor.YELLOW + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.WHITE + "Autor: " + ChatColor.GREEN + "Brocolitx");
        sender.sendMessage(ChatColor.WHITE + "Descripción: " + ChatColor.GRAY + plugin.getDescription().getDescription());
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "Sistemas activos:");
        sender.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "Sistema de experiencia");
        sender.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "Banco de XP");
        sender.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "Transferencias de XP");
        sender.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "SellWands");
        sender.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "Cheques de XP");
        sender.sendMessage((plugin.isRankupSystemEnabled() ? ChatColor.GREEN + "✓ " : ChatColor.RED + "✗ ") +
                ChatColor.WHITE + "Sistema de Rankup 2.0");

        if (plugin.isRankupSystemEnabled()) {
            RankupManager rankupManager = plugin.getRankupManager();
            sender.sendMessage(ChatColor.GRAY + "  └ " + rankupManager.getRanks().size() + " rangos configurados");
            if (rankupManager.isPlaceholderAPIEnabled()) {
                sender.sendMessage(ChatColor.GRAY + "  └ PlaceholderAPI integrado");
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "═══════════════════════════════════");
    }

    /**
     * Manejadores de comandos específicos (birthday, gender, country)
     * Simplificados para que deleguen a sus comandos específicos
     */

    private void handleBirthday(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Usa " + ChatColor.WHITE + "/birthday" + ChatColor.YELLOW + " para gestionar tu cumpleaños.");
    }

    private void handleGender(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Usa " + ChatColor.WHITE + "/genero" + ChatColor.YELLOW + " para gestionar tu género.");
    }


    private void handleCountry(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "La detección de país es automática al unirse al servidor.");
    }

    private void handleGuiReload(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.reload")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "🔄 Iniciando recarga específica de GUIs...");

        try {
            long startTime = System.currentTimeMillis();

            // 1. Verificar archivo antes de recargar
            File guisFile = new File(plugin.getDataFolder(), "guis.yml");
            if (!guisFile.exists()) {
                sender.sendMessage(ChatColor.RED + "❌ Archivo guis.yml no encontrado.");
                return;
            }

            sender.sendMessage(ChatColor.GRAY + "• Recargando archivo guis.yml...");

            // 2. Recargar configuración de GUIs
            plugin.reloadGuisConfig();
            sender.sendMessage(ChatColor.GREEN + "  ✓ Archivo guis.yml cargado");

            // 3. Reinicializar todos los GUIs
            sender.sendMessage(ChatColor.GRAY + "• Reinicializando sistemas de GUI...");

            GeneroGUI.initialize(plugin);
            sender.sendMessage(ChatColor.GREEN + "  ✓ GeneroGUI reinicializado");

            ProfileGUI.initialize(plugin);
            sender.sendMessage(ChatColor.GREEN + "  ✓ ProfileGUI reinicializado");

            long duration = System.currentTimeMillis() - startTime;

            // 4. Verificar configuración cargada
            boolean generoEnabled = plugin.getGuisConfig().getBoolean("gender_gui.enabled", true);
            boolean profileEnabled = plugin.getGuisConfig().getBoolean("profile_gui.enabled", true);

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + "✅ Recarga de GUIs completada exitosamente");
            sender.sendMessage(ChatColor.GRAY + "Tiempo: " + duration + "ms");
            sender.sendMessage("");

            sender.sendMessage(ChatColor.AQUA + "📊 Estado de los GUIs:");
            sender.sendMessage(ChatColor.WHITE + "GeneroGUI: " + (generoEnabled ? ChatColor.GREEN + "Habilitado" : ChatColor.RED + "Deshabilitado"));
            sender.sendMessage(ChatColor.WHITE + "ProfileGUI: " + (profileEnabled ? ChatColor.GREEN + "Habilitado" : ChatColor.RED + "Deshabilitado"));

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + "🎯 Los cambios se aplicarán inmediatamente al abrir los menús");

            // Log en consola
            plugin.getLogger().info("GUIs recargados específicamente por " + sender.getName() + " en " + duration + "ms");

        } catch (Exception e) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "❌ Error recargando GUIs:");
            sender.sendMessage(ChatColor.RED + e.getMessage());
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "💡 Consejos:");
            sender.sendMessage(ChatColor.GRAY + "• Verifica que guis.yml tenga sintaxis YAML válida");
            sender.sendMessage(ChatColor.GRAY + "• Usa un validador YAML online para verificar el archivo");
            sender.sendMessage(ChatColor.GRAY + "• Revisa la consola para más detalles del error");

            plugin.getLogger().severe("Error en recarga específica de GUIs por " + sender.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Debug de estadísticas del jugador
     */
    private void debugPlayerStatistics(Player target) {
        debugPlayerStatistics(target, target);
    }

    private void debugPlayerStatistics(Player target, CommandSender admin) {
        if (!plugin.isRankupSystemEnabled()) {
            admin.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            return;
        }

        // Verificar si el admin es un Player para poder usar el método correcto
        if (admin instanceof Player) {
            plugin.getRankupManager().debugPlayerStatistics(target, (Player) admin);
        } else {
            // Para console, crear una versión simplificada
            admin.sendMessage(ChatColor.AQUA + "═══ DEBUG ESTADÍSTICAS - " + target.getName() + " ═══");

            try {
                // Estadísticas básicas de Minecraft
                admin.sendMessage("Nivel: " + target.getLevel());
                admin.sendMessage("Mobs matados: " + target.getStatistic(org.bukkit.Statistic.MOB_KILLS));
                admin.sendMessage("Animales criados: " + target.getStatistic(org.bukkit.Statistic.ANIMALS_BRED));
                admin.sendMessage("Peces pescados: " + target.getStatistic(org.bukkit.Statistic.FISH_CAUGHT));

                // Usar reflexión para obtener el método getTotalBlocksMined
                java.lang.reflect.Method method = plugin.getRankupManager().getClass()
                        .getDeclaredMethod("getTotalBlocksMined", Player.class);
                method.setAccessible(true);
                double totalMined = (Double) method.invoke(plugin.getRankupManager(), target);
                admin.sendMessage("Total bloques minados (calculado): " + totalMined);

            } catch (Exception e) {
                admin.sendMessage("Error obteniendo estadísticas: " + e.getMessage());
            }

            admin.sendMessage("═══════════════════════════════════");
        }
    }
    /**
     * Debug específico de placeholders
     */
    private void debugPlaceholdersCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden probar placeholders desde el comando debug");
            return;
        }

        debugPlaceholders((Player) sender);
    }

    private void debugPlaceholders(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "🔌 " + ChatColor.BOLD + "DEBUG PLACEHOLDERS");
        player.sendMessage(ChatColor.GRAY + "════════════════════════════════════════");

        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            player.sendMessage(ChatColor.RED + "❌ PlaceholderAPI no está instalado");
            return;
        }

        // Lista de placeholders a probar
        String[] testPlaceholders = {
                "%player_name%",
                "%player_level%",
                "%statistic_mine_block%",
                "%statistic_mob_kills%",
                "%statistic_animals_bred%",
                "%statistic_fish_caught%",
                "%vault_eco_balance%",
                "%mcmmo_level_mining%",
                "%mcmmo_power_level%"
        };

        for (String placeholder : testPlaceholders) {
            try {
                String result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);

                // Verificar si el placeholder fue procesado
                if (result.equals(placeholder)) {
                    player.sendMessage(ChatColor.RED + "  ✗ " + placeholder + " = " + ChatColor.GRAY + "No procesado");
                } else {
                    player.sendMessage(ChatColor.GREEN + "  ✓ " + placeholder + " = " + ChatColor.WHITE + result);
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "  ❌ " + placeholder + " = ERROR: " + e.getMessage());
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "💡 Si ves 'No procesado', significa que falta una expansión");
        player.sendMessage(ChatColor.GRAY + "Instala: /papi ecloud download Player");
        player.sendMessage(ChatColor.GRAY + "         /papi ecloud download Vault");
        player.sendMessage(ChatColor.GRAY + "════════════════════════════════════════");
    }
    /**
     * Debug específico de requisitos de rankup
     */
    private void debugRequirements(Player player) {
        if (!plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "📋 " + ChatColor.BOLD + "DEBUG REQUISITOS DE RANKUP");
        player.sendMessage(ChatColor.GRAY + "════════════════════════════════════════");

        try {
            var rankupManager = plugin.getRankupManager();
            String currentRank = rankupManager.getCurrentRank(player);

            if (currentRank == null) {
                player.sendMessage(ChatColor.RED + "❌ No se pudo detectar tu rango actual");
                return;
            }

            var rankData = rankupManager.getRanks().get(currentRank);
            if (rankData == null || rankData.getNextRank() == null) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "⭐ Ya tienes el rango máximo");
                return;
            }

            player.sendMessage(ChatColor.WHITE + "🎯 Rango actual: " + ChatColor.YELLOW + currentRank);
            player.sendMessage(ChatColor.WHITE + "⬆️ Siguiente: " + ChatColor.GREEN + rankData.getNextRank());
            player.sendMessage("");

            // Probar cada requisito individualmente
            Map<String, Object> requirements = rankData.getRequirements();

            for (Map.Entry<String, Object> req : requirements.entrySet()) {
                String type = req.getKey();
                double required = ((Number) req.getValue()).doubleValue();

                try {
                    // Usar reflexión para llamar getCurrentRequirementValue
                    java.lang.reflect.Method method = rankupManager.getClass()
                            .getDeclaredMethod("getCurrentRequirementValue", Player.class, String.class);
                    method.setAccessible(true);

                    double current = (Double) method.invoke(rankupManager, player, type);

                    String status = current >= required ?
                            ChatColor.GREEN + "✓" : ChatColor.RED + "✗";

                    String reqName = type.replace("_", " ");
                    player.sendMessage(status + ChatColor.WHITE + " " + reqName + ": " +
                            ChatColor.YELLOW + String.format("%.0f", current) +
                            ChatColor.GRAY + "/" +
                            ChatColor.WHITE + String.format("%.0f", required));

                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "❌ " + type + ": Error - " + e.getMessage());
                }
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error en debug de requisitos: " + e.getMessage());
            e.printStackTrace();
        }

        player.sendMessage(ChatColor.GRAY + "════════════════════════════════════════");
    }
    /**
     * Actualiza el método showHelp para incluir los nuevos comandos
     */
    private void showHelp(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        // Comandos básicos - 🗑️ REMOVIDO "/score coords"
        List<String> basicCommands = Arrays.asList(
                ChatColor.WHITE + "/score" + ChatColor.GRAY + " - Ver tu puntuación",
                ChatColor.WHITE + "/score version" + ChatColor.GRAY + " - Ver versión del plugin",
                ChatColor.WHITE + "/score help [página]" + ChatColor.GRAY + " - Mostrar ayuda"
        );

        // Comandos administrativos
        List<String> adminCommands = new ArrayList<>();
        if (sender.hasPermission("survivalcore.reload")) {
            adminCommands.add(ChatColor.WHITE + "/score reload" + ChatColor.GRAY + " - Recarga completa del plugin");
            adminCommands.add(ChatColor.WHITE + "/score reloadrankup" + ChatColor.GRAY + " - Recarga solo el sistema de rankup");
            adminCommands.add(ChatColor.WHITE + "/score reloadguis" + ChatColor.GRAY + " - Recarga solo los GUIs");
        }
        if (sender.hasPermission("survivalcore.debug")) {
            adminCommands.add(ChatColor.WHITE + "/score debug [tipo]" + ChatColor.GRAY + " - Comandos de debug");
            adminCommands.add(ChatColor.WHITE + "/score status" + ChatColor.GRAY + " - Ver estado de sistemas");
        }
        if (sender.hasPermission("survivalcore.admin")) {
            adminCommands.add(ChatColor.WHITE + "/score emergency" + ChatColor.GRAY + " - Reinicio de emergencia");
        }

        // Combinar comandos
        List<String> allCommands = new ArrayList<>(basicCommands);
        allCommands.addAll(adminCommands);

        int commandsPerPage = 8;
        int totalPages = (int) Math.ceil((double) allCommands.size() / commandsPerPage);

        if (page < 1 || page > totalPages) {
            page = 1;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.WHITE + "AYUDA DE SCORE" + ChatColor.GOLD + " ═══════");
        sender.sendMessage(ChatColor.YELLOW + "Página " + page + " de " + totalPages);
        sender.sendMessage("");

        int start = (page - 1) * commandsPerPage;
        int end = Math.min(start + commandsPerPage, allCommands.size());

        for (int i = start; i < end; i++) {
            sender.sendMessage(allCommands.get(i));
        }

        sender.sendMessage("");
        if (page < totalPages) {
            sender.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/score help " + (page + 1) +
                    ChatColor.GRAY + " para la siguiente página.");
        }

        // Información específica de rankup si está disponible
        if (plugin.isRankupSystemEnabled() && sender.hasPermission("survivalcore.reload")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "💡 Sistema de Rankup 2.0:");
            sender.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + "/score reloadrankup" +
                    ChatColor.GRAY + " - Recarga rápida solo del sistema de rankup");
            sender.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + "/score debug rankup" +
                    ChatColor.GRAY + " - Debug específico del sistema de rankup");
        }

        // 🆕 MOSTRAR COMANDOS DE COORDENADAS COMO COMANDOS SEPARADOS
        if (sender.hasPermission("survivalcore.coords")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "💡 Comandos de utilidad:");
            sender.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + "/coords" +
                    ChatColor.GRAY + " - Obtener coordenadas clickeables para copiar");
            sender.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + "/coordenadas" +
                    ChatColor.GRAY + " - Alias en español del comando anterior");
        }

        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "version", "help"
            ));

            // Comandos administrativos básicos
            if (sender.hasPermission("survivalcore.reload")) {
                completions.addAll(Arrays.asList("reload", "reloadrankup", "reloadguis"));
            }

            // Comandos de debug
            if (sender.hasPermission("survivalcore.debug")) {
                completions.addAll(Arrays.asList("debug", "status"));
            }

            // Comandos de emergencia
            if (sender.hasPermission("survivalcore.admin")) {
                completions.add("emergency");
            }

            // Comandos de información personal (siempre disponibles)
            completions.addAll(Arrays.asList("birthday", "gender", "country"));

            return completions.stream()
                    .filter(completion -> completion.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            List<String> debugCommands = new ArrayList<>(Arrays.asList(
                    "rankup", "placeholders", "systems", "menus",
                    "rankup_detection", "rd"  // 🆕 NUEVOS COMANDOS
            ));
            if (sender instanceof Player) {
                debugCommands.add("player");
            }
            return debugCommands.stream()
                    .filter(completion -> completion.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3 &&
                (args[1].equalsIgnoreCase("rankup_detection") || args[1].equalsIgnoreCase("rd"))) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Tab completion para help (páginas)
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return Arrays.asList("1", "2", "3").stream()
                    .filter(completion -> completion.startsWith(args[1]))
                    .toList();
        }

        return new ArrayList<>();
    }
    /**
     * 🆕 NUEVO: Maneja la recarga específica del sistema de rankup
     */
    private void handleRankupReload(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.reload")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
            return;
        }

        if (!plugin.isRankupSystemEnabled()) {
            sender.sendMessage(ChatColor.RED + "❌ El sistema de Rankup no está disponible.");
            sender.sendMessage(ChatColor.GRAY + "Motivo: LuckPerms no está instalado o habilitado.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "🔄 Recargando configuración de Rankup 2.0...");

        try {
            long startTime = System.currentTimeMillis();

            // Verificar archivo antes de recargar
            File rankupsFile = new File(plugin.getDataFolder(), "rankups.yml");
            if (!rankupsFile.exists()) {
                sender.sendMessage(ChatColor.RED + "❌ Archivo rankups.yml no encontrado.");
                sender.sendMessage(ChatColor.YELLOW + "💡 Creando archivo por defecto...");

                plugin.getRankupManager().createDefaultConfigFile();

                if (!rankupsFile.exists()) {
                    sender.sendMessage(ChatColor.RED + "❌ No se pudo crear rankups.yml");
                    return;
                }
                sender.sendMessage(ChatColor.GREEN + "✓ Archivo rankups.yml creado");
            }

            // Obtener información previa para comparación
            RankupManager rankupManager = plugin.getRankupManager();
            int oldRanksCount = rankupManager.getRanks().size();
            int oldPrestigesCount = rankupManager.getPrestiges().size();
            long oldCooldown = rankupManager.getCooldownTime();

            // Realizar recarga
            rankupManager.reloadConfig();

            // Obtener nueva información
            int newRanksCount = rankupManager.getRanks().size();
            int newPrestigesCount = rankupManager.getPrestiges().size();
            long newCooldown = rankupManager.getCooldownTime();

            long duration = System.currentTimeMillis() - startTime;

            // Mostrar resultado
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + "✅ Sistema de Rankup 2.0 recargado exitosamente");
            sender.sendMessage(ChatColor.GRAY + "Tiempo: " + duration + "ms");
            sender.sendMessage("");

            // Mostrar cambios
            sender.sendMessage(ChatColor.AQUA + "📊 Estadísticas actualizadas:");
            sender.sendMessage(formatStatChange("Rangos", oldRanksCount, newRanksCount));
            sender.sendMessage(formatStatChange("Prestiges", oldPrestigesCount, newPrestigesCount));
            sender.sendMessage(formatStatChange("Cooldown", oldCooldown / 1000 + "s", newCooldown / 1000 + "s"));

            sender.sendMessage(ChatColor.WHITE + "PlaceholderAPI: " +
                    (rankupManager.isPlaceholderAPIEnabled() ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
            sender.sendMessage(ChatColor.WHITE + "Efectos: " +
                    (rankupManager.areEffectsEnabled() ? ChatColor.GREEN + "Habilitados" : ChatColor.RED + "Deshabilitados"));
            sender.sendMessage(ChatColor.WHITE + "Broadcast: " +
                    (rankupManager.isBroadcastEnabled() ? ChatColor.GREEN + "Habilitado" : ChatColor.RED + "Deshabilitado"));

            // Validar configuración
            if (newRanksCount == 0) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.RED + "⚠️ ADVERTENCIA: No se cargaron rangos");
                sender.sendMessage(ChatColor.YELLOW + "Verifica que rankups.yml tenga una configuración válida");
            }

            // Mensaje de éxito final
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GREEN + "🎯 El sistema de Rankup está listo para usar");

            // Log en consola
            plugin.getLogger().info("Sistema de Rankup recargado por " + sender.getName() +
                    " - " + newRanksCount + " rangos activos");

        } catch (Exception e) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "❌ Error recargando sistema de Rankup:");
            sender.sendMessage(ChatColor.RED + e.getMessage());
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "💡 Consejos:");
            sender.sendMessage(ChatColor.GRAY + "• Verifica que rankups.yml tenga sintaxis YAML válida");
            sender.sendMessage(ChatColor.GRAY + "• Asegúrate de que LuckPerms esté funcionando");
            sender.sendMessage(ChatColor.GRAY + "• Usa /score debug rankup para más información");

            plugin.getLogger().severe("Error recargando Rankup solicitado por " + sender.getName() + ": " + e.getMessage());
        }
    }

    /**
     * 🆕 NUEVO: Maneja reinicio de emergencia de sistemas
     */
    private void handleEmergencyRestart(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para usar comandos de emergencia.");
            return;
        }

        sender.sendMessage(ChatColor.RED + "🚨 Iniciando reinicio de emergencia...");
        sender.sendMessage(ChatColor.YELLOW + "⚠️ Esto reiniciará sistemas críticos del plugin");

        boolean success = plugin.emergencySystemRestart();

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✅ Reinicio de emergencia completado");
            sender.sendMessage(ChatColor.GRAY + "Todos los sistemas están operativos");
        } else {
            sender.sendMessage(ChatColor.RED + "⚠️ Reinicio completado con errores");
            sender.sendMessage(ChatColor.YELLOW + "Revisa la consola para más detalles");
        }
    }

    /**
     * 🆕 NUEVO: Muestra el estado actual de todos los sistemas
     */
    private void handleSystemStatus(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.debug")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para ver el estado del sistema.");
            return;
        }

        sender.sendMessage(ChatColor.AQUA + "═══ ESTADO DE SISTEMAS ═══");

        Map<String, String> status = plugin.getSystemsStatus();

        for (Map.Entry<String, String> entry : status.entrySet()) {
            String systemName = entry.getKey();
            String systemStatus = entry.getValue();

            ChatColor color = systemStatus.startsWith("OK") ? ChatColor.GREEN :
                    systemStatus.contains("ERROR") ? ChatColor.RED :
                            systemStatus.equals("NULL") ? ChatColor.DARK_RED :
                                    ChatColor.YELLOW;

            sender.sendMessage(ChatColor.WHITE + systemName + ": " + color + systemStatus);
        }

        // Información adicional del servidor
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Información del servidor:");
        sender.sendMessage(ChatColor.WHITE + "Jugadores online: " + ChatColor.GREEN +
                plugin.getServer().getOnlinePlayers().size());
        sender.sendMessage(ChatColor.WHITE + "Versión de SurvivalCore: " + ChatColor.GREEN +
                plugin.getDescription().getVersion());

        long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage(ChatColor.WHITE + "Memoria: " + ChatColor.YELLOW +
                usedMemory + "MB/" + maxMemory + "MB");
    }

    /**
     * Formatea un cambio en estadísticas para mostrar diferencias
     */
    private String formatStatChange(String name, Object oldValue, Object newValue) {
        String oldStr = String.valueOf(oldValue);
        String newStr = String.valueOf(newValue);

        if (oldStr.equals(newStr)) {
            return ChatColor.WHITE + name + ": " + ChatColor.GRAY + newStr + " (sin cambios)";
        } else {
            return ChatColor.WHITE + name + ": " + ChatColor.YELLOW + oldStr +
                    ChatColor.GRAY + " → " + ChatColor.GREEN + newStr;
        }
    }

    private void handleCoordinates(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo los jugadores pueden obtener sus coordenadas.");
            return;
        }

        Player player = (Player) sender;
        var location = player.getLocation();

        // Obtener coordenadas redondeadas
        int x = (int) Math.round(location.getX());
        int y = (int) Math.round(location.getY());
        int z = (int) Math.round(location.getZ());
        String world = location.getWorld() != null ? location.getWorld().getName() : "unknown";

        // Formatear las coordenadas para copiar
        String coordsText = x + " " + y + " " + z;

        // Crear mensaje clickeable usando hover y click events
        net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent("📍 Tus coordenadas: ");
        message.setColor(net.md_5.bungee.api.ChatColor.YELLOW);

        net.md_5.bungee.api.chat.TextComponent clickableCoords = new net.md_5.bungee.api.chat.TextComponent(coordsText);
        clickableCoords.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        clickableCoords.setBold(true);

        // Añadir evento de click para copiar al portapapeles
        clickableCoords.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.COPY_TO_CLIPBOARD,
                coordsText
        ));

        // Añadir hover text
        clickableCoords.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.hover.content.Text("§aHaz clic para copiar: §f" + coordsText)
        ));

        net.md_5.bungee.api.chat.TextComponent instruction = new net.md_5.bungee.api.chat.TextComponent(" §7(Haz clic para copiar)");

        // Combinar los componentes
        message.addExtra(clickableCoords);
        message.addExtra(instruction);

        // Enviar mensaje
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.WHITE + "TUS COORDENADAS" + ChatColor.GOLD + " ═══════");
        player.spigot().sendMessage(message);
        player.sendMessage(ChatColor.WHITE + "🌍 Mundo: " + ChatColor.GREEN + world);
        player.sendMessage(ChatColor.GRAY + "💡 Haz clic en las coordenadas para copiarlas");
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════════════");

        // Log en consola si es necesario
        plugin.getLogger().info("Jugador " + player.getName() + " consultó sus coordenadas: " + coordsText + " en " + world);
    }

}