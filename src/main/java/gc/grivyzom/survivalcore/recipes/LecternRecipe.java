package gc.grivyzom.survivalcore.recipes;

import org.bukkit.inventory.ItemStack;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Receta shapeless de dos inputs con comparación de meta. */
public final class LecternRecipe {
    private final String id;       // ← nuevo
    private final int level;
    private final int xpCost;      // ← nuevo
    private final ItemStack[] inputs;
    private final ItemStack   result;


    public LecternRecipe(String id, int level, int xpCost,
                         ItemStack first, ItemStack second, ItemStack result) {
        this.id      = (id == null ? "" : id);
        this.level   = level;
        this.xpCost  = xpCost;
        this.inputs  = new ItemStack[] { first.clone(), second.clone() };
        this.result  = result.clone();
        Arrays.sort(this.inputs, (a,b) -> a.hashCode() - b.hashCode());
    }

    public String     getId()     { return id;     }
    public int        getLevel()  { return level;  }
    public int        getXpCost() { return xpCost; }
    public ItemStack  getResult() { return result.clone(); }
    public ItemStack[] getInputs(){ return inputs.clone(); }


    /** Compara tipo + meta (DisplayName, lore, NBT, etc.). */
    public static boolean equalsWithMeta(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isSimilar(b);
    }

    /** Devuelve true si (x,y) o (y,x) coincide con la receta. */
    public boolean matches(ItemStack x, ItemStack y) {
        return (equalsWithMeta(inputs[0], x) && equalsWithMeta(inputs[1], y)) ||
                (equalsWithMeta(inputs[0], y) && equalsWithMeta(inputs[1], x));
    }

    @Override public int hashCode() { return Objects.hash(level, Arrays.hashCode(inputs)); }
    @Override public boolean equals(Object o) {
        if (!(o instanceof LecternRecipe r)) return false;
        return level==r.level && matches(r.inputs[0], r.inputs[1]);
    }

}
