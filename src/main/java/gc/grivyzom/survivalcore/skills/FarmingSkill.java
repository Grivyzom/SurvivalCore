package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class FarmingSkill extends Skill {
    private final Main plugin;

    public FarmingSkill(Main plugin, int level, long duration) {
        super("Cultivo Rápido", level, duration);
        this.plugin = plugin;
    }

    @Override
    public void onActivate(Player player) {
        // Efecto de crecimiento acelerado
        int amplifier = Math.min(getLevel() / 3, 2); // Máximo nivel 2 de efecto
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FAST_DIGGING,
                (int) (getDuration() / 50),
                amplifier,
                false,
                false,
                true
        ));

        player.sendMessage(ChatColor.GREEN + "¡Cultivo Rápido activado! (Nivel " + getLevel() + ")");

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getSkillManager().deactivateSkill(player);
            }
        }.runTaskLater(plugin, getDuration());
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        player.sendMessage(ChatColor.RED + "Cultivo Rápido ha finalizado.");
    }

    @Override
    public long getEffectiveCooldown() {
        return Math.max(30000 - (getLevel() * 1000), 10000); // Mínimo 10 segundos
    }
}