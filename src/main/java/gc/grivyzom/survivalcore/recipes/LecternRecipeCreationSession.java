package gc.grivyzom.survivalcore.recipes;

import org.bukkit.inventory.ItemStack;

public class LecternRecipeCreationSession {
    private enum Stage { FIRST, SECOND, RESULT }
    private Stage stage = Stage.FIRST;
    private final ItemStack[] slots = new ItemStack[3];
    private final int level;
    private final String id;
    private final int    xpCost;

    public LecternRecipeCreationSession(String id, int level, int xpCost) {
        this.id     = id;
        this.level  = level;
        this.xpCost = xpCost;
    }

    /** Avanza y devuelve la próxima instrucción. */
    public Next advance(ItemStack it) {
        switch (stage) {
            case FIRST  -> { slots[0]=it.clone(); stage=Stage.SECOND; return Next.ASK_SECOND; }
            case SECOND -> { slots[1]=it.clone(); stage=Stage.RESULT; return Next.ASK_RESULT; }
            case RESULT -> { slots[2]=it.clone(); return Next.COMPLETE; }
            default -> throw new IllegalStateException();
        }
    }
    public LecternRecipe toRecipe() {
        return new LecternRecipe(id, level, xpCost, slots[0], slots[1], slots[2]);
    }
    public enum Next { ASK_SECOND, ASK_RESULT, COMPLETE }
}
