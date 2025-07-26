package gc.grivyzom.survivalcore.flowerpot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Clase que representa una Maceta Mágica
 * Sistema escalable para colocar flores mágicas y generar efectos de área
 *
 * @author Brocolitx
 * @version 1.0
 */
public class MagicFlowerPot {

    private final JavaPlugin plugin;
    private final NamespacedKey isMagicPotKey;
    private final NamespacedKey potLevelKey;
    private final NamespacedKey potRangeKey;
    private final NamespacedKey containedFlowerKey;
    private final NamespacedKey potIdKey;

    public MagicFlowerPot(JavaPlugin plugin) {
        this.plugin = plugin;
        this.isMagicPotKey = new NamespacedKey(plugin, "is_magic_flowerpot");
        this.potLevelKey = new NamespacedKey(plugin, "pot_level");
        this.potRangeKey = new NamespacedKey(plugin, "pot_range");
        this.containedFlowerKey = new NamespacedKey(plugin, "contained_flower");
        this.potIdKey = new NamespacedKey(plugin, "pot_id");
    }

    /**
     * Crea una nueva Maceta Mágica con nivel específico
     */
    public ItemStack createMagicFlowerPot(int level) {
        ItemStack pot = new ItemStack(Material.FLOWER_POT);
        ItemMeta meta = pot.getItemMeta();

        if (meta == null) return pot;

        // Configurar metadatos básicos
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✿ Maceta Mágica ✿" +
                ChatColor.GRAY + " [Nivel " + level + "]");

        // Configurar lore según el nivel
        List<String> lore = createLoreForLevel(level);
        meta.setLore(lore);

        // Configurar datos persistentes
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(isMagicPotKey, PersistentDataType.BYTE, (byte) 1);
        container.set(potLevelKey, PersistentDataType.INTEGER, level);
        container.set(potRangeKey, PersistentDataType.INTEGER, calculateRangeForLevel(level));
        container.set(containedFlowerKey, PersistentDataType.STRING, "none");
        container.set(potIdKey, PersistentDataType.STRING, generatePotId());

        pot.setItemMeta(meta);
        return pot;
    }

    /**
     * Verifica si un item es una Maceta Mágica
     */
    public boolean isMagicFlowerPot(ItemStack item) {
        if (item == null || item.getType() != Material.FLOWER_POT) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(isMagicPotKey, PersistentDataType.BYTE);
    }

    /**
     * Obtiene el nivel de una maceta mágica
     */
    public int getPotLevel(ItemStack item) {
        if (!isMagicFlowerPot(item)) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(potLevelKey, PersistentDataType.INTEGER, 1);
    }

    /**
     * Obtiene el rango de efectos de una maceta mágica
     */
    public int getPotRange(ItemStack item) {
        if (!isMagicFlowerPot(item)) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(potRangeKey, PersistentDataType.INTEGER, 3);
    }

    /**
     * Obtiene la flor contenida en la maceta
     */
    public String getContainedFlower(ItemStack item) {
        if (!isMagicFlowerPot(item)) return "none";

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "none";

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(containedFlowerKey, PersistentDataType.STRING, "none");
    }

    /**
     * Establece la flor contenida en la maceta
     */
    public ItemStack setContainedFlower(ItemStack pot, String flowerId) {
        if (!isMagicFlowerPot(pot)) return pot;

        ItemStack updatedPot = pot.clone();
        ItemMeta meta = updatedPot.getItemMeta();
        if (meta == null) return pot;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(containedFlowerKey, PersistentDataType.STRING, flowerId);

        // Actualizar el lore para mostrar la flor
        updateLoreWithFlower(meta, flowerId);

        updatedPot.setItemMeta(meta);
        return updatedPot;
    }

    /**
     * Verifica si la maceta tiene una flor
     */
    public boolean hasFlower(ItemStack item) {
        String flower = getContainedFlower(item);
        return flower != null && !flower.equals("none");
    }

    /**
     * Obtiene el ID único de la maceta
     */
    public String getPotId(ItemStack item) {
        if (!isMagicFlowerPot(item)) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(potIdKey, PersistentDataType.STRING);
    }

    /**
     * Crea el lore para una maceta según su nivel
     */
    private List<String> createLoreForLevel(int level) {
        return Arrays.asList(
                ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                ChatColor.WHITE + "  ✿ Maceta Mágica de Nivel " + ChatColor.AQUA + level,
                ChatColor.WHITE + "  📏 Rango: " + ChatColor.GREEN + calculateRangeForLevel(level) + " bloques",
                ChatColor.WHITE + "  🌸 Flor: " + ChatColor.YELLOW + "Vacía",
                ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "",
                ChatColor.GREEN + "▶ Coloca en el suelo para usar",
                ChatColor.GREEN + "▶ Click con flor mágica para plantar",
                ChatColor.YELLOW + "▶ Mayor nivel = mayor rango",
                ChatColor.GRAY + "▶ Solo acepta flores mágicas especiales"
        );
    }

    /**
     * Actualiza el lore cuando se coloca una flor
     */
    private void updateLoreWithFlower(ItemMeta meta, String flowerId) {
        int level = meta.getPersistentDataContainer()
                .getOrDefault(potLevelKey, PersistentDataType.INTEGER, 1);

        String flowerDisplayName = getFlowerDisplayName(flowerId);

        List<String> newLore = Arrays.asList(
                ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                ChatColor.WHITE + "  ✿ Maceta Mágica de Nivel " + ChatColor.AQUA + level,
                ChatColor.WHITE + "  📏 Rango: " + ChatColor.GREEN + calculateRangeForLevel(level) + " bloques",
                ChatColor.WHITE + "  🌸 Flor: " + ChatColor.LIGHT_PURPLE + flowerDisplayName,
                ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "",
                ChatColor.GREEN + "✓ Maceta activa irradiando efectos",
                ChatColor.GREEN + "▶ Click para remover la flor",
                ChatColor.AQUA + "▶ Los efectos se aplican en área",
                ChatColor.GRAY + "▶ ID: #" + getPotIdFromMeta(meta)
        );

        meta.setLore(newLore);
    }

    /**
     * Calcula el rango de efectos según el nivel
     */
    private int calculateRangeForLevel(int level) {
        // Rango base 3, +2 por cada nivel adicional
        return 3 + (level - 1) * 2;
    }

    /**
     * Obtiene el nombre de display de una flor (preparado para futuras flores)
     */
    private String getFlowerDisplayName(String flowerId) {
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
     * Genera un ID único para la maceta
     */
    private String generatePotId() {
        return String.valueOf(System.currentTimeMillis() % 100000);
    }

    /**
     * Obtiene el ID de la maceta desde los metadatos
     */
    private String getPotIdFromMeta(ItemMeta meta) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(potIdKey, PersistentDataType.STRING, "00000");
    }

    /**
     * Mejora una maceta al siguiente nivel
     */
    public ItemStack upgradePot(ItemStack pot) {
        if (!isMagicFlowerPot(pot)) return pot;

        int currentLevel = getPotLevel(pot);
        int maxLevel = plugin.getConfig().getInt("magic_flowerpot.max_level", 5);

        if (currentLevel >= maxLevel) return pot;

        return createMagicFlowerPot(currentLevel + 1);
    }

    /**
     * Verifica si una maceta se puede mejorar
     */
    public boolean canUpgrade(ItemStack pot) {
        if (!isMagicFlowerPot(pot)) return false;

        int currentLevel = getPotLevel(pot);
        int maxLevel = plugin.getConfig().getInt("magic_flowerpot.max_level", 5);

        return currentLevel < maxLevel;
    }
}