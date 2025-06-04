package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando es el cumpleaños de un jugador (al conectarse)
 */
public class PlayerBirthdayEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String birthday;
    private boolean fireworksEnabled = true;
    private boolean broadcastEnabled = true;

    public PlayerBirthdayEvent(Player player, String birthday) {
        this.player = player;
        this.birthday = birthday;
    }

    public Player getPlayer() { return player; }
    public String getBirthday() { return birthday; }
    public boolean isFireworksEnabled() { return fireworksEnabled; }
    public void setFireworksEnabled(boolean enabled) { this.fireworksEnabled = enabled; }
    public boolean isBroadcastEnabled() { return broadcastEnabled; }
    public void setBroadcastEnabled(boolean enabled) { this.broadcastEnabled = enabled; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}