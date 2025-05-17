package gc.grivyzom.survivalcore.masteries;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Arrays;
import java.util.List;

public class MasteryExcavacionRapida extends Mastery {
    private static final List<String> DESCRIPTION = Arrays.asList(
            ChatColor.GRAY + "Aumenta tu velocidad de excavación",
            ChatColor.GRAY + "Efecto: " + ChatColor.AQUA + "Minería más rápida",
            ChatColor.GRAY + "Condición: " + ChatColor.YELLOW + "Disponible solo bajo capa 35",
            ChatColor.GRAY + "Duración: " + ChatColor.AQUA + "30 segundos + 10s por nivel",
            ChatColor.GRAY + "Probabilidad: " + ChatColor.GREEN + "20% + 5% por nivel" // Esta línea puede mantenerse para la activación automática si se desea
    );

    public MasteryExcavacionRapida(int level) {
        super("excavacion_rapida", "Excavación Rápida", level, DESCRIPTION);
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
            case 1: return 20;
            case 2: return 25;
            case 3: return 30;
            case 4: return 35;
            case 5: return 40;
            default: return 0;
        }
    }

    @Override
    public void applyEffect(Player player) {
        // Solo se activa la maestría si el jugador está bajo la capa 35.
        if (player.getLocation().getBlockY() < 35) {
            // Duración base: 30 segundos + 10 segundos por cada nivel adicional.
            int duration = 20 * (30 + (10 * level));
            // Se mejora tanto la duración como la potencia de la prisa minera:
            // - Para niveles 1-2: velocidad base (amplifier 0)
            // - Para niveles 3-4: velocidad incrementada (amplifier 1)
            // - Para nivel 5: máxima prisa (amplifier 2)
            int amplifier = 0;
            if (level >= 3 && level < 5) {
                amplifier = 1;
            } else if (level >= 5) {
                amplifier = 2;
            }
            // En activación manual (por click derecho) queremos forzar la activación del efecto,
            // así que eliminamos (o reducimos) la comprobación de probabilidad.
            // Si prefieres que en modo automático aún se aplique la probabilidad, podrías
            // implementar un método adicional; aquí para activación manual se activa siempre.
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.FAST_DIGGING,
                    duration,
                    amplifier,
                    false,
                    false,
                    true
            ));
        }
    }
}
