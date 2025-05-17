package gc.grivyzom.survivalcore.masteries;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Arrays;
import java.util.List;

public class MasteryResistenciaExplosiones extends Mastery {
    private static final List<String> DESCRIPTION = Arrays.asList(
            ChatColor.GRAY + "Reduce el daño por explosiones",
            ChatColor.GRAY + "Efecto: " + ChatColor.AQUA + "Resistencia a explosiones",
            ChatColor.GRAY + "Reducción de daño: " + ChatColor.GREEN + "20% + 5% por nivel",
            ChatColor.GRAY + "Duración: " + ChatColor.AQUA + "1 minuto tras recibir daño"
    );

    public MasteryResistenciaExplosiones(int level) {
        super("resistencia_explosiones", "Resistencia a Explosiones", level, DESCRIPTION);
    }

    @Override
    public String getProfession() {
        return "mining";
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getXPRequiredForLevel(int level) {
        switch(level) {
            case 1: return 8;
            case 2: return 10;
            case 3: return 12;
            case 4: return 15;
            case 5: return 18;
            default: return 0;
        }
    }


    @Override
    public void applyEffect(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.DAMAGE_RESISTANCE,
                20 * 60, // 1 minuto
                level >= 3 ? 1 : 0, // Nivel II a partir de nivel 3
                false,
                false,
                true
        ));
    }
}