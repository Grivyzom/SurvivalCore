package gc.grivyzom.survivalcore.flowers.config;

import org.bukkit.ChatColor;

/**
 * Representa la definición de un tier (calidad) de flor mágica
 *
 * @author Brocolitx
 * @version 1.0
 */
public class TierDefinition {

    private final String name;
    private final String color;
    private final int maxLevel;
    private final double effectMultiplier;
    private final double particleMultiplier;
    private final int rarityWeight;
    private final String displayName;

    public TierDefinition(String name, String color, int maxLevel, double effectMultiplier,
                          double particleMultiplier, int rarityWeight) {
        this.name = name.toUpperCase();
        this.color = color;
        this.maxLevel = maxLevel;
        this.effectMultiplier = effectMultiplier;
        this.particleMultiplier = particleMultiplier;
        this.rarityWeight = rarityWeight;
        this.displayName = ChatColor.translateAlternateColorCodes('&', color + name);
    }

    // =================== GETTERS ===================

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public String getColorCode() {
        return ChatColor.translateAlternateColorCodes('&', color);
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public double getEffectMultiplier() {
        return effectMultiplier;
    }

    public double getParticleMultiplier() {
        return particleMultiplier;
    }

    public int getRarityWeight() {
        return rarityWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    // =================== MÉTODOS DE UTILIDAD ===================

    /**
     * Calcula el multiplicador de efecto para un nivel específico
     */
    public double getEffectMultiplierForLevel(int level) {
        if (level <= 0 || level > maxLevel) return effectMultiplier;

        // Multiplicador base + escalamiento por nivel
        double baseMultiplier = effectMultiplier;
        double levelBonus = (level - 1) * 0.1; // +10% por nivel adicional

        return baseMultiplier + levelBonus;
    }

    /**
     * Calcula el multiplicador de partículas para un nivel específico
     */
    public double getParticleMultiplierForLevel(int level) {
        if (level <= 0 || level > maxLevel) return particleMultiplier;

        double baseMultiplier = particleMultiplier;
        double levelBonus = (level - 1) * 0.15; // +15% por nivel adicional

        return baseMultiplier + levelBonus;
    }

    /**
     * Verifica si este tier es más raro que otro
     */
    public boolean isRarerThan(TierDefinition other) {
        return this.rarityWeight < other.rarityWeight;
    }

    /**
     * Obtiene la probabilidad relativa de este tier
     */
    public double getRelativeProbability(int totalWeight) {
        return (double) rarityWeight / totalWeight;
    }

    /**
     * Formatea el nombre del tier con color para mostrar
     */
    public String getFormattedName() {
        return ChatColor.translateAlternateColorCodes('&', color + name);
    }

    /**
     * Obtiene una descripción completa del tier
     */
    public String getDescription() {
        return String.format("%s (Nivel Max: %d, Multiplicador: %.1fx, Rareza: %d)",
                getFormattedName(), maxLevel, effectMultiplier, rarityWeight);
    }

    // =================== VALIDACIÓN ===================

    /**
     * Valida que la configuración del tier sea correcta
     */
    public boolean isValid() {
        return !name.isEmpty()
                && !color.isEmpty()
                && maxLevel > 0
                && effectMultiplier > 0
                && particleMultiplier > 0
                && rarityWeight > 0;
    }

    /**
     * Obtiene una lista de errores de validación
     */
    public java.util.List<String> getValidationErrors() {
        java.util.List<String> errors = new java.util.ArrayList<>();

        if (name.isEmpty()) {
            errors.add("Nombre del tier no puede estar vacío");
        }

        if (color.isEmpty()) {
            errors.add("Color del tier no puede estar vacío");
        }

        if (maxLevel <= 0) {
            errors.add("Nivel máximo debe ser mayor a 0");
        }

        if (effectMultiplier <= 0) {
            errors.add("Multiplicador de efecto debe ser mayor a 0");
        }

        if (particleMultiplier <= 0) {
            errors.add("Multiplicador de partículas debe ser mayor a 0");
        }

        if (rarityWeight <= 0) {
            errors.add("Peso de rareza debe ser mayor a 0");
        }

        return errors;
    }

    // =================== COMPARACIÓN Y EQUALS ===================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TierDefinition that = (TierDefinition) obj;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return String.format("TierDefinition{name='%s', maxLevel=%d, effectMult=%.2f, rarityWeight=%d}",
                name, maxLevel, effectMultiplier, rarityWeight);
    }

    // =================== FACTORY METHODS ===================

    /**
     * Crea un tier básico con valores por defecto
     */
    public static TierDefinition createBasic(String name, String color) {
        return new TierDefinition(name, color, 5, 1.0, 1.0, 100);
    }

    /**
     * Crea un tier desde una configuración mínima
     */
    public static TierDefinition fromMinimalConfig(String name, String color, int maxLevel) {
        return new TierDefinition(name, color, maxLevel, 1.0, 1.0, 100);
    }
}