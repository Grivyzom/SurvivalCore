package gc.grivyzom.survivalcore.flowers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Factoría para crear Flores Mágicas
 * Sistema preparado para trabajar con las Macetas Mágicas
 *
 * @author Brocolitx
 * @version 1.0
 */
public class MagicFlowerFactory {

    private final JavaPlugin plugin;
    private final NamespacedKey isMagicFlowerKey;
    private final NamespacedKey flowerIdKey;
    private final NamespacedKey flowerLevelKey;

    public MagicFlowerFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.isMagicFlowerKey = new NamespacedKey(plugin, "is_magic_flower");
        this.flowerIdKey = new NamespacedKey(plugin, "flower_id");
        this.flowerLevelKey = new NamespacedKey(plugin, "flower_level");
    }

    /**
     * Crea una flor mágica específica
     */
    public ItemStack createMagicFlower(FlowerType type, int level) {
        ItemStack flower = new ItemStack(type.getMaterial());
        ItemMeta meta = flower.getItemMeta();

        if (meta == null) return flower;

        // Configurar metadatos básicos
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✿ " + type.getDisplayName() + " ✿" +
                ChatColor.GRAY + " [Nivel " + level + "]");

        // Configurar lore
        List<String> lore = createLoreForFlower(type, level);
        meta.setLore(lore);

        // Añadir brillo mágico
        meta.addEnchant(Enchantment.LURE, 1, true);

        // Configurar datos persistentes
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(isMagicFlowerKey, PersistentDataType.BYTE, (byte) 1);
        container.set(flowerIdKey, PersistentDataType.STRING, type.getId());
        container.set(flowerLevelKey, PersistentDataType.INTEGER, level);

        flower.setItemMeta(meta);
        return flower;
    }

    /**
     * Verifica si un ítem es una flor mágica
     */
    public boolean isMagicFlower(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(isMagicFlowerKey, PersistentDataType.BYTE);
    }

    /**
     * Obtiene el ID de una flor mágica
     */
    public String getFlowerId(ItemStack item) {
        if (!isMagicFlower(item)) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(flowerIdKey, PersistentDataType.STRING);
    }

    /**
     * Obtiene el nivel de una flor mágica
     */
    public int getFlowerLevel(ItemStack item) {
        if (!isMagicFlower(item)) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(flowerLevelKey, PersistentDataType.INTEGER, 1);
    }

    /**
     * Crea el lore para una flor mágica
     */
    private List<String> createLoreForFlower(FlowerType type, int level) {
        return Arrays.asList(
                ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                ChatColor.WHITE + "  ✿ " + type.getDisplayName() + " de Nivel " + ChatColor.AQUA + level,
                ChatColor.WHITE + "  ⚡ Efecto: " + ChatColor.GREEN + type.getEffectDescription(),
                ChatColor.WHITE + "  🎯 Potencia: " + getPowerDescription(level),
                ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "",
                ChatColor.GREEN + "▶ Úsala en una Maceta Mágica",
                ChatColor.YELLOW + "▶ Mayor nivel = efectos más potentes",
                ChatColor.LIGHT_PURPLE + "▶ Solo funciona en macetas especiales",
                ChatColor.GRAY + "▶ Las macetas normales no la aceptan"
        );
    }

    /**
     * Obtiene la descripción de potencia según el nivel
     */
    private String getPowerDescription(int level) {
        switch (level) {
            case 1: return ChatColor.WHITE + "Básica";
            case 2: return ChatColor.GREEN + "Mejorada";
            case 3: return ChatColor.BLUE + "Avanzada";
            case 4: return ChatColor.LIGHT_PURPLE + "Superior";
            case 5: return ChatColor.GOLD + "Épica";
            default: return ChatColor.GRAY + "Desconocida";
        }
    }

    /**
     * Enum que define los tipos de flores mágicas disponibles
     */
    public enum FlowerType {
        LOVE_FLOWER(
                "love_flower",
                "Flor del Amor",
                Material.POPPY,
                "Regeneración continua"
        ),
        HEALING_FLOWER(
                "healing_flower",
                "Flor Sanadora",
                Material.DANDELION,
                "Curación instantánea"
        ),
        SPEED_FLOWER(
                "speed_flower",
                "Flor de Velocidad",
                Material.BLUE_ORCHID,
                "Aumenta la velocidad"
        ),
        STRENGTH_FLOWER(
                "strength_flower",
                "Flor de Fuerza",
                Material.ALLIUM,
                "Incrementa la fuerza"
        ),
        NIGHT_VISION_FLOWER(
                "night_vision_flower",
                "Flor Nocturna",
                Material.AZURE_BLUET,
                "Visión en la oscuridad"
        );

        private final String id;
        private final String displayName;
        private final Material material;
        private final String effectDescription;

        FlowerType(String id, String displayName, Material material, String effectDescription) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.effectDescription = effectDescription;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public String getEffectDescription() { return effectDescription; }

        /**
         * Obtiene un tipo de flor por su ID
         */
        public static FlowerType getById(String id) {
            for (FlowerType type : values()) {
                if (type.getId().equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }
}