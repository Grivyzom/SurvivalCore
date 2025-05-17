package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class MiningAccelerationSkillFactory implements SkillFactory {
    private final Main plugin;

    public MiningAccelerationSkillFactory(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Aceleración Minera";
    }

    @Override
    public Material getIcon() {
        return Material.SUGAR;
    }

    @Override
    public List<String> getDynamicLore(Player player, SkillManager skillManager) {
        int level = skillManager.getSkillLevel(player, getName());
        long remainingCooldown = skillManager.getRemainingCooldown(player, getName());
        String cooldownStr = remainingCooldown > 0 ?
                ChatColor.RED + formatTime(remainingCooldown) :
                ChatColor.GREEN + "Listo";

        return Arrays.asList(
                ChatColor.GRAY + "Habilidad de Minería",
                "",
                ChatColor.WHITE + "Otorga el efecto de Prisa Minera,",
                ChatColor.WHITE + "aumentando la velocidad de excavación.",
                "",
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Estado:",
                ChatColor.GREEN + "• Nivel actual: " + ChatColor.YELLOW + level,
                ChatColor.GREEN + "• Cooldown: " + cooldownStr,
                "",
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Mejoras por nivel:",
                ChatColor.GREEN + "• +1 nivel de Prisa cada 5 niveles",
                ChatColor.GREEN + "• +1s de duración por nivel",
                ChatColor.GREEN + "• Cooldown: -1.5s por nivel",
                "",
                ChatColor.DARK_GRAY + "Duración actual: " + ChatColor.GRAY + (10 + (level - 1)) + "s",
                ChatColor.DARK_GRAY + "Cooldown actual: " + ChatColor.GRAY +
                        Math.max(30 - (level - 1) * 1.5, 10) + "s"
        );
    }

    @Override
    public List<String> getLore() {
        return Arrays.asList(
                ChatColor.GRAY + "Habilidad de Minería",
                "",
                ChatColor.WHITE + "Otorga el efecto de Prisa Minera,",
                ChatColor.WHITE + "aumentando la velocidad de excavación."
        );
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public Skill create(Main plugin, int level, long duration) {
        return new MiningAccelerationSkill(plugin, level, duration);
    }

    @Override
    public String getProfession() {
        return "mining"; // Usando identificador interno consistente
    }

    @Override
    public int getRequiredProfessionLevel() {
        return 1; // Requiere nivel 1 de Minería
    }

    @Override
    public String getSummary() {
        return "Otorga Mining Haste durante 10 segundos (mejora con niveles).";
    }
}