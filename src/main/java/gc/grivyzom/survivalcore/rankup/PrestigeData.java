package gc.grivyzom.survivalcore.rankup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Representa los datos de un prestige en el sistema de rankup.
 *
 * @author Brocolitx
 * @version 1.0
 */
public class PrestigeData {
    private String prestigeId;
    private String displayName;
    private int level;
    private Map<String, Object> requirements = new HashMap<>();
    private Map<String, Object> rewards = new HashMap<>();
    private boolean resetRanks = true;
    private List<String> keepProgress = new ArrayList<>();

    // Constructores
    public PrestigeData() {}

    public PrestigeData(String prestigeId, String displayName, int level) {
        this.prestigeId = prestigeId;
        this.displayName = displayName;
        this.level = level;
    }

    // Getters y Setters
    public String getPrestigeId() { return prestigeId; }
    public void setPrestigeId(String prestigeId) { this.prestigeId = prestigeId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public Map<String, Object> getRequirements() { return requirements; }
    public void setRequirements(Map<String, Object> requirements) {
        this.requirements = requirements != null ? requirements : new HashMap<>();
    }

    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) {
        this.rewards = rewards != null ? rewards : new HashMap<>();
    }

    public boolean isResetRanks() { return resetRanks; }
    public void setResetRanks(boolean resetRanks) { this.resetRanks = resetRanks; }

    public List<String> getKeepProgress() { return keepProgress; }
    public void setKeepProgress(List<String> keepProgress) {
        this.keepProgress = keepProgress != null ? keepProgress : new ArrayList<>();
    }

    // Métodos de utilidad
    public boolean hasRequirement(String requirement) {
        return requirements.containsKey(requirement);
    }

    public Object getRequirement(String requirement) {
        return requirements.get(requirement);
    }

    public boolean hasReward(String reward) {
        return rewards.containsKey(reward);
    }

    public Object getReward(String reward) {
        return rewards.get(reward);
    }

    public boolean shouldKeepProgress(String progressType) {
        return keepProgress.contains(progressType);
    }

    @Override
    public String toString() {
        return String.format("PrestigeData{prestigeId='%s', displayName='%s', level=%d, resetRanks=%s}",
                prestigeId, displayName, level, resetRanks);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PrestigeData that = (PrestigeData) obj;
        return prestigeId != null ? prestigeId.equals(that.prestigeId) : that.prestigeId == null;
    }

    @Override
    public int hashCode() {
        return prestigeId != null ? prestigeId.hashCode() : 0;
    }
}