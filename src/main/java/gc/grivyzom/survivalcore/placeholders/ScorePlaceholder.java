package gc.grivyzom.survivalcore.placeholders;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.UserData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class ScorePlaceholder extends PlaceholderExpansion {

    private final Main plugin;

    public ScorePlaceholder(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean register() {
        return super.register();
    }

    @Override
    public String getIdentifier() {
        return "score";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Este método es llamado cada vez que se solicita un placeholder.
     * Por ejemplo: %score_gender% devolverá el género del jugador.
     */
    @Override
    public String onPlaceholderRequest(Player p, String identifier) {
        if (p == null) {
            return "No Elegido";
        }
        // Usar UUID para obtener datos correctos
        UserData data = plugin.getDatabaseManager().getUserData(p.getUniqueId().toString());
        if (data == null) {
            return "No Elegido";
        }
        switch (identifier.toLowerCase()) {
            case "gender":
                String gender = data.getGenero();
                return (gender == null || gender.trim().isEmpty()) ? "No Elegido" : gender;
            case "country":
                String country = data.getPais();
                return (country == null || country.trim().isEmpty()) ? "No Elegido" : country;
            case "birthday":
                String birthday = data.getCumpleaños();
                return (birthday == null || birthday.trim().isEmpty()) ? "No Elegido" : birthday;
            case "farminglevel":
                return String.valueOf(data.getFarmingLevel());
            case "farmingxp":
                return String.valueOf(data.getFarmingXP());
            case "mininglevel":
                return String.valueOf(data.getMiningLevel());
            case "miningxp":
                return String.valueOf(data.getMiningXP());
            default:
                return null;
        }
    }
}
