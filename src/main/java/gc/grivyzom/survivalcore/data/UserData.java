package gc.grivyzom.survivalcore.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Clase que representa los datos de un usuario en SurvivalCore
 *
 * @author Brocolitx
 * @version 2.0
 */
public class UserData {
    private String uuid;
    private String nombre;
    private String cumpleaños;
    private String genero;
    private String pais;

    private int farmingLevel;
    private long farmingXP;  // Cambiado de int a long para soportar valores grandes
    private int miningLevel;
    private long miningXP;   // Cambiado de int a long para soportar valores grandes

    private Map<String, Integer> abilities;
    private long bankedXp;

    // Nuevos campos para el banco de XP
    private int bankLevel;
    private long bankCapacity;

    public UserData() {
        this.abilities = new HashMap<>();
        this.bankedXp = 0;
        this.bankLevel = 1;
        this.bankCapacity = 68000; // Capacidad inicial por defecto
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
        updateBankCapacity(); // Actualizar capacidad cuando cambia el nivel
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
        // Método de compatibilidad
        setAbilities(masteryLevels);
    }

    // =================== MÉTODOS DE COMPATIBILIDAD ===================

    /**
     * Método de compatibilidad para obtener cumpleaños
     */
    public String getBirthday() {
        return getCumpleaños();
    }

    /**
     * Método de compatibilidad para establecer cumpleaños
     */
    public void setBirthday(String birthday) {
        setCumpleaños(birthday);
    }

    /**
     * Método de compatibilidad para obtener género
     */
    public String getGender() {
        return getGenero();
    }

    /**
     * Método de compatibilidad para establecer género
     */
    public void setGender(String gender) {
        setGenero(gender);
    }

    /**
     * Método de compatibilidad para obtener país
     */
    public String getCountry() {
        return getPais();
    }

    /**
     * Método de compatibilidad para establecer país
     */
    public void setCountry(String country) {
        setPais(country);
    }

    /**
     * Método de compatibilidad para farming XP
     */
    public long getFarmingXp() {
        return getFarmingXP();
    }

    /**
     * Método de compatibilidad para mining XP
     */
    public long getMiningXp() {
        return getMiningXP();
    }

    // =================== MÉTODOS AUXILIARES ===================

    /**
     * Actualiza la capacidad del banco basándose en el nivel
     */
    private void updateBankCapacity() {
        // Capacidad base + incremento por nivel
        long baseCapacity = 68000;      // Capacidad inicial
        long capacityPerLevel = 170000; // Incremento por nivel

        this.bankCapacity = baseCapacity + ((bankLevel - 1) * capacityPerLevel);
    }

    /**
     * Calcula la puntuación total del jugador
     */
    public long getTotalScore() {
        return farmingXP + miningXP;
    }

    /**
     * Verifica si el banco está lleno
     */
    public boolean isBankFull() {
        return bankedXp >= bankCapacity;
    }

    /**
     * Obtiene el espacio disponible en el banco
     */
    public long getAvailableBankSpace() {
        return Math.max(0, bankCapacity - bankedXp);
    }

    /**
     * Verifica si se puede depositar una cantidad específica
     */
    public boolean canDeposit(long amount) {
        return bankedXp + amount <= bankCapacity;
    }

    /**
     * Deposita XP en el banco
     * @param amount Cantidad a depositar
     * @return true si se depositó exitosamente, false si no había espacio
     */
    public boolean depositXp(long amount) {
        if (canDeposit(amount)) {
            bankedXp += amount;
            return true;
        }
        return false;
    }

    /**
     * Retira XP del banco
     * @param amount Cantidad a retirar
     * @return true si se retiró exitosamente, false si no había suficiente
     */
    public boolean withdrawXp(long amount) {
        if (bankedXp >= amount) {
            bankedXp -= amount;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("UserData{uuid='%s', nombre='%s', farmingLv=%d, miningLv=%d, bankedXp=%d, bankLv=%d}",
                uuid, nombre, farmingLevel, miningLevel, bankedXp, bankLevel);
    }
}