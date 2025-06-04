package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador sube de nivel una habilidad
 */
public class PlayerSkillLevelUpEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String skillName;
    private final int oldLevel;
    private final int newLevel;

    public PlayerSkillLevelUpEvent(Player player, String skillName, int oldLevel, int newLevel) {
        this.player = player;
        this.skillName = skillName;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() { return player; }
    public String getSkillName() { return skillName; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}