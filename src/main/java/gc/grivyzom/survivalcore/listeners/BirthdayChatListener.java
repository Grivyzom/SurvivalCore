package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.commands.BirthdayCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class BirthdayChatListener implements Listener {

    private final BirthdayCommand command;

    public BirthdayChatListener(BirthdayCommand cmd) {
        this.command = cmd;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        command.processChat(e.getPlayer(), e.getMessage());
    }


    /**
     * Dispara evento cuando es el cumpleaños de un jugador
     */
    public void firePlayerBirthdayEvent(Player player, String birthday) {
        PlayerBirthdayEvent event = new PlayerBirthdayEvent(player, birthday);
        Bukkit.getPluginManager().callEvent(event);

        // Aplicar configuraciones del evento
        if (!event.isBroadcastEnabled()) {
            // No hacer broadcast si está deshabilitado
            return;
        }

        if (!event.isFireworksEnabled()) {
            // No lanzar fuegos artificiales si está deshabilitado
            return;
        }
    }

}
