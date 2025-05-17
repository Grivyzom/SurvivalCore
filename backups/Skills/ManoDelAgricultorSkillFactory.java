// ManoDelAgricultorSkillFactory.java
package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Material;
import org.bukkit.ChatColor;
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
        return Arrays.asList(
                ChatColor.GRAY + "Habilidad de Granjería",
                "",
                ChatColor.WHITE + "• Al cosechar Glow Berries o Sweet Berries, obtendrás la otra baya.",
                ChatColor.WHITE + "• En cultivos plantados (Wheat, Carrot, Potatoes, etc.)",
                ChatColor.WHITE + "  podrás obtener hasta 3 drops adicionales de cultivos de otro tipo.",
                "",
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Mejoras por nivel:",
                ChatColor.GREEN + "• Aumenta la probabilidad de obtener drops extra por nivel",
                ChatColor.GREEN + "• Incrementa la cantidad máxima de drops extra (máx. 3)",
                ChatColor.GREEN + "• Cooldown reducido gradualmente",
                "",
                ChatColor.DARK_GRAY + "Cooldown: " + ChatColor.GRAY + "45s base (Nivel 1)"
        );
    }

    @Override
    public String getSummary() {
        return "Permite obtener drops extra al cosechar: si rompes Glow o Sweet Berries obtendrás la otra baya, " +
                "y en cultivos plantados podrás recibir hasta 3 drops adicionales de cultivos de distinto tipo.";
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

}