package gc.grivyzom.survivalcore.rankup.menu;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.rankup.RankupManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestor de menús híbrido que detecta automáticamente el tipo de cliente
 * y muestra menús de Bedrock o Java según corresponda
 *
 * @author Brocolitx
 * @version 1.0
 */
public class BedrockMenuManager {

    private final Main plugin;
    private final RankupManager rankupManager;
    private final MenuManager javaMenuManager;

    // Cache para detección de clientes
    private final Map<UUID, ClientType> clientTypeCache = new HashMap<>();

    // Reflexión para BedrockGUI API
    private Object bedrockGuiApi;
    private Method openMenuMethod;
    private Method addMenuMethod;
    private Method createMenuBuilderMethod;
    private Class<?> formPlayerClass;
    private Class<?> formMenuTypeClass;
    private boolean bedrockGuiAvailable = false;

    // Estados de menús registrados
    private boolean bedrockMenusRegistered = false;

    public BedrockMenuManager(Main plugin, RankupManager rankupManager, MenuManager javaMenuManager) {
        this.plugin = plugin;
        this.rankupManager = rankupManager;
        this.javaMenuManager = javaMenuManager;

        initBedrockGUI();

        if (bedrockGuiAvailable) {
            registerBedrockMenus();
        }
    }

    /**
     * Inicializa la integración con BedrockGUI usando reflexión
     */
    private void initBedrockGUI() {
        try {
            // Verificar si BedrockGUI está disponible
            if (plugin.getServer().getPluginManager().getPlugin("BedrockGUI") == null) {
                plugin.getLogger().info("📱 BedrockGUI no detectado - Solo menús Java disponibles");
                return;
            }

            // Cargar clases usando reflexión
            Class<?> bedrockGuiClass = Class.forName("net.onebeastchris.bedrockgui.BedrockGUI");
            Class<?> apiClass = Class.forName("net.onebeastchris.bedrockgui.api.BedrockGuiAPI");
            formPlayerClass = Class.forName("net.onebeastchris.bedrockgui.api.FormPlayer");
            formMenuTypeClass = Class.forName("net.onebeastchris.bedrockgui.api.FormMenuType");

            // Obtener instancia de API
            Method getInstanceMethod = bedrockGuiClass.getMethod("getInstance");
            Object bedrockGuiInstance = getInstanceMethod.invoke(null);

            Method getApiMethod = bedrockGuiInstance.getClass().getMethod("getApi");
            bedrockGuiApi = getApiMethod.invoke(bedrockGuiInstance);

            // Obtener métodos necesarios
            openMenuMethod = apiClass.getMethod("openMenu", formPlayerClass, String.class, String[].class);
            addMenuMethod = apiClass.getMethod("addMenu", String.class, Class.forName("net.onebeastchris.bedrockgui.api.FormMenu"));
            createMenuBuilderMethod = apiClass.getMethod("createMenuBuilder");

            bedrockGuiAvailable = true;
            plugin.getLogger().info("✅ BedrockGUI integrado correctamente - Menús híbridos disponibles");

        } catch (Exception e) {
            plugin.getLogger().warning("⚠️ Error integrando BedrockGUI: " + e.getMessage());
            plugin.getLogger().info("🔧 Los menús funcionarán solo para clientes Java");
            bedrockGuiAvailable = false;
        }
    }

    /**
     * Registra los menús de Bedrock usando la API
     */
    private void registerBedrockMenus() {
        if (!bedrockGuiAvailable || bedrockMenusRegistered) {
            return;
        }

        try {
            plugin.getLogger().info("📱 Registrando menús de Bedrock...");

            // Registrar callbacks personalizados
            registerBedrockCallbacks();

            // Crear y registrar menú principal
            createMainBedrockMenu();

            // Crear menú de progreso
            createProgressBedrockMenu();

            // Crear menú de confirmación de rankup
            createRankupConfirmMenu();

            bedrockMenusRegistered = true;
            plugin.getLogger().info("✅ Menús de Bedrock registrados correctamente");

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error registrando menús de Bedrock: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Registra callbacks personalizados para acciones de Bedrock
     */
    private void registerBedrockCallbacks() throws Exception {
        // Callback para rankup
        registerCallback("rankup_action", (player, actionValue) -> {
            Player bukkitPlayer = plugin.getServer().getPlayer(getPlayerName(player));
            if (bukkitPlayer != null) {
                performRankup(bukkitPlayer);
            }
        });

        // Callback para ver progreso
        registerCallback("progress_action", (player, actionValue) -> {
            Player bukkitPlayer = plugin.getServer().getPlayer(getPlayerName(player));
            if (bukkitPlayer != null) {
                openProgressMenu(bukkitPlayer);
            }
        });

        // Callback para lista de rangos
        registerCallback("ranks_list_action", (player, actionValue) -> {
            Player bukkitPlayer = plugin.getServer().getPlayer(getPlayerName(player));
            if (bukkitPlayer != null) {
                showRanksList(bukkitPlayer);
            }
        });

        // Callback para volver al menú principal
        registerCallback("back_to_main", (player, actionValue) -> {
            Player bukkitPlayer = plugin.getServer().getPlayer(getPlayerName(player));
            if (bukkitPlayer != null) {
                openMainMenu(bukkitPlayer);
            }
        });
    }

    /**
     * Registra un callback usando reflexión
     */
    private void registerCallback(String name, Object callback) throws Exception {
        Method registerMethod = bedrockGuiApi.getClass().getMethod("registerButtonCallback", String.class,
                Class.forName("java.util.function.BiConsumer"));
        registerMethod.invoke(bedrockGuiApi, name, callback);
    }

    /**
     * Crea el menú principal de Bedrock
     */
    private void createMainBedrockMenu() throws Exception {
        Object builder = createMenuBuilderMethod.invoke(bedrockGuiApi);
        Class<?> builderClass = builder.getClass();

        // Configurar tipo y título
        Object simpleType = getEnumValue(formMenuTypeClass, "SIMPLE");
        Method setTypeMethod = builderClass.getMethod("setType", formMenuTypeClass);
        setTypeMethod.invoke(builder, simpleType);

        Method setTitleMethod = builderClass.getMethod("setTitle", String.class);
        setTitleMethod.invoke(builder, "§5§l⭐ Sistema de Rangos ⭐");

        Method setDescriptionMethod = builderClass.getMethod("setDescription", String.class);
        setDescriptionMethod.invoke(builder, "§7Selecciona una opción para gestionar tu progreso");

        // Añadir botones
        Method addButtonMethod = builderClass.getMethod("addButton", String.class, String.class, Object.class);

        // Botón de Rankup
        addButtonMethod.invoke(builder, "§a⬆ Hacer Rankup", null, createFormAction("rankup_action"));

        // Botón de Progreso
        addButtonMethod.invoke(builder, "§b📊 Ver Mi Progreso", null, createFormAction("progress_action"));

        // Botón de Lista de Rangos
        addButtonMethod.invoke(builder, "§2📋 Lista de Rangos", null, createFormAction("ranks_list_action"));

        // Botón de Información
        addButtonMethod.invoke(builder, "§e📖 Mi Información", null, createFormAction("command rankup info"));

        // Construir y registrar menú
        Method buildMethod = builderClass.getMethod("build");
        Object menu = buildMethod.invoke(builder);

        addMenuMethod.invoke(bedrockGuiApi, "rankup_main", menu);
    }

    /**
     * Crea el menú de progreso para Bedrock
     */
    private void createProgressBedrockMenu() throws Exception {
        Object builder = createMenuBuilderMethod.invoke(bedrockGuiApi);
        Class<?> builderClass = builder.getClass();

        // Configurar como menú modal
        Object modalType = getEnumValue(formMenuTypeClass, "MODAL");
        Method setTypeMethod = builderClass.getMethod("setType", formMenuTypeClass);
        setTypeMethod.invoke(builder, modalType);

        Method setTitleMethod = builderClass.getMethod("setTitle", String.class);
        setTitleMethod.invoke(builder, "§b📊 Tu Progreso");

        Method setDescriptionMethod = builderClass.getMethod("setDescription", String.class);
        setDescriptionMethod.invoke(builder, "§7Ver progreso detallado en chat o abrir menú completo");

        // Botones
        Method addButtonMethod = builderClass.getMethod("addButton", String.class, String.class, Object.class);
        addButtonMethod.invoke(builder, "§a📋 Ver en Chat", null, createFormAction("command rankup progress"));
        addButtonMethod.invoke(builder, "§e⬅ Volver", null, createFormAction("back_to_main"));

        // Construir y registrar
        Method buildMethod = builderClass.getMethod("build");
        Object menu = buildMethod.invoke(builder);
        addMenuMethod.invoke(bedrockGuiApi, "rankup_progress", menu);
    }

    /**
     * Crea menú de confirmación para rankup
     */
    private void createRankupConfirmMenu() throws Exception {
        Object builder = createMenuBuilderMethod.invoke(bedrockGuiApi);
        Class<?> builderClass = builder.getClass();

        Object modalType = getEnumValue(formMenuTypeClass, "MODAL");
        Method setTypeMethod = builderClass.getMethod("setType", formMenuTypeClass);
        setTypeMethod.invoke(builder, modalType);

        Method setTitleMethod = builderClass.getMethod("setTitle", String.class);
        setTitleMethod.invoke(builder, "§6Confirmar Rankup");

        Method setDescriptionMethod = builderClass.getMethod("setDescription", String.class);
        setDescriptionMethod.invoke(builder, "§7¿Estás seguro de que quieres intentar hacer rankup?");

        Method addButtonMethod = builderClass.getMethod("addButton", String.class, String.class, Object.class);
        addButtonMethod.invoke(builder, "§a✓ Sí, hacer rankup", null, createFormAction("command rankup"));
        addButtonMethod.invoke(builder, "§c✗ Cancelar", null, createFormAction("back_to_main"));

        Method buildMethod = builderClass.getMethod("build");
        Object menu = buildMethod.invoke(builder);
        addMenuMethod.invoke(bedrockGuiApi, "rankup_confirm", menu);
    }

    /**
     * Abre el menú principal detectando automáticamente el tipo de cliente
     */
    public void openMainMenu(Player player) {
        ClientType clientType = detectClientType(player);

        switch (clientType) {
            case BEDROCK -> {
                if (bedrockGuiAvailable) {
                    openBedrockMenu(player, "rankup_main");
                } else {
                    // Fallback a Java si Bedrock no está disponible
                    javaMenuManager.openMainMenu(player);
                }
            }
            case JAVA -> javaMenuManager.openMainMenu(player);
            case UNKNOWN -> {
                // Por defecto usar Java
                player.sendMessage(ChatColor.YELLOW + "🔍 Detectando tipo de cliente...");
                javaMenuManager.openMainMenu(player);
            }
        }
    }

    /**
     * Abre el menú de progreso
     */
    public void openProgressMenu(Player player) {
        ClientType clientType = detectClientType(player);

        switch (clientType) {
            case BEDROCK -> {
                if (bedrockGuiAvailable) {
                    openBedrockMenu(player, "rankup_progress");
                } else {
                    javaMenuManager.openProgressMenu(player);
                }
            }
            case JAVA -> javaMenuManager.openProgressMenu(player);
            case UNKNOWN -> javaMenuManager.openProgressMenu(player);
        }
    }

    /**
     * Detecta el tipo de cliente del jugador
     */
    private ClientType detectClientType(Player player) {
        UUID uuid = player.getUniqueId();

        // Verificar cache
        if (clientTypeCache.containsKey(uuid)) {
            return clientTypeCache.get(uuid);
        }

        ClientType detected = detectClientTypeInternal(player);
        clientTypeCache.put(uuid, detected);

        return detected;
    }

    /**
     * Detecta internamente el tipo de cliente usando varios métodos
     */
    private ClientType detectClientTypeInternal(Player player) {
        try {
            // Método 1: Verificar si BedrockGUI puede detectar al jugador
            if (bedrockGuiAvailable) {
                if (isBedrockPlayer(player)) {
                    return ClientType.BEDROCK;
                }
            }

            // Método 2: Verificar por nombre de usuario (pattern de Bedrock)
            String playerName = player.getName();
            if (playerName.matches("^[a-zA-Z0-9_]{1,16}$") && playerName.length() <= 16) {
                // Podría ser Java, verificar más
                if (playerName.contains(" ") || playerName.length() > 16) {
                    return ClientType.BEDROCK;
                }
            }

            // Método 3: Verificar por IP/conexión (si está disponible)
            String address = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";

            // Método 4: Por defecto asumir Java
            return ClientType.JAVA;

        } catch (Exception e) {
            plugin.getLogger().warning("Error detectando tipo de cliente para " + player.getName() + ": " + e.getMessage());
            return ClientType.UNKNOWN;
        }
    }

    /**
     * Verifica si un jugador es de Bedrock usando BedrockGUI
     */
    private boolean isBedrockPlayer(Player player) {
        try {
            // Usar la API de BedrockGUI para verificar
            Method getFormPlayerMethod = bedrockGuiApi.getClass().getMethod("getFormPlayer", Player.class);
            Object formPlayer = getFormPlayerMethod.invoke(bedrockGuiApi, player);

            return formPlayer != null;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Abre un menú de Bedrock específico
     */
    private void openBedrockMenu(Player player, String menuId) {
        try {
            Object formPlayer = getFormPlayer(player);
            if (formPlayer != null) {
                openMenuMethod.invoke(bedrockGuiApi, formPlayer, menuId, new String[0]);
                player.sendMessage(ChatColor.GREEN + "📱 Menú de Bedrock abierto");
            } else {
                // Fallback a Java
                plugin.getLogger().warning("No se pudo obtener FormPlayer para " + player.getName() + ", usando menú Java");
                javaMenuManager.openMainMenu(player);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error abriendo menú de Bedrock: " + e.getMessage());
            // Fallback a Java
            javaMenuManager.openMainMenu(player);
        }
    }

    /**
     * Obtiene un FormPlayer de la API de BedrockGUI
     */
    private Object getFormPlayer(Player player) throws Exception {
        Method getFormPlayerMethod = bedrockGuiApi.getClass().getMethod("getFormPlayer", Player.class);
        return getFormPlayerMethod.invoke(bedrockGuiApi, player);
    }

    /**
     * Obtiene el nombre de un FormPlayer
     */
    private String getPlayerName(Object formPlayer) {
        try {
            Method getNameMethod = formPlayer.getClass().getMethod("getName");
            return (String) getNameMethod.invoke(formPlayer);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Crea una FormAction usando reflexión
     */
    private Object createFormAction(String action) throws Exception {
        Class<?> formActionClass = Class.forName("net.onebeastchris.bedrockgui.api.FormAction");
        return formActionClass.getConstructor(String.class, Class.forName("java.util.function.BiConsumer"))
                .newInstance(action, null);
    }

    /**
     * Obtiene un valor enum usando reflexión
     */
    private Object getEnumValue(Class<?> enumClass, String valueName) throws Exception {
        Method valueOfMethod = enumClass.getMethod("valueOf", String.class);
        return valueOfMethod.invoke(null, valueName);
    }

    /**
     * Realiza rankup para jugadores de Bedrock
     */
    private void performRankup(Player player) {
        if (rankupManager.isOnCooldown(player.getUniqueId())) {
            long remaining = rankupManager.getRemainingCooldown(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "⏰ Espera " + (remaining / 1000) + " segundos");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "🔄 Procesando rankup...");

        rankupManager.attemptRankup(player).thenAccept(result -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    player.sendMessage(ChatColor.GREEN + "🎉 ¡Rankup exitoso!");
                    player.sendMessage(result.getMessage());
                } else {
                    player.sendMessage(ChatColor.RED + "❌ Rankup no disponible");
                    player.sendMessage(result.getMessage());
                }
            });
        });
    }

    /**
     * Muestra la lista de rangos para jugadores de Bedrock
     */
    private void showRanksList(Player player) {
        player.performCommand("rankup list");
    }

    /**
     * Limpia la cache de un jugador
     */
    public void cleanupPlayer(Player player) {
        clientTypeCache.remove(player.getUniqueId());
        javaMenuManager.cleanupPlayer(player);
    }

    /**
     * Verifica si el sistema híbrido está disponible
     */
    public boolean isHybridSystemAvailable() {
        return bedrockGuiAvailable && javaMenuManager != null;
    }

    /**
     * Obtiene estadísticas del sistema híbrido
     */
    public Map<String, Object> getHybridStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("bedrockGuiAvailable", bedrockGuiAvailable);
        stats.put("bedrockMenusRegistered", bedrockMenusRegistered);
        stats.put("cachedClientTypes", clientTypeCache.size());

        // Estadísticas por tipo de cliente
        long bedrockPlayers = clientTypeCache.values().stream()
                .mapToLong(type -> type == ClientType.BEDROCK ? 1 : 0)
                .sum();
        long javaPlayers = clientTypeCache.values().stream()
                .mapToLong(type -> type == ClientType.JAVA ? 1 : 0)
                .sum();

        stats.put("detectedBedrockPlayers", bedrockPlayers);
        stats.put("detectedJavaPlayers", javaPlayers);

        if (javaMenuManager != null) {
            Map<String, Object> javaStats = javaMenuManager.getMenuStats();
            stats.put("javaMenuStats", javaStats);
        }

        return stats;
    }

    /**
     * Fuerza la detección de un jugador
     */
    public ClientType forceDetectClient(Player player) {
        clientTypeCache.remove(player.getUniqueId());
        return detectClientType(player);
    }

    /**
     * Enum para tipos de cliente
     */
    public enum ClientType {
        JAVA,
        BEDROCK,
        UNKNOWN
    }
}
