package gc.grivyzom.survivalcore.masteries;

import org.bukkit.entity.Player;
import java.util.List;

public abstract class Mastery {
    protected final String id;
    protected final String name;
    protected int level;
    protected final List<String> description;

    public Mastery(String id, String name, int level, List<String> description) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.description = description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public List<String> getDescription() { return description; }

    public boolean isMaxLevel(int level) {
        return level >= getMaxLevel();
    }

    public abstract String getProfession();
    public abstract int getMaxLevel();
    public abstract int getXPRequiredForLevel(int level);
    public abstract void applyEffect(Player player);
}