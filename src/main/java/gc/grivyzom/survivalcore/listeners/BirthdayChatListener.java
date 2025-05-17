package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.commands.BirthdayCommand;
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
}
