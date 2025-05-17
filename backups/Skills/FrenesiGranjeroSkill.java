package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class FrenesiGranjeroSkill extends Skill {
    private final Main plugin;
    private BukkitRunnable effectTask;
    private BukkitRunnable soundTask;

    public FrenesiGranjeroSkill(Main plugin, int level, long duration) {
        super("Frenesí Granjero", level, duration);
        this.plugin = plugin;
    }

    // Retorna el bonus extra de XP según el nivel de la habilidad.
    // Nivel 1: 1.5%, Nivel 2: 2.5%, Nivel 3: 3.5%, Nivel 4: 4.5%,
    // Nivel 5: 5.5%, Nivel 6: 6.5%, Nivel 7: 7.5%, Nivel 8: 10%
    public double getXpBonusPercentage() {
        int lvl = getLevel();
        if (lvl == 1) return 0.015;
        else if (lvl == 2) return 0.025;
        else if (lvl == 3) return 0.035;
        else if (lvl == 4) return 0.045;
        else if (lvl == 5) return 0.055;
        else if (lvl == 6) return 0.065;
        else if (lvl == 7) return 0.075;
        else if (lvl >= 8) return 0.10;
        return 0.0;
    }

    // Calcula la duración efectiva de la habilidad en ticks, según el nivel.
    // Nivel 1: 6 segundos (6*20 ticks), Nivel 2: 7 segundos, etc.
    public long getEffectiveDuration() {
        return (getLevel() + 5) * 20L; // Ejemplo: nivel 1 -> 6 seg, nivel 2 -> 7 seg, ...
    }

    @Override
    public void onActivate(Player player) {
        double xpBonusPercent = getXpBonusPercentage() * 100; // para mostrar en %
        long effectiveDuration = getEffectiveDuration();

        // Aplicar efecto visual (por ejemplo, efecto de velocidad)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                (int) effectiveDuration,
                1,
                false,
                false,
                true
        ));

        // Mensaje de activación: solo se muestra el bonus de XP y la duración
        player.sendMessage(ChatColor.GREEN + "¡Frenesí Granjero activado! " +
                "Bonificación de XP: +" + String.format("%.1f", xpBonusPercent) + "%, " +
                "duración: " + (effectiveDuration / 20) + " segundos");

        // Reproducir un sonido inmediato de activación
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Tarea repetitiva para reproducir sonidos durante la habilidad (opcional)
        soundTask = new BukkitRunnable() {
            @Override
            public void run() {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
        };
        soundTask.runTaskTimer(plugin, 0, 20);

        // Programar la desactivación de la habilidad tras expirar la duración
        effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getSkillManager().deactivateSkill(player);
            }
        };
        effectTask.runTaskLater(plugin, effectiveDuration);
    }

    @Override
    public void onDeactivate(Player player) {
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
        }
        if (soundTask != null && !soundTask.isCancelled()) {
            soundTask.cancel();
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        player.sendMessage(ChatColor.RED + "Frenesí Granjero ha finalizado.");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
    }

    @Override
    public long getEffectiveCooldown() {
        return Math.max(30000 - (getLevel() - 1) * 2000, 10000);
    }
}
