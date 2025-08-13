package gc.grivyzom.survivalcore;

import gc.grivyzom.survivalcore.commands.*;
import gc.grivyzom.survivalcore.flowers.integration.ConfigurableFlowerIntegration;
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
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
import gc.grivyzom.survivalcore.util.SocialMediaValidator;
import gc.grivyzom.survivalcore.commands.SocialCommand;


import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private ConfigurableFlowerIntegration flowerIntegration;

    // Configuración de GUIs
    private FileConfiguration guisConfig;
    private File guisConfigFile;


    /* =================== CICLO DE VIDA =================== */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        // Cargar configuración de GUIs
        loadGuisConfig();

        loadSettings();
        if (!initDatabase()) return;
        initManagers();

        // Inicializar GUIs con la configuración cargada
        initializeGuis();

        // 🔧 CORRECCIÓN: Inicializar ConfigurableFlowerIntegration DESPUÉS de managers
        try {
            flowerIntegration = new ConfigurableFlowerIntegration(this);
            getLogger().info("✓ Sistema de flores configurables inicializado correctamente");

            // Verificar que se cargó flowers.yml
            int flowerCount = flowerIntegration.getConfigManager().getAllFlowerIds().size();
            getLogger().info("✓ Cargadas " + flowerCount + " flores configurables desde flowers.yml");

        } catch (Exception e) {
            getLogger().severe("❌ Error inicializando sistema de flores configurables:");
            getLogger().severe("Tipo: " + e.getClass().getSimpleName());
            getLogger().severe("Mensaje: " + e.getMessage());
            e.printStackTrace();

            // No deshabilitar el plugin, solo registrar el error
            flowerIntegration = null;
            getLogger().warning("⚠ Sistema de flores configurables deshabilitado");
        }

        RecipeUnlockManager.load();

        initSellWand();
        xpTransferManager = new XpTransferManager(this);
        xpTransferCommand = new XpTransferCommand(this);

        if (!initRankupSystem()) {
            getLogger().warning("Sistema de Rankup no pudo inicializarse - comandos relacionados estarán deshabilitados");
        }

        registerCommands();
        registerListeners();
        hookPlaceholderAPI();
        scheduleBackups();

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
        if (flowerIntegration != null) {
            flowerIntegration.shutdown();
        }

        // 🆕 NUEVO: Limpiar MenuManager del sistema de rankup
        if (rankupManager != null) {
            try {
                rankupManager.shutdown();
                getLogger().info("✅ Sistema de Rankup 2.0 finalizado correctamente");
            } catch (Exception e) {
                getLogger().warning("Error finalizando sistema de Rankup: " + e.getMessage());
            }
        }

        getLogger().info("SurvivalCore deshabilitado.");
    }

    /* =================== INICIALIZACIÓN =================== */

    /**
     * Carga el archivo de configuración de GUIs
     */
    private void loadGuisConfig() {
        guisConfigFile = new File(getDataFolder(), "guis.yml");

        // Si no existe, intentar crearlo desde los recursos
        if (!guisConfigFile.exists()) {
            try {
                // Intentar guardar desde recursos
                saveResource("guis.yml", false);
                getLogger().info("✓ Archivo guis.yml creado desde los recursos");
            } catch (Exception e) {
                // Si no existe en recursos, crear uno básico
                getLogger().warning("No se encontró guis.yml en recursos, creando configuración básica...");
                createDefaultGuisConfig();
            }
        }

        // Cargar la configuración
        guisConfig = YamlConfiguration.loadConfiguration(guisConfigFile);
        getLogger().info("✓ Configuración de GUIs cargada correctamente");
    }

    /**
     * Crea una configuración básica de GUIs si no existe
     */
    private void createDefaultGuisConfig() {
        try {
            if (!guisConfigFile.exists()) {
                guisConfigFile.getParentFile().mkdirs();
                guisConfigFile.createNewFile();

                FileConfiguration config = YamlConfiguration.loadConfiguration(guisConfigFile);

                // Configuración básica para el GUI de género
                config.set("gender_gui.enabled", true);
                config.set("gender_gui.title", "&d&lSelecciona tu Género");
                config.set("gender_gui.size", 27);
                config.set("gender_gui.cooldown_days", 7);

                // Sonidos
                config.set("gender_gui.sounds.open", "UI_BUTTON_CLICK");
                config.set("gender_gui.sounds.select", "ENTITY_PLAYER_LEVELUP");
                config.set("gender_gui.sounds.cooldown", "ENTITY_VILLAGER_NO");

                // Items básicos
                config.set("gender_gui.items.masculino.slot", 11);
                config.set("gender_gui.items.masculino.material", "LIGHT_BLUE_WOOL");
                config.set("gender_gui.items.masculino.name", "&b&lMasculino");

                config.set("gender_gui.items.femenino.slot", 13);
                config.set("gender_gui.items.femenino.material", "PINK_WOOL");
                config.set("gender_gui.items.femenino.name", "&d&lFemenino");

                config.set("gender_gui.items.otro.slot", 15);
                config.set("gender_gui.items.otro.material", "LIME_WOOL");
                config.set("gender_gui.items.otro.name", "&a&lOtro");

                // Configuración básica para el GUI de perfil
                config.set("profile_gui.enabled", true);
                config.set("profile_gui.title", "&6&lPerfil de {player}");
                config.set("profile_gui.size", 54);

                config.save(guisConfigFile);
                getLogger().info("✓ Configuración básica de GUIs creada");
            }
        } catch (Exception e) {
            getLogger().severe("Error creando configuración básica de GUIs: " + e.getMessage());
        }
    }

    /**
     * Recarga la configuración de GUIs
     */
    public void reloadGuisConfig() {
        if (guisConfigFile == null) {
            guisConfigFile = new File(getDataFolder(), "guis.yml");
        }

        // Recargar archivo de configuración
        guisConfig = YamlConfiguration.loadConfiguration(guisConfigFile);

        try {
            // Reinicializar todos los GUIs
            initializeGuis();
            getLogger().info("✓ Configuración de GUIs recargada exitosamente");

        } catch (Exception e) {
            getLogger().severe("❌ Error recargando configuración de GUIs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obtiene la configuración de GUIs
     */
    public FileConfiguration getGuisConfig() {
        if (guisConfig == null) {
            loadGuisConfig();
        }
        return guisConfig;
    }

    /**
     * Guarda la configuración de GUIs
     */
    public void saveGuisConfig() {
        if (guisConfig == null || guisConfigFile == null) return;

        try {
            guisConfig.save(guisConfigFile);
        } catch (Exception e) {
            getLogger().severe("No se pudo guardar guis.yml: " + e.getMessage());
        }
    }

    /**
     * Inicializa los GUIs con la configuración cargada
     */
    private void initializeGuis() {
        try {
            // Inicializar GUI de género
            GeneroGUI.initialize(this);
            getLogger().info("✓ GUI de género inicializado correctamente");

            // Inicializar GUI de perfil
            ProfileGUI.initialize(this);
            getLogger().info("✓ GUI de perfil inicializado correctamente");

            getLogger().info("✓ Todos los GUIs inicializados con configuración personalizada");

        } catch (Exception e) {
            getLogger().severe("❌ Error inicializando GUIs: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
                getLogger().warning("LuckPerms no encontrado - Sistema de Rankup 2.0 deshabilitado");
                getLogger().warning("Para usar el sistema de rankup, instala LuckPerms");
                return false;
            }

            // Verificar si LuckPerms está habilitado
            if (!getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
                getLogger().warning("LuckPerms no está habilitado - Sistema de Rankup 2.0 deshabilitado");
                return false;
            }

            // Intentar inicializar RankupManager 2.0
            rankupManager = new RankupManager(this);
            getLogger().info("✅ Sistema de Rankup 2.0 inicializado correctamente con LuckPerms.");

            // Mostrar estadísticas de carga
            int ranksCount = rankupManager.getRanks().size();
            boolean papiEnabled = rankupManager.isPlaceholderAPIEnabled();

            getLogger().info("📊 Estadísticas de Rankup 2.0:");
            getLogger().info("  • Rangos cargados: " + ranksCount);
            getLogger().info("  • PlaceholderAPI: " + (papiEnabled ? "Disponible" : "No disponible"));
            getLogger().info("  • Efectos: " + (rankupManager.areEffectsEnabled() ? "Habilitados" : "Deshabilitados"));
            getLogger().info("  • Broadcast: " + (rankupManager.isBroadcastEnabled() ? "Habilitado" : "Deshabilitado"));

            return true;

        } catch (Exception e) {
            getLogger().severe("❌ Error crítico al inicializar el sistema de Rankup 2.0:");
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

    private void registerCommands() {
        birthdayCommand = new BirthdayCommand(this);

        registerCommand("repair", new RepairCommand(this));
        registerCommand("reparar", new RepairCommand(this));
        registerCommand("birthday", birthdayCommand);
        registerCommand("perfil", new PerfilCommand(this));
        registerCommand("score", new ScoreCommand(this));
        registerCommand("genero", new GeneroCommand(this));
        registerCommand("lectern", new LecternRecipeCreateCommand(this, lecternRecipeManager));

        // 🔍 DEBUG: Añadir logs para verificar el registro
        getLogger().info("🔍 Registrando comandos de coordenadas...");

        try {
            CoordsCommand coordsCommand = new CoordsCommand(this);

            // Verificar que los comandos existen en plugin.yml
            if (getCommand("coords") != null) {
                registerCommand("coords", coordsCommand);
                getLogger().info("✅ Comando /coords registrado correctamente");
            } else {
                getLogger().severe("❌ Comando 'coords' no encontrado en plugin.yml");
            }

            if (getCommand("coordenadas") != null) {
                registerCommand("coordenadas", coordsCommand);
                getLogger().info("✅ Comando /coordenadas registrado correctamente");
            } else {
                getLogger().severe("❌ Comando 'coordenadas' no encontrado en plugin.yml");
            }

        } catch (Exception e) {
            getLogger().severe("❌ Error registrando comandos de coordenadas: " + e.getMessage());
            e.printStackTrace();
        }

        // Resto de comandos...
        registerCommand("xpgive", xpTransferCommand);
        registerCommand("xptransfers", xpTransferCommand);
        registerCommand("xptransferlog", xpTransferCommand);

        registerCommand("sellwand", new SellWandCommand(this, sellWandManager));
        registerCommand("sw", new SellWandCommand(this, sellWandManager));

        // Comandos de Cheques de XP
        xpChequeCommand = new XpChequeCommand(this);
        registerCommand("cheque", xpChequeCommand);

        registerCommand("xpbank", new XpBankCommand(this));

        // Comandos de macetas y flores mágicas
        MagicFlowerPotCommand magicFlowerPotCommand = new MagicFlowerPotCommand(this);
        registerCommand("flowerpot", magicFlowerPotCommand);
        registerCommand("magicflower", magicFlowerPotCommand);

        // 🆕 COMANDO DE REDES SOCIALES
        registerCommand("social", new SocialCommand(this));

        // 🆕 COMANDOS DE RANKUP 2.0 - Solo registrar si el sistema está disponible
        if (rankupManager != null) {
            RankupCommand rankupCmd = new RankupCommand(this, rankupManager);
            registerCommand("rankup", rankupCmd);
            registerCommand("prestige", rankupCmd);
            registerCommand("ranks", rankupCmd);
            getLogger().info("✅ Comandos de Rankup 2.0 registrados correctamente.");
        } else {
            getLogger().warning("⚠️ Comandos de rankup NO registrados - Sistema no disponible");
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

        // 🆕 LISTENER DE RANKUP 2.0 - Solo registrar si el sistema está disponible
        if (rankupManager != null) {
            pm.registerEvents(new RankupMenuListener(this), this);
            getLogger().info("✅ Listeners de Rankup 2.0 registrados correctamente.");
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
    public ConfigurableFlowerIntegration getFlowerIntegration() { return flowerIntegration;}
    /**
     * Verificar si el sistema de rankup está disponible
     */
    public boolean isRankupSystemEnabled() {
        return rankupManager != null;
    }

    /**
     * Refresca valores que cambian al recargar configuración.
     * VERSIÓN MEJORADA con soporte completo para todos los sistemas incluyendo Rankup 2.0
     */
    public void updateInternalConfig() {
        getLogger().info("🔄 Iniciando actualización de configuración interna...");

        boolean hasErrors = false;
        StringBuilder report = new StringBuilder();

        try {
            // Recargar configuración básica
            this.cropXpChance = getConfig().getDouble("plugin.cropXpChance", this.cropXpChance);
            getLogger().info("✓ Configuración básica actualizada");

            // 🆕 CRÍTICO: Recargar configuración de GUIs PRIMERO
            try {
                getLogger().info("🔄 Recargando configuración de GUIs...");
                reloadGuisConfig(); // Esto recarga el archivo guis.yml

                // Reinicializar los GUIs con la nueva configuración
                initializeGuis(); // Esto aplica los cambios cargados

                getLogger().info("✅ Sistema de GUIs recargado exitosamente");
                report.append(ChatColor.GREEN + "✓ Sistema de GUIs actualizado\n");
            } catch (Exception e) {
                hasErrors = true;
                getLogger().severe("❌ Error recargando GUIs: " + e.getMessage());
                report.append(ChatColor.RED + "✗ Sistema de GUIs: ").append(e.getMessage()).append("\n");
            }

            // Recargar configuración de transferencias
            if (xpTransferManager != null) {
                try {
                    xpTransferManager.reloadConfig();
                    getLogger().info("✓ Configuración de transferencias XP actualizada");
                    report.append(ChatColor.GREEN + "✓ Transferencias XP\n");
                } catch (Exception e) {
                    hasErrors = true;
                    getLogger().warning("Error recargando transferencias XP: " + e.getMessage());
                    report.append(ChatColor.RED + "✗ Transferencias XP: ").append(e.getMessage()).append("\n");
                }
            }

            // Recargar configuración de cheques
            if (xpChequeCommand != null && xpChequeCommand.getChequeManager() != null) {
                try {
                    xpChequeCommand.getChequeManager().reloadConfig();
                    getLogger().info("✓ Configuración de cheques XP actualizada");
                    report.append(ChatColor.GREEN + "✓ Cheques XP\n");
                } catch (Exception e) {
                    hasErrors = true;
                    getLogger().warning("Error recargando cheques XP: " + e.getMessage());
                    report.append(ChatColor.RED + "✗ Cheques XP: ").append(e.getMessage()).append("\n");
                }
            }

            // Recargar configuración de SellWand
            if (sellWandManager != null) {
                try {
                    sellWandManager.reloadConfig();
                    getLogger().info("✓ Configuración de SellWand actualizada");
                    report.append(ChatColor.GREEN + "✓ SellWand\n");
                } catch (Exception e) {
                    hasErrors = true;
                    getLogger().warning("Error recargando SellWand: " + e.getMessage());
                    report.append(ChatColor.RED + "✗ SellWand: ").append(e.getMessage()).append("\n");
                }
            }

            // Recargar configuración de mining si existe
            if (miningConfig != null) {
                try {
                    miningConfig.reload();
                    getLogger().info("✓ Configuración de minería actualizada");
                    report.append(ChatColor.GREEN + "✓ Configuración de minería\n");
                } catch (Exception e) {
                    hasErrors = true;
                    getLogger().warning("Error recargando configuración de minería: " + e.getMessage());
                    report.append(ChatColor.RED + "✗ Configuración de minería: ").append(e.getMessage()).append("\n");
                }
            }

            if (magicFlowerPotManager != null) {
                try {
                    magicFlowerPotManager.forceUpdate();
                    getLogger().info("✓ Configuración de macetas mágicas actualizada");
                    report.append(ChatColor.GREEN + "✓ Macetas mágicas\n");
                } catch (Exception e) {
                    hasErrors = true;
                    getLogger().warning("Error recargando macetas mágicas: " + e.getMessage());
                    report.append(ChatColor.RED + "✗ Macetas mágicas: ").append(e.getMessage()).append("\n");
                }
            }

            // Recargar configuración de crops si existe
            if (cropConfig != null) {
                try {
                    cropConfig.reload();
                    getLogger().info("✓ Configuración de cultivos actualizada");
                    report.append(ChatColor.GREEN + "✓ Configuración de cultivos\n");
                } catch (Exception e) {
                    hasErrors = true;
                    getLogger().warning("Error recargando configuración de cultivos: " + e.getMessage());
                    report.append(ChatColor.RED + "✗ Configuración de cultivos: ").append(e.getMessage()).append("\n");
                }
            }

            // SISTEMA DE FLORES CONFIGURABLES
            if (flowerIntegration != null) {
                try {
                    getLogger().info("✓ Sistema de flores configurables verificado");
                    report.append(ChatColor.GREEN + "✓ Flores configurables\n");
                } catch (Exception e) {
                    hasErrors = true;
                    getLogger().warning("Error verificando sistema de flores: " + e.getMessage());
                    report.append(ChatColor.RED + "✗ Flores configurables: ").append(e.getMessage()).append("\n");
                }
            }

            // SISTEMA DE RANKUP SIMPLIFICADO - Sin híbrido
            if (rankupManager != null) {
                try {
                    getLogger().info("🔄 Recargando configuración de Rankup 2.0...");
                    rankupManager.reloadConfig();

                    // Mostrar estadísticas actualizadas
                    int ranksCount = rankupManager.getRanks().size();
                    boolean papiEnabled = rankupManager.isPlaceholderAPIEnabled();

                    getLogger().info("✅ Configuración de Rankup 2.0 actualizada");
                    getLogger().info("  • Rangos: " + ranksCount);
                    getLogger().info("  • PlaceholderAPI: " + (papiEnabled ? "Disponible" : "No disponible"));
                    getLogger().info("  • Cooldown: " + (rankupManager.getCooldownTime() / 1000) + "s");
                    getLogger().info("  • Efectos: " + (rankupManager.areEffectsEnabled() ? "Habilitados" : "Deshabilitados"));

                    report.append(ChatColor.GREEN + "✓ Sistema de Rankup 2.0\n");

                } catch (Exception e) {
                    hasErrors = true;
                    getLogger().severe("❌ Error recargando configuración de Rankup 2.0: " + e.getMessage());
                    e.printStackTrace();
                    report.append(ChatColor.RED + "✗ Sistema de Rankup 2.0: ").append(e.getMessage()).append("\n");
                }
            } else {
                getLogger().info("ℹ️ Sistema de Rankup 2.0 no está disponible");
                report.append(ChatColor.GRAY + "- Sistema de Rankup: No disponible\n");
            }

            // Mostrar reporte final si hay contenido
            if (report.length() > 0) {
                getLogger().info("📊 Reporte de actualización:");
                getLogger().info(ChatColor.stripColor(report.toString()));
            }

            if (hasErrors) {
                getLogger().warning("⚠️ Configuración interna actualizada con advertencias");
                getLogger().warning("Revisa los logs anteriores para más detalles");
            } else {
                getLogger().info("✅ Configuración interna actualizada correctamente.");
            }

        } catch (Exception e) {
            getLogger().severe("❌ Error crítico actualizando configuración interna: " + e.getMessage());
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

    /**
     * Método auxiliar para recargar solo el sistema de Rankup
     * Útil para comandos específicos de recarga de rankup
     */
    public void reloadRankupSystem() {
        if (!isRankupSystemEnabled()) {
            getLogger().warning("⚠️ Sistema de Rankup no está disponible para recarga");
            return;
        }

        try {
            getLogger().info("🔄 Recargando solo el sistema de Rankup 2.0...");

            long startTime = System.currentTimeMillis();
            rankupManager.reloadConfig();
            long duration = System.currentTimeMillis() - startTime;

            // Estadísticas detalladas
            int ranksCount = rankupManager.getRanks().size();
            int prestigesCount = rankupManager.getPrestiges().size();

            getLogger().info("✅ Sistema de Rankup 2.0 recargado exitosamente en " + duration + "ms");
            getLogger().info("📊 Estadísticas:");
            getLogger().info("  • Rangos activos: " + ranksCount);
            getLogger().info("  • Prestiges: " + prestigesCount);
            getLogger().info("  • PlaceholderAPI: " + (rankupManager.isPlaceholderAPIEnabled() ? "✓" : "✗"));

        } catch (Exception e) {
            getLogger().severe("❌ Error crítico recargando solo el sistema de Rankup:");
            getLogger().severe("Tipo: " + e.getClass().getSimpleName());
            getLogger().severe("Mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Verifica el estado de todos los sistemas después de una recarga
     */
    public Map<String, String> getSystemsStatus() {
        Map<String, String> status = new LinkedHashMap<>();

        // Sistemas básicos
        status.put("DatabaseManager", databaseManager != null ? "OK" : "NULL");
        status.put("CooldownManager", cooldownManager != null ? "OK" : "NULL");
        status.put("CropConfig", cropConfig != null ? "OK" : "NULL");
        status.put("MiningConfig", miningConfig != null ? "OK" : "NULL");

        // Managers avanzados
        status.put("XpTransferManager", xpTransferManager != null ? "OK" : "NULL");
        status.put("SellWandManager", sellWandManager != null ? "OK" : "NULL");
        status.put("XpChequeManager", xpChequeCommand != null ? "OK" : "NULL");
        status.put("LecternRecipeManager", lecternRecipeManager != null ? "OK" : "NULL");
        status.put("MagicFlowerPotManager", magicFlowerPotManager != null ? "OK" : "NULL");
        status.put("FlowerIntegration", flowerIntegration != null ? "OK" : "NULL");

        // Sistema de Rankup
        if (rankupManager != null) {
            try {
                int ranksCount = rankupManager.getRanks().size();
                boolean papiEnabled = rankupManager.isPlaceholderAPIEnabled();
                boolean menuSystemEnabled = rankupManager.isMenuSystemAvailable();

                StringBuilder rankupStatus = new StringBuilder();
                rankupStatus.append("OK (").append(ranksCount).append(" rangos, ");
                rankupStatus.append("PAPI: ").append(papiEnabled ? "✓" : "✗").append(", ");
                rankupStatus.append("Menús: ").append(menuSystemEnabled ? "✓" : "✗").append(")");

                status.put("RankupManager", rankupStatus.toString());

                // Detalles del sistema de menús si está disponible
                if (menuSystemEnabled) {
                    Map<String, Object> menuStats = rankupManager.getMenuStats();
                    if (menuStats != null) {
                        status.put("MenuSystem", "OK (" +
                                "Cache: " + menuStats.get("cachedMenus") + ", " +
                                "Players: " + menuStats.get("playerSettings") + ")");
                    }
                } else {
                    status.put("MenuSystem", "BASIC (Solo comandos)");
                }

            } catch (Exception e) {
                status.put("RankupManager", "ERROR: " + e.getMessage());
                status.put("MenuSystem", "ERROR: " + e.getMessage());
            }
        } else {
            status.put("RankupManager", "NULL (LuckPerms requerido)");
            status.put("MenuSystem", "NULL");
        }

        // Base de datos
        try {
            databaseManager.testConnection();
            status.put("Database Connection", "OK");
        } catch (Exception e) {
            status.put("Database Connection", "ERROR: " + e.getMessage());
        }

        // Plugins externos
        status.put("LuckPerms", getPluginStatus("LuckPerms"));
        status.put("PlaceholderAPI", getPluginStatus("PlaceholderAPI"));
        status.put("Vault", getPluginStatus("Vault"));

        return status;
    }

    /**
     * Obtiene el estado de un plugin externo
     */
    private String getPluginStatus(String pluginName) {
        var pluginManager = getServer().getPluginManager();
        var targetPlugin = pluginManager.getPlugin(pluginName);

        if (targetPlugin == null) {
            return "NO INSTALADO";
        } else if (targetPlugin.isEnabled()) {
            return "HABILITADO (" + targetPlugin.getDescription().getVersion() + ")";
        } else {
            return "DESHABILITADO";
        }
    }

    public void debugMenuSystemDetailed(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "═══ DEBUG SISTEMA BÁSICO ═══");

        Map<String, Object> info = getMenuSystemInfo();

        String status = (String) info.get("status");
        String description = (String) info.get("description");

        sender.sendMessage(ChatColor.WHITE + "Estado: " + getStatusColor(status) + status);
        sender.sendMessage(ChatColor.WHITE + "Descripción: " + ChatColor.GRAY + description);

        if ("BASIC_COMMANDS".equals(status)) {
            sender.sendMessage(ChatColor.GREEN + "✅ Sistema básico funcionando correctamente");

            // Mostrar estadísticas
            sender.sendMessage(ChatColor.YELLOW + "Información del sistema:");
            sender.sendMessage(ChatColor.WHITE + "  • Tipo: " + ChatColor.YELLOW + "Comandos de texto");
            sender.sendMessage(ChatColor.WHITE + "  • Rangos disponibles: " + ChatColor.YELLOW + info.get("ranksAvailable"));
            sender.sendMessage(ChatColor.WHITE + "  • PlaceholderAPI: " +
                    (((Boolean) info.getOrDefault("placeholderAPI", false)) ?
                            ChatColor.GREEN + "Disponible" : ChatColor.RED + "No disponible"));

        } else if ("RANKUP_SYSTEM_DISABLED".equals(status)) {
            sender.sendMessage(ChatColor.RED + "❌ Sistema de rankup completamente deshabilitado");
            sender.sendMessage(ChatColor.GRAY + "Instala y configura LuckPerms para habilitar el sistema");

        } else {
            sender.sendMessage(ChatColor.RED + "❌ Error en el sistema");
            String error = (String) info.get("error");
            if (error != null) {
                sender.sendMessage(ChatColor.RED + "Error: " + error);
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Comandos disponibles:");
        sender.sendMessage(ChatColor.GRAY + "  • /rankup - Intentar hacer rankup");
        sender.sendMessage(ChatColor.GRAY + "  • /rankup progress - Ver progreso");
        sender.sendMessage(ChatColor.GRAY + "  • /rankup list - Ver rangos");
        sender.sendMessage(ChatColor.GRAY + "  • /ranks - Menú básico de texto");
    }
    /**
     * Método de emergencia para reinicializar sistemas críticos
     * Útil si algo falla durante la recarga
     */
    public boolean emergencySystemRestart() {
        getLogger().warning("🚨 Iniciando reinicio de emergencia de sistemas...");

        boolean allSuccess = true;

        try {
            // Reinicializar cooldown manager
            if (cooldownManager == null) {
                cooldownManager = new CooldownManager(getConfig().getLong("plugin.cooldownMs", 5000));
                getLogger().info("✓ CooldownManager reinicializado");
            }

            // Reinicializar configuraciones
            if (cropConfig == null) {
                cropConfig = new CropExperienceConfig(this);
                getLogger().info("✓ CropExperienceConfig reinicializado");
            }

            if (miningConfig == null) {
                miningConfig = new MiningExperienceConfig(this);
                getLogger().info("✓ MiningExperienceConfig reinicializado");
            }

            // Verificar sistema de rankup
            if (rankupManager != null) {
                try {
                    boolean hadMenuSystem = rankupManager.isMenuSystemAvailable();

                    // Intentar reinicializar MenuManager si no está disponible
                    if (!hadMenuSystem) {
                        getLogger().info("🔄 Intentando reinicializar MenuManager...");

                        // El MenuManager se inicializa automáticamente en RankupManager
                        // Si falló antes, intentamos recargar la configuración completa
                        rankupManager.reloadConfig();

                        if (rankupManager.isMenuSystemAvailable()) {
                            getLogger().info("✓ MenuManager reinicializado exitosamente");
                        } else {
                            getLogger().warning("⚠️ MenuManager sigue no disponible - funcionará en modo básico");
                            allSuccess = false;
                        }
                    } else {
                        getLogger().info("✓ MenuManager ya estaba funcionando correctamente");
                    }

                } catch (Exception e) {
                    getLogger().severe("❌ Error reinicializando MenuManager: " + e.getMessage());
                    allSuccess = false;
                }
            }

            // Verificar base de datos
            try {
                databaseManager.testConnection();
                getLogger().info("✓ Conexión a base de datos verificada");
            } catch (Exception e) {
                getLogger().severe("❌ Problema con base de datos: " + e.getMessage());
                allSuccess = false;
            }

        } catch (Exception e) {
            getLogger().severe("❌ Error crítico en reinicio de emergencia: " + e.getMessage());
            allSuccess = false;
        }

        if (allSuccess) {
            getLogger().info("✅ Reinicio de emergencia completado exitosamente");
        } else {
            getLogger().warning("⚠️ Reinicio de emergencia completado con errores");
        }

        return allSuccess;
    }

    /**
     * Verifica si LuckPerms está disponible
     */
    private boolean isLuckPermsAvailable() {
        var luckPermsPlugin = getServer().getPluginManager().getPlugin("LuckPerms");
        return luckPermsPlugin != null && luckPermsPlugin.isEnabled();
    }

    /**
     * Obtiene información detallada sobre el sistema de menús
     * MÉTODO YA IMPLEMENTADO en Main.java - verificar que esté presente
     */
    /**
     * Obtiene información del sistema básico (sin menús híbridos)
     */
    public Map<String, Object> getMenuSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        try {
            if (!isRankupSystemEnabled()) {
                info.put("status", "RANKUP_SYSTEM_DISABLED");
                info.put("description", "Sistema de rankup no disponible");
                return info;
            }

            // Sistema básico siempre disponible
            info.put("status", "BASIC_COMMANDS");
            info.put("description", "Sistema básico de comandos disponible");
            info.put("menuType", "TEXT_BASED");
            info.put("hybridSystem", false);

            // Estadísticas básicas
            int ranksCount = rankupManager.getRanks().size();
            info.put("ranksAvailable", ranksCount);
            info.put("placeholderAPI", rankupManager.isPlaceholderAPIEnabled());

        } catch (Exception e) {
            info.put("status", "ERROR");
            info.put("description", "Error obteniendo información");
            info.put("error", e.getMessage());
        }

        return info;
    }
    /**
     * Debug específico del sistema de menús
     * AÑADIR este método público a Main.java
     */
    public void debugMenuSystem(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "═══ DEBUG SISTEMA DE MENÚS ═══");

        Map<String, Object> menuInfo = getMenuSystemInfo();

        String status = (String) menuInfo.get("status");
        String description = (String) menuInfo.get("description");

        sender.sendMessage(ChatColor.WHITE + "Estado: " + getStatusColor(status) + status);
        sender.sendMessage(ChatColor.WHITE + "Descripción: " + ChatColor.GRAY + description);

        if ("AVAILABLE".equals(status)) {
            sender.sendMessage(ChatColor.GREEN + "✅ Sistema de menús completamente funcional");

            // Mostrar estadísticas
            sender.sendMessage(ChatColor.YELLOW + "Estadísticas:");
            sender.sendMessage(ChatColor.WHITE + "  • Menús en caché: " + ChatColor.YELLOW + menuInfo.get("cachedMenus"));
            sender.sendMessage(ChatColor.WHITE + "  • Configuraciones de jugador: " + ChatColor.YELLOW + menuInfo.get("playerSettings"));
            sender.sendMessage(ChatColor.WHITE + "  • Auto-refresh: " + ChatColor.YELLOW +
                    (((Integer) menuInfo.getOrDefault("autoRefreshInterval", 0)) > 0 ? "Habilitado" : "Deshabilitado"));

            // Información del archivo
            boolean configExists = (Boolean) menuInfo.getOrDefault("config_file_exists", false);
            sender.sendMessage(ChatColor.WHITE + "  • Archivo de config: " +
                    (configExists ? ChatColor.GREEN + "EXISTS" : ChatColor.RED + "NOT FOUND"));

            if (configExists) {
                long size = (Long) menuInfo.getOrDefault("config_file_size", 0L);
                sender.sendMessage(ChatColor.WHITE + "  • Tamaño del archivo: " + ChatColor.YELLOW + size + " bytes");
            }

        } else if ("BASIC_MODE".equals(status)) {
            sender.sendMessage(ChatColor.YELLOW + "⚠️ Funcionando en modo básico");
            sender.sendMessage(ChatColor.GRAY + "Solo comandos /rankup, /rankup progress, etc. están disponibles");
            sender.sendMessage(ChatColor.GRAY + "Los menús interactivos no están disponibles");

        } else if ("RANKUP_SYSTEM_DISABLED".equals(status)) {
            sender.sendMessage(ChatColor.RED + "❌ Sistema de rankup completamente deshabilitado");
            sender.sendMessage(ChatColor.GRAY + "Instala y configura LuckPerms para habilitar el sistema");

        } else {
            sender.sendMessage(ChatColor.RED + "❌ Error en el sistema de menús");
            String error = (String) menuInfo.get("error");
            if (error != null) {
                sender.sendMessage(ChatColor.RED + "Error: " + error);
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Comandos útiles:");
        sender.sendMessage(ChatColor.GRAY + "  • /score reload - Recargar configuración completa");
        sender.sendMessage(ChatColor.GRAY + "  • /score reloadrankup - Recargar solo rankup y menús");
        sender.sendMessage(ChatColor.GRAY + "  • /score emergency - Reinicio de emergencia");
    }


    /**
     * Obtiene color según el estado
     * MÉTODO YA IMPLEMENTADO en Main.java - verificar que esté presente
     */
    private String getStatusColor(String status) {
        return switch (status) {
            case "AVAILABLE" -> ChatColor.GREEN.toString();
            case "BASIC_MODE" -> ChatColor.YELLOW.toString();
            case "RANKUP_SYSTEM_DISABLED" -> ChatColor.RED.toString();
            case "ERROR" -> ChatColor.DARK_RED.toString();
            default -> ChatColor.GRAY.toString();
        };
    }
}