package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class MiningOreDetectionSkillFactory implements SkillFactory {
    private final Main plugin;

    public MiningOreDetectionSkillFactory(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Detección de Menas";
    }

    @Override
    public Material getIcon() {
        return Material.COMPASS;
    }

    @Override
    public List<String> getDynamicLore(Player player, SkillManager skillManager) {
        int level = skillManager.getSkillLevel(player, getName());
        long remainingCooldown = skillManager.getRemainingCooldown(player, getName());
        String cooldownStr = remainingCooldown > 0 ?
                ChatColor.RED + formatTime(remainingCooldown) :
                ChatColor.GREEN + "Listo";

        int radius = 40 + (level - 1) * 3;
        int duration = 8 + (level - 1);

        return Arrays.asList(
                ChatColor.GRAY + "Habilidad de Minería",
                "",
                ChatColor.WHITE + "Revela menas en un radio de " + radius + " bloques",
                ChatColor.WHITE + "durante " + duration + " segundos.",
                "",
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Estado:",
                ChatColor.GREEN + "• Nivel actual: " + ChatColor.YELLOW + level,
                ChatColor.GREEN + "• Cooldown: " + cooldownStr,
                "",
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Mejoras:",
                ChatColor.GREEN + "• Radio: " + radius + " bloques",
                ChatColor.GREEN + "• Duración: " + duration + "s",
                ChatColor.GREEN + "• Cooldown: " + Math.max(60 - (level - 1) * 1.5, 30) + "s"
        );
    }

    @Override
    public List<String> getLore() {
        return Arrays.asList(
                ChatColor.GRAY + "Habilidad de Minería",
                "",
                ChatColor.WHITE + "Revela menas en un radio de bloques",
                ChatColor.WHITE + "durante unos segundos."
        );
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public Skill create(Main plugin, int level, long duration) {
        return new MiningOreDetectionSkill(plugin, level, duration);
    }

    @Override
    public String getProfession() {
        return "mining"; // Usando identificador interno consistente
    }

    @Override
    public int getRequiredProfessionLevel() {
        return 2; // Requiere nivel 2 de Minería
    }

    @Override
    public String getSummary() {
        return "Muestra partículas cerca de menas (radio y duración mejoran con niveles).";
    }
}