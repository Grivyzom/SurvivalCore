package gc.grivyzom.survivalcore.commands;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.gui.GeneroGUI;
import gc.grivyzom.survivalcore.gui.ProfileGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Comando /perfil actualizado para usar el sistema configurable
 *
 * @author Brocolitx
 * @version 3.0
 */
public class PerfilCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public PerfilCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }

        // /perfil - Abrir propio perfil
        if (args.length == 0) {
            ProfileGUI.open(player, plugin);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "reload", "r" -> handleReload(player);
            case "help", "ayuda" -> showHelp(player);
            case "debug" -> handleDebug(player);
            default -> {
                // Intentar abrir perfil de otro jugador (si tiene permisos)
                if (player.hasPermission("survivalcore.perfil.others")) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target == null) {
                        player.sendMessage(ChatColor.RED + "Jugador no encontrado: " + args[0]);
                        return true;
                    }

                    // TODO: Implementar vista de perfil de otros jugadores
                    player.sendMessage(ChatColor.YELLOW + "Funcionalidad de ver perfil de otros jugadores en desarrollo.");
                    player.sendMessage(ChatColor.GRAY + "Por ahora, solo puedes ver tu propio perfil.");
                } else {
                    player.sendMessage(ChatColor.RED + "Comando desconocido. Usa /perfil help para ver la ayuda.");
                }
            }
        }

        return true;
    }

    /**
     * Maneja la recarga de configuración del perfil
     */
    private void handleReload(Player player) {
        if (!player.hasPermission("survivalcore.reload")) {
            player.sendMessage(ChatColor.RED + "No tienes permisos para recargar la configuración.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "🔄 Recargando configuración de GUIs...");

        try {
            long startTime = System.currentTimeMillis();

            // 1. Recargar configuración de GUIs
            plugin.reloadGuisConfig();
            player.sendMessage(ChatColor.GREEN + "  ✓ Archivo guis.yml recargado");

            // 2. Reinicializar ProfileGUI
            ProfileGUI.initialize(plugin);
            player.sendMessage(ChatColor.GREEN + "  ✓ ProfileGUI reinicializado");

            // 3. Reinicializar GeneroGUI también
            GeneroGUI.initialize(plugin);
            player.sendMessage(ChatColor.GREEN + "  ✓ GeneroGUI reinicializado");

            long duration = System.currentTimeMillis() - startTime;

            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✅ Configuración de GUIs recargada exitosamente en " + duration + "ms");
            player.sendMessage(ChatColor.GRAY + "Los cambios se aplicarán al abrir los menús nuevamente.");

            plugin.getLogger().info("Configuración de GUIs recargada por " + player.getName() + " en " + duration + "ms");

        } catch (Exception e) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ Error recargando configuración de GUIs:");
            player.sendMessage(ChatColor.RED + e.getMessage());

            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "💡 Consejos:");
            player.sendMessage(ChatColor.GRAY + "• Verifica que guis.yml tenga sintaxis YAML válida");
            player.sendMessage(ChatColor.GRAY + "• Revisa la consola para más detalles del error");
            player.sendMessage(ChatColor.GRAY + "• Usa /score reload para una recarga completa");

            plugin.getLogger().severe("Error recargando GUIs solicitado por " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Muestra información de debug del sistema de perfil
     */
    private void handleDebug(Player player) {
        if (!player.hasPermission("survivalcore.debug")) {
            player.sendMessage(ChatColor.RED + "No tienes permisos para usar comandos de debug.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "═══ DEBUG SISTEMA DE PERFIL ═══");

        // Verificar configuración
        if (plugin.getGuisConfig() != null) {
            player.sendMessage(ChatColor.GREEN + "✓ Archivo guis.yml cargado");

            if (plugin.getGuisConfig().getConfigurationSection("profile_gui") != null) {
                player.sendMessage(ChatColor.GREEN + "✓ Configuración de profile_gui encontrada");

                boolean enabled = plugin.getGuisConfig().getBoolean("profile_gui.enabled", true);
                player.sendMessage(ChatColor.WHITE + "Estado: " +
                        (enabled ? ChatColor.GREEN + "HABILITADO" : ChatColor.RED + "DESHABILITADO"));

                String title = plugin.getGuisConfig().getString("profile_gui.title", "N/A");
                player.sendMessage(ChatColor.WHITE + "Título: " + ChatColor.YELLOW + title);

                int size = plugin.getGuisConfig().getInt("profile_gui.size", 54);
                player.sendMessage(ChatColor.WHITE + "Tamaño: " + ChatColor.YELLOW + size);

                // Verificar páginas
                if (plugin.getGuisConfig().getConfigurationSection("profile_gui.pages") != null) {
                    var pages = plugin.getGuisConfig().getConfigurationSection("profile_gui.pages");
                    int pageCount = pages.getKeys(false).size();
                    player.sendMessage(ChatColor.WHITE + "Páginas configuradas: " + ChatColor.YELLOW + pageCount);

                    for (String pageName : pages.getKeys(false)) {
                        var page = pages.getConfigurationSection(pageName);
                        if (page != null && page.getConfigurationSection("items") != null) {
                            int itemCount = page.getConfigurationSection("items").getKeys(false).size();
                            player.sendMessage(ChatColor.GRAY + "  • " + pageName + ": " + itemCount + " items");
                        }
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "✗ No se encontraron páginas configuradas");
                }

            } else {
                player.sendMessage(ChatColor.RED + "✗ Configuración de profile_gui no encontrada");
            }
        } else {
            player.sendMessage(ChatColor.RED + "✗ Archivo guis.yml no cargado");
        }

        // Verificar datos del jugador
        try {
            var userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            if (userData != null) {
                player.sendMessage(ChatColor.GREEN + "✓ Datos del jugador cargados correctamente");
                player.sendMessage(ChatColor.WHITE + "Redes sociales: " + ChatColor.YELLOW + userData.getSocialMediaCount() + "/7");
            } else {
                player.sendMessage(ChatColor.RED + "✗ Error cargando datos del jugador");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✗ Error accediendo a la base de datos: " + e.getMessage());
        }

        // Verificar sistema de rankup
        if (plugin.isRankupSystemEnabled()) {
            player.sendMessage(ChatColor.GREEN + "✓ Sistema de rankup disponible");
        } else {
            player.sendMessage(ChatColor.YELLOW + "⚠ Sistema de rankup no disponible");
        }

        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════");
    }

    /**
     * Muestra la ayuda del comando
     */
    private void showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "     AYUDA DEL COMANDO PERFIL");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "/perfil" + ChatColor.GRAY + " - Abrir tu perfil");

        if (player.hasPermission("survivalcore.perfil.others")) {
            player.sendMessage(ChatColor.WHITE + "/perfil <jugador>" + ChatColor.GRAY + " - Ver perfil de otro jugador");
        }

        if (player.hasPermission("survivalcore.reload")) {
            player.sendMessage(ChatColor.WHITE + "/perfil reload" + ChatColor.GRAY + " - Recargar configuración");
        }

        if (player.hasPermission("survivalcore.debug")) {
            player.sendMessage(ChatColor.WHITE + "/perfil debug" + ChatColor.GRAY + " - Información de debug");
        }

        player.sendMessage(ChatColor.WHITE + "/perfil help" + ChatColor.GRAY + " - Mostrar esta ayuda");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Tu perfil incluye:");
        player.sendMessage(ChatColor.GRAY + "• Información personal (género, cumpleaños, país)");
        player.sendMessage(ChatColor.GRAY + "• Redes sociales configurables");
        player.sendMessage(ChatColor.GRAY + "• Estadísticas de niveles y experiencia");
        player.sendMessage(ChatColor.GRAY + "• Información de rankup (si está disponible)");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "💡 Tip: Usa /social para gestionar tus redes sociales");
        player.sendMessage(ChatColor.GREEN + "💡 Tip: Usa /genero para cambiar tu género");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // Comandos básicos
            completions.add("help");

            // Comandos administrativos
            if (player.hasPermission("survivalcore.reload")) {
                completions.add("reload");
            }

            if (player.hasPermission("survivalcore.debug")) {
                completions.add("debug");
            }

            // Jugadores online (si tiene permisos)
            if (player.hasPermission("survivalcore.perfil.others")) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    completions.add(onlinePlayer.getName());
                }
            }

            return completions.stream()
                    .filter(completion -> completion.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }

        return new ArrayList<>();
    }
}