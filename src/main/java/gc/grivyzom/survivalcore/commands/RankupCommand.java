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

import java.io.File;
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
            // Usar sistema híbrido que detecta automáticamente
            return openRanksMenuHybrid(player);
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "gui", "menu" -> {
                return openRanksMenuHybrid(player);
            }
            case "list", "lista" -> showRankList(player);
            case "top", "leaderboard" -> showTopRanks(player);
            case "client", "cliente" -> {  // 🆕 NUEVO
                return showClientInfo(player);
            }
            case "hybrid", "hibrido" -> {  // 🆕 NUEVO
                if (player.hasPermission("survivalcore.rankup.admin")) {
                    return showHybridSystemInfo(player);
                } else {
                    player.sendMessage(ChatColor.RED + "❌ Sin permisos para ver información híbrida");
                }
            }
            case "debug" -> {
                if (player.hasPermission("survivalcore.rankup.admin")) {
                    debugMenuSystem(player);
                } else {
                    player.sendMessage(ChatColor.RED + "❌ Sin permisos para debug");
                }
            }
            case "fix" -> {
                if (player.hasPermission("survivalcore.rankup.admin")) {
                    return fixMenuSystem(player);
                } else {
                    player.sendMessage(ChatColor.RED + "❌ Sin permisos para reparar sistema");
                }
            }
            case "create" -> {
                if (player.hasPermission("survivalcore.rankup.admin")) {
                    return createMenuFile(player);
                } else {
                    player.sendMessage(ChatColor.RED + "❌ Sin permisos para crear archivos");
                }
            }
            case "status" -> {  // 🆕 MEJORADO
                return showHybridMenuStatus(player);
            }
            case "reload" -> {  // 🆕 NUEVO
                if (player.hasPermission("survivalcore.rankup.admin")) {
                    return reloadHybridSystem(player);
                } else {
                    player.sendMessage(ChatColor.RED + "❌ Sin permisos para recargar");
                }
            }
            default -> {
                player.sendMessage(ChatColor.RED + "❌ Subcomando desconocido para /ranks");
                player.sendMessage(ChatColor.YELLOW + "Uso: /ranks [gui|list|client|hybrid|debug|fix|status]");
                if (player.hasPermission("survivalcore.rankup.admin")) {
                    player.sendMessage(ChatColor.GRAY + "Admin: hybrid, debug, fix, create, reload");
                }
            }
        }
        return true;
    }

    /**
     * Muestra información sobre el tipo de cliente del jugador
     */
    private boolean showClientInfo(Player player) {
        if (!rankupManager.isHybridMenuSystemAvailable()) {
            player.sendMessage(ChatColor.YELLOW + "💻 Sistema estándar Java");
            player.sendMessage(ChatColor.GRAY + "Para habilitar detección híbrida, instala BedrockGUI");
            return true;
        }

        try {
            var clientType = rankupManager.detectClientType(player);

            player.sendMessage(ChatColor.AQUA + "═══ INFORMACIÓN DE CLIENTE ═══");

            switch (clientType) {
                case BEDROCK -> {
                    player.sendMessage(ChatColor.GREEN + "📱 Tipo: " + ChatColor.YELLOW + "Minecraft Bedrock Edition");
                    player.sendMessage(ChatColor.WHITE + "• Plataforma: Móvil, Consola o Windows 10");
                    player.sendMessage(ChatColor.WHITE + "• Menús: Optimizados para táctil");
                    player.sendMessage(ChatColor.WHITE + "• Interfaz: Simplificada y accesible");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "✨ Funciones disponibles:");
                    player.sendMessage(ChatColor.WHITE + "  ✓ Menús de formularios nativos");
                    player.sendMessage(ChatColor.WHITE + "  ✓ Detección automática");
                    player.sendMessage(ChatColor.WHITE + "  ✓ Compatibilidad total con comandos");
                }
                case JAVA -> {
                    player.sendMessage(ChatColor.GREEN + "💻 Tipo: " + ChatColor.YELLOW + "Minecraft Java Edition");
                    player.sendMessage(ChatColor.WHITE + "• Plataforma: PC (Windows, Mac, Linux)");
                    player.sendMessage(ChatColor.WHITE + "• Menús: Interactivos completos");
                    player.sendMessage(ChatColor.WHITE + "• Interfaz: Avanzada con configuración");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "✨ Funciones disponibles:");
                    player.sendMessage(ChatColor.WHITE + "  ✓ Menús inventario interactivos");
                    player.sendMessage(ChatColor.WHITE + "  ✓ Configuración personalizable");
                    player.sendMessage(ChatColor.WHITE + "  ✓ Efectos visuales y sonoros");
                }
                case UNKNOWN -> {
                    player.sendMessage(ChatColor.GRAY + "❓ Tipo: " + ChatColor.YELLOW + "No detectado");
                    player.sendMessage(ChatColor.WHITE + "• Usando configuración por defecto");
                    player.sendMessage(ChatColor.WHITE + "• Menús básicos disponibles");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.YELLOW + "💡 Nota:");
                    player.sendMessage(ChatColor.GRAY + "  La detección mejora con el uso");
                }
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "🎮 Comandos principales:");
            player.sendMessage(ChatColor.WHITE + "  /ranks - Menú principal automático");
            player.sendMessage(ChatColor.WHITE + "  /rankup - Intentar subir de rango");
            player.sendMessage(ChatColor.WHITE + "  /rankup progress - Ver progreso");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error obteniendo información de cliente: " + e.getMessage());
        }

        return true;
    }

    /**
     * Muestra información completa del sistema híbrido (admin)
     */
    private boolean showHybridSystemInfo(Player player) {
        player.sendMessage(ChatColor.AQUA + "═══ SISTEMA HÍBRIDO RANKUP ═══");

        try {
            // Estado general
            boolean hybridAvailable = rankupManager.isHybridMenuSystemAvailable();
            player.sendMessage(ChatColor.WHITE + "Estado híbrido: " +
                    (hybridAvailable ? ChatColor.GREEN + "DISPONIBLE" : ChatColor.RED + "NO DISPONIBLE"));

            if (hybridAvailable) {
                Map<String, Object> hybridInfo = rankupManager.getHybridSystemInfo();

                // Información de BedrockGUI
                boolean bedrockGuiInstalled = (Boolean) hybridInfo.getOrDefault("bedrockGuiPluginInstalled", false);
                player.sendMessage(ChatColor.WHITE + "BedrockGUI: " +
                        (bedrockGuiInstalled ? ChatColor.GREEN + "INSTALADO" : ChatColor.RED + "NO INSTALADO"));

                if (bedrockGuiInstalled) {
                    String version = (String) hybridInfo.getOrDefault("bedrockGuiVersion", "Desconocida");
                    boolean enabled = (Boolean) hybridInfo.getOrDefault("bedrockGuiEnabled", false);
                    player.sendMessage(ChatColor.GRAY + "  • Versión: " + version);
                    player.sendMessage(ChatColor.GRAY + "  • Estado: " + (enabled ? "Habilitado" : "Deshabilitado"));
                }

                // Estadísticas de clientes
                long bedrockPlayers = ((Number) hybridInfo.getOrDefault("detectedBedrockPlayers", 0)).longValue();
                long javaPlayers = ((Number) hybridInfo.getOrDefault("detectedJavaPlayers", 0)).longValue();
                long totalCached = ((Number) hybridInfo.getOrDefault("totalCachedClients", 0)).longValue();

                player.sendMessage(ChatColor.WHITE + "Clientes detectados:");
                player.sendMessage(ChatColor.GRAY + "  • Bedrock: " + ChatColor.GREEN + bedrockPlayers);
                player.sendMessage(ChatColor.GRAY + "  • Java: " + ChatColor.YELLOW + javaPlayers);
                player.sendMessage(ChatColor.GRAY + "  • Total en caché: " + totalCached);

                // Estado de menús
                boolean bedrockMenusRegistered = (Boolean) hybridInfo.getOrDefault("bedrockMenusRegistered", false);
                boolean javaMenusAvailable = (Boolean) hybridInfo.getOrDefault("javaMenusAvailable", false);

                player.sendMessage(ChatColor.WHITE + "Menús disponibles:");
                player.sendMessage(ChatColor.GRAY + "  • Bedrock: " +
                        (bedrockMenusRegistered ? ChatColor.GREEN + "Registrados" : ChatColor.RED + "No registrados"));
                player.sendMessage(ChatColor.GRAY + "  • Java: " +
                        (javaMenusAvailable ? ChatColor.GREEN + "Disponibles" : ChatColor.RED + "No disponibles"));

            } else {
                // Mostrar por qué no está disponible
                boolean javaMenus = rankupManager.isMenuSystemAvailable();
                player.sendMessage(ChatColor.WHITE + "Menús Java: " +
                        (javaMenus ? ChatColor.GREEN + "DISPONIBLES" : ChatColor.RED + "NO DISPONIBLES"));

                if (!javaMenus) {
                    player.sendMessage(ChatColor.RED + "❌ Ningún sistema de menús está disponible");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "⚠ Solo menús Java disponibles");
                    player.sendMessage(ChatColor.GRAY + "Para habilitar Bedrock, instala BedrockGUI");
                }
            }

            // Salud del sistema
            Map<String, String> health = rankupManager.getHybridSystemHealth();
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Salud del sistema:");
            for (Map.Entry<String, String> entry : health.entrySet()) {
                String component = entry.getKey();
                String status = entry.getValue();
                ChatColor color = getHealthColor(status);
                player.sendMessage(ChatColor.GRAY + "  • " + component + ": " + color + status);
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error obteniendo información híbrida: " + e.getMessage());
        }

        return true;
    }

    /**
     * Muestra el estado híbrido del sistema de menús
     */
    private boolean showHybridMenuStatus(Player player) {
        player.sendMessage(ChatColor.AQUA + "═══ ESTADO HÍBRIDO DE MENÚS ═══");

        // Estado del sistema de rankup
        boolean rankupEnabled = plugin.isRankupSystemEnabled();
        player.sendMessage(ChatColor.WHITE + "Sistema Rankup: " +
                (rankupEnabled ? ChatColor.GREEN + "HABILITADO" : ChatColor.RED + "DESHABILITADO"));

        if (!rankupEnabled) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            player.sendMessage(ChatColor.GRAY + "Causa: LuckPerms no está instalado o configurado");
            return true;
        }

        // Estado híbrido
        boolean hybridAvailable = rankupManager.isHybridMenuSystemAvailable();
        player.sendMessage(ChatColor.WHITE + "Sistema Híbrido: " +
                (hybridAvailable ? ChatColor.GREEN + "COMPLETO" : ChatColor.YELLOW + "PARCIAL"));

        // Detalle por tipo
        if (hybridAvailable) {
            player.sendMessage(ChatColor.GREEN + "✅ Detección automática activa");
            player.sendMessage(ChatColor.WHITE + "  📱 Bedrock: Menús de formularios nativos");
            player.sendMessage(ChatColor.WHITE + "  💻 Java: Menús inventario interactivos");
        } else {
            boolean javaOnly = rankupManager.isMenuSystemAvailable();
            if (javaOnly) {
                player.sendMessage(ChatColor.YELLOW + "⚠ Solo Java disponible");
                player.sendMessage(ChatColor.GRAY + "  Para Bedrock completo, instala BedrockGUI");
            } else {
                player.sendMessage(ChatColor.RED + "❌ Solo comandos básicos");
                player.sendMessage(ChatColor.GRAY + "  Configura MenuManager para menús");
            }
        }

        // Plugin dependencies
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Dependencias:");

        boolean bedrockGuiInstalled = plugin.getServer().getPluginManager().getPlugin("BedrockGUI") != null;
        player.sendMessage(ChatColor.GRAY + "  • BedrockGUI: " +
                (bedrockGuiInstalled ? ChatColor.GREEN + "Instalado" : ChatColor.RED + "No instalado"));

        boolean luckPermsInstalled = plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null;
        player.sendMessage(ChatColor.GRAY + "  • LuckPerms: " +
                (luckPermsInstalled ? ChatColor.GREEN + "Instalado" : ChatColor.RED + "No instalado"));

        boolean papiInstalled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        player.sendMessage(ChatColor.GRAY + "  • PlaceholderAPI: " +
                (papiInstalled ? ChatColor.GREEN + "Instalado" : ChatColor.YELLOW + "Opcional"));

        // Recomendaciones
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "💡 Comandos disponibles:");
        player.sendMessage(ChatColor.WHITE + "  /ranks - Menú automático híbrido");
        player.sendMessage(ChatColor.WHITE + "  /ranks client - Tu tipo de cliente");

        if (player.hasPermission("survivalcore.rankup.admin")) {
            player.sendMessage(ChatColor.GRAY + "Admin:");
            player.sendMessage(ChatColor.WHITE + "  /ranks hybrid - Info detallada del sistema");
            player.sendMessage(ChatColor.WHITE + "  /ranks fix - Reparar menús");
            player.sendMessage(ChatColor.WHITE + "  /ranks reload - Recargar sistema");
        }

        return true;
    }


    /**
     * Abre el menú usando el sistema híbrido (detecta automáticamente)
     */
    private boolean openRanksMenuHybrid(Player player) {
        try {
            if (!rankupManager.isMenuSystemAvailable()) {
                player.sendMessage(ChatColor.RED + "❌ Sistema de menús no disponible");
                showFallbackCommands(player);
                return true;
            }

            if (rankupManager.isHybridMenuSystemAvailable()) {
                // Sistema híbrido completo
                player.sendMessage(ChatColor.GREEN + "🚀 Abriendo menú híbrido...");
                rankupManager.openMainMenuHybrid(player);
            } else {
                // Solo Java
                player.sendMessage(ChatColor.YELLOW + "💻 Abriendo menú Java...");
                var menuManager = rankupManager.getMenuManager();
                if (menuManager != null) {
                    menuManager.openMainMenu(player);
                } else {
                    showFallbackCommands(player);
                }
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error abriendo menú híbrido: " + e.getMessage());
            plugin.getLogger().severe("Error en menú híbrido para " + player.getName() + ": " + e.getMessage());
            showFallbackCommands(player);
        }

        return true;
    }

    /**
     * Comando para reparar el sistema de menús automáticamente
     */
    private boolean fixMenuSystem(Player player) {
        player.sendMessage(ChatColor.YELLOW + "🔧 Iniciando reparación automática del sistema de menús...");

        boolean fixed = false;
        int steps = 0;

        try {
            // Paso 1: Verificar y crear directorio
            File menusDir = new File(plugin.getDataFolder(), "menus");
            if (!menusDir.exists()) {
                player.sendMessage(ChatColor.GRAY + "📁 Creando directorio menus/...");
                if (menusDir.mkdirs()) {
                    player.sendMessage(ChatColor.GREEN + "✓ Directorio creado");
                    steps++;
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Error creando directorio");
                    return true;
                }
            } else {
                player.sendMessage(ChatColor.GREEN + "✓ Directorio menus/ existe");
                steps++;
            }

            // Paso 2: Verificar y crear archivo de configuración
            File menuFile = new File(menusDir, "rankup_menu.yml");
            if (!menuFile.exists()) {
                player.sendMessage(ChatColor.GRAY + "📄 Creando archivo rankup_menu.yml...");
                if (createDefaultMenuFile()) {
                    player.sendMessage(ChatColor.GREEN + "✓ Archivo de configuración creado");
                    steps++;
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Error creando archivo de configuración");
                    return true;
                }
            } else {
                player.sendMessage(ChatColor.GREEN + "✓ Archivo rankup_menu.yml existe");
                steps++;
            }

            // Paso 3: Reinicializar MenuManager
            if (rankupManager.getMenuManager() == null) {
                player.sendMessage(ChatColor.GRAY + "🔄 Reinicializando MenuManager...");
                if (rankupManager.reinitializeMenuManager()) {
                    player.sendMessage(ChatColor.GREEN + "✓ MenuManager reinicializado");
                    fixed = true;
                    steps++;
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Error reinicializando MenuManager");
                }
            } else {
                player.sendMessage(ChatColor.GREEN + "✓ MenuManager ya está activo");

                // Verificar si está saludable
                if (rankupManager.isMenuSystemHealthy()) {
                    player.sendMessage(ChatColor.GREEN + "✓ MenuManager está funcionando correctamente");
                    fixed = true;
                } else {
                    player.sendMessage(ChatColor.YELLOW + "⚠ MenuManager existe pero no está saludable, reinicializando...");
                    if (rankupManager.reinitializeMenuManager()) {
                        player.sendMessage(ChatColor.GREEN + "✓ MenuManager reparado");
                        fixed = true;
                    } else {
                        player.sendMessage(ChatColor.RED + "✗ No se pudo reparar MenuManager");
                    }
                }
                steps++;
            }

            // Paso 4: Recargar configuración si es necesario
            if (fixed) {
                player.sendMessage(ChatColor.GRAY + "🔄 Recargando configuración...");
                try {
                    rankupManager.reloadConfig();
                    player.sendMessage(ChatColor.GREEN + "✓ Configuración recargada");
                    steps++;
                } catch (Exception e) {
                    player.sendMessage(ChatColor.YELLOW + "⚠ Error recargando configuración: " + e.getMessage());
                }
            }

            // Resultado final
            player.sendMessage("");
            if (fixed) {
                player.sendMessage(ChatColor.GREEN + "🎉 ¡Sistema de menús reparado exitosamente!");
                player.sendMessage(ChatColor.WHITE + "Completados " + steps + " pasos de reparación");
                player.sendMessage(ChatColor.YELLOW + "💡 Prueba ahora: " + ChatColor.WHITE + "/ranks");
            } else {
                player.sendMessage(ChatColor.RED + "❌ No se pudo reparar el sistema completamente");
                player.sendMessage(ChatColor.YELLOW + "Pasos completados: " + steps + "/4");
                player.sendMessage(ChatColor.GRAY + "Intenta: /score reload o contacta a un administrador");
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error durante la reparación: " + e.getMessage());
            plugin.getLogger().severe("Error en fix de menús: " + e.getMessage());
        }

        return true;
    }


    /**
     * Comando para crear manualmente el archivo de menú
     */
    private boolean createMenuFile(Player player) {
        player.sendMessage(ChatColor.YELLOW + "📄 Creando archivo de configuración de menú...");

        try {
            File menusDir = new File(plugin.getDataFolder(), "menus");
            File menuFile = new File(menusDir, "rankup_menu.yml");

            if (menuFile.exists()) {
                player.sendMessage(ChatColor.YELLOW + "⚠ El archivo ya existe. ¿Sobrescribir?");
                player.sendMessage(ChatColor.GRAY + "Usa /ranks create force para forzar la recreación");
                return true;
            }

            if (createDefaultMenuFile()) {
                player.sendMessage(ChatColor.GREEN + "✅ Archivo creado exitosamente");
                player.sendMessage(ChatColor.WHITE + "Ubicación: " + menuFile.getPath());
                player.sendMessage(ChatColor.YELLOW + "💡 Usa /score reload para aplicar cambios");
            } else {
                player.sendMessage(ChatColor.RED + "❌ Error creando archivo");
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error: " + e.getMessage());
        }

        return true;
    }

    /**
     * Muestra el estado actual del sistema de menús
     */
    private boolean showMenuStatus(Player player) {
        player.sendMessage(ChatColor.AQUA + "═══ ESTADO DEL SISTEMA DE MENÚS ═══");

        // Estado general
        boolean rankupEnabled = plugin.isRankupSystemEnabled();
        player.sendMessage(ChatColor.WHITE + "Sistema Rankup: " +
                (rankupEnabled ? ChatColor.GREEN + "HABILITADO" : ChatColor.RED + "DESHABILITADO"));

        if (!rankupEnabled) {
            player.sendMessage(ChatColor.RED + "❌ Sistema de rankup no disponible");
            player.sendMessage(ChatColor.GRAY + "Causa: LuckPerms no está instalado o configurado");
            return true;
        }

        // Estado del MenuManager
        boolean menuManagerExists = rankupManager.getMenuManager() != null;
        player.sendMessage(ChatColor.WHITE + "MenuManager: " +
                (menuManagerExists ? ChatColor.GREEN + "DISPONIBLE" : ChatColor.RED + "NO DISPONIBLE"));

        if (menuManagerExists) {
            boolean healthy = rankupManager.isMenuSystemHealthy();
            player.sendMessage(ChatColor.WHITE + "Estado: " +
                    (healthy ? ChatColor.GREEN + "SALUDABLE" : ChatColor.YELLOW + "CON PROBLEMAS"));

            // Estadísticas
            try {
                Map<String, Object> stats = rankupManager.getMenuStats();
                if (stats != null) {
                    player.sendMessage(ChatColor.WHITE + "Estadísticas:");
                    player.sendMessage(ChatColor.GRAY + "  • Menús en caché: " + stats.get("cachedMenus"));
                    player.sendMessage(ChatColor.GRAY + "  • Configuraciones: " + stats.get("playerSettings"));
                    player.sendMessage(ChatColor.GRAY + "  • Habilitado: " + stats.get("menuEnabled"));
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.YELLOW + "⚠ Error obteniendo estadísticas");
            }
        }

        // Estado de archivos
        player.sendMessage(ChatColor.WHITE + "Archivos:");

        File menusDir = new File(plugin.getDataFolder(), "menus");
        player.sendMessage(ChatColor.GRAY + "  • Directorio menus/: " +
                (menusDir.exists() ? ChatColor.GREEN + "EXISTS" : ChatColor.RED + "NOT FOUND"));

        File menuFile = new File(menusDir, "rankup_menu.yml");
        player.sendMessage(ChatColor.GRAY + "  • rankup_menu.yml: " +
                (menuFile.exists() ? ChatColor.GREEN + "EXISTS" : ChatColor.RED + "NOT FOUND"));

        if (menuFile.exists()) {
            player.sendMessage(ChatColor.GRAY + "    - Tamaño: " + menuFile.length() + " bytes");
            player.sendMessage(ChatColor.GRAY + "    - Legible: " +
                    (menuFile.canRead() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        }

        // Recomendaciones
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "💡 Comandos útiles:");

        if (!menuManagerExists) {
            player.sendMessage(ChatColor.WHITE + "  /ranks fix " + ChatColor.GRAY + "- Reparar automáticamente");
            if (!menuFile.exists()) {
                player.sendMessage(ChatColor.WHITE + "  /ranks create " + ChatColor.GRAY + "- Crear archivo de configuración");
            }
        }

        player.sendMessage(ChatColor.WHITE + "  /score reload " + ChatColor.GRAY + "- Recargar configuración completa");
        player.sendMessage(ChatColor.WHITE + "  /ranks debug " + ChatColor.GRAY + "- Debug detallado");

        return true;
    }


    /**
     * Debug específico del sistema de menús para jugadores
     */
    private void debugMenuSystem(Player player) {
        player.sendMessage(ChatColor.AQUA + "═══ DEBUG MENÚS RANKUP ═══");

        // Estado del MenuManager
        boolean menuManagerAvailable = rankupManager.getMenuManager() != null;
        player.sendMessage(ChatColor.WHITE + "MenuManager: " +
                (menuManagerAvailable ? ChatColor.GREEN + "DISPONIBLE" : ChatColor.RED + "NO DISPONIBLE"));

        // Verificar archivo de configuración
        File menuFile = new File(plugin.getDataFolder(), "menus/rankup_menu.yml");
        player.sendMessage(ChatColor.WHITE + "Archivo config: " +
                (menuFile.exists() ? ChatColor.GREEN + "EXISTS" : ChatColor.RED + "NOT FOUND"));

        if (menuFile.exists()) {
            player.sendMessage(ChatColor.WHITE + "  Tamaño: " + ChatColor.YELLOW + menuFile.length() + " bytes");
            player.sendMessage(ChatColor.WHITE + "  Puede leer: " +
                    (menuFile.canRead() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        }

        // Verificar directorio
        File menusDir = new File(plugin.getDataFolder(), "menus");
        player.sendMessage(ChatColor.WHITE + "Directorio menus/: " +
                (menusDir.exists() ? ChatColor.GREEN + "EXISTS" : ChatColor.RED + "NOT FOUND"));

        if (menusDir.exists()) {
            player.sendMessage(ChatColor.WHITE + "  Puede escribir: " +
                    (menusDir.canWrite() ? ChatColor.GREEN + "SÍ" : ChatColor.RED + "NO"));
        }

        // Estado del sistema de rankup
        player.sendMessage(ChatColor.WHITE + "Sistema Rankup: " +
                (plugin.isRankupSystemEnabled() ? ChatColor.GREEN + "HABILITADO" : ChatColor.RED + "DESHABILITADO"));

        if (menuManagerAvailable) {
            try {
                Map<String, Object> stats = rankupManager.getMenuStats();
                if (stats != null) {
                    player.sendMessage(ChatColor.WHITE + "Estadísticas:");
                    player.sendMessage(ChatColor.WHITE + "  Cache: " + ChatColor.YELLOW + stats.get("cachedMenus"));
                    player.sendMessage(ChatColor.WHITE + "  Jugadores: " + ChatColor.YELLOW + stats.get("playerSettings"));
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Error obteniendo estadísticas: " + e.getMessage());
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Soluciones:");
        if (!menuFile.exists()) {
            player.sendMessage(ChatColor.WHITE + "  1. " + ChatColor.GRAY + "Crear archivo: /ranks debug create");
        }
        player.sendMessage(ChatColor.WHITE + "  2. " + ChatColor.GRAY + "Recargar: /score reload");
        player.sendMessage(ChatColor.WHITE + "  3. " + ChatColor.GRAY + "Reinicio: /score emergency");
    }

    /**
     * Crea el archivo de menú por defecto
     */
    private boolean createDefaultMenuFile() {
        try {
            // Crear directorio si no existe
            File menusDir = new File(plugin.getDataFolder(), "menus");
            if (!menusDir.exists()) {
                menusDir.mkdirs();
            }

            // Intentar copiar desde resources
            try {
                plugin.saveResource("menus/rankup_menu.yml", false);
                return true;
            } catch (Exception e) {
                // Si no existe en resources, crear uno básico
                return createBasicMenuFile();
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error creando archivo de menú: " + e.getMessage());
            return false;
        }
    }

    /**
     * Crea un archivo de menú básico manualmente
     */
    private boolean createBasicMenuFile() {
        try {
            File menuFile = new File(plugin.getDataFolder(), "menus/rankup_menu.yml");

            // Contenido básico del menú
            String basicContent = """
                    # Configuración básica de menú de rankup
                    menu_settings:
                      enabled: true
                      enable_sounds: true
                      enable_particles: true
                      auto_refresh_interval: -1
                                
                    menus:
                      main:
                        title: "&5&l⭐ Sistema de Rangos ⭐"
                        size: 45
                        background:
                          enabled: true
                          material: "BLACK_STAINED_GLASS_PANE"
                          name: "&r"
                        
                        items:
                          player_info:
                            slot: 13
                            material: "PLAYER_HEAD"
                            skull_owner: "%player%"
                            name: "&e&l%player%"
                            lore:
                              - ""
                              - "&7▶ Rango actual: %score_rank_display%"
                              - "&7▶ Siguiente rango: %score_next_rank%"
                              - "&7▶ Progreso: &e%score_rankup_progress%"
                              - ""
                          
                          rankup_button:
                            slot: 20
                            material: "DIAMOND"
                            name: "&a&l⬆ HACER RANKUP"
                            lore:
                              - ""
                              - "&fHaz clic para intentar subir de rango"
                              - ""
                              - "&a✓ Haz clic para rankup"
                            enchanted: true
                                
                    sounds:
                      menu_open: "BLOCK_ENDER_CHEST_OPEN:0.8:1.0"
                      menu_close: "BLOCK_ENDER_CHEST_CLOSE:0.5:1.0"
                      button_click: "UI_BUTTON_CLICK:0.8:1.0"
                                
                    messages:
                      menu_opened: "&a✓ Menú de rangos abierto"
                      menu_closed: "&7Menú cerrado"
                    """;

            // Escribir archivo
            java.nio.file.Files.writeString(menuFile.toPath(), basicContent);

            plugin.getLogger().info("✓ Archivo básico de menú creado: " + menuFile.getPath());
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error creando archivo básico de menú: " + e.getMessage());
            return false;
        }
    }

    /**
     * Intenta abrir el menú con diagnóstico completo
     */
    private boolean openRanksMenuWithDiagnostic(Player player) {
        player.sendMessage(ChatColor.YELLOW + "🔍 Diagnosticando sistema de menús...");

        try {
            // Verificar MenuManager
            if (rankupManager.getMenuManager() == null) {
                player.sendMessage(ChatColor.RED + "❌ MenuManager no inicializado");
                player.sendMessage(ChatColor.YELLOW + "💡 Causas posibles:");
                player.sendMessage(ChatColor.GRAY + "  • Archivo menus/rankup_menu.yml no existe");
                player.sendMessage(ChatColor.GRAY + "  • Error en configuración del menú");
                player.sendMessage(ChatColor.GRAY + "  • Permisos de archivo");

                // Verificar archivo
                File menuFile = new File(plugin.getDataFolder(), "menus/rankup_menu.yml");
                player.sendMessage(ChatColor.GRAY + "📁 Archivo menú: " +
                        (menuFile.exists() ? ChatColor.GREEN + "EXISTS" : ChatColor.RED + "NOT FOUND"));

                if (!menuFile.exists()) {
                    player.sendMessage(ChatColor.YELLOW + "🔧 Intentando crear archivo por defecto...");
                    if (createDefaultMenuFile()) {
                        player.sendMessage(ChatColor.GREEN + "✓ Archivo creado. Recarga el plugin: /score reload");
                    } else {
                        player.sendMessage(ChatColor.RED + "❌ No se pudo crear archivo por defecto");
                    }
                }

                showFallbackCommands(player);
                return true;
            }

            // Intentar abrir menú
            player.sendMessage(ChatColor.GREEN + "✓ MenuManager disponible, abriendo menú...");
            rankupManager.getMenuManager().openMainMenu(player);
            return true;

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error abriendo menú: " + e.getMessage());
            plugin.getLogger().severe("Error detallado abriendo menú para " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();

            showFallbackCommands(player);
            return true;
        }
    }

    /**
     * Muestra comandos alternativos cuando el menú no funciona
     */
    private void showFallbackCommands(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚠ Usando menú básico - Configura MenuManager para menús avanzados");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "📋 Comandos disponibles:");
        player.sendMessage(ChatColor.GREEN + "  /rankup " + ChatColor.GRAY + "- Intentar rankup");
        player.sendMessage(ChatColor.GREEN + "  /rankup progress " + ChatColor.GRAY + "- Ver progreso detallado");
        player.sendMessage(ChatColor.GREEN + "  /rankup list " + ChatColor.GRAY + "- Ver lista de rangos");
        player.sendMessage(ChatColor.GREEN + "  /rankup info " + ChatColor.GRAY + "- Información de tu rango");
        player.sendMessage("");

        if (player.hasPermission("survivalcore.rankup.admin")) {
            player.sendMessage(ChatColor.RED + "🔧 Admin:");
            player.sendMessage(ChatColor.WHITE + "  /score debug menus " + ChatColor.GRAY + "- Debug del sistema de menús");
            player.sendMessage(ChatColor.WHITE + "  /score reload " + ChatColor.GRAY + "- Recargar configuración");
            player.sendMessage("");
        }
    }


    /**
     * Intenta hacer rankup con mensajes mejorados
     */
    private void attemptRankup(Player player) {
        // Verificar cooldown inmediatamente para dar feedback rápido
        if (rankupManager.isOnCooldown(player.getUniqueId())) {
            long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "⏰ Debes esperar " + (remaining / 1000) + " segundos");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "🔄 Verificando requisitos...");

        rankupManager.attemptRankup(player).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    // ✅ ÉXITO SIMPLIFICADO
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "🎉 " + ChatColor.BOLD + "¡RANKUP EXITOSO!");
                    player.sendMessage(ChatColor.WHITE + result.getMessage());
                    player.sendMessage(ChatColor.GRAY + "💡 Usa §e/rankup progress §7para ver tu siguiente objetivo");
                    player.sendMessage("");
                } else {
                    // ❌ ERROR SIMPLIFICADO
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "RANKUP NO DISPONIBLE");
                    player.sendMessage("");
                    player.sendMessage(result.getMessage());
                    player.sendMessage("");
                    player.sendMessage(ChatColor.YELLOW + "💡 Comandos útiles:");
                    player.sendMessage(ChatColor.GRAY + "  • §e/rankup progress §7- Ver progreso detallado");
                    player.sendMessage(ChatColor.GRAY + "  • §e/ranks §7- Abrir menú interactivo");
                    player.sendMessage("");
                }
            });
        });
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
        rankupManager.getPlayerProgress(player).thenAccept(progress -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (progress.getCurrentRank() == null) {
                    player.sendMessage(ChatColor.RED + "❌ Error obteniendo tu progreso");
                    return;
                }

                // 📊 PROGRESO SIMPLIFICADO
                player.sendMessage("");
                player.sendMessage(ChatColor.AQUA + "📊 " + ChatColor.BOLD + "TU PROGRESO");
                player.sendMessage("");

                if (progress.getNextRank() == null) {
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "🏆 ¡Has alcanzado el rango máximo!");
                    player.sendMessage("");
                    return;
                }

                // Barra de progreso principal
                double percentage = progress.getOverallProgress();
                String progressBar = createProgressBar(percentage, 20);
                player.sendMessage(ChatColor.WHITE + "Progreso general: " + ChatColor.YELLOW +
                        String.format("%.1f%%", percentage));
                player.sendMessage(progressBar);
                player.sendMessage("");

                // Lista de requisitos más compacta
                player.sendMessage(ChatColor.WHITE + "Requisitos:");

                List<RequirementProgress> sortedReqs = progress.getRequirements().values()
                        .stream()
                        .sorted((a, b) -> Boolean.compare(b.isCompleted(), a.isCompleted()))
                        .toList();

                for (RequirementProgress reqProgress : sortedReqs) {
                    String status = reqProgress.isCompleted() ?
                            ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                    String reqName = formatRequirementName(reqProgress.getType());
                    String value = formatRequirementValue(reqProgress);

                    player.sendMessage("  " + status + " " + ChatColor.WHITE + reqName +
                            ": " + ChatColor.YELLOW + value);
                }

                player.sendMessage("");

                if (percentage >= 100.0) {
                    player.sendMessage(ChatColor.GREEN + "🎉 ¡Listo para rankup! Usa §e/rankup");
                } else {
                    long incomplete = sortedReqs.stream().mapToLong(req -> req.isCompleted() ? 0 : 1).sum();
                    player.sendMessage(ChatColor.YELLOW + "⚡ Te faltan " + incomplete + " requisitos");
                }
                player.sendMessage("");
            });
        });
    }

    /**
     * Muestra ayuda simplificada
     */
    private void showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "📖 " + ChatColor.BOLD + "AYUDA RANKUP");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Comandos básicos:");
        player.sendMessage(ChatColor.WHITE + "  §e/rankup §7- Subir de rango");
        player.sendMessage(ChatColor.WHITE + "  §e/rankup info §7- Info de tu rango");
        player.sendMessage(ChatColor.WHITE + "  §e/rankup progress §7- Ver progreso");
        player.sendMessage(ChatColor.WHITE + "  §e/ranks §7- Menú interactivo");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Comandos de información:");
        player.sendMessage(ChatColor.WHITE + "  §e/rankup list §7- Lista de rangos");
        player.sendMessage("");

        if (player.hasPermission("survivalcore.rankup.admin")) {
            player.sendMessage(ChatColor.RED + "🔧 Admin:");
            player.sendMessage(ChatColor.WHITE + "  §e/rankup debug [jugador] §7- Debug de jugador");
            player.sendMessage(ChatColor.WHITE + "  §e/rankup debug config §7- Debug de configuración");
            player.sendMessage(ChatColor.WHITE + "  §e/rankup debug groups §7- Debug de grupos LuckPerms");
            player.sendMessage(ChatColor.WHITE + "  §e/rankup reload §7- Recargar configuración");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "💡 Debug útil:");
            player.sendMessage(ChatColor.GRAY + "  Si un jugador no puede hacer rankup, usa:");
            player.sendMessage(ChatColor.GRAY + "  §e/rankup debug <jugador> §7para ver detalles");
        }
    }

    /**
     * Lista de rangos simplificada
     */
    private void showRankList(Player player) {
        Map<String, RankupManager.SimpleRankData> rankups = rankupManager.getRanks();
        if (rankups.isEmpty()) {
            player.sendMessage(ChatColor.RED + "❌ No hay rangos configurados");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "📋 " + ChatColor.BOLD + "LISTA DE RANGOS");
        player.sendMessage("");

        List<RankupManager.SimpleRankData> sortedRanks = rankups.values().stream()
                .sorted(Comparator.comparingInt(RankupManager.SimpleRankData::getOrder))
                .collect(Collectors.toList());

        String currentRank = rankupManager.getCurrentRank(player);

        for (RankupManager.SimpleRankData rank : sortedRanks) {
            String marker = rank.getId().equals(currentRank) ?
                    ChatColor.GREEN + "► " : ChatColor.GRAY + "  ";

            String status = rank.getId().equals(currentRank) ?
                    ChatColor.GREEN + " (TU RANGO)" : "";

            player.sendMessage(marker + rank.getDisplayName() +
                    ChatColor.GRAY + " (#" + rank.getOrder() + ")" + status);
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 Usa §e/rankup progress §7para ver tu progreso");
        player.sendMessage("");
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

        // 🆕 NUEVO: Comando especial para verificar configuración
        if (targetName.equalsIgnoreCase("config") || targetName.equalsIgnoreCase("configuracion")) {
            debugConfiguration(player);
            return;
        }

        // 🆕 NUEVO: Comando especial para verificar grupos de LuckPerms
        if (targetName.equalsIgnoreCase("groups") || targetName.equalsIgnoreCase("grupos")) {
            debugLuckPermsGroups(player);
            return;
        }

        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            player.sendMessage(ChatColor.RED + "❌ Jugador no encontrado: " + targetName);
            player.sendMessage(ChatColor.YELLOW + "💡 Comandos especiales:");
            player.sendMessage(ChatColor.GRAY + "  • /rankup debug config - Verificar configuración");
            player.sendMessage(ChatColor.GRAY + "  • /rankup debug groups - Verificar grupos de LuckPerms");
            return;
        }

        rankupManager.debugPlayerRankup(target, player);
    }

    private void debugConfiguration(Player player) {
        player.sendMessage(ChatColor.AQUA + "═══ DEBUG CONFIGURACIÓN RANKUP ═══");

        Map<String, RankupManager.SimpleRankData> rankups = rankupManager.getRanks();

        player.sendMessage(ChatColor.WHITE + "📊 Estadísticas generales:");
        player.sendMessage(ChatColor.GRAY + "  • Total de rangos: " + ChatColor.YELLOW + rankups.size());
        player.sendMessage(ChatColor.GRAY + "  • Cooldown: " + ChatColor.YELLOW + (rankupManager.getCooldownTime() / 1000) + "s");
        player.sendMessage(ChatColor.GRAY + "  • PlaceholderAPI: " +
                (rankupManager.isPlaceholderAPIEnabled() ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));

        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "🔗 Cadena de rangos:");

        // Mostrar cadena de rangos ordenada
        List<RankupManager.SimpleRankData> sortedRanks = rankups.values().stream()
                .sorted(Comparator.comparingInt(RankupManager.SimpleRankData::getOrder))
                .collect(java.util.stream.Collectors.toList());

        for (RankupManager.SimpleRankData rank : sortedRanks) {
            String arrow = rank.hasNextRank() ? " → " + rank.getNextRank() : " (FINAL)";
            String color = rank.hasNextRank() ? ChatColor.WHITE.toString() : ChatColor.LIGHT_PURPLE.toString(); // 🔧 CORRECCIÓN

            player.sendMessage(ChatColor.GRAY + "  " + rank.getOrder() + ". " +
                    color + rank.getId() + ChatColor.GRAY + arrow);
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "📋 Requisitos por rango:");

        for (RankupManager.SimpleRankData rank : sortedRanks) {
            if (rank.hasNextRank()) {
                int reqCount = rank.getRequirements().size();
                player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + rank.getId() +
                        " → " + rank.getNextRank() + ChatColor.GRAY + " (" + reqCount + " requisitos)");
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "💡 Usa /rankup debug groups para verificar LuckPerms");
    }

    private void debugLuckPermsGroups(Player player) {
        player.sendMessage(ChatColor.AQUA + "═══ DEBUG GRUPOS LUCKPERMS ═══");

        Map<String, RankupManager.SimpleRankData> rankups = rankupManager.getRanks();

        // Obtener configuración del sistema
        String groupPrefix = ""; // Esto debería obtenerse del RankupManager
        // Como no tenemos acceso directo, lo extraemos desde un archivo de configuración

        player.sendMessage(ChatColor.WHITE + "🔧 Configuración:");
        player.sendMessage(ChatColor.GRAY + "  • Prefijo de grupos: '" + ChatColor.AQUA + groupPrefix + ChatColor.GRAY + "'");

        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "📋 Verificación de grupos:");

        boolean allGroupsExist = true;

        for (RankupManager.SimpleRankData rank : rankups.values()) {
            String groupName = groupPrefix.isEmpty() ? rank.getId() : groupPrefix + rank.getId();

            // Verificar si el grupo existe (esto requiere acceso al RankupManager)
            // Por ahora solo mostramos la estructura esperada
            player.sendMessage(ChatColor.GRAY + "  • Rango '" + ChatColor.WHITE + rank.getId() +
                    ChatColor.GRAY + "' → Grupo '" + ChatColor.YELLOW + groupName + ChatColor.GRAY + "'");
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "🔧 Comandos de LuckPerms útiles:");
        player.sendMessage(ChatColor.GRAY + "  • /lp listgroups - Ver todos los grupos");
        player.sendMessage(ChatColor.GRAY + "  • /lp group <grupo> info - Info de un grupo específico");
        player.sendMessage(ChatColor.GRAY + "  • /lp creategroup <grupo> - Crear grupo si no existe");

        if (!allGroupsExist) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "⚠️ Algunos grupos no existen en LuckPerms");
            player.sendMessage(ChatColor.YELLOW + "Esto puede causar problemas con el rankup");
        }
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

        bar.append(ChatColor.GRAY.toString()); // 🔧 CORRECCIÓN: Agregar .toString()
        for (int i = filled; i < length; i++) {
            bar.append("█");
        }

        // Agregar porcentaje al final
        bar.append(" ").append(ChatColor.WHITE.toString()).append(String.format("%.1f%%", percentage));

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
                 "ender_dragon_kills", "wither_kills" -> String.format("%,.0f/%,.0f", current, required);
            case "farming_level", "mining_level" -> String.format("Lv.%.0f/%.0f", current, required);
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
                case "ranks" -> {
                    if (args.length == 1) {
                        List<String> ranksCompletions = new ArrayList<>(Arrays.asList("gui", "list", "top", "status"));
                        if (sender.hasPermission("survivalcore.rankup.admin")) {
                            ranksCompletions.addAll(Arrays.asList("debug", "fix", "create"));
                        }
                        return ranksCompletions.stream()
                                .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }

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

    /**
     * Recarga el sistema híbrido completo
     */
    private boolean reloadHybridSystem(Player player) {
        player.sendMessage(ChatColor.YELLOW + "🔄 Recargando sistema híbrido...");

        try {
            long startTime = System.currentTimeMillis();

            // Recargar configuración completa
            plugin.reloadConfig();

            // Recargar sistema de rankup
            rankupManager.reloadConfig();

            long duration = System.currentTimeMillis() - startTime;

            // Verificar estado después de recarga
            boolean hybridAvailable = rankupManager.isHybridMenuSystemAvailable();
            boolean javaAvailable = rankupManager.isMenuSystemAvailable();

            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✅ Sistema híbrido recargado en " + duration + "ms");

            player.sendMessage(ChatColor.YELLOW + "Estado después de recarga:");
            player.sendMessage(ChatColor.WHITE + "  • Híbrido: " +
                    (hybridAvailable ? ChatColor.GREEN + "Disponible" : ChatColor.RED + "No disponible"));
            player.sendMessage(ChatColor.WHITE + "  • Java: " +
                    (javaAvailable ? ChatColor.GREEN + "Disponible" : ChatColor.RED + "No disponible"));

            if (hybridAvailable) {
                Map<String, Object> stats = rankupManager.getHybridSystemInfo();
                player.sendMessage(ChatColor.WHITE + "  • BedrockGUI: " +
                        ((Boolean) stats.getOrDefault("bedrockGuiDetected", false) ? ChatColor.GREEN + "Activo" : ChatColor.RED + "Inactivo"));
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "💡 Prueba: /ranks para verificar funcionamiento");

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "❌ Error recargando sistema híbrido: " + e.getMessage());
            plugin.getLogger().severe("Error en recarga híbrida: " + e.getMessage());
        }

        return true;
    }

    /**
     * Obtiene color basado en el estado de salud
     */
    private ChatColor getHealthColor(String status) {
        return switch (status.toUpperCase()) {
            case "HEALTHY", "FULLY_AVAILABLE", "OK" -> ChatColor.GREEN;
            case "PARTIAL", "JAVA_ONLY", "UNHEALTHY" -> ChatColor.YELLOW;
            case "NOT_AVAILABLE", "COMMANDS_ONLY" -> ChatColor.RED;
            default -> ChatColor.GRAY;
        };
    }
}
