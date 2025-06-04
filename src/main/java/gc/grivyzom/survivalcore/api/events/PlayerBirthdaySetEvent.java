package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador establece su cumpleaños
 */
public class PlayerBirthdaySetEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String birthday;
    private final boolean isToday;

    public PlayerBirthdaySetEvent(Player player, String birthday, boolean isToday) {
        this.player = player;
        this.birthday = birthday;
        this.isToday = isToday;
    }

    public Player getPlayer() { return player; }
    public String getBirthday() { return birthday; }
    public boolean isBirthdayToday() { return isToday; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}