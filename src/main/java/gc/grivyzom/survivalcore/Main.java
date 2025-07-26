package gc.grivyzom.survivalcore;

import gc.grivyzom.survivalcore.commands.*;
import gc.grivyzom.survivalcore.recipes.LecternRecipeCreateCommand;
import gc.grivyzom.survivalcore.config.CropExperienceConfig;
import gc.grivyzom.survivalcore.config.MiningExperienceConfig;
import gc.grivyzom.survivalcore.data.DatabaseManager;
import gc.grivyzom.survivalcore.gui.*;
import gc.grivyzom.survivalcore.listeners.*;
import gc.grivyzom.survivalcore.placeholders.ScorePlaceholder;
import gc.grivyzom.survivalcore.recipes.LecternRecipeManager;
import gc.grivyzom.survivalcore.sellwand.SellWandCommand;
import gc.grivyzom.survivalcore.sellwand.SellWandListener;
import gc.grivyzom.survivalcore.sellwand.SellWandManager;
import gc.grivyzom.survivalcore.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import gc.grivyzom.survivalcore.api.SurvivalCoreAPI;
import gc.grivyzom.survivalcore.api.events.*;
import gc.grivyzom.survivalcore.commands.XpChequeCommand;
import gc.grivyzom.survivalcore.listeners.XpChequeListener;
import gc.grivyzom.survivalcore.util.XpChequeManager;
import gc.grivyzom.survivalcore.rankup.*;
import gc.grivyzom.survivalcore.listeners.RankupMenuListener;
import gc.grivyzom.survivalcore.rankup.RankupManager;
import gc.grivyzom.survivalcore.commands.RankupCommand;
import gc.grivyzom.survivalcore.listeners.RankupMenuListener;
import gc.grivyzom.survivalcore.commands.XpBankCommand;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotManager;
import gc.grivyzom.survivalcore.commands.MagicFlowerPotCommand;
import gc.grivyzom.survivalcore.commands.MagicFlowerPotCommand;
import gc.grivyzom.survivalcore.listeners.MagicFlowerPotListener;


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
    private PlacedBlocksManager placedBlocksManager;
    private BirthdayCommand birthdayCommand;
    private LecternRecipeManager lecternRecipeManager;
    private XpTransferManager xpTransferManager;
    private XpTransferCommand xpTransferCommand;
    private SellWandManager sellWandManager;
    private XpChequeCommand xpChequeCommand;
    private RankupManager rankupManager;
    private MagicFlowerPotManager magicFlowerPotManager;

    /* =================== CICLO DE VIDA =================== */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        loadSettings();
        if (!initDatabase()) return;
        initManagers();
        RecipeUnlockManager.load();

        // Inicializar SellWand
        initSellWand();

        // Inicializar managers de transferencia ANTES de registrar comandos
        xpTransferManager = new XpTransferManager(this);
        xpTransferCommand = new XpTransferCommand(this);

        // Inicializar RankupManager con validación
        if (!initRankupSystem()) {
            getLogger().warning("Sistema de Rankup no pudo inicializarse - comandos relacionados estarán deshabilitados");
        }

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
        if (magicFlowerPotManager != null) magicFlowerPotManager.shutdown();

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
        lecternRecipeManager = new LecternRecipeManager(this);
        magicFlowerPotManager = new MagicFlowerPotManager(this);
    }

    /**
     * Inicializa el sistema de rankup de forma segura
     * @return true si se inicializó correctamente, false si hubo errores
     */
    private boolean initRankupSystem() {
        try {
            // Verificar si LuckPerms está disponible
            if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                getLogger().warning("LuckPerms no encontrado - Sistema de Rankup deshabilitado");
                getLogger().warning("Para usar el sistema de rankup, instala LuckPerms");
                return false;
            }

            // Verificar si LuckPerms está habilitado
            if (!getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
                getLogger().warning("LuckPerms no está habilitado - Sistema de Rankup deshabilitado");
                return false;
            }

            // Intentar inicializar RankupManager
            rankupManager = new RankupManager(this);
            getLogger().info("Sistema de Rankup inicializado correctamente con LuckPerms.");
            return true;

        } catch (Exception e) {
            getLogger().severe("Error crítico al inicializar el sistema de Rankup:");
            getLogger().severe("Tipo de error: " + e.getClass().getSimpleName());
            getLogger().severe("Mensaje: " + e.getMessage());
            e.printStackTrace();

            // Deshabilitar solo el sistema de rankup, no el plugin completo
            rankupManager = null;
            return false;
        }
    }

    private void initSellWand() {
        try {
            sellWandManager = new SellWandManager(this);
            getLogger().info("SellWand system initialized successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize SellWand system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =================== COMANDOS ===================
    private void registerCommands() {
        birthdayCommand = new BirthdayCommand(this);

        registerCommand("repair", new RepairCommand(this));
        registerCommand("reparar", new RepairCommand(this));
        registerCommand("birthday", birthdayCommand);
        registerCommand("perfil", new PerfilCommand(this));
        registerCommand("score", new ScoreCommand(this));
        registerCommand("genero", new GeneroCommand(this));
        registerCommand("lectern", new LecternRecipeCreateCommand(this, lecternRecipeManager));

        // Comandos de transferencia de XP
        registerCommand("xpgive", xpTransferCommand);
        registerCommand("xptransfers", xpTransferCommand);
        registerCommand("xptransferlog", xpTransferCommand);

        registerCommand("sellwand", new SellWandCommand(this, sellWandManager));
        registerCommand("sw", new SellWandCommand(this, sellWandManager));

        // Comandos de Cheques de XP
        xpChequeCommand = new XpChequeCommand(this);
        registerCommand("cheque", xpChequeCommand);

        registerCommand("xpbank", new XpBankCommand(this));

        // 🆕 COMANDOS DE MACETAS Y FLORES MÁGICAS
        registerCommand("flowerpot", new MagicFlowerPotCommand(this));
        registerCommand("magicflower", new MagicFlowerPotCommand(this));

        // COMANDOS DE RANKUP - Solo registrar si el sistema está disponible
        if (rankupManager != null) {
            RankupCommand rankupCmd = new RankupCommand(this, rankupManager);
            registerCommand("rankup", rankupCmd);
            registerCommand("prestige", rankupCmd);
            registerCommand("ranks", rankupCmd);
            getLogger().info("Comandos de rankup registrados correctamente.");
        } else {
            getLogger().warning("Comandos de rankup NO registrados - Sistema no disponible");
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();

        pm.registerEvents(new BirthdayChatListener(birthdayCommand), this);
        pm.registerEvents(new BirthdayListener(this), this);
        pm.registerEvents(new JoinQuitListener(this), this);
        pm.registerEvents(new GeneroGUIListener(this), this);
        pm.registerEvents(new ProfileGUIListener(this), this);
        pm.registerEvents(new MagicLecternListener(this), this);
        pm.registerEvents(new MagicLecternMenu(), this);
        pm.registerEvents(new MagicLecternRecipesMenu(), this);
        pm.registerEvents(new CropExperienceListener(this), this);
        pm.registerEvents(new ExperiencePotListener(this), this);
        pm.registerEvents(new ExperiencePotMenu(), this);
        pm.registerEvents(new MiningExperienceListener(this, miningConfig), this);
        pm.registerEvents(new MiningBlockPlaceListener(this, miningConfig), this);
        pm.registerEvents(new XpChequeListener(this, xpChequeCommand.getChequeManager()), this);
        pm.registerEvents(new SellWandListener(this, sellWandManager), this);
        pm.registerEvents(new MagicFlowerPotListener(this), this);

        // LISTENER DE RANKUP - Solo registrar si el sistema está disponible
        if (rankupManager != null) {
            pm.registerEvents(new RankupMenuListener(this), this);
            getLogger().info("Listeners de rankup registrados correctamente.");
        }
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
    }

    /* =================== GETTERS PÚBLICOS =================== */
    public DatabaseManager getDatabaseManager()                 { return databaseManager; }
    public CooldownManager getCooldownManager()                 { return cooldownManager; }
    public CropExperienceConfig getCropExperienceConfig()       { return cropConfig; }
    public double getCropXpChance()                             { return cropXpChance; }
    public PlacedBlocksManager getPlacedBlocksManager()         { return placedBlocksManager; }
    public MiningExperienceConfig getMiningConfig()             { return miningConfig; }
    public LecternRecipeManager getLecternRecipeManager()       { return lecternRecipeManager; }
    public XpTransferManager getXpTransferManager()             { return xpTransferManager; }
    public XpTransferCommand getXpTransferCommand()             { return xpTransferCommand; }
    public RankupManager getRankupManager()                     { return rankupManager; }
    public SellWandManager getSellWandManager()                 { return sellWandManager; }
    public XpChequeCommand getXpChequeCommand()                 { return xpChequeCommand; }

    /**
     * Verificar si el sistema de rankup está disponible
     */
    public boolean isRankupSystemEnabled() {
        return rankupManager != null;
    }

    /**
     * Refresca valores que cambian al recargar configuración.
     */
    /**
     * Refresca valores que cambian al recargar configuración.
     * VERSIÓN MEJORADA con soporte completo para todos los sistemas
     */
    public void updateInternalConfig() {
        getLogger().info("Iniciando actualización de configuración interna...");

        try {
            // Recargar configuración básica
            this.cropXpChance = getConfig().getDouble("plugin.cropXpChance", this.cropXpChance);
            getLogger().info("✓ Configuración básica actualizada");

            // Recargar configuración de transferencias
            if (xpTransferManager != null) {
                try {
                    xpTransferManager.reloadConfig();
                    getLogger().info("✓ Configuración de transferencias XP actualizada");
                } catch (Exception e) {
                    getLogger().warning("Error recargando transferencias XP: " + e.getMessage());
                }
            }

            // Recargar configuración de cheques
            if (xpChequeCommand != null && xpChequeCommand.getChequeManager() != null) {
                try {
                    xpChequeCommand.getChequeManager().reloadConfig();
                    getLogger().info("✓ Configuración de cheques XP actualizada");
                } catch (Exception e) {
                    getLogger().warning("Error recargando cheques XP: " + e.getMessage());
                }
            }

            // Recargar configuración de SellWand
            if (sellWandManager != null) {
                try {
                    sellWandManager.reloadConfig();
                    getLogger().info("✓ Configuración de SellWand actualizada");
                } catch (Exception e) {
                    getLogger().warning("Error recargando SellWand: " + e.getMessage());
                }
            }

            // Recargar configuración de mining si existe
            if (miningConfig != null) {
                try {
                    miningConfig.reload();
                    getLogger().info("✓ Configuración de minería actualizada");
                } catch (Exception e) {
                    getLogger().warning("Error recargando configuración de minería: " + e.getMessage());
                }
            }

            if (magicFlowerPotManager != null) {
                try {
                    magicFlowerPotManager.forceUpdate();
                    getLogger().info("✓ Configuración de macetas mágicas actualizada");
                } catch (Exception e) {
                    getLogger().warning("Error recargando macetas mágicas: " + e.getMessage());
                }
            }

            // Recargar configuración de crops si existe
            if (cropConfig != null) {
                try {
                    cropConfig.reload();
                    getLogger().info("✓ Configuración de cultivos actualizada");
                } catch (Exception e) {
                    getLogger().warning("Error recargando configuración de cultivos: " + e.getMessage());
                }
            }

            // 🚀 NUEVO: Recargar configuración de rankup
            if (rankupManager != null) {
                try {
                    rankupManager.reloadConfig();
                    getLogger().info("✓ Configuración de rankup actualizada");
                } catch (Exception e) {
                    getLogger().severe("Error recargando configuración de rankup: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                getLogger().info("ℹ Sistema de rankup no está disponible");
            }


            getLogger().info("Configuración interna actualizada correctamente.");

        } catch (Exception e) {
            getLogger().severe("Error crítico actualizando configuración interna: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =================== EVENTOS PERSONALIZADOS ===================

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

    public MagicFlowerPotManager getMagicFlowerPotManager() {
        return magicFlowerPotManager;
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