package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class ManoDelAgricultorSkillFactory implements SkillFactory {
    private final Main plugin;

    public ManoDelAgricultorSkillFactory(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Mano del Agricultor";
    }

    @Override
    public Material getIcon() {
        return Material.CARROT;
    }

    @Override
    public List<String> getLore() {
        // Lore base (estático); la versión dinámica se sobrescribe en getDynamicLore
        return Arrays.asList(
                ChatColor.WHITE + "Obtén cultivos extra al cosechar",
                ChatColor.GRAY + "Haz click para activar."
        );
    }

    @Override
    public Skill create(Main plugin, int level, long duration) {
        return new ManoDelAgricultorSkill(plugin, level, duration);
    }

    @Override
    public String getProfession() {
        return "farming";
    }

    @Override
    public int getRequiredProfessionLevel() {
        return 1;
    }

    @Override
    public String getSummary() {
        return "Permite obtener cultivos extra del mismo tipo al cosechar, mejorando la producción de granos.";
    }

    @Override
    public List<String> getDynamicLore(Player player, SkillManager skillManager) {
        int skillLevel = skillManager.getSkillLevel(player, getName());
        Skill tempSkill = create(plugin, skillLevel, 0);
        long cooldown = tempSkill.getEffectiveCooldown();
        int minDrops = 1;
        int maxDrops = 4 + (skillLevel - 1);
        // Utiliza el multiplicador (limitado a niveles 1-5)
        double multiplier = 1.0 + (Math.min(skillLevel, 5) - 1) * 0.1;

        return Arrays.asList(
                ChatColor.WHITE + "Extra cultivos base: " + minDrops + " - " + maxDrops,
                ChatColor.GRAY + "Multiplicador: x" + String.format("%.1f", multiplier),
                ChatColor.GRAY + "Cooldown: " + (cooldown / 1000) + " s",
                ChatColor.GRAY + "Solo se activa si el cultivo está maduro."
        );
    }
}
