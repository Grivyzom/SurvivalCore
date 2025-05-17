package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Material;
import org.bukkit.ChatColor;
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
                ChatColor.GRAY + "Habilidad de Granjería",
                "",
                ChatColor.WHITE + "Esta habilidad permite al jugador",
                ChatColor.WHITE + "ganar un porcentaje extra de XP",
                ChatColor.WHITE + "al cosechar cultivos.",
                "",
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Mejoras por nivel:",
                ChatColor.GREEN + "• +1.5% XP (Nivel 1)",
                ChatColor.GREEN + "• +10% XP (Nivel 8)",
                ChatColor.GREEN + "• Duración: +1s por nivel",
                ChatColor.GREEN + "• Cooldown: -2s por nivel",
                "",
                ChatColor.DARK_GRAY + "Duración: " + ChatColor.GRAY + "6-13 segundos"
        );
    }

    @Override
    public Skill create(Main plugin, int level, long duration) {
        return new FrenesiGranjeroSkill(plugin, level, duration);
    }

    @Override
    public String getProfession() {
        return "farming"; // Usando identificador interno consistente
    }

    @Override
    public int getRequiredProfessionLevel() {
        return 2; // Requiere nivel 2 de Granjería
    }

    @Override
    public String getSummary() {
        return "Activa el modo frenético y aumenta los drops extra al romper cultivos durante un breve lapso de tiempo.";
    }
}