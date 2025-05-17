package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Material;
import java.util.Arrays;
import java.util.List;

public class MiningSkillFactory implements SkillFactory {

    @Override
    public String getName() {
        return "Minería Experta";
    }

    @Override
    public Material getIcon() {
        return Material.STONE;
    }

    @Override
    public List<String> getLore() {
        return Arrays.asList("Aumenta la suerte al minar.", "Click Izquierdo: Mejorar | Click Derecho: Activar");
    }

    @Override
    public Skill create(Main plugin, int level, long duration) {
        return new MiningSkill(plugin, level, duration);
    }

    // SOLUCIÓN: Sobrescribimos este método para que retorne "mining".
    @Override
    public String getProfession() {
        return "mining";
    }

    @Override
    public int getRequiredProfessionLevel() {
        // Ajusta el nivel requerido según tu diseño (en este ejemplo, se requiere el nivel 1)
        return 1;
    }

    @Override
    public String getSummary() {
        return "Aumenta la probabilidad de obtener mejores drops al minar.";
    }
}
