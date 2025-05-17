package gc.grivyzom.survivalcore.skills;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class SkillManager {
    private final Map<String, Skill> activeSkills = new HashMap<>();
    private final Map<String, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<String, Integer> violationCounts = new HashMap<>();
    private final Map<String, Long> violationTimestamps = new HashMap<>();
    private final List<SkillFactory> registeredSkillFactories = new ArrayList<>();
    private final Map<String, Map<String, Integer>> playerSkillLevels = new HashMap<>();
    private final Map<String, Map<String, Integer>> playerSkillXP = new HashMap<>();
    private final Map<String, Long> lastXPGainTimes = new HashMap<>();
    private final Map<String, Map<String, Integer>> lastProgressDisplay = new HashMap<>();

    private static final int MAX_XP_PER_EVENT = 500;
    private static final int MAX_SKILL_LEVEL = 50;
    private static final long XP_GAIN_COOLDOWN = 1000; // milisegundos

    private final Main plugin;

    public SkillManager(Main plugin) {
        this.plugin = plugin;
    }

    public void registerSkill(SkillFactory factory) {
        registeredSkillFactories.add(factory);
    }

    public List<SkillFactory> getRegisteredSkills() {
        return Collections.unmodifiableList(registeredSkillFactories);
    }

    public int getSkillLevel(Player player, String skillName) {
        String uuid = player.getUniqueId().toString();
        return playerSkillLevels
                .getOrDefault(uuid, Collections.emptyMap())
                .getOrDefault(skillName, 1);
    }

    public int getSkillXP(Player player, String skillName) {
        String uuid = player.getUniqueId().toString();
        return playerSkillXP
                .getOrDefault(uuid, Collections.emptyMap())
                .getOrDefault(skillName, 0);
    }

    public int getRequiredXPForLevel(int level) {
        if (level >= MAX_SKILL_LEVEL) return 0;
        return 100 * level * level;
    }

    public SkillFactory getFactoryForSkill(String skillName) {
        return registeredSkillFactories.stream()
                .filter(f -> f.getName().equalsIgnoreCase(skillName))
                .findFirst().orElse(null);
    }

    public boolean canActivateSkill(Player player, Skill skill) {
        long now = System.currentTimeMillis();
        String uuid = player.getUniqueId().toString();
        var playerCooldowns = cooldowns.computeIfAbsent(uuid, k -> new HashMap<>());
        Long last = playerCooldowns.get(skill.getName());
        long cd = skill.getEffectiveCooldown();
        if (last != null && now - last < cd) {
            int count = violationCounts.getOrDefault(uuid, 0) + 1;
            violationCounts.put(uuid, count);
            violationTimestamps.put(uuid, now);
            if (count >= 3) {
                player.sendMessage(ChatColor.RED + "¡Demasiados intentos! Espera 60 segundos.");
                playerCooldowns.put(skill.getName(), now + 60000);
                violationCounts.put(uuid, 0);
            }
            return false;
        }
        return true;
    }

    public void activateSkill(Player player, Skill skill) {
        if (!canActivateSkill(player, skill)) {
            player.sendMessage(ChatColor.RED + "La habilidad " + skill.getName() + " está en cooldown.");
            return;
        }
        long now = System.currentTimeMillis();
        String uuid = player.getUniqueId().toString();
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(skill.getName(), now);
        activeSkills.put(uuid, skill);
        skill.onActivate(player);
    }

    public Skill getActiveSkill(Player player) {
        return activeSkills.get(player.getUniqueId().toString());
    }

    public boolean hasActiveSkill(Player player, String skillName) {
        Skill s = getActiveSkill(player);
        return s != null && s.getName().equalsIgnoreCase(skillName);
    }

    public void deactivateSkill(Player player) {
        String uuid = player.getUniqueId().toString();
        Skill skill = activeSkills.remove(uuid);
        if (skill != null) skill.onDeactivate(player);
    }

    public void addSkillXP(Player player, String skillName, int xp) {
        if (xp <= 0) return;
        String uuid = player.getUniqueId().toString();
        String key = uuid + "-" + skillName;
        long now = System.currentTimeMillis();
        Long lastGain = lastXPGainTimes.get(key);
        if (lastGain != null && now - lastGain < XP_GAIN_COOLDOWN) return;
        lastXPGainTimes.put(key, now);
        xp = Math.min(xp, MAX_XP_PER_EVENT);

        var xpMap = playerSkillXP.computeIfAbsent(uuid, k -> new HashMap<>());
        var levels = playerSkillLevels.computeIfAbsent(uuid, k -> new HashMap<>());
        int currentLevel = levels.getOrDefault(skillName, 1);
        int currentXP = xpMap.getOrDefault(skillName, 0);
        int newXP = currentXP + xp;
        int reqXP = getRequiredXPForLevel(currentLevel);

        while (reqXP > 0 && newXP >= reqXP && currentLevel < MAX_SKILL_LEVEL) {
            newXP -= reqXP;
            currentLevel++;
            levels.put(skillName, currentLevel);
            player.sendMessage(ChatColor.GOLD + "¡" + skillName + " sube al nivel " + currentLevel + "!");
            updateActiveSkillLevel(player, skillName, currentLevel);

            SkillFactory factory = getFactoryForSkill(skillName);
            if (factory != null) {
                UserData data = plugin.getDatabaseManager().getUserData(uuid);
                if (factory.getProfession().equalsIgnoreCase("mining")) {
                    data.setMiningLevel(currentLevel);
                    data.setMiningXP(newXP);
                } else if (factory.getProfession().equalsIgnoreCase("farming")) {
                    data.setFarmingLevel(currentLevel);
                    data.setFarmingXP(newXP);
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> plugin.getDatabaseManager().saveUserData(data));
            }
            reqXP = getRequiredXPForLevel(currentLevel);
            lastProgressDisplay.computeIfAbsent(uuid, k -> new HashMap<>()).put(skillName, 0);
        }
        xpMap.put(skillName, newXP);

        SkillFactory factory = getFactoryForSkill(skillName);
        if (factory != null) {
            UserData data = plugin.getDatabaseManager().getUserData(uuid);
            if (factory.getProfession().equalsIgnoreCase("mining")) {
                data.setMiningXP(newXP);
            } else if (factory.getProfession().equalsIgnoreCase("farming")) {
                data.setFarmingXP(newXP);
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> plugin.getDatabaseManager().saveUserData(data));
        }
        sendProgressMessage(player, skillName, currentLevel, newXP, reqXP);
    }

    private void updateActiveSkillLevel(Player player, String skillName, int newLevel) {
        String uuid = player.getUniqueId().toString();
        Skill s = activeSkills.get(uuid);
        if (s != null && s.getName().equalsIgnoreCase(skillName)) s.setLevel(newLevel);
    }

    private void sendProgressMessage(Player player, String skillName, int level, int currentXP, int reqXP) {
        if (reqXP <= 0 || currentXP <= 0) return;
        int percent = (int)((currentXP / (double)reqXP)*100);
        String uuid = player.getUniqueId().toString();
        var progressMap = lastProgressDisplay.computeIfAbsent(uuid, k -> new HashMap<>());
        int lastNotified = progressMap.getOrDefault(skillName, 0);
        int threshold = (percent/25)*25;
        if (threshold > lastNotified) {
            player.sendMessage(ChatColor.GRAY + "Progreso de " + skillName + " (Nvl " + level + "): " +
                    ChatColor.YELLOW + currentXP + "/" + reqXP + ChatColor.GRAY + " (" + percent + "%)");
            progressMap.put(skillName, threshold);
        }
    }

    public boolean upgradeSkill(Player player, SkillFactory factory) {
        if (factory == null) {
            player.sendMessage(ChatColor.RED + "Habilidad no encontrada.");
            return false;
        }
        String skillName = factory.getName();
        String uuid = player.getUniqueId().toString();
        int currLevel = getSkillLevel(player, skillName);
        if (currLevel >= MAX_SKILL_LEVEL) {
            player.sendMessage(ChatColor.RED + "¡Nivel máximo alcanzado!");
            return false;
        }
        int reqXPPoints = getRequiredXPForLevel(currLevel);
        int requiredMCLevels = 0;
        int accXP = 0;
        while (accXP < reqXPPoints) {
            requiredMCLevels++;
            accXP += (requiredMCLevels <= 16) ? 17 : (requiredMCLevels <= 31) ? 3 : 7;
        }
        if (player.getLevel() < requiredMCLevels) {
            player.sendMessage(ChatColor.RED + "Te faltan " + (requiredMCLevels - player.getLevel())
                    + " niveles de XP.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_HIT, 1f, 1f);
            return false;
        }
        player.setLevel(player.getLevel() - requiredMCLevels);
        int newLevel = currLevel + 1;
        playerSkillLevels.computeIfAbsent(uuid, k -> new HashMap<>()).put(skillName, newLevel);
        player.sendMessage(ChatColor.GREEN + skillName + " nivel " + newLevel + " alcanzado.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        updateActiveSkillLevel(player, skillName, newLevel);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UserData data = plugin.getDatabaseManager().getUserData(uuid);
            if (factory.getProfession().equalsIgnoreCase("mining")) data.setMiningLevel(newLevel);
            else if (factory.getProfession().equalsIgnoreCase("farming")) data.setFarmingLevel(newLevel);
            plugin.getDatabaseManager().saveUserData(data);
        });
        return true;
    }

    public long getRemainingCooldown(Player player, String skillName) {
        String uuid = player.getUniqueId().toString();
        var playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return 0;
        Long last = playerCooldowns.get(skillName);
        if (last == null) return 0;
        SkillFactory factory = getFactoryForSkill(skillName);
        if (factory == null) return 0;
        Skill temp = factory.create(plugin, getSkillLevel(player, skillName), 0);
        long rem = (last + temp.getEffectiveCooldown()) - System.currentTimeMillis();
        return Math.max(0, rem);
    }
}
