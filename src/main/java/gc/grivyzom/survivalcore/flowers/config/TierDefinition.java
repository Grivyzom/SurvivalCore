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

    public TierDefinition(String name, String color, int maxLevel,
                          double effectMultiplier, double particleMultiplier,
                          int rarityWeight) {
        this.name = name.toUpperCase();
        this.color = color;
        this.maxLevel = maxLevel;
        this.effectMultiplier = effectMultiplier;
        this.particleMultiplier = particleMultiplier;
        this.rarityWeight = rarityWeight;
    }

    // =================== GETTERS ===================

    public String getName() { return name; } // 🔧 CORREGIDO: Cambiar name() por getName()
    public String getColor() { return color; }
    public int getMaxLevel() { return maxLevel; }
    public double getEffectMultiplier() { return effectMultiplier; }
    public double getParticleMultiplier() { return particleMultiplier; }
    public int getRarityWeight() { return rarityWeight; }

    // =================== MÉTODOS DE CÁLCULO ===================

    /**
     * Obtiene el multiplicador de efectos para un nivel específico
     */
    public double getEffectMultiplierForLevel(int level) {
        double baseMultiplier = effectMultiplier;
        double levelBonus = (level - 1) * 0.1; // +10% por nivel adicional
        return baseMultiplier + levelBonus;
    }

    /**
     * Obtiene el multiplicador de partículas para un nivel específico
     */
    public double getParticleMultiplierForLevel(int level) {
        double baseMultiplier = particleMultiplier;
        double levelBonus = (level - 1) * 0.2; // +20% por nivel adicional
        return baseMultiplier + levelBonus;
    }

    /**
     * Obtiene el nivel máximo permitido para un tier, considerando override
     */
    public int getMaxLevelForTier(int requestedLevel) {
        return Math.min(requestedLevel, maxLevel);
    }

    /**
     * Calcula la rareza relativa de este tier
     */
    public double getRelativeRarity(int totalWeight) {
        return (double) rarityWeight / totalWeight;
    }

    /**
     * Verifica si es un tier de alta calidad
     */
    public boolean isHighTier() {
        return effectMultiplier >= 2.0 || maxLevel >= 7;
    }

    /**
     * Verifica si es un tier legendario o mítico
     */
    public boolean isLegendaryTier() {
        return name.equals("LEGENDARY") || name.equals("MYTHIC") || effectMultiplier >= 2.5;
    }

    @Override
    public String toString() {
        return String.format("TierDefinition{name='%s', maxLevel=%d, effectMult=%.1f, rarity=%d}",
                name, maxLevel, effectMultiplier, rarityWeight);
    }

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
}

// ═══════════════════════════════════════════════════════════════════

/**
 * Calculadora de fórmulas para efectos dinámicos
 *
 * @author Brocolitx
 * @version 1.0
 */
