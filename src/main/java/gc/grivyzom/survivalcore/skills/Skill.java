package gc.grivyzom.survivalcore.skills;

import org.bukkit.entity.Player;

public abstract class Skill {
    protected String name;
    protected int level;
    protected long duration;

    public Skill(String name, int level, long duration) {
        this.name = name;
        this.level = level;
        this.duration = duration;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public long getDuration() {
        return duration;
    }

    public void setLevel(int newLevel) {
        this.level = newLevel;
    }

    public abstract void onActivate(Player player);
    public abstract void onDeactivate(Player player);
    public abstract long getEffectiveCooldown();
}
