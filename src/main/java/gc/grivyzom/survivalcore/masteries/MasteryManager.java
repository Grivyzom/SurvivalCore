package gc.grivyzom.survivalcore.masteries;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MasteryManager {
    private final Main plugin;
    private final Map<String, Mastery> masteries = new ConcurrentHashMap<>();
    private final Map<String, Long> activationCooldowns = new ConcurrentHashMap<>();

    public MasteryManager(Main plugin) {
        this.plugin = plugin;
        registerDefaultMasteries();
    }

    private void registerDefaultMasteries() {
        registerMastery(new MasteryVisionNocturna(1));
        registerMastery(new MasteryExcavacionRapida(1));
        registerMastery(new MasteryResistenciaExplosiones(1));
        registerMastery(new MasteryCultivacion(plugin, 1));
    }

    public void registerMastery(Mastery mastery) {
        masteries.put(mastery.getId(), mastery);
    }

    public Mastery getMastery(String id) {
        return masteries.get(id);
    }

    public Mastery getMasteryByName(String name, String profession) {
        return masteries.values().stream()
                .filter(m -> m.getName().equalsIgnoreCase(name)
                        && m.getProfession().equalsIgnoreCase(profession))
                .findFirst().orElse(null);
    }

    public List<Mastery> getMasteriesForProfession(String profession) {
        return masteries.values().stream()
                .filter(m -> m.getProfession().equalsIgnoreCase(profession))
                .collect(Collectors.toList());
    }

    public boolean canUpgradeMastery(Player player, String masteryId) {
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        Mastery m = masteries.get(masteryId);
        if (m == null || data == null) return false;
        int lvl = data.getMasteryLevels().getOrDefault(masteryId, 0);
        return lvl < m.getMaxLevel();
    }

    public boolean upgradeMastery(Player player, String masteryId) {
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        Mastery m = masteries.get(masteryId);
        if (m == null || data == null) return false;

        int curr = data.getMasteryLevels().getOrDefault(masteryId, 0);
        if (curr >= m.getMaxLevel()) {
            player.sendMessage(ChatColor.RED + "¡Ya alcanzaste el nivel máximo de esta maestría!");
            return false;
        }

        int needed = m.getXPRequiredForLevel(curr + 1);
        if (player.getLevel() < needed) {
            player.sendMessage(ChatColor.RED + "Necesitas " + needed + " niveles para mejorar esta maestría.");
            return false;
        }

        player.setLevel(player.getLevel() - needed);
        int newLvl = curr + 1;
        data.getMasteryLevels().put(masteryId, newLvl);

        // Guardado asíncrono de datos
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().saveUserData(data)
        );

        try {
            Mastery inst;
            if (m instanceof MasteryCultivacion) {
                inst = new MasteryCultivacion(plugin, newLvl);
            } else {
                inst = m.getClass().getDeclaredConstructor(int.class).newInstance(newLvl);
            }
            inst.applyEffect(player);
            player.sendMessage(ChatColor.GREEN + "¡" + inst.getName() + " mejorada al nivel " + newLvl + "! ");
            playSound(player, Sound.ENTITY_PLAYER_LEVELUP);

            if (newLvl == inst.getMaxLevel()) {
                player.sendTitle(
                        ChatColor.GOLD + "¡Maestría Máxima!",
                        ChatColor.AQUA + inst.getName() + " Nivel " + newLvl,
                        10, 70, 20
                );
                playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE);
                player.getWorld().spawnParticle(
                        Particle.FIREWORKS_SPARK,
                        player.getLocation().add(0,1,0),
                        50, 0.5, 0.5, 0.5, 0.2
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error al instanciar maestría: " + e.getMessage());
            return false;
        }

        return true;
    }

    public boolean activateMastery(Player player, String masteryId, long cooldownMillis) {
        String key = player.getUniqueId() + "_" + masteryId;
        long now = System.currentTimeMillis();
        Long cdUntil = activationCooldowns.get(key);
        if (cdUntil != null && now < cdUntil) {
            long rem = (cdUntil - now) / 1000;
            player.sendMessage(ChatColor.RED + "Espera " + rem + "s para usar esta maestría de nuevo.");
            return false;
        }

        Mastery m = getMastery(masteryId);
        if (m == null) {
            player.sendMessage(ChatColor.RED + "Maestría no encontrada.");
            return false;
        }
        try {
            m.applyEffect(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Error al activar maestría " + masteryId + ": " + e.getMessage());
            return false;
        }
        activationCooldowns.put(key, now + cooldownMillis);
        return true;
    }

    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 0.7f, 1f);
    }

    public void applyAllEffects(Player player) {
        UserData data = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
        if (data == null) return;
        data.getMasteryLevels().forEach((id, lvl) -> {
            Mastery m = masteries.get(id);
            if (m != null) {
                try {
                    Mastery inst = m.getClass().getDeclaredConstructor(int.class).newInstance(lvl);
                    inst.applyEffect(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error aplicando efecto de maestría " + id + ": " + e.getMessage());
                }
            }
        });
    }
}