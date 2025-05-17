package gc.grivyzom.survivalcore.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerCropExpGainEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String cropKey;
    private int baseXP;
    private double bonusMultiplier;
    private int finalXP;
    private boolean cancelled;

    public PlayerCropExpGainEvent(Player player, String cropKey, int baseXP, double bonusMultiplier, int finalXP) {
        this.player = player;
        this.cropKey = cropKey;
        this.baseXP = baseXP;
        this.bonusMultiplier = bonusMultiplier;
        this.finalXP = finalXP;
        this.cancelled = false;
    }

    public Player getPlayer() {
        return player;
    }

    public String getCropKey() {
        return cropKey;
    }

    public int getBaseXP() {
        return baseXP;
    }

    public void setBaseXP(int baseXP) {
        this.baseXP = baseXP;
    }

    public double getBonusMultiplier() {
        return bonusMultiplier;
    }

    public void setBonusMultiplier(double bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
    }

    public int getFinalXP() {
        return finalXP;
    }

    public void setFinalXP(int finalXP) {
        this.finalXP = finalXP;
    }

    public boolean isCancelled() {
        return cancelled;
    }

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
