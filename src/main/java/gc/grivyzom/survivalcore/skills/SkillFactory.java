package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public interface SkillFactory {
    String getName();
    Material getIcon();
    List<String> getLore();
    Skill create(Main plugin, int level, long duration);

    // Cambiado para usar identificadores internos consistentes
    default String getProfession() {
        return "general"; // Valor por defecto
    }

    int getRequiredProfessionLevel();
    String getSummary();

    default List<String> getDynamicLore(Player player, SkillManager skillManager) {
        // Implementación base que usa getLore() por defecto
        return getLore();
    }
}