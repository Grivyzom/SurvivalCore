package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador sube de nivel en una profesión
 */
public class PlayerProfessionLevelUpEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String profession;
    private final int oldLevel;
    private final int newLevel;

    public PlayerProfessionLevelUpEvent(Player player, String profession, int oldLevel, int newLevel) {
        this.player = player;
        this.profession = profession;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() { return player; }
    public String getProfession() { return profession; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}