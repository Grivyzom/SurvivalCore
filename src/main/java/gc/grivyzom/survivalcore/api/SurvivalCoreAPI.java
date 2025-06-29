package gc.grivyzom.survivalcore.api;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.data.DatabaseManager;
import gc.grivyzom.survivalcore.data.UserData;
import gc.grivyzom.survivalcore.util.XpTransferManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * API principal de SurvivalCore para integración con otros plugins.
 *
 * Proporciona acceso completo a:
 * - Sistema de banco de experiencia
 * - Datos del jugador (género, cumpleaños, país)
 * - Niveles y experiencia de profesiones
 * - Habilidades y maestrías
 * - Sistema de transferencias
 *
 * @author Brocolitx
 * @version 2.0
 */
public class SurvivalCoreAPI {

    private static SurvivalCoreAPI instance;
    private final Main plugin;
    private final DatabaseManager database;
    private final XpTransferManager transferManager;

    /**
     * Constructor interno - no usar directamente
     */
    SurvivalCoreAPI(Main plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabaseManager();
        this.transferManager = plugin.getXpTransferManager();
    }

    /**
     * Inicializa la API - SOLO para uso interno del plugin
     */
    public static void initialize(Main plugin) {
        if (instance == null) {
            instance = new SurvivalCoreAPI(plugin);
        }
    }

    /**
     * Obtiene la instancia de la API para otros plugins
     *
     * @return Instancia de SurvivalCoreAPI o null si el plugin no está cargado
     */
    public static SurvivalCoreAPI getInstance() {
        return instance;
    }

    /**
     * Verifica si la API está disponible
     *
     * @return true si la API está lista para usar
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Obtiene la instancia de SurvivalCore desde otro plugin
     *
     * @param plugin Tu plugin que quiere usar la API
     * @return Instancia de SurvivalCoreAPI o null si no está disponible
     */
    public static SurvivalCoreAPI getAPI(JavaPlugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("SurvivalCore")) {
            plugin.getLogger().warning("SurvivalCore no está habilitado!");
            return null;
        }

        if (instance == null) {
            plugin.getLogger().warning("SurvivalCore API no está inicializada!");
            return null;
        }

        plugin.getLogger().info("Conectado exitosamente a SurvivalCore API v2.0");
        return instance;
    }

    // ================================
    // BANCO DE EXPERIENCIA
    // ================================

    /**
     * Obtiene la experiencia almacenada en el banco del jugador
     *
     * @param player Jugador objetivo
     * @return XP almacenada en el banco
     */
    public long getBankedXP(OfflinePlayer player) {
        return database.getBankedXp(player.getUniqueId().toString());
    }

    /**
     * Obtiene la capacidad máxima del banco del jugador
     *
     * @param player Jugador objetivo
     * @return Capacidad máxima en XP
     */
    public long getBankCapacity(OfflinePlayer player) {
        return database.getBankCapacity(player.getUniqueId().toString());
    }

    /**
     * Añade XP al banco del jugador (respeta límites)
     *
     * @param player Jugador objetivo
     * @param amount Cantidad de XP a añadir
     * @return XP realmente añadida (puede ser menor si se alcanza el límite)
     */
    public long addBankedXP(OfflinePlayer player, long amount) {
        return database.addXpCapped(player.getUniqueId().toString(), amount);
    }

    /**
     * Retira XP del banco del jugador
     *
     * @param player Jugador objetivo
     * @param amount Cantidad de XP a retirar
     * @return true si se pudo retirar, false si no había suficiente
     */
    public boolean withdrawBankedXP(OfflinePlayer player, long amount) {
        return database.withdrawBankedXp(player.getUniqueId().toString(), amount);
    }

    /**
     * Mejora la capacidad del banco del jugador
     *
     * @param player Jugador objetivo
     * @param newCapacity Nueva capacidad en XP
     * @return true si se pudo mejorar
     */
    public boolean upgradeBankCapacity(OfflinePlayer player, long newCapacity) {
        return database.upgradeBankCapacity(player.getUniqueId().toString(), newCapacity);
    }

    // ================================
    // DATOS DEL JUGADOR
    // ================================

    /**
     * Obtiene todos los datos del jugador de forma asíncrona
     *
     * @param player Jugador objetivo
     * @return CompletableFuture con los datos del jugador
     */
    public CompletableFuture<UserData> getUserDataAsync(OfflinePlayer player) {
        return CompletableFuture.supplyAsync(() ->
                database.getUserData(player.getUniqueId().toString())
        );
    }

    /**
     * Obtiene los datos del jugador (método síncrono - usar con cuidado)
     *
     * @param player Jugador objetivo
     * @return Datos del jugador
     */
    public UserData getUserData(OfflinePlayer player) {
        return database.getUserData(player.getUniqueId().toString());
    }

    /**
     * Obtiene el género del jugador
     *
     * @param player Jugador objetivo
     * @return Género del jugador ("Masculino", "Femenino", "Otro") o null
     */
    public String getPlayerGender(OfflinePlayer player) {
        UserData data = getUserData(player);
        return data != null ? data.getGenero() : null;
    }

    /**
     * Establece el género del jugador
     *
     * @param player Jugador objetivo
     * @param gender Género ("Masculino", "Femenino", "Otro")
     * @return CompletableFuture que se completa cuando se guarda
     */
    public CompletableFuture<Void> setPlayerGender(OfflinePlayer player, String gender) {
        return CompletableFuture.runAsync(() -> {
            UserData data = getUserData(player);
            if (data != null) {
                data.setGenero(gender);
                database.saveUserData(data);
            }
        });
    }

    /**
     * Obtiene el cumpleaños del jugador
     *
     * @param player Jugador objetivo
     * @return Fecha de cumpleaños o null si no está establecida
     */
    public LocalDate getPlayerBirthday(OfflinePlayer player) {
        UserData data = getUserData(player);
        if (data != null && data.getCumpleaños() != null) {
            try {
                return LocalDate.parse(data.getCumpleaños());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Establece el cumpleaños del jugador
     *
     * @param player Jugador objetivo
     * @param birthday Fecha de cumpleaños
     * @return CompletableFuture que se completa cuando se guarda
     */
    public CompletableFuture<Void> setPlayerBirthday(OfflinePlayer player, LocalDate birthday) {
        return CompletableFuture.runAsync(() -> {
            UserData data = getUserData(player);
            if (data != null) {
                data.setCumpleaños(birthday.toString());
                database.saveUserData(data);
            }
        });
    }

    /**
     * Obtiene el país del jugador
     *
     * @param player Jugador objetivo
     * @return País del jugador o null
     */
    public String getPlayerCountry(OfflinePlayer player) {
        UserData data = getUserData(player);
        return data != null ? data.getPais() : null;
    }

    /**
     * Establece el país del jugador
     *
     * @param player Jugador objetivo
     * @param country País del jugador
     * @return CompletableFuture que se completa cuando se guarda
     */
    public CompletableFuture<Void> setPlayerCountry(OfflinePlayer player, String country) {
        return CompletableFuture.runAsync(() -> {
            UserData data = getUserData(player);
            if (data != null) {
                data.setPais(country);
                database.saveUserData(data);
            }
        });
    }

    // ================================
    // PROFESIONES Y NIVELES
    // ================================

    /**
     * Obtiene el nivel de granjería del jugador
     *
     * @param player Jugador objetivo
     * @return Nivel de granjería
     */
    public int getFarmingLevel(OfflinePlayer player) {
        UserData data = getUserData(player);
        return data != null ? data.getFarmingLevel() : 1;
    }

    /**
     * Obtiene la experiencia de granjería del jugador
     *
     * @param player Jugador objetivo
     * @return XP de granjería
     */
    public long getFarmingXP(OfflinePlayer player) {
        UserData data = getUserData(player);
        return data != null ? data.getFarmingXP() : 0;
    }

    /**
     * Establece el nivel de granjería del jugador
     *
     * @param player Jugador objetivo
     * @param level Nuevo nivel
     * @return CompletableFuture que se completa cuando se guarda
     */
    public CompletableFuture<Void> setFarmingLevel(OfflinePlayer player, int level) {
        return CompletableFuture.runAsync(() -> {
            UserData data = getUserData(player);
            if (data != null) {
                data.setFarmingLevel(level);
                database.saveUserData(data);
            }
        });
    }

    /**
     * Añade XP de granjería al jugador
     *
     * @param player Jugador objetivo
     * @param xp Cantidad de XP a añadir
     * @return CompletableFuture que se completa cuando se guarda
     */
    public CompletableFuture<Void> addFarmingXP(OfflinePlayer player, long xp) {
        return CompletableFuture.runAsync(() -> {
            UserData data = getUserData(player);
            if (data != null) {
                data.setFarmingXP(data.getFarmingXP() + xp);
                database.saveUserData(data);
            }
        });
    }

    /**
     * Obtiene el nivel de minería del jugador
     *
     * @param player Jugador objetivo
     * @return Nivel de minería
     */
    public int getMiningLevel(OfflinePlayer player) {
        UserData data = getUserData(player);
        return data != null ? data.getMiningLevel() : 1;
    }

    /**
     * Obtiene la experiencia de minería del jugador
     *
     * @param player Jugador objetivo
     * @return XP de minería
     */
    public long getMiningXP(OfflinePlayer player) {
        UserData data = getUserData(player);
        return data != null ? data.getMiningXP() : 0;
    }

    /**
     * Establece el nivel de minería del jugador
     *
     * @param player Jugador objetivo
     * @param level Nuevo nivel
     * @return CompletableFuture que se completa cuando se guarda
     */
    public CompletableFuture<Void> setMiningLevel(OfflinePlayer player, int level) {
        return CompletableFuture.runAsync(() -> {
            UserData data = getUserData(player);
            if (data != null) {
                data.setMiningLevel(level);
                database.saveUserData(data);
            }
        });
    }

    /**
     * Añade XP de minería al jugador
     *
     * @param player Jugador objetivo
     * @param xp Cantidad de XP a añadir
     * @return CompletableFuture que se completa cuando se guarda
     */
    public CompletableFuture<Void> addMiningXP(OfflinePlayer player, long xp) {
        return CompletableFuture.runAsync(() -> {
            UserData data = getUserData(player);
            if (data != null) {
                data.setMiningXP(data.getMiningXP() + xp);
                database.saveUserData(data);
            }
        });
    }

    // ================================
    // SISTEMA DE TRANSFERENCIAS
    // ================================

    /**
     * Obtiene el límite diario de transferencias del jugador
     *
     * @param player Jugador objetivo (debe estar online para verificar permisos)
     * @return Límite diario (-1 = ilimitado)
     */
    public int getDailyTransferLimit(Player player) {
        return transferManager.getDailyLimit(player);
    }

    /**
     * Obtiene cuánto ha transferido el jugador hoy
     *
     * @param player Jugador objetivo
     * @return Cantidad transferida hoy
     */
    public int getDailyTransferred(OfflinePlayer player) {
        return transferManager.getDailyTransferred(player.getUniqueId().toString());
    }

    /**
     * Transfiere experiencia entre jugadores desde la barra
     *
     * @param sender Jugador que envía (debe estar online)
     * @param targetName Nombre del jugador destinatario
     * @param levels Niveles a transferir
     */
    public void transferPlayerXP(Player sender, String targetName, int levels) {
        transferManager.transferFromPlayer(sender, targetName, levels);
    }

    /**
     * Transfiere experiencia desde el banco
     *
     * @param sender Jugador que envía (debe estar online)
     * @param targetName Nombre del jugador destinatario
     * @param levels Niveles a transferir
     */
    public void transferBankXP(Player sender, String targetName, int levels) {
        transferManager.transferFromBank(sender, targetName, levels);
    }

    // ================================
    // UTILIDADES
    // ================================

    /**
     * Guarda los datos de un jugador de forma asíncrona
     *
     * @param data Datos a guardar
     * @return CompletableFuture que se completa cuando se guarda
     */
    public CompletableFuture<Void> saveUserDataAsync(UserData data) {
        return CompletableFuture.runAsync(() -> database.saveUserData(data));
    }

    /**
     * Verifica si hoy es el cumpleaños del jugador
     *
     * @param player Jugador objetivo
     * @return true si hoy es su cumpleaños
     */
    public boolean isBirthdayToday(OfflinePlayer player) {
        LocalDate birthday = getPlayerBirthday(player);
        if (birthday == null) return false;

        LocalDate today = LocalDate.now();
        return birthday.getMonth() == today.getMonth() &&
                birthday.getDayOfMonth() == today.getDayOfMonth();
    }

    /**
     * Obtiene la versión de la API
     *
     * @return Versión de la API
     */
    public String getAPIVersion() {
        return "2.0";
    }

    /**
     * Obtiene la versión del plugin SurvivalCore
     *
     * @return Versión del plugin
     */
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }
}