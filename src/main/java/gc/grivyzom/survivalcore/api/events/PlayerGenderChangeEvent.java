package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador cambia su género
 */
public class PlayerGenderChangeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String oldGender;
    private final String newGender;

    public PlayerGenderChangeEvent(Player player, String oldGender, String newGender) {
        this.player = player;
        this.oldGender = oldGender;
        this.newGender = newGender;
    }

    public Player getPlayer() { return player; }
    public String getOldGender() { return oldGender; }
    public String getNewGender() { return newGender; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}