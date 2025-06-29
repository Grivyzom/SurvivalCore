package gc.grivyzom.survivalcore.data;

import java.util.HashMap;
import java.util.Map;

public class UserData {
    private String uuid;
    private String nombre;
    private String cumpleaños;
    private String genero;
    private String pais;

    private int farmingLevel;
    private long farmingXP;  // Cambiado de int a long
    private int miningLevel;
    private long miningXP;   // Cambiado de int a long

    private Map<String, Integer> abilities;
    private long bankedXp;

    public UserData() {
        this.abilities = new HashMap<>();
        this.bankedXp = 0;
    }

    public UserData(String uuid, String nombre, String cumpleaños, String genero, String pais) {
        this.uuid = uuid;
        this.nombre = nombre;
        this.cumpleaños = cumpleaños;
        this.genero = genero;
        this.pais = pais;
        this.farmingLevel = 1;
        this.farmingXP = 0;      // Ahora es long
        this.miningLevel = 1;
        this.miningXP = 0;       // Ahora es long
        this.abilities = new HashMap<>();

    }

    // Getters y Setters
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

    public int getFarmingLevel() { return farmingLevel; }
    public void setFarmingLevel(int farmingLevel) { this.farmingLevel = farmingLevel; }

    // Cambiados a long
    public long getFarmingXP() { return farmingXP; }
    public void setFarmingXP(long farmingXP) { this.farmingXP = farmingXP; }

    public int getMiningLevel() { return miningLevel; }
    public void setMiningLevel(int miningLevel) { this.miningLevel = miningLevel; }

    // Cambiados a long
    public long getMiningXP() { return miningXP; }
    public void setMiningXP(long miningXP) { this.miningXP = miningXP; }

    public Map<String, Integer> getAbilities() {
        if (abilities == null) abilities = new HashMap<>();
        return abilities;
    }
    public void setAbilities(Map<String, Integer> abilities) {
        this.abilities = abilities;
    }

    public void setMasteryLevels(Map<String, Integer> masteryLevels) {

    }

    public long getBankedXp() { return bankedXp; }
    public void setBankedXp(long xp) { this.bankedXp = xp; }
}