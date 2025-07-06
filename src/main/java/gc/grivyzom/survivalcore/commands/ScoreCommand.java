package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Comando principal /score con soporte completo para reload de todos los sistemas
 *
 * @author Brocolitx
 * @version 2.0
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
            case "birthday", "cumpleanos" -> handleBirthday(sender, args);
            case "gender", "genero" -> handleGender(sender, args);
            case "country", "pais" -> handleCountry(sender, args);
            case "help", "ayuda" -> showHelp(sender, args);
            case "debug" -> handleDebug(sender, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /score help para ver la ayuda.");
                return true;
            }
        }

        return true;
    }

    /**
     * Maneja el reload completo del plugin - VERSIÓN COMPLETA
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
            // 1. Recargar configuración principal
            plugin.reloadConfig();
            report.append(ChatColor.GREEN + "✓ Configuración principal\n");

            // 2. Actualizar configuración interna (incluye rankup)
            plugin.updateInternalConfig();
            report.append(ChatColor.GREEN + "✓ Configuración interna\n");

            // 3. Verificar estado del sistema de rankup
            if (plugin.isRankupSystemEnabled()) {
                try {
                    var rankupManager = plugin.getRankupManager();
                    int ranksCount = rankupManager.getRankups().size();
                    int prestigesCount = rankupManager.getPrestiges().size();

                    report.append(ChatColor.GREEN + "✓ Sistema de rankup (" + ranksCount + " rangos, " + prestigesCount + " prestiges)\n");

                    if (rankupManager.isPlaceholderAPIEnabled()) {
                        report.append(ChatColor.GREEN + "✓ PlaceholderAPI integrado\n");
                    } else {
                        report.append(ChatColor.YELLOW + "⚠ PlaceholderAPI no disponible\n");
                    }
                } catch (Exception e) {
                    hasErrors = true;
                    report.append(ChatColor.RED + "✗ Sistema de rankup: ").append(e.getMessage()).append("\n");
                    plugin.getLogger().severe("Error verificando rankup: " + e.getMessage());
                }
            } else {
                report.append(ChatColor.YELLOW + "⚠ Sistema de rankup: No disponible (LuckPerms requerido)\n");
            }

            // 4. Verificar configuraciones específicas
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

            // 5. Verificar managers
            report.append(verifyManager("XpTransferManager", plugin.getXpTransferManager()));
            report.append(verifyManager("SellWandManager", plugin.getSellWandManager()));
            report.append(verifyManager("XpChequeManager", plugin.getXpChequeCommand()));
            report.append(verifyManager("LecternRecipeManager", plugin.getLecternRecipeManager()));

            // 6. Verificar base de datos
            try {
                plugin.getDatabaseManager().testConnection();
                report.append(ChatColor.GREEN + "✓ Conexión a base de datos\n");
            } catch (Exception e) {
                hasErrors = true;
                report.append(ChatColor.RED + "✗ Base de datos: ").append(e.getMessage()).append("\n");
            }

            // 7. Verificar PlaceholderAPI
            try {
                if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    report.append(ChatColor.GREEN + "✓ PlaceholderAPI disponible\n");

                    // Verificar si nuestros placeholders están registrados
                    var papiPlugin = (me.clip.placeholderapi.PlaceholderAPIPlugin)
                            plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");

                    if (papiPlugin.getLocalExpansionManager().getExpansion("score") != null) {
                        report.append(ChatColor.GREEN + "✓ Placeholders de SurvivalCore registrados\n");
                    } else {
                        report.append(ChatColor.YELLOW + "⚠ Placeholders de SurvivalCore no registrados\n");
                    }
                } else {
                    report.append(ChatColor.YELLOW + "⚠ PlaceholderAPI no instalado\n");
                }
            } catch (Exception e) {
                report.append(ChatColor.YELLOW + "⚠ Error verificando PlaceholderAPI: " + e.getMessage() + "\n");
            }

        } catch (Exception e) {
            hasErrors = true;
            report.append(ChatColor.RED + "✗ Error crítico: ").append(e.getMessage()).append("\n");
            plugin.getLogger().severe("Error crítico durante reload: " + e.getMessage());
            e.printStackTrace();
        }

        long duration = System.currentTimeMillis() - startTime;

        // Mostrar reporte final
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "═══════ REPORTE DE RECARGA ═══════");
        sender.sendMessage(report.toString());

        if (hasErrors) {
            sender.sendMessage(ChatColor.RED + "⚠ Recarga completada con errores");
            sender.sendMessage(ChatColor.GRAY + "Revisa la consola para más detalles");
        } else {
            sender.sendMessage(ChatColor.GREEN + "✅ Recarga completada exitosamente");
        }

        sender.sendMessage(ChatColor.GRAY + "Tiempo: " + duration + "ms");
        sender.sendMessage(ChatColor.AQUA + "═══════════════════════════════════");

        // Log en consola
        if (hasErrors) {
            plugin.getLogger().warning("Recarga completada con errores en " + duration + "ms");
        } else {
            plugin.getLogger().info("Recarga completada exitosamente en " + duration + "ms");
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
            if (sender instanceof Player) {
                sender.sendMessage(ChatColor.WHITE + "  /score debug player - Debug de tu información");
            }
            return;
        }

        String debugType = args[1].toLowerCase();

        switch (debugType) {
            case "rankup" -> debugRankupSystem(sender);
            case "placeholders" -> debugPlaceholders(sender);
            case "systems" -> debugAllSystems(sender);
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
     * Debug del sistema de rankup
     */
    private void debugRankupSystem(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "═══ DEBUG SISTEMA RANKUP ═══");

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
            var rankupManager = plugin.getRankupManager();

            sender.sendMessage(ChatColor.GREEN + "✅ Sistema de rankup: HABILITADO");
            sender.sendMessage(ChatColor.WHITE + "Rangos cargados: " + ChatColor.YELLOW + rankupManager.getRankups().size());
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

            // Mostrar algunos rangos como ejemplo
            if (!rankupManager.getRankups().isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Primeros rangos:");
                rankupManager.getRankups().values().stream()
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
     * Debug de placeholders
     */
    private void debugPlaceholders(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "═══ DEBUG PLACEHOLDERS ═══");

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
                sender.sendMessage(ChatColor.GREEN + "✅ Placeholders disponibles:");
                sender.sendMessage(ChatColor.YELLOW + "Sistema de Rankup:");
                sender.sendMessage(ChatColor.WHITE + "  %score_rank% - Rango actual");
                sender.sendMessage(ChatColor.WHITE + "  %score_rankup_next% - Siguiente rango");
                sender.sendMessage(ChatColor.WHITE + "  %score_rankup_percentage% - Porcentaje de progreso");
                sender.sendMessage(ChatColor.WHITE + "  %score_rankup_progress_bar% - Barra de progreso");
                sender.sendMessage(ChatColor.WHITE + "  %score_rankup_is_max% - ¿Es rango máximo?");
                sender.sendMessage(ChatColor.YELLOW + "Datos del jugador:");
                sender.sendMessage(ChatColor.WHITE + "  %score_farming_level% - Nivel de farming");
                sender.sendMessage(ChatColor.WHITE + "  %score_mining_level% - Nivel de minería");
                sender.sendMessage(ChatColor.WHITE + "  %score_total_score% - Puntuación total");
                sender.sendMessage(ChatColor.WHITE + "  %score_banked_xp% - XP en banco");

                // Test de placeholder si es un jugador
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    sender.sendMessage(ChatColor.YELLOW + "Test en vivo:");
                    sender.sendMessage(ChatColor.WHITE + "  Tu rango: " +
                            me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%score_rank%"));
                    sender.sendMessage(ChatColor.WHITE + "  Tu nivel farming: " +
                            me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%score_farming_level%"));
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
                (plugin.getRankupManager() != null ? ChatColor.GREEN + "OK" : ChatColor.RED + "NULL"));

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
     * Debug de información del jugador
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

            // Información de rankup si está disponible
            if (plugin.isRankupSystemEnabled()) {
                try {
                    String currentRank = plugin.getRankupManager().getCurrentRank(player);
                    player.sendMessage(ChatColor.YELLOW + "Sistema de Rankup:");
                    player.sendMessage(ChatColor.WHITE + "  Rango actual: " +
                            (currentRank != null ? currentRank : "Sin detectar"));

                    if (currentRank != null) {
                        var rankData = plugin.getRankupManager().getRankups().get(currentRank);
                        if (rankData != null) {
                            player.sendMessage(ChatColor.WHITE + "  Display: " + rankData.getDisplayName());
                            player.sendMessage(ChatColor.WHITE + "  Orden: " + rankData.getOrder());
                            player.sendMessage(ChatColor.WHITE + "  Siguiente: " +
                                    (rankData.getNextRank() != null ? rankData.getNextRank() : "Rango máximo"));
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
     * Muestra la puntuación del jugador
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

            // Información de rankup si está disponible
            if (plugin.isRankupSystemEnabled()) {
                try {
                    String currentRank = plugin.getRankupManager().getCurrentRank(player);
                    if (currentRank != null) {
                        var rankData = plugin.getRankupManager().getRankups().get(currentRank);
                        String displayName = rankData != null ? rankData.getDisplayName() : currentRank;
                        player.sendMessage(ChatColor.WHITE + "🏆 Rango: " + displayName);

                        if (rankData != null && rankData.hasNextRank()) {
                            var nextRankData = plugin.getRankupManager().getRankups().get(rankData.getNextRank());
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
     * Muestra la versión del plugin
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
                ChatColor.WHITE + "Sistema de Rankup");
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

    /**
     * Muestra el menú de ayuda
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

        ConfigurationSection helpSection = plugin.getConfig().getConfigurationSection("help.commands");
        if (helpSection == null) {
            sender.sendMessage(ChatColor.RED + "Ayuda no configurada.");
            return;
        }

        List<String> commands = new ArrayList<>();
        for (String key : helpSection.getKeys(false)) {
            String command = helpSection.getString(key + ".command");
            String message = helpSection.getString(key + ".message");
            String permission = helpSection.getString(key + ".permission", "");

            if (permission.isEmpty() || sender.hasPermission(permission)) {
                commands.add(ChatColor.translateAlternateColorCodes('&', message));
            }
        }

        int commandsPerPage = 8;
        int totalPages = (int) Math.ceil((double) commands.size() / commandsPerPage);

        if (page < 1 || page > totalPages) {
            page = 1;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.WHITE + "AYUDA DE SCORE" + ChatColor.GOLD + " ═══════");
        sender.sendMessage(ChatColor.YELLOW + "Página " + page + " de " + totalPages);
        sender.sendMessage("");

        int start = (page - 1) * commandsPerPage;
        int end = Math.min(start + commandsPerPage, commands.size());

        for (int i = start; i < end; i++) {
            sender.sendMessage(commands.get(i));
        }

        sender.sendMessage("");
        if (page < totalPages) {
            sender.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/score help " + (page + 1) +
                    ChatColor.GRAY + " para la siguiente página.");
        }
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "version", "reload", "birthday", "gender", "country", "help"
            ));

            if (sender.hasPermission("survivalcore.debug")) {
                completions.add("debug");
            }

            return completions.stream()
                    .filter(completion -> completion.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            List<String> debugCommands = new ArrayList<>(Arrays.asList("rankup", "placeholders", "systems"));
            if (sender instanceof Player) {
                debugCommands.add("player");
            }
            return debugCommands.stream()
                    .filter(completion -> completion.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return new ArrayList<>();
    }
}