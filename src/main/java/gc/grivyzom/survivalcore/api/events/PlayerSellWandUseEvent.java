package gc.grivyzom.survivalcore.api.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;

/**
 * Evento disparado cuando un jugador usa una SellWand
 * Versión simplificada - Solo maneja experiencia vanilla
 *
 * @author Brocolitx
 * @version 2.0 - Simplificado
 */
public class PlayerSellWandUseEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Map<Material, Integer> itemsSold;
    private final double totalEarnings;
    private final long experienceGained;
    private final String experienceType; // Siempre será "vanilla"

    private boolean cancelled = false;

    /**
     * Constructor del evento SellWand simplificado
     *
     * @param player El jugador que usó la SellWand
     * @param itemsSold Mapa de materiales vendidos y sus cantidades
     * @param totalEarnings Total de puntos ganados por la venta
     * @param experienceGained Experiencia vanilla ganada
     * @param experienceType Tipo de experiencia (siempre "vanilla")
     */
    public PlayerSellWandUseEvent(Player player, Map<Material, Integer> itemsSold,
                                  double totalEarnings, long experienceGained, String experienceType) {
        this.player = player;
        this.itemsSold = itemsSold;
        this.totalEarnings = totalEarnings;
        this.experienceGained = experienceGained;
        this.experienceType = experienceType;
    }

    /**
     * Obtiene el jugador que usó la SellWand
     *
     * @return El jugador
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Obtiene el mapa de items vendidos
     * Formato: Material -> Cantidad vendida
     *
     * @return Mapa de items vendidos
     */
    public Map<Material, Integer> getItemsSold() {
        return itemsSold;
    }

    /**
     * Obtiene el total de ganancias en puntos
     *
     * @return Total de puntos ganados
     */
    public double getTotalEarnings() {
        return totalEarnings;
    }

    /**
     * Obtiene la experiencia vanilla ganada
     *
     * @return Cantidad de XP vanilla
     */
    public long getExperienceGained() {
        return experienceGained;
    }

    /**
     * Obtiene el tipo de experiencia
     * En la versión simplificada siempre retorna "vanilla"
     *
     * @return "vanilla"
     */
    public String getExperienceType() {
        return experienceType;
    }

    /**
     * Obtiene el número total de items vendidos
     * Suma todas las cantidades de todos los materiales
     *
     * @return Total de items vendidos
     */
    public int getTotalItemCount() {
        return itemsSold.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Verifica si se vendió un material específico
     *
     * @param material El material a verificar
     * @return true si se vendió ese material
     */
    public boolean hasSoldMaterial(Material material) {
        return itemsSold.containsKey(material);
    }

    /**
     * Obtiene la cantidad vendida de un material específico
     *
     * @param material El material a consultar
     * @return Cantidad vendida (0 si no se vendió)
     */
    public int getAmountSold(Material material) {
        return itemsSold.getOrDefault(material, 0);
    }

    /**
     * Obtiene el valor promedio por item vendido
     *
     * @return Valor promedio por item
     */
    public double getAverageValuePerItem() {
        int totalItems = getTotalItemCount();
        return totalItems > 0 ? totalEarnings / totalItems : 0.0;
    }

    /**
     * Verifica si la venta fue de alto valor
     * Se considera alto valor si supera los 500 puntos
     *
     * @return true si es una venta de alto valor
     */
    public boolean isHighValueSale() {
        return totalEarnings >= 500.0;
    }

    /**
     * Verifica si la venta fue de bajo valor
     * Se considera bajo valor si es menor a 50 puntos
     *
     * @return true si es una venta de bajo valor
     */
    public boolean isLowValueSale() {
        return totalEarnings < 50.0;
    }

    /**
     * Obtiene el número de tipos diferentes de items vendidos
     *
     * @return Número de materiales diferentes
     */
    public int getUniqueItemTypes() {
        return itemsSold.size();
    }

    /**
     * Verifica si solo se vendió un tipo de item
     *
     * @return true si solo se vendió un tipo de material
     */
    public boolean isSingleItemType() {
        return itemsSold.size() == 1;
    }

    /**
     * Obtiene la tasa de conversión aplicada
     * Calcula cuánta XP se obtuvo por punto ganado
     *
     * @return Tasa de XP por punto
     */
    public double getXpConversionRate() {
        return totalEarnings > 0 ? (double) experienceGained / totalEarnings : 0.0;
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

    /**
     * Representación en string del evento para debugging
     *
     * @return String descriptivo del evento
     */
    @Override
    public String toString() {
        return String.format(
                "PlayerSellWandUseEvent{player=%s, uniqueItems=%d, totalItems=%d, totalEarnings=%.2f, " +
                        "experienceGained=%d, type=%s, cancelled=%s}",
                player.getName(),
                getUniqueItemTypes(),
                getTotalItemCount(),
                totalEarnings,
                experienceGained,
                experienceType,
                cancelled
        );
    }

    /**
     * Información detallada del evento para logs
     *
     * @return String con información completa
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("SellWand Event Details:\n");
        info.append("  Player: ").append(player.getName()).append("\n");
        info.append("  Items sold: ").append(getTotalItemCount()).append(" (").append(getUniqueItemTypes()).append(" types)\n");
        info.append("  Total earnings: ").append(String.format("%.2f", totalEarnings)).append(" points\n");
        info.append("  Experience gained: ").append(experienceGained).append(" XP (").append(experienceType).append(")\n");
        info.append("  Conversion rate: ").append(String.format("%.4f", getXpConversionRate())).append(" XP/point\n");
        info.append("  Sale category: ");

        if (isHighValueSale()) {
            info.append("High Value");
        } else if (isLowValueSale()) {
            info.append("Low Value");
        } else {
            info.append("Medium Value");
        }

        return info.toString();
    }
}