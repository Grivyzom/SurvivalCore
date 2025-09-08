package gc.grivyzom.survivalcore.recipes;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Categorías de recetas para el Lectern Magic
 * Inspirado en el sistema de crafteo de Minecraft
 *
 * @author Mojang Team (Brocolitx)
 * @version 1.0
 */
public enum RecipeCategory {
    BASIC("Básicas", Material.CRAFTING_TABLE, ChatColor.WHITE),
    TOOLS("Herramientas", Material.IRON_PICKAXE, ChatColor.GREEN),
    COMBAT("Combate", Material.IRON_SWORD, ChatColor.RED),
    MAGIC("Magia", Material.ENCHANTED_BOOK, ChatColor.LIGHT_PURPLE),
    BUILDING("Construcción", Material.BRICKS, ChatColor.YELLOW),
    FOOD("Alimentos", Material.BREAD, ChatColor.GOLD),
    UTILITY("Utilidad", Material.COMPASS, ChatColor.BLUE),
    LEGENDARY("Legendario", Material.NETHER_STAR, ChatColor.DARK_PURPLE);

    private final String displayName;
    private final Material icon;
    private final ChatColor color;

    RecipeCategory(String displayName, Material icon, ChatColor color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String getDisplayName() {
        return color + displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public ChatColor getColor() {
        return color;
    }

    /**
     * Obtiene la categoría basada en palabras clave del nombre de la receta
     */
    public static RecipeCategory fromRecipeName(String recipeName) {
        if (recipeName == null) return BASIC;

        String name = recipeName.toLowerCase();

        // Detectar por palabras clave
        if (name.contains("pan") || name.contains("estofado") || name.contains("comida") ||
                name.contains("nutritivo") || name.contains("abundante")) {
            return FOOD;
        }

        if (name.contains("pico") || name.contains("martillo") || name.contains("herramienta") ||
                name.contains("reparado") || name.contains("acelerado")) {
            return TOOLS;
        }

        if (name.contains("espada") || name.contains("combat") || name.contains("arma") ||
                name.contains("afilada") || name.contains("guerra")) {
            return COMBAT;
        }

        if (name.contains("orbe") || name.contains("cristal") || name.contains("báculo") ||
                name.contains("magia") || name.contains("mágico") || name.contains("amuleto") ||
                name.contains("grimorio") || name.contains("corona")) {
            return MAGIC;
        }

        if (name.contains("bloque") || name.contains("construcción") || name.contains("edificar") ||
                name.contains("arquitecto")) {
            return BUILDING;
        }

        if (name.contains("brújula") || name.contains("utilidad") || name.contains("almacenamiento") ||
                name.contains("viajero")) {
            return UTILITY;
        }

        if (name.contains("mjolnir") || name.contains("cetro") || name.contains("corazón") ||
                name.contains("legendario") || name.contains("épico") || name.contains("divino") ||
                name.contains("supremo") || name.contains("eterno")) {
            return LEGENDARY;
        }

        return BASIC;
    }

    /**
     * Obtiene una descripción de la categoría
     */
    public String getDescription() {
        return switch (this) {
            case BASIC -> "Recetas fundamentales para la supervivencia";
            case TOOLS -> "Mejora tus herramientas de trabajo";
            case COMBAT -> "Armas y armaduras para la batalla";
            case MAGIC -> "Items imbuidos con poder arcano";
            case BUILDING -> "Materiales y herramientas de construcción";
            case FOOD -> "Comida mejorada y nutritiva";
            case UTILITY -> "Items útiles para la aventura";
            case LEGENDARY -> "Artefactos de poder incomparable";
        };
    }
}