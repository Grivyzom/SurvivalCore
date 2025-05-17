package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Material;
import java.util.Arrays;
import java.util.List;

public class FarmingSkillFactory implements SkillFactory {

    @Override
    public String getName() {
        return "Cultivo Rápido";
    }

    @Override
    public Material getIcon() {
        return Material.WHEAT;
    }

    @Override
    public List<String> getLore() {
        return Arrays.asList("Acelera el crecimiento de tus cultivos.", "Click Izquierdo: Mejorar | Click Derecho: Activar");
    }

    @Override
    public Skill create(Main plugin, int level, long duration) {
        return new FarmingSkill(plugin, level, duration);
    }

    // Sobrescribimos el método para que retorne "farming".
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
        return "Reduce el tiempo de crecimiento de las plantas al activar esta habilidad.";
    }
}
