// ManoDelAgricultorSkill.java
package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Random;

public class ManoDelAgricultorSkill extends Skill {
    private final Main plugin;
    private BukkitRunnable effectTask;
    private final Random random = new Random();

    public ManoDelAgricultorSkill(Main plugin, int level, long duration) {
        super("Mano del Agricultor", level, duration);
        this.plugin = plugin;
    }

    @Override
    public void onActivate(Player player) {
        // Efecto visual (Glow)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                (int) (getDuration() / 50),
                0,
                false,
                false,
                true
        ));

        // Efectos sonoros y partículas al activar
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 20);

        // Mensaje de activación actualizado
        int probabilidad = Math.min(50 + (getLevel() * 2), 100);
        int maxExtras = Math.min(1 + (getLevel() / 10), 3);
        player.sendMessage(ChatColor.GREEN + "¡Has activado Mano del Agricultor! (Nivel " + getLevel() + ") " +
                "Efectos activos:\n" +
                ChatColor.WHITE + "• Intercambio de bayas: Glow ↔ Sweet Berries\n" +
                ChatColor.WHITE + "• Probabilidad de drops extra: " + probabilidad + "%\n" +
                ChatColor.WHITE + "• Drops extra: entre 1 y " + maxExtras + "\n" +
                ChatColor.GRAY + "Duración: " + (getDuration() / 1000) + " segundos");

        effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getSkillManager().deactivateSkill(player);
            }
        };
        effectTask.runTaskLater(plugin, getDuration());
    }

    public int getExtraDropAmount() {
        int chance = Math.min(50 + (getLevel() * 2), 100);
        if (random.nextInt(100) >= chance) {
            return 0;
        }
        int maxExtra = Math.min(1 + (getLevel() / 10), 3);
        return 1 + random.nextInt(maxExtra); // Extra drop entre 1 y maxExtra inclusive
    }

    @Override
    public long getEffectiveCooldown() {
        long baseCooldown = 45000L;
        long reduction = (long) (Math.sqrt(getLevel()) * 2000L);
        return Math.max(baseCooldown - reduction, 20000L);
    }

    @Override
    public void onDeactivate(Player player) {
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
        }
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.sendMessage(ChatColor.RED + "Mano del Agricultor ha finalizado.");
    }

    public int getMinExtraDrops() {
        return Math.max(1, getLevel() / 10);
    }

    public int getMaxExtraDrops() {
        return Math.min(2 + (getLevel() / 10) + (getLevel() / 20), 6);
    }

}