package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class FrenesiGranjeroSkillFactory implements SkillFactory {
    private final Main plugin;

    public FrenesiGranjeroSkillFactory(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Frenesí Granjero";
    }

    @Override
    public Material getIcon() {
        return Material.NETHER_STAR;
    }

    @Override
    public List<String> getLore() {
        return Arrays.asList(
                ChatColor.WHITE + "Activa el modo frenético",
                ChatColor.GRAY + "Haz click para activar."
        );
    }

    @Override
    public Skill create(Main plugin, int level, long duration) {
        return new FrenesiGranjeroSkill(plugin, level, duration);
    }

    @Override
    public String getProfession() {
        return "farming";
    }

    @Override
    public int getRequiredProfessionLevel() {
        return 2;
    }

    @Override
    public String getSummary() {
        return "Activa el modo frenético y aumenta los drops extra al romper cultivos durante un breve lapso de tiempo.";
    }

    @Override
    public List<String> getDynamicLore(Player player, SkillManager skillManager) {
        int skillLevel = skillManager.getSkillLevel(player, getName());
        Skill tempSkill = create(plugin, skillLevel, 0);
        long cooldown = tempSkill.getEffectiveCooldown();
        if (skillLevel <= 3) {
            return Arrays.asList(
                    ChatColor.WHITE + "Cooldown: " + (cooldown / 1000) + " s",
                    ChatColor.GRAY + "Sin bonus extra cultivos."
            );
        } else if (skillLevel <= 6) {
            int bonus = skillLevel - 3;
            return Arrays.asList(
                    ChatColor.WHITE + "Cooldown: " + (cooldown / 1000) + " s",
                    ChatColor.GRAY + "Bonus extra cultivos: +" + bonus
            );
        } else {
            return Arrays.asList(
                    ChatColor.WHITE + "Cooldown: " + (cooldown / 1000) + " s"
            );
        }
    }
}
