package gc.grivyzom.survivalcore.api.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;

/**
 * Evento que se dispara cuando un jugador usa una SellWand
 */
public class PlayerSellWandUseEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Map<Material, Integer> itemsSold;
    private final double totalValue;
    private final long experienceGained;
    private final String experienceType;

    public PlayerSellWandUseEvent(Player player, Map<Material, Integer> itemsSold,
                                  double totalValue, long experienceGained, String experienceType) {
        this.player = player;
        this.itemsSold = itemsSold;
        this.totalValue = totalValue;
        this.experienceGained = experienceGained;
        this.experienceType = experienceType;
    }

    /**
     * Obtiene el jugador que usó la SellWand
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Obtiene los items que se vendieron
     */
    public Map<Material, Integer> getItemsSold() {
        return itemsSold;
    }

    /**
     * Obtiene el valor total de la venta
     */
    public double getTotalValue() {
        return totalValue;
    }

    /**
     * Obtiene la experiencia ganada
     */
    public long getExperienceGained() {
        return experienceGained;
    }

    /**
     * Obtiene el tipo de experiencia distribuida
     * @return "farming", "mining", "vanilla", "mixed"
     */
    public String getExperienceType() {
        return experienceType;
    }

    /**
     * Obtiene la cantidad total de items vendidos
     */
    public int getTotalItemsCount() {
        return itemsSold.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}