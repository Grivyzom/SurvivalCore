package gc.grivyzom.survivalcore.masteries;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Arrays;
import java.util.List;

public class MasteryVisionNocturna extends Mastery {
    private static final List<String> DESCRIPTION = Arrays.asList(
            ChatColor.GRAY + "Visión mejorada en las profundidades",
            ChatColor.GRAY + "Se activa al Y<35 y se quita al Y>=35",
            ChatColor.GRAY + "Duración: " + ChatColor.AQUA + "90s + 30s por nivel",
            ChatColor.GRAY + "Cooldown: " + ChatColor.RED + "15m - 1m por nivel"
    );

    public MasteryVisionNocturna(int level) {
        super("vision_nocturna", "Visión Nocturna", level, DESCRIPTION);
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
            case 1: return 5;
            case 2: return 8;
            case 3: return 12;
            case 4: return 17;
            case 5: return 23;
            default: return 0;
        }
    }

    /** Duración en ticks */
    public int getDurationTicks() {
        return 20 * (90 + 30 * level);
    }

    /** Cooldown en ms */
    public long getCooldownMillis() {
        int minutos = Math.max(1, 15 - level); // mínimo 1 minuto
        return minutos * 60 * 1000L;
    }

    /** Aplica el efecto sin validación de Y ni cooldown */
    @Override
    public void applyEffect(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION,
                getDurationTicks(),
                level >= 4 ? 1 : 0,
                false, false, true
        ));
    }
}
