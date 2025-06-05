package gc.grivyzom.survivalcore.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Utilidades para el sistema de reparación
 *
 * @author Brocolitx
 * @version 1.0
 */
public class RepairUtils {

    /**
     * Categoriza un ítem según su tipo
     */
    public enum ItemCategory {
        SWORD("Espada"),
        AXE("Hacha"),
        PICKAXE("Pico"),
        SHOVEL("Pala"),
        HOE("Azada"),
        BOW("Arco"),
        CROSSBOW("Ballesta"),
        TRIDENT("Tridente"),
        SHIELD("Escudo"),
        HELMET("Casco"),
        CHESTPLATE("Pechera"),
        LEGGINGS("Pantalones"),
        BOOTS("Botas"),
        FISHING_ROD("Caña de pescar"),
        SHEARS("Tijeras"),
        FLINT_AND_STEEL("Mechero"),
        ELYTRA("Élitros"),
        OTHER("Otro");

        private final String displayName;

        ItemCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Obtiene la categoría de un ítem
     */
    public static ItemCategory getItemCategory(ItemStack item) {
        if (item == null) return ItemCategory.OTHER;

        String name = item.getType().name();

        if (name.contains("SWORD")) return ItemCategory.SWORD;
        if (name.contains("_AXE") && !name.contains("PICKAXE")) return ItemCategory.AXE;
        if (name.contains("PICKAXE")) return ItemCategory.PICKAXE;
        if (name.contains("SHOVEL")) return ItemCategory.SHOVEL;
        if (name.contains("HOE")) return ItemCategory.HOE;
        if (name.equals("BOW")) return ItemCategory.BOW;
        if (name.equals("CROSSBOW")) return ItemCategory.CROSSBOW;
        if (name.equals("TRIDENT")) return ItemCategory.TRIDENT;
        if (name.equals("SHIELD")) return ItemCategory.SHIELD;
        if (name.contains("HELMET")) return ItemCategory.HELMET;
        if (name.contains("CHESTPLATE")) return ItemCategory.CHESTPLATE;
        if (name.contains("LEGGINGS")) return ItemCategory.LEGGINGS;
        if (name.contains("BOOTS")) return ItemCategory.BOOTS;
        if (name.equals("FISHING_ROD")) return ItemCategory.FISHING_ROD;
        if (name.equals("SHEARS")) return ItemCategory.SHEARS;
        if (name.equals("FLINT_AND_STEEL")) return ItemCategory.FLINT_AND_STEEL;
        if (name.equals("ELYTRA")) return ItemCategory.ELYTRA;

        return ItemCategory.OTHER;
    }

    /**
     * Verifica si un ítem es un arma
     */
    public static boolean isWeapon(ItemStack item) {
        ItemCategory category = getItemCategory(item);
        return category == ItemCategory.SWORD ||
                category == ItemCategory.AXE ||
                category == ItemCategory.BOW ||
                category == ItemCategory.CROSSBOW ||
                category == ItemCategory.TRIDENT;
    }

    /**
     * Verifica si un ítem es una herramienta
     */
    public static boolean isTool(ItemStack item) {
        ItemCategory category = getItemCategory(item);
        return category == ItemCategory.PICKAXE ||
                category == ItemCategory.SHOVEL ||
                category == ItemCategory.HOE ||
                category == ItemCategory.SHEARS ||
                category == ItemCategory.FISHING_ROD ||
                category == ItemCategory.FLINT_AND_STEEL;
    }

    /**
     * Verifica si un ítem es armadura
     */
    public static boolean isArmor(ItemStack item) {
        ItemCategory category = getItemCategory(item);
        return category == ItemCategory.HELMET ||
                category == ItemCategory.CHESTPLATE ||
                category == ItemCategory.LEGGINGS ||
                category == ItemCategory.BOOTS ||
                category == ItemCategory.SHIELD ||
                category == ItemCategory.ELYTRA;
    }

    /**
     * Obtiene el daño actual de un ítem
     */
    public static int getDamage(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return damageable.getDamage();
        }

        return 0;
    }

    /**
     * Obtiene el porcentaje de durabilidad restante
     */
    public static double getDurabilityPercentage(ItemStack item) {
        if (item == null) return 0;

        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability == 0) return 100;

        int damage = getDamage(item);
        int currentDurability = maxDurability - damage;

        return (double) currentDurability / maxDurability * 100;
    }

    /**
     * Calcula el valor de un ítem basado en su material y encantamientos
     */
    public static int calculateItemValue(ItemStack item) {
        if (item == null) return 0;

        int baseValue = getMaterialValue(item.getType());
        int enchantmentValue = 0;

        // Añadir valor por encantamientos
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            int level = entry.getValue();
            enchantmentValue += getEnchantmentValue(entry.getKey()) * level;
        }

        return baseValue + enchantmentValue;
    }

    /**
     * Obtiene el valor base de un material
     */
    private static int getMaterialValue(Material material) {
        String name = material.name();

        // Valores base por material
        if (name.contains("NETHERITE")) return 100;
        if (name.contains("DIAMOND")) return 50;
        if (name.contains("EMERALD")) return 40;
        if (name.contains("GOLD")) return 20;
        if (name.contains("IRON")) return 15;
        if (name.contains("CHAINMAIL")) return 12;
        if (name.contains("STONE")) return 5;
        if (name.contains("LEATHER")) return 3;
        if (name.contains("WOOD")) return 2;

        return 10; // Valor por defecto
    }

    /**
     * Obtiene el valor de un encantamiento
     */
    private static int getEnchantmentValue(Enchantment enchantment) {
        // Encantamientos valiosos
        if (enchantment.equals(Enchantment.MENDING)) return 50;
        if (enchantment.equals(Enchantment.SILK_TOUCH)) return 40;
        if (enchantment.equals(Enchantment.LUCK)) return 35;
        if (enchantment.equals(Enchantment.LOOT_BONUS_BLOCKS)) return 30;
        if (enchantment.equals(Enchantment.DAMAGE_ALL)) return 25;
        if (enchantment.equals(Enchantment.DIG_SPEED)) return 25;
        if (enchantment.equals(Enchantment.PROTECTION_ENVIRONMENTAL)) return 20;
        if (enchantment.equals(Enchantment.DURABILITY)) return 15;

        // Encantamientos estándar
        return 10;
    }

    /**
     * Formatea la durabilidad de un ítem para mostrar
     */
    public static String formatDurability(ItemStack item) {
        if (item == null) return "N/A";

        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability == 0) return "∞";

        int damage = getDamage(item);
        int currentDurability = maxDurability - damage;
        double percentage = getDurabilityPercentage(item);

        return String.format("%d/%d (%.1f%%)", currentDurability, maxDurability, percentage);
    }

    /**
     * Obtiene el color para mostrar la durabilidad
     */
    public static String getDurabilityColor(double percentage) {
        if (percentage > 75) return "§a"; // Verde
        if (percentage > 50) return "§e"; // Amarillo
        if (percentage > 25) return "§6"; // Naranja
        return "§c"; // Rojo
    }

    /**
     * Crea una barra de progreso visual
     */
    public static String createProgressBar(double percentage, int length) {
        int filled = (int) Math.ceil(percentage / 100.0 * length);
        StringBuilder bar = new StringBuilder();

        bar.append(getDurabilityColor(percentage));
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }

        bar.append("§7");
        for (int i = filled; i < length; i++) {
            bar.append("█");
        }

        return bar.toString();
    }

    /**
     * Verifica si un ítem tiene un encantamiento específico
     */
    public static boolean hasEnchantment(ItemStack item, Enchantment enchantment) {
        return item != null && item.containsEnchantment(enchantment);
    }

    /**
     * Cuenta el número total de encantamientos
     */
    public static int countEnchantments(ItemStack item) {
        if (item == null) return 0;
        return item.getEnchantments().size();
    }

    /**
     * Calcula el nivel total de encantamientos
     */
    public static int getTotalEnchantmentLevel(ItemStack item) {
        if (item == null) return 0;

        int total = 0;
        for (Integer level : item.getEnchantments().values()) {
            total += level;
        }

        return total;
    }

    /**
     * Verifica si un ítem necesita reparación
     */
    public static boolean needsRepair(ItemStack item) {
        return getDamage(item) > 0;
    }

    /**
     * Verifica si un ítem está críticamente dañado (menos del 10% de durabilidad)
     */
    public static boolean isCriticallyDamaged(ItemStack item) {
        return getDurabilityPercentage(item) < 10;
    }

    /**
     * Obtiene una descripción del estado del ítem
     */
    public static String getConditionDescription(ItemStack item) {
        double percentage = getDurabilityPercentage(item);

        if (percentage == 100) return "§aPerfecto estado";
        if (percentage >= 75) return "§aLigeramente usado";
        if (percentage >= 50) return "§eModerado desgaste";
        if (percentage >= 25) return "§6Muy desgastado";
        if (percentage >= 10) return "§cMuy dañado";
        return "§4¡A punto de romperse!";
    }
}