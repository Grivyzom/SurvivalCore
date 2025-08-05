package gc.grivyzom.survivalcore.rankup;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.menu.BedrockMenuManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Interfaz gráfica para el sistema de rankup 2.0 - SISTEMA HÍBRIDO
 * Detecta automáticamente Bedrock/Java y muestra el menú apropiado
 *
 * @author Brocolitx
 * @version 3.0 - Sistema Híbrido Bedrock/Java
 */
public class RankupGUI {

    /**
     * Abre el menú principal del sistema de rangos
     * NUEVA VERSIÓN: Detecta automáticamente Bedrock vs Java
     */
    public static void openMainMenu(Player player, Main plugin) {
        if (plugin.getRankupManager() == null) {
            player.sendMessage("§c❌ Sistema de rankup no disponible");
            return;
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();

            // 🎯 NUEVO: Usar sistema híbrido que detecta automáticamente
            if (rankupManager.isHybridMenuSystemAvailable()) {
                // Sistema híbrido completo disponible
                BedrockMenuManager.ClientType clientType = rankupManager.detectClientType(player);

                switch (clientType) {
                    case BEDROCK -> {
                        player.sendMessage(ChatColor.GREEN + "📱 Detectado cliente Bedrock - Abriendo menú optimizado");
                        rankupManager.openMainMenuHybrid(player);
                    }
                    case JAVA -> {
                        player.sendMessage(ChatColor.GREEN + "💻 Detectado cliente Java - Abriendo menú interactivo");
                        rankupManager.openMainMenuHybrid(player);
                    }
                    case UNKNOWN -> {
                        player.sendMessage(ChatColor.YELLOW + "🔍 Tipo de cliente desconocido - Usando menú por defecto");
                        rankupManager.openMainMenuHybrid(player);
                    }
                }
            } else if (rankupManager.isMenuSystemAvailable()) {
                // Solo menús Java disponibles
                player.sendMessage(ChatColor.YELLOW + "💻 Solo menús Java disponibles");
                var menuManager = rankupManager.getMenuManager();
                if (menuManager != null) {
                    menuManager.openMainMenu(player);
                } else {
                    openMainMenuLegacy(player, plugin);
                }
            } else {
                // Fallback a comandos
                openMainMenuLegacy(player, plugin);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo menú híbrido principal: " + e.getMessage());
            player.sendMessage("§c❌ Error abriendo menú. Contacta a un administrador.");
        }
    }

    /**
     * Abre el menú de progreso detallado
     * NUEVA VERSIÓN: Sistema híbrido
     */
    public static void openProgressMenu(Player player, Main plugin) {
        if (plugin.getRankupManager() == null) {
            player.sendMessage("§c❌ Sistema de rankup no disponible");
            return;
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();

            if (rankupManager.isHybridMenuSystemAvailable()) {
                // Usar sistema híbrido
                rankupManager.openProgressMenuHybrid(player);
            } else if (rankupManager.isMenuSystemAvailable()) {
                // Solo Java
                var menuManager = rankupManager.getMenuManager();
                if (menuManager != null) {
                    menuManager.openProgressMenu(player);
                } else {
                    openProgressMenuLegacy(player, plugin);
                }
            } else {
                // Fallback a comandos
                openProgressMenuLegacy(player, plugin);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo menú de progreso híbrido: " + e.getMessage());
            player.sendMessage("§c❌ Error abriendo menú de progreso.");
        }
    }

    /**
     * Abre la lista de todos los rangos
     * NUEVA VERSIÓN: Sistema híbrido
     */
    public static void openRanksList(Player player, Main plugin) {
        if (plugin.getRankupManager() == null) {
            player.sendMessage("§c❌ Sistema de rankup no disponible");
            return;
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();

            if (rankupManager.isHybridMenuSystemAvailable()) {
                // Para lista de rangos, usar comando ya que es más efectivo para ambos tipos
                BedrockMenuManager.ClientType clientType = rankupManager.detectClientType(player);

                if (clientType == BedrockMenuManager.ClientType.BEDROCK) {
                    player.sendMessage(ChatColor.GREEN + "📱 Mostrando lista de rangos optimizada para Bedrock");
                } else {
                    player.sendMessage(ChatColor.GREEN + "💻 Mostrando lista de rangos para Java");
                }

                player.performCommand("rankup list");

            } else if (rankupManager.isMenuSystemAvailable()) {
                var menuManager = rankupManager.getMenuManager();
                if (menuManager != null) {
                    menuManager.openRanksListMenu(player);
                } else {
                    openRanksListLegacy(player, plugin);
                }
            } else {
                openRanksListLegacy(player, plugin);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo lista de rangos híbrida: " + e.getMessage());
            player.sendMessage("§c❌ Error abriendo lista de rangos.");
        }
    }

    /**
     * Abre el menú de configuración personal
     * NUEVA VERSIÓN: Detecta tipo de cliente
     */
    public static void openSettingsMenu(Player player, Main plugin) {
        if (plugin.getRankupManager() == null) {
            player.sendMessage("§c❌ Sistema de rankup no disponible");
            return;
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();

            if (rankupManager.isHybridMenuSystemAvailable()) {
                BedrockMenuManager.ClientType clientType = rankupManager.detectClientType(player);

                if (clientType == BedrockMenuManager.ClientType.BEDROCK) {
                    // Para Bedrock, mostrar configuración simplificada
                    player.sendMessage(ChatColor.YELLOW + "📱 Configuración para Bedrock:");
                    player.sendMessage(ChatColor.WHITE + "• Los menús se optimizan automáticamente");
                    player.sendMessage(ChatColor.WHITE + "• Usa comandos para configuración avanzada");
                    player.sendMessage(ChatColor.GRAY + "Comandos: /rankup help");
                } else {
                    // Para Java, usar menú completo
                    var menuManager = rankupManager.getMenuManager();
                    if (menuManager != null) {
                        menuManager.openSettingsMenu(player);
                    } else {
                        player.sendMessage("§e⚠ Menú de configuración no disponible");
                    }
                }
            } else {
                var menuManager = rankupManager.getMenuManager();
                if (menuManager != null) {
                    menuManager.openSettingsMenu(player);
                } else {
                    player.sendMessage("§e⚠ Menú de configuración no disponible - Usa el sistema básico");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo configuración híbrida: " + e.getMessage());
            player.sendMessage("§c❌ Error abriendo configuración.");
        }
    }

    /**
     * Detecta y muestra información sobre el tipo de cliente
     */
    public static void showClientInfo(Player player, Main plugin) {
        if (plugin.getRankupManager() == null) {
            player.sendMessage("§c❌ Sistema de rankup no disponible");
            return;
        }

        try {
            RankupManager rankupManager = plugin.getRankupManager();

            if (rankupManager.isHybridMenuSystemAvailable()) {
                BedrockMenuManager.ClientType clientType = rankupManager.detectClientType(player);

                player.sendMessage(ChatColor.AQUA + "═══ INFORMACIÓN DE CLIENTE ═══");

                switch (clientType) {
                    case BEDROCK -> {
                        player.sendMessage(ChatColor.GREEN + "📱 Cliente: " + ChatColor.YELLOW + "Minecraft Bedrock Edition");
                        player.sendMessage(ChatColor.WHITE + "• Menús optimizados para móviles y consolas");
                        player.sendMessage(ChatColor.WHITE + "• Interfaz simplificada y táctil");
                        player.sendMessage(ChatColor.WHITE + "• Compatibilidad automática");
                    }
                    case JAVA -> {
                        player.sendMessage(ChatColor.GREEN + "💻 Cliente: " + ChatColor.YELLOW + "Minecraft Java Edition");
                        player.sendMessage(ChatColor.WHITE + "• Menús interactivos completos");
                        player.sendMessage(ChatColor.WHITE + "• Funcionalidades avanzadas");
                        player.sendMessage(ChatColor.WHITE + "• Configuración personalizable");
                    }
                    case UNKNOWN -> {
                        player.sendMessage(ChatColor.GRAY + "❓ Cliente: " + ChatColor.YELLOW + "No detectado");
                        player.sendMessage(ChatColor.WHITE + "• Usando configuración por defecto");
                        player.sendMessage(ChatColor.WHITE + "• Menús básicos disponibles");
                    }
                }

                // Mostrar capacidades disponibles
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "Capacidades disponibles:");
                player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "Sistema de rankup completo");
                player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "Detección automática de cliente");
                player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "Menús optimizados por plataforma");

            } else {
                player.sendMessage(ChatColor.YELLOW + "💻 Sistema estándar Java activado");
                player.sendMessage(ChatColor.GRAY + "Para habilitar soporte Bedrock, instala BedrockGUI");
            }

        } catch (Exception e) {
            player.sendMessage("§c❌ Error obteniendo información de cliente");
        }
    }

    // =================== MÉTODOS LEGACY (FALLBACK) ===================

    /**
     * Método legacy del menú principal
     */
    @Deprecated
    private static void openMainMenuLegacy(Player player, Main plugin) {
        player.sendMessage("§e⚠ Usando menú básico - Configura MenuManager para menús avanzados");

        player.sendMessage("§7Comandos disponibles:");
        player.sendMessage("§e/rankup §7- Intentar rankup");
        player.sendMessage("§e/rankup progress §7- Ver progreso");
        player.sendMessage("§e/rankup list §7- Ver rangos disponibles");
        player.sendMessage("§e/rankup info §7- Tu información");
    }

    /**
     * Método legacy del menú de progreso
     */
    @Deprecated
    private static void openProgressMenuLegacy(Player player, Main plugin) {
        player.sendMessage("§e⚠ Menú de progreso no disponible - Usa §e/rankup progress");
    }

    /**
     * Método legacy de la lista de rangos
     */
    @Deprecated
    private static void openRanksListLegacy(Player player, Main plugin) {
        player.sendMessage("§e⚠ Lista de rangos no disponible - Usa §e/rankup list");
    }

    // =================== MÉTODOS DE UTILIDAD ACTUALIZADOS ===================

    /**
     * Verifica si el sistema de menús híbrido está disponible
     */
    public static boolean isHybridMenuSystemAvailable(Main plugin) {
        if (plugin.getRankupManager() == null) {
            return false;
        }

        return plugin.getRankupManager().isHybridMenuSystemAvailable();
    }

    /**
     * Verifica si el sistema de menús está disponible (cualquier tipo)
     */
    public static boolean isMenuSystemAvailable(Main plugin) {
        if (plugin.getRankupManager() == null) {
            return false;
        }

        return plugin.getRankupManager().isMenuSystemAvailable();
    }

    /**
     * Obtiene información sobre el estado del sistema de menús híbrido
     */
    public static String getHybridMenuSystemStatus(Main plugin) {
        if (!plugin.isRankupSystemEnabled()) {
            return "§c❌ Sistema de rankup deshabilitado";
        }

        RankupManager rankupManager = plugin.getRankupManager();

        if (rankupManager.isHybridMenuSystemAvailable()) {
            Map<String, Object> hybridInfo = rankupManager.getHybridSystemInfo();
            boolean bedrockAvailable = (Boolean) hybridInfo.getOrDefault("bedrockGuiDetected", false);
            boolean javaAvailable = (Boolean) hybridInfo.getOrDefault("javaMenusAvailable", false);

            if (bedrockAvailable && javaAvailable) {
                return "§a✓ Sistema híbrido completo (Bedrock + Java)";
            } else if (javaAvailable) {
                return "§e⚠ Solo menús Java disponibles";
            } else {
                return "§c❌ Sistema de menús no disponible";
            }
        } else if (rankupManager.isMenuSystemAvailable()) {
            return "§e⚠ MenuManager básico disponible - Solo Java";
        } else {
            return "§c❌ Solo comandos básicos disponibles";
        }
    }

    /**
     * Obtiene estadísticas detalladas del sistema híbrido
     */
    public static Map<String, Object> getHybridSystemStats(Main plugin) {
        Map<String, Object> stats = new HashMap<>();

        if (!plugin.isRankupSystemEnabled()) {
            stats.put("status", "RANKUP_DISABLED");
            return stats;
        }

        RankupManager rankupManager = plugin.getRankupManager();

        stats.put("hybridSystemAvailable", rankupManager.isHybridMenuSystemAvailable());
        stats.put("menuSystemAvailable", rankupManager.isMenuSystemAvailable());

        if (rankupManager.isHybridMenuSystemAvailable()) {
            stats.putAll(rankupManager.getHybridSystemInfo());
        }

        return stats;
    }
}