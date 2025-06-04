package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador mejora su banco de experiencia
 */
public class PlayerBankUpgradeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final long oldCapacity;
    private final long newCapacity;
    private final int newLevel;

    public PlayerBankUpgradeEvent(Player player, long oldCapacity, long newCapacity, int newLevel) {
        this.player = player;
        this.oldCapacity = oldCapacity;
        this.newCapacity = newCapacity;
        this.newLevel = newLevel;
    }

    public Player getPlayer() { return player; }
    public long getOldCapacity() { return oldCapacity; }
    public long getNewCapacity() { return newCapacity; }
    public int getNewLevel() { return newLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}