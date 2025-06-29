package gc.grivyzom.survivalcore.rankup;

import java.util.HashMap;
import java.util.Map;

/**
 * Representa los datos de un rango en el sistema de rankup.
 *
 * @author Brocolitx
 * @version 1.0
 */
public class RankupData {
    private String rankId;
    private String displayName;
    private String nextRank;
    private int order;
    private String permissionNode;
    private Map<String, Object> requirements = new HashMap<>();
    private Map<String, Object> rewards = new HashMap<>();

    // Constructores
    public RankupData() {}

    public RankupData(String rankId, String displayName, String nextRank, int order) {
        this.rankId = rankId;
        this.displayName = displayName;
        this.nextRank = nextRank;
        this.order = order;
    }

    // Getters y Setters
    public String getRankId() { return rankId; }
    public void setRankId(String rankId) { this.rankId = rankId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getNextRank() { return nextRank; }
    public void setNextRank(String nextRank) { this.nextRank = nextRank; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public String getPermissionNode() { return permissionNode; }
    public void setPermissionNode(String permissionNode) { this.permissionNode = permissionNode; }

    public Map<String, Object> getRequirements() { return requirements; }
    public void setRequirements(Map<String, Object> requirements) {
        this.requirements = requirements != null ? requirements : new HashMap<>();
    }

    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) {
        this.rewards = rewards != null ? rewards : new HashMap<>();
    }

    // Métodos de utilidad
    public boolean hasNextRank() {
        return nextRank != null && !nextRank.isEmpty();
    }

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

    @Override
    public String toString() {
        return String.format("RankupData{rankId='%s', displayName='%s', nextRank='%s', order=%d}",
                rankId, displayName, nextRank, order);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RankupData that = (RankupData) obj;
        return rankId != null ? rankId.equals(that.rankId) : that.rankId == null;
    }

    @Override
    public int hashCode() {
        return rankId != null ? rankId.hashCode() : 0;
    }
}

