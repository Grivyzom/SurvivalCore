package gc.grivyzom.survivalcore.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento disparado cuando un jugador retira XP de su banco
 */
public class PlayerBankWithdrawEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final long amount;
    private final long newBalance;
    private boolean cancelled = false;

    public PlayerBankWithdrawEvent(Player player, long amount, long newBalance) {
        this.player = player;
        this.amount = amount;
        this.newBalance = newBalance;
    }

    public Player getPlayer() { return player; }
    public long getAmount() { return amount; }
    public long getNewBalance() { return newBalance; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}