package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador gana XP en una profesión
 */
public class PlayerProfessionXPGainEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String profession;
    private final long xpGained;
    private final long totalXP;
    private boolean cancelled = false;

    public PlayerProfessionXPGainEvent(Player player, String profession, long xpGained, long totalXP) {
        this.player = player;
        this.profession = profession;
        this.xpGained = xpGained;
        this.totalXP = totalXP;
    }

    public Player getPlayer() { return player; }
    public String getProfession() { return profession; }
    public long getXPGained() { return xpGained; }
    public long getTotalXP() { return totalXP; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}