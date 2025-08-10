package gc.grivyzom.survivalcore.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Clase que representa los datos de un usuario en SurvivalCore
 * Actualizada con redes sociales y cooldown de género
 *
 * @author Brocolitx
 * @version 3.0
 */
public class UserData {
    private String uuid;
    private String nombre;
    private String cumpleaños;
    private String genero;
    private String pais;

    // Redes sociales
    private String discord;
    private String instagram;
    private String github;
    private String tiktok;
    private String twitch;
    private String kick;
    private String youtube;

    // Cooldown de cambio de género
    private long ultimoCambioGenero;

    private int farmingLevel;
    private long farmingXP;
    private int miningLevel;
    private long miningXP;

    private Map<String, Integer> abilities;
    private long bankedXp;

    private int bankLevel;
    private long bankCapacity;

    public UserData() {
        this.abilities = new HashMap<>();
        this.bankedXp = 0;
        this.bankLevel = 1;
        this.bankCapacity = 68000;
        this.ultimoCambioGenero = 0;
    }

    public UserData(String uuid, String nombre, String cumpleaños, String genero, String pais) {
        this.uuid = uuid;
        this.nombre = nombre;
        this.cumpleaños = cumpleaños;
        this.genero = genero;
        this.pais = pais;
        this.farmingLevel = 1;
        this.farmingXP = 0;
        this.miningLevel = 1;
        this.miningXP = 0;
        this.abilities = new HashMap<>();
        this.bankedXp = 0;
        this.bankLevel = 1;
        this.bankCapacity = 68000;
        this.ultimoCambioGenero = 0;

        // Inicializar redes sociales como null
        this.discord = null;
        this.instagram = null;
        this.github = null;
        this.tiktok = null;
        this.twitch = null;
        this.kick = null;
        this.youtube = null;
    }

    // =================== GETTERS Y SETTERS BÁSICOS ===================

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCumpleaños() { return cumpleaños; }
    public void setCumpleaños(String cumpleaños) { this.cumpleaños = cumpleaños; }

    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }

    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }

    // =================== REDES SOCIALES ===================

    public String getDiscord() { return discord; }
    public void setDiscord(String discord) { this.discord = discord; }

    public String getInstagram() { return instagram; }
    public void setInstagram(String instagram) { this.instagram = instagram; }

    public String getGithub() { return github; }
    public void setGithub(String github) { this.github = github; }

    public String getTiktok() { return tiktok; }
    public void setTiktok(String tiktok) { this.tiktok = tiktok; }

    public String getTwitch() { return twitch; }
    public void setTwitch(String twitch) { this.twitch = twitch; }

    public String getKick() { return kick; }
    public void setKick(String kick) { this.kick = kick; }

    public String getYoutube() { return youtube; }
    public void setYoutube(String youtube) { this.youtube = youtube; }

    // =================== COOLDOWN DE GÉNERO ===================

    public long getUltimoCambioGenero() { return ultimoCambioGenero; }
    public void setUltimoCambioGenero(long ultimoCambioGenero) {
        this.ultimoCambioGenero = ultimoCambioGenero;
    }

    /**
     * Verifica si tiene al menos una red social configurada
     */
    public boolean hasAnySocialMedia() {
        return discord != null || instagram != null || github != null ||
                tiktok != null || twitch != null || kick != null || youtube != null;
    }

    /**
     * Cuenta cuántas redes sociales tiene configuradas
     */
    public int getSocialMediaCount() {
        int count = 0;
        if (discord != null && !discord.isEmpty()) count++;
        if (instagram != null && !instagram.isEmpty()) count++;
        if (github != null && !github.isEmpty()) count++;
        if (tiktok != null && !tiktok.isEmpty()) count++;
        if (twitch != null && !twitch.isEmpty()) count++;
        if (kick != null && !kick.isEmpty()) count++;
        if (youtube != null && !youtube.isEmpty()) count++;
        return count;
    }

    /**
     * Obtiene un mapa de todas las redes sociales configuradas
     */
    public Map<String, String> getSocialMediaMap() {
        Map<String, String> socials = new HashMap<>();
        if (discord != null && !discord.isEmpty()) socials.put("Discord", discord);
        if (instagram != null && !instagram.isEmpty()) socials.put("Instagram", instagram);
        if (github != null && !github.isEmpty()) socials.put("Github", github);
        if (tiktok != null && !tiktok.isEmpty()) socials.put("TikTok", tiktok);
        if (twitch != null && !twitch.isEmpty()) socials.put("Twitch", twitch);
        if (kick != null && !kick.isEmpty()) socials.put("Kick", kick);
        if (youtube != null && !youtube.isEmpty()) socials.put("YouTube", youtube);
        return socials;
    }

    // =================== FARMING ===================

    public int getFarmingLevel() { return farmingLevel; }
    public void setFarmingLevel(int farmingLevel) { this.farmingLevel = farmingLevel; }

    public long getFarmingXP() { return farmingXP; }
    public void setFarmingXP(long farmingXP) { this.farmingXP = farmingXP; }

    // =================== MINING ===================

    public int getMiningLevel() { return miningLevel; }
    public void setMiningLevel(int miningLevel) { this.miningLevel = miningLevel; }

    public long getMiningXP() { return miningXP; }
    public void setMiningXP(long miningXP) { this.miningXP = miningXP; }

    // =================== BANCO DE XP ===================

    public long getBankedXp() { return bankedXp; }
    public void setBankedXp(long bankedXp) { this.bankedXp = bankedXp; }

    public int getBankLevel() { return bankLevel; }
    public void setBankLevel(int bankLevel) {
        this.bankLevel = bankLevel;
        updateBankCapacity();
    }

    public long getBankCapacity() { return bankCapacity; }
    public void setBankCapacity(long bankCapacity) { this.bankCapacity = bankCapacity; }

    // =================== HABILIDADES ===================

    public Map<String, Integer> getAbilities() {
        if (abilities == null) abilities = new HashMap<>();
        return abilities;
    }

    public void setAbilities(Map<String, Integer> abilities) {
        this.abilities = abilities;
    }

    public void setMasteryLevels(Map<String, Integer> masteryLevels) {
        setAbilities(masteryLevels);
    }

    // =================== MÉTODOS DE COMPATIBILIDAD ===================

    public String getBirthday() { return getCumpleaños(); }
    public void setBirthday(String birthday) { setCumpleaños(birthday); }

    public String getGender() { return getGenero(); }
    public void setGender(String gender) { setGenero(gender); }

    public String getCountry() { return getPais(); }
    public void setCountry(String country) { setPais(country); }

    public long getFarmingXp() { return getFarmingXP(); }
    public long getMiningXp() { return getMiningXP(); }

    // =================== MÉTODOS AUXILIARES ===================

    private void updateBankCapacity() {
        long baseCapacity = 68000;
        long capacityPerLevel = 170000;
        this.bankCapacity = baseCapacity + ((bankLevel - 1) * capacityPerLevel);
    }

    public long getTotalScore() {
        return farmingXP + miningXP;
    }

    public boolean isBankFull() {
        return bankedXp >= bankCapacity;
    }

    public long getAvailableBankSpace() {
        return Math.max(0, bankCapacity - bankedXp);
    }

    public boolean canDeposit(long amount) {
        return bankedXp + amount <= bankCapacity;
    }

    public boolean depositXp(long amount) {
        if (canDeposit(amount)) {
            bankedXp += amount;
            return true;
        }
        return false;
    }

    public boolean withdrawXp(long amount) {
        if (bankedXp >= amount) {
            bankedXp -= amount;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("UserData{uuid='%s', nombre='%s', farmingLv=%d, miningLv=%d, bankedXp=%d, bankLv=%d, socials=%d}",
                uuid, nombre, farmingLevel, miningLevel, bankedXp, bankLevel, getSocialMediaCount());
    }
}