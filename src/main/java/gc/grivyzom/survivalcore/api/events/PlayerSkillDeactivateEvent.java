package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando una habilidad se desactiva
 */
public class PlayerSkillDeactivateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String skillName;
    private final int skillLevel;

    public PlayerSkillDeactivateEvent(Player player, String skillName, int skillLevel) {
        this.player = player;
        this.skillName = skillName;
        this.skillLevel = skillLevel;
    }

    public Player getPlayer() { return player; }
    public String getSkillName() { return skillName; }
    public int getSkillLevel() { return skillLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}