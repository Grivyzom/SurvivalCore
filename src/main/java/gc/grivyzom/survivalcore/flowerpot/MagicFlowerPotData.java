package gc.grivyzom.survivalcore.flowerpot;

import org.bukkit.Location;

/**
 * Clase de datos que almacena información sobre una maceta mágica colocada
 * MEJORADA v1.1 - Soporte para nivel de flores mágicas
 *
 * @author Brocolitx
 * @version 1.1
 */
public class MagicFlowerPotData {

    private final String potId;
    private final int level;
    private String flowerId;
    private int flowerLevel; // 🆕 NUEVO: Nivel de la flor mágica
    private long lastUpdate;
    private final Location location;
    private long placedTime;

    public MagicFlowerPotData(String potId, int level, String flowerId, long lastUpdate, Location location) {
        this.potId = potId;
        this.level = level;
        this.flowerId = flowerId;
        this.flowerLevel = 1; // 🆕 Nivel por defecto
        this.lastUpdate = lastUpdate;
        this.location = location.clone();
        this.placedTime = System.currentTimeMillis();
    }

    /**
     * 🆕 NUEVO: Constructor que incluye el nivel de la flor
     */
    public MagicFlowerPotData(String potId, int level, String flowerId, int flowerLevel, long lastUpdate, Location location) {
        this.potId = potId;
        this.level = level;
        this.flowerId = flowerId;
        this.flowerLevel = flowerLevel;
        this.lastUpdate = lastUpdate;
        this.location = location.clone();
        this.placedTime = System.currentTimeMillis();
    }

    // =================== GETTERS BÁSICOS ===================

    public String getPotId() {
        return potId;
    }

    public int getLevel() {
        return level;
    }

    public String getFlowerId() {
        return flowerId;
    }

    /**
     * 🆕 NUEVO: Obtiene el nivel de la flor mágica
     */
    public int getFlowerLevel() {
        return flowerLevel;
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

    // =================== SETTERS ===================

    public void setFlowerId(String flowerId) {
        this.flowerId = flowerId;
    }

    /**
     * 🆕 NUEVO: Establece el nivel de la flor mágica
     */
    public void setFlowerLevel(int flowerLevel) {
        this.flowerLevel = Math.max(1, Math.min(5, flowerLevel)); // Limitar entre 1 y 5
    }

    /**
     * 🆕 NUEVO: Actualiza tanto el ID como el nivel de la flor
     */
    public void setFlower(String flowerId, int flowerLevel) {
        this.flowerId = flowerId;
        this.flowerLevel = Math.max(1, Math.min(5, flowerLevel));
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public void setPlacedTime(long placedTime) {
        this.placedTime = placedTime;
    }

    // =================== MÉTODOS DE UTILIDAD ===================

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
     * Calcula el rango de efectos basado en el nivel de la maceta
     */
    public int getEffectRange() {
        return 3 + (level - 1) * 2;
    }

    /**
     * 🆕 NUEVO: Calcula la potencia del efecto basada en el nivel de la flor
     */
    public int getEffectAmplifier() {
        if (!hasFlower()) return 0;

        // El amplificador del efecto se basa en el nivel de la flor (0-based)
        return Math.max(0, flowerLevel - 1);
    }

    /**
     * 🆕 NUEVO: Calcula la duración del efecto modificada por el nivel de la flor
     */
    public int getEffectDuration(int baseDuration) {
        if (!hasFlower()) return baseDuration;

        // La duración se incrementa ligeramente con el nivel de la flor
        double multiplier = 1.0 + (flowerLevel - 1) * 0.2; // +20% por nivel adicional
        return (int) (baseDuration * multiplier);
    }

    /**
     * 🆕 NUEVO: Obtiene una descripción completa de la flor
     */
    public String getFlowerDescription() {
        if (!hasFlower()) return "Vacía";

        String flowerName = getFlowerDisplayName();
        if (flowerLevel > 1) {
            return flowerName + " (Nivel " + flowerLevel + ")";
        }
        return flowerName;
    }

    /**
     * 🆕 NUEVO: Obtiene el nombre de display de la flor actual
     */
    private String getFlowerDisplayName() {
        if (!hasFlower()) return "Ninguna";

        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return "Flor del Amor";
            case "healing_flower":
                return "Flor Sanadora";
            case "speed_flower":
                return "Flor de Velocidad";
            case "strength_flower":
                return "Flor de Fuerza";
            case "night_vision_flower":
                return "Flor Nocturna";
            default:
                return "Flor Desconocida";
        }
    }

    /**
     * 🆕 NUEVO: Verifica si la flor es de nivel máximo
     */
    public boolean isMaxLevelFlower() {
        return hasFlower() && flowerLevel >= 5;
    }

    /**
     * 🆕 NUEVO: Obtiene el multiplicador de partículas basado en el nivel de la flor
     */
    public double getParticleMultiplier() {
        if (!hasFlower()) return 1.0;
        return 1.0 + (flowerLevel - 1) * 0.5; // +50% por nivel adicional
    }

    /**
     * Obtiene una representación en string de los datos - ACTUALIZADA
     */
    @Override
    public String toString() {
        return String.format("MagicFlowerPotData{id='%s', level=%d, flower='%s' (Lv.%d), location=%s, active=%s}",
                potId, level, flowerId, flowerLevel,
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

    // =================== MÉTODOS DE COMPATIBILIDAD ===================

    /**
     * 🆕 NUEVO: Resetea la flor (quita la flor de la maceta)
     */
    public void removeFlower() {
        this.flowerId = "none";
        this.flowerLevel = 1;
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 🆕 NUEVO: Crea una copia de los datos con una nueva flor
     */
    public MagicFlowerPotData withFlower(String newFlowerId, int newFlowerLevel) {
        MagicFlowerPotData copy = new MagicFlowerPotData(
                this.potId, this.level, newFlowerId, newFlowerLevel,
                System.currentTimeMillis(), this.location
        );
        copy.setPlacedTime(this.placedTime);
        return copy;
    }

    /**
     * 🆕 NUEVO: Actualiza los datos de la flor manteniendo el timestamp
     */
    public void updateFlower(String newFlowerId, int newFlowerLevel) {
        this.flowerId = newFlowerId;
        this.flowerLevel = Math.max(1, Math.min(5, newFlowerLevel));
        this.lastUpdate = System.currentTimeMillis();
    }
}