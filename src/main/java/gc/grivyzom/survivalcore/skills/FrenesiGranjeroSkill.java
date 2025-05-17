package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class FrenesiGranjeroSkill extends Skill {
    private final Main plugin;
    private BukkitRunnable effectTask;

    public FrenesiGranjeroSkill(Main plugin, int level, long duration) {
        super("Frenesí Granjero", level, duration);
        this.plugin = plugin;
    }

    @Override
    public void onActivate(Player player) {
        // Efecto visual y de velocidad
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                (int) (getDuration() / 50),
                1,
                false,
                false,
                true
        ));

        int bonus = getExtraBonus();
        String msg = ChatColor.GREEN + "¡Frenesí Granjero activado! Cooldown: " + (getEffectiveCooldown() / 1000) + " s.";
        if (bonus > 0) {
            msg += " Bonus extra cultivos: +" + bonus;
        }
        player.sendMessage(msg);

        effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getSkillManager().deactivateSkill(player);
            }
        };
        effectTask.runTaskLater(plugin, getDuration());
    }

    @Override
    public void onDeactivate(Player player) {
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        player.sendMessage(ChatColor.RED + "Frenesí Granjero ha finalizado.");
    }

    // En Frenesí, el multiplicador de drops puede mantenerse o usarse para otro efecto, si se desea
    public double getDropMultiplier() {
        return 1.0 + (getLevel() - 1) * 0.1;
    }

    // Nuevo método: retorna bonus extra solo para niveles 4 a 6
    public int getExtraBonus() {
        if (getLevel() >= 4 && getLevel() <= 6) {
            return getLevel() - 3;
        }
        return 0;
    }

    @Override
    public long getEffectiveCooldown() {
        // Fórmula de cooldown (se reduce con el nivel)
        long baseCooldown = 30000;
        long reduction = (getLevel() - 1) * 2000;
        return Math.max(baseCooldown - reduction, 10000);
    }
}
