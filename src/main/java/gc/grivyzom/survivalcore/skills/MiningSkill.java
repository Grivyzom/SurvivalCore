package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class MiningSkill extends Skill {
    private final Main plugin;

    public MiningSkill(Main plugin, int level, long duration) {
        super("Minería Experta", level, duration);
        this.plugin = plugin;
    }

    @Override
    public void onActivate(Player player) {
        // Efecto de suerte en minería
        int amplifier = Math.min(getLevel() / 5, 2); // Máximo nivel 2 de efecto
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.LUCK,
                (int) (getDuration() / 50),
                amplifier,
                false,
                false,
                true
        ));

        player.sendMessage(ChatColor.GREEN + "¡Minería Experta activada! (Nivel " + getLevel() + ")");

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getSkillManager().deactivateSkill(player);
            }
        }.runTaskLater(plugin, getDuration());
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.LUCK);
        player.sendMessage(ChatColor.RED + "Minería Experta ha finalizado.");
    }

    @Override
    public long getEffectiveCooldown() {
        return Math.max(40000 - (getLevel() * 1500), 15000); // Mínimo 15 segundos
    }
}