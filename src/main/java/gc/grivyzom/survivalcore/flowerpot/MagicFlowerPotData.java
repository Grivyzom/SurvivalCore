package gc.grivyzom.survivalcore.flowerpot;

import org.bukkit.Location;

/**
 * Clase de datos que almacena información sobre una maceta mágica colocada
 *
 * @author Brocolitx
 * @version 1.0
 */
public class MagicFlowerPotData {

    private final String potId;
    private final int level;
    private String flowerId;
    private long lastUpdate;
    private final Location location;
    private long placedTime;

    public MagicFlowerPotData(String potId, int level, String flowerId, long lastUpdate, Location location) {
        this.potId = potId;
        this.level = level;
        this.flowerId = flowerId;
        this.lastUpdate = lastUpdate;
        this.location = location.clone();
        this.placedTime = System.currentTimeMillis();
    }

    // Getters
    public String getPotId() {
        return potId;
    }

    public int getLevel() {
        return level;
    }

    public String getFlowerId() {
        return flowerId;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public Location getLocation() {
        return location.clone();
    }

    public long getPlacedTime() {
        return placedTime;
    }

    // Setters
    public void setFlowerId(String flowerId) {
        this.flowerId = flowerId;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public void setPlacedTime(long placedTime) {
        this.placedTime = placedTime;
    }

    // Métodos de utilidad

    /**
     * Verifica si la maceta tiene una flor plantada
     */
    public boolean hasFlower() {
        return flowerId != null && !flowerId.equals("none");
    }

    /**
     * Obtiene el tiempo que la maceta ha estado activa (en milisegundos)
     */
    public long getActiveTime() {
        return System.currentTimeMillis() - placedTime;
    }

    /**
     * Obtiene el tiempo desde la última actualización (en milisegundos)
     */
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdate;
    }

    /**
     * Calcula el rango de efectos basado en el nivel
     */
    public int getEffectRange() {
        return 3 + (level - 1) * 2;
    }

    /**
     * Obtiene una representación en string de los datos
     */
    @Override
    public String toString() {
        return String.format("MagicFlowerPotData{id='%s', level=%d, flower='%s', location=%s, active=%s}",
                potId, level, flowerId,
                String.format("(%d,%d,%d)", location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                hasFlower() ? "true" : "false");
    }

    /**
     * Verifica si dos objetos MagicFlowerPotData son iguales
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        MagicFlowerPotData that = (MagicFlowerPotData) obj;
        return potId.equals(that.potId) && location.equals(that.location);
    }

    /**
     * Genera un hash code basado en el ID y la ubicación
     */
    @Override
    public int hashCode() {
        return potId.hashCode() * 31 + location.hashCode();
    }
}