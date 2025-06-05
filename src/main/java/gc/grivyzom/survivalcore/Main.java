package gc.grivyzom.survivalcore;

import gc.grivyzom.survivalcore.commands.*;
import gc.grivyzom.survivalcore.recipes.LecternRecipeCreateCommand;
import gc.grivyzom.survivalcore.config.CropExperienceConfig;
import gc.grivyzom.survivalcore.config.MiningExperienceConfig;
import gc.grivyzom.survivalcore.data.DatabaseManager;
import gc.grivyzom.survivalcore.gui.*;
import gc.grivyzom.survivalcore.listeners.*;
import gc.grivyzom.survivalcore.masteries.*;
import gc.grivyzom.survivalcore.placeholders.ScorePlaceholder;
import gc.grivyzom.survivalcore.recipes.LecternRecipeManager;
import gc.grivyzom.survivalcore.skills.*;
import gc.grivyzom.survivalcore.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import gc.grivyzom.survivalcore.api.SurvivalCoreAPI;
import gc.grivyzom.survivalcore.api.events.*;

import java.time.LocalDate;
import java.util.logging.Level;

/**
 * Clase principal del plugin SurvivalCore.
 * Organizada en secciones para mejorar legibilidad y mantenimiento.
 */
public class Main extends JavaPlugin {

    /* =================== CAMPOS =================== */
    // Configuración – se cargan en loadSettings()
    private String dbType, host, dbName, dbUser, dbPass;
    private int port;
    private double cropXpChance;
    private long backupIntervalTicks;

    // Managers y utilidades
    private DatabaseManager databaseManager;
    private CooldownManager cooldownManager;
    private CropExperienceConfig cropConfig;
    private MiningExperienceConfig miningConfig;
    private SkillManager skillManager;
    private MasteryManager masteryManager;
    private PlacedBlocksManager placedBlocksManager;
    private BirthdayCommand birthdayCommand;
    private LecternRecipeManager lecternRecipeManager;   // +getter
    private XpTransferManager xpTransferManager;
    private XpTransferCommand xpTransferCommand;

    /* =================== CICLO DE VIDA =================== */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        loadSettings();
        if (!initDatabase()) return;     // detiene la carga si la BD falla
        initManagers();
        RecipeUnlockManager.load();         // <<< Añade aquí

        // Inicializar managers de transferencia ANTES de registrar comandos
        xpTransferManager = new XpTransferManager(this);
        xpTransferCommand = new XpTransferCommand(this);

        registerCommands();
        registerListeners();
        hookPlaceholderAPI();
        scheduleBackups();

        // Registrar el listener del Atril Mágico
        getServer().getPluginManager().registerEvents(
                new LecternRecipeUseListener(this),
                this
        );

        getLogger().info("SurvivalCore habilitado correctamente.");
        SurvivalCoreAPI.initialize(this);
        getLogger().info("SurvivalCore API v2.0 inicializada y lista para otros plugins.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        getLogger().info("SurvivalCore deshabilitado.");
    }

    /* =================== INICIALIZACIÓN =================== */
    /** Carga valores de config en campos para uso posterior. */
    private void loadSettings() {
        dbType  = getConfig().getString("database.type", "mysql");
        host    = getConfig().getString("database.host", "localhost");
        port    = getConfig().getInt("database.port", 3306);
        dbName  = getConfig().getString("database.database", "survivalcore");
        dbUser  = getConfig().getString("database.user", "root");
        dbPass  = getConfig().getString("database.password", "");

        cropXpChance      = getConfig().getDouble("plugin.cropXpChance", 0.65);
        backupIntervalTicks = getConfig().getLong("plugin.backupIntervalTicks", 6L * 20 * 60 * 60); // 6 h
    }

    /** Inicializa DatabaseManager; deshabilita el plugin si falla. */
    private boolean initDatabase() {
        String url = dbType.equalsIgnoreCase("mysql")
                ? String.format("jdbc:mysql://%s:%d/%s?autoReconnect=true", host, port, dbName)
                : String.format("jdbc:sqlite:%s/userdata.db", getDataFolder().getAbsolutePath());
        try {
            databaseManager = new DatabaseManager(url, dbUser, dbPass);
            return true;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "No se pudo inicializar la base de datos", ex);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /** Crea e inicializa todos los managers y caches. */
    private void initManagers() {
        cooldownManager     = new CooldownManager(getConfig().getLong("plugin.cooldownMs", 5000));
        cropConfig          = new CropExperienceConfig(this);
        miningConfig        = new MiningExperienceConfig(this);
        placedBlocksManager = new PlacedBlocksManager(this);

        skillManager = new SkillManager(this);
        skillManager.registerSkill(new FrenesiGranjeroSkillFactory(this));
        skillManager.registerSkill(new ManoDelAgricultorSkillFactory(this));
        skillManager.registerSkill(new MiningAccelerationSkillFactory(this));
        skillManager.registerSkill(new MiningOreDetectionSkillFactory(this));
        lecternRecipeManager = new LecternRecipeManager(this);
        masteryManager = new MasteryManager(this);
        masteryManager.registerMastery(new MasteryVisionNocturna(1));
    }

    /* =================== REGISTRO COMANDOS & LISTENERS =================== */
    private void registerCommands() {
        birthdayCommand = new BirthdayCommand(this);
        xpTransferCommand = new XpTransferCommand(this); // Inicializar antes de registrar
        registerCommand("repair", new RepairCommand(this));
        registerCommand("reparar", new RepairCommand(this)); // Alias en español
        registerCommand("birthday", birthdayCommand);
        registerCommand("perfil",   new PerfilCommand(this));
        registerCommand("score",    new ScoreCommand(this));
        registerCommand("skills",   new SkillsCommand(this));
        registerCommand("mastery",  new MasteryCommand(this));
        registerCommand("genero",   new GeneroCommand(this));
        registerCommand("lectern", new LecternRecipeCreateCommand(this, lecternRecipeManager));

        // Registrar comandos de transferencia de XP
        registerCommand("xpgive", xpTransferCommand);
        registerCommand("xptransfers", xpTransferCommand);
        registerCommand("xptransferlog", xpTransferCommand);
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();

        // Cumpleaños y login/logout
        pm.registerEvents(new BirthdayChatListener(birthdayCommand), this);
        pm.registerEvents(new BirthdayListener(this), this);
        pm.registerEvents(new JoinQuitListener(this), this);
        pm.registerEvents(new GeneroGUIListener(this), this);

        // GUI y menús
        pm.registerEvents(new ProfesionesMenuListener(this), this);
        pm.registerEvents(new SkillsMenuListener(this), this);
        pm.registerEvents(new ProfileGUIListener(this), this);
        pm.registerEvents(new VisionNocturnaListener(this, masteryManager), this);
        pm.registerEvents(new MasteryProfessionsMenuListener(this), this);
        pm.registerEvents(new MasteryViewListener(this), this);
        pm.registerEvents(new FarmingGUIListener(this), this);
        pm.registerEvents(new MagicLecternListener(this), this);
        pm.registerEvents(new MagicLecternMenu(), this);  // ← AÑADE ESTA LÍNEA
        pm.registerEvents(new MagicLecternRecipesMenu(), this);  // <<< Nuevo menú de recetas

        // Experiencia, minería y bloques colocados
        pm.registerEvents(new CropExperienceListener(this), this);
        pm.registerEvents(new ExperiencePotListener(this), this);
        pm.registerEvents(new ExperiencePotMenu(), this);
        pm.registerEvents(new MiningExperienceListener(this, miningConfig), this);
        pm.registerEvents(new MiningBlockPlaceListener(this, miningConfig), this);
    }

    /* =================== INTEGRACIONES EXTERNAS =================== */
    private void hookPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ScorePlaceholder(this).register();
            getLogger().info("PlaceholderAPI integrado.");
        }
    }

    /* =================== TAREAS PROGRAMADAS =================== */
    private void scheduleBackups() {
        BackupManager backup = new BackupManager(this, host, port, dbName, dbUser, dbPass);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, backup::performBackup, backupIntervalTicks, backupIntervalTicks);
    }

    /* =================== UTILIDADES =================== */
    private void registerCommand(String name, org.bukkit.command.CommandExecutor exec) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(exec);
        } else {
            getLogger().warning("No se pudo registrar el comando: " + name);
        }
        // REMOVIDO: Las llamadas recursivas que causaban el StackOverflow
    }

    /* =================== GETTERS PÚBLICOS =================== */
    public DatabaseManager getDatabaseManager()                 { return databaseManager; }
    public CooldownManager getCooldownManager()                 { return cooldownManager; }
    public CropExperienceConfig getCropExperienceConfig()       { return cropConfig; }
    public double getCropXpChance()                             { return cropXpChance; }
    public SkillManager getSkillManager()                       { return skillManager; }
    public MasteryManager getMasteryManager()                   { return masteryManager; }
    public PlacedBlocksManager getPlacedBlocksManager()         { return placedBlocksManager; }
    public MiningExperienceConfig getMiningConfig()             { return miningConfig; }
    public LecternRecipeManager getLecternRecipeManager() { return lecternRecipeManager; }
    public XpTransferManager getXpTransferManager() { return xpTransferManager; }
    public XpTransferCommand getXpTransferCommand() { return xpTransferCommand; }
    /**
     * Refresca valores que cambian al recargar configuración.
     */


    public void updateInternalConfig() {
        this.cropXpChance = getConfig().getDouble("plugin.cropXpChance", this.cropXpChance);
        if (xpTransferManager != null) {
            xpTransferManager.reloadConfig();
        }
        getLogger().info("Configuración interna actualizada.");
    }

    /**
     * Dispara evento cuando un jugador deposita XP en el banco
     */
  // NUEVOS MÉTODOS para disparar eventos personalizados:

    /**
     * Dispara evento cuando un jugador deposita XP en el banco
     */
    public void firePlayerBankDepositEvent(Player player, long amount, long newBalance) {
        PlayerBankDepositEvent event = new PlayerBankDepositEvent(player, amount, newBalance);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            // Revertir el depósito si fue cancelado
            getDatabaseManager().withdrawBankedXp(player.getUniqueId().toString(), amount);
            player.sendMessage(ChatColor.RED + "Depósito cancelado por otro plugin.");
        }
    }
    /**
     * Dispara evento cuando un jugador retira XP del banco
     */
    public void firePlayerBankWithdrawEvent(Player player, long amount, long newBalance) {
        PlayerBankWithdrawEvent event = new PlayerBankWithdrawEvent(player, amount, newBalance);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando un jugador mejora su banco
     */
    public void firePlayerBankUpgradeEvent(Player player, long oldCapacity, long newCapacity, int newLevel) {
        PlayerBankUpgradeEvent event = new PlayerBankUpgradeEvent(player, oldCapacity, newCapacity, newLevel);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando un jugador sube de nivel en una profesión
     */
    public void firePlayerProfessionLevelUpEvent(Player player, String profession, int oldLevel, int newLevel) {
        PlayerProfessionLevelUpEvent event = new PlayerProfessionLevelUpEvent(player, profession, oldLevel, newLevel);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando un jugador gana XP en una profesión
     */
    public void firePlayerProfessionXPGainEvent(Player player, String profession, long xpGained, long totalXP) {
        PlayerProfessionXPGainEvent event = new PlayerProfessionXPGainEvent(player, profession, xpGained, totalXP);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            // No aplicar la ganancia de XP
            return;
        }
    }

    /**
     * Dispara evento cuando un jugador activa una habilidad
     */
    public void firePlayerSkillActivateEvent(Player player, String skillName, int skillLevel, long duration) {
        PlayerSkillActivateEvent event = new PlayerSkillActivateEvent(player, skillName, skillLevel, duration);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando una habilidad se desactiva
     */
    public void firePlayerSkillDeactivateEvent(Player player, String skillName, int skillLevel) {
        PlayerSkillDeactivateEvent event = new PlayerSkillDeactivateEvent(player, skillName, skillLevel);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando un jugador sube de nivel una habilidad
     */
    public void firePlayerSkillLevelUpEvent(Player player, String skillName, int oldLevel, int newLevel) {
        PlayerSkillLevelUpEvent event = new PlayerSkillLevelUpEvent(player, skillName, oldLevel, newLevel);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando un jugador mejora una maestría
     */
    public void firePlayerMasteryUpgradeEvent(Player player, String masteryId, int oldLevel, int newLevel, boolean isMaxLevel) {
        PlayerMasteryUpgradeEvent event = new PlayerMasteryUpgradeEvent(player, masteryId, oldLevel, newLevel, isMaxLevel);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando un jugador transfiere XP
     */
    public void firePlayerXPTransferEvent(Player sender, String receiverName, long amount, PlayerXPTransferEvent.TransferType type) {
        PlayerXPTransferEvent event = new PlayerXPTransferEvent(sender, receiverName, amount, type);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando un jugador cambia su género
     */
    public void firePlayerGenderChangeEvent(Player player, String oldGender, String newGender) {
        PlayerGenderChangeEvent event = new PlayerGenderChangeEvent(player, oldGender, newGender);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando un jugador establece su cumpleaños
     */
    public void firePlayerBirthdaySetEvent(Player player, String birthday) {
        boolean isToday = false;
        try {
            LocalDate birthDate = LocalDate.parse(birthday);
            LocalDate today = LocalDate.now();
            isToday = birthDate.getMonth() == today.getMonth() &&
                    birthDate.getDayOfMonth() == today.getDayOfMonth();
        } catch (Exception ignored) {}

        PlayerBirthdaySetEvent event = new PlayerBirthdaySetEvent(player, birthday, isToday);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Dispara evento cuando es el cumpleaños de un jugador
     */
    public void firePlayerBirthdayEvent(Player player, String birthday) {
        PlayerBirthdayEvent event = new PlayerBirthdayEvent(player, birthday);
        Bukkit.getPluginManager().callEvent(event);

        // Aplicar configuraciones del evento
        if (!event.isBroadcastEnabled()) {
            // No hacer broadcast si está deshabilitado
            return;
        }

        if (!event.isFireworksEnabled()) {
            // No lanzar fuegos artificiales si está deshabilitado
            return;
        }

    }

}
