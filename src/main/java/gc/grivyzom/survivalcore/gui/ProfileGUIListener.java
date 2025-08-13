package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.util.MenuSecurityHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Listener para el GUI de perfil usando MenuSecurityHandler
 * Versión simplificada con seguridad centralizada
 *
 * @author Brocolitx
 * @version 5.0 - Con MenuSecurityHandler
 */
public class ProfileGUIListener implements Listener {

    private final Main plugin;
    private final MenuSecurityHandler securityHandler;

    public ProfileGUIListener(Main plugin) {
        this.plugin = plugin;

        // Crear el manejador de seguridad
        this.securityHandler = new MenuSecurityHandler(plugin)
                // Registrar todos los posibles títulos del menú de perfil
                .registerTitle("Perfil de")  // Título parcial que matchea cualquier "Perfil de {jugador}"
                .registerTitle("Profile")     // Por si usas títulos en inglés
                .registerTitle("👤")          // Por si usas emojis/símbolos en títulos

                // Configurar que solo se permite click izquierdo
                .setAllowOnlyLeftClick(true)

                // Configurar el manejador de clicks
                .setClickHandler((player, event) -> {
                    // Llamar al método handleClick de ProfileGUI
                    ProfileGUI.handleClick(
                            player,
                            event.getCurrentItem(),
                            event.getRawSlot(),
                            plugin
                    );
                })

                // Configurar el manejador de cierre
                .setCloseHandler(player -> {
                    // Limpiar el cache del jugador
                    ProfileGUI.cleanupPlayer(player);

                    // Log opcional
                    if (plugin.getConfig().getBoolean("debug.log_gui_interactions", false)) {
                        plugin.getLogger().info("Jugador " + player.getName() + " cerró el GUI de perfil");
                    }
                })

                // Activar debug si está configurado
                .setDebugMode(plugin.getConfig().getBoolean("debug.menu_security", false));
    }

    /**
     * Método para agregar títulos adicionales dinámicamente
     * Útil si los títulos se cargan desde configuración
     */
    public void registerAdditionalTitle(String title) {
        securityHandler.registerTitle(title);
    }

    /**
     * Método para limpiar cuando se desactiva el plugin
     */
    public void cleanup() {
        securityHandler.cleanup();
    }

    /**
     * Obtener el security handler para uso externo si es necesario
     */
    public MenuSecurityHandler getSecurityHandler() {
        return securityHandler;
    }
}