package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador mejora una maestría
 */
public class PlayerMasteryUpgradeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String masteryId;
    private final int oldLevel;
    private final int newLevel;
    private final boolean isMaxLevel;

    public PlayerMasteryUpgradeEvent(Player player, String masteryId, int oldLevel, int newLevel, boolean isMaxLevel) {
        this.player = player;
        this.masteryId = masteryId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.isMaxLevel = isMaxLevel;
    }

    public Player getPlayer() { return player; }
    public String getMasteryId() { return masteryId; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }
    public boolean isMaxLevel() { return isMaxLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}