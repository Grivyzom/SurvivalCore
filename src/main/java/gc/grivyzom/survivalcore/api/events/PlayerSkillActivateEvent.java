package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador activa una habilidad
 */
public class PlayerSkillActivateEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String skillName;
    private final int skillLevel;
    private final long duration;
    private boolean cancelled = false;

    public PlayerSkillActivateEvent(Player player, String skillName, int skillLevel, long duration) {
        this.player = player;
        this.skillName = skillName;
        this.skillLevel = skillLevel;
        this.duration = duration;
    }

    public Player getPlayer() { return player; }
    public String getSkillName() { return skillName; }
    public int getSkillLevel() { return skillLevel; }
    public long getDuration() { return duration; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}