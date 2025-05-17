package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class ManoDelAgricultorSkill extends Skill {
    private final Main plugin;
    private BukkitRunnable effectTask;

    public ManoDelAgricultorSkill(Main plugin, int level, long duration) {
        super("Mano del Agricultor", level, duration);
        this.plugin = plugin;
    }

    @Override
    public void onActivate(Player player) {
        // Efecto visual: Glow (solo se activará si el cultivo está maduro, validación realizada en el listener)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                (int) (getDuration() / 50),
                0,
                false,
                false,
                true
        ));

        player.sendMessage(ChatColor.GREEN + "¡Has activado Mano del Agricultor! (Nivel " + getLevel() +
                ") Obtendrás cultivos extra al cosechar.");

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
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.sendMessage(ChatColor.RED + "Mano del Agricultor ha finalizado.");
    }

    public int getMinExtraDrops() {
        return 1; // Siempre mínimo 1 cultivo extra
    }

    public int getMaxExtraDrops() {
        return 4 + (getLevel() - 1); // 4 base + 1 por nivel extra
    }

    // Nuevo método: Multiplicador de drops (para niveles 1 a 5 se incrementa, luego se "trunca")
    public double getDropMultiplier() {
        int effectiveLevel = Math.min(getLevel(), 5);
        return 1.0 + (effectiveLevel - 1) * 0.1; // Por ejemplo, nivel 1 = 1.0, nivel 5 = 1.4
    }

    // Se calcula la cantidad de cultivos extra aplicando el multiplicador

    public int getExtraDropAmount() {
        int baseAmount = getMinExtraDrops() + (int)(Math.random() * (getMaxExtraDrops() - getMinExtraDrops() + 1));
        return (int) Math.round(baseAmount * getDropMultiplier());
    }

    @Override
    public long getEffectiveCooldown() {
        // Se reduce el cooldown a mayor nivel. Puedes ajustar la fórmula según desees.
        long baseCooldown = 45000;
        long reduction = (getLevel() - 1) * 3000;
        return Math.max(baseCooldown - reduction, 15000);
    }
}
