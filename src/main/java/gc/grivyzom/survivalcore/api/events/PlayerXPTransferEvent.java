package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador transfiere experiencia a otro
 */
public class PlayerXPTransferEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player sender;
    private final String receiverName;
    private final long amount;
    private final TransferType type;
    private boolean cancelled = false;

    public enum TransferType {
        PLAYER_XP, BANK_XP
    }

    public PlayerXPTransferEvent(Player sender, String receiverName, long amount, TransferType type) {
        this.sender = sender;
        this.receiverName = receiverName;
        this.amount = amount;
        this.type = type;
    }

    public Player getSender() { return sender; }
    public String getReceiverName() { return receiverName; }
    public long getAmount() { return amount; }
    public TransferType getType() { return type; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}