package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPot;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotManager;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotData;
import gc.grivyzom.survivalcore.flowers.MagicFlowerFactory;
import gc.grivyzom.survivalcore.flowers.config.ConfigurableFlowerFactory;
import gc.grivyzom.survivalcore.flowers.config.FlowerConfigManager;
import gc.grivyzom.survivalcore.flowers.config.FlowerDefinition;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.data.BlockData;
import java.util.Collection;

/**
 * Listener que maneja todos los eventos relacionados con las Macetas Mágicas
 * CORREGIDO v1.4 - Solucionado problema de importaciones conflictivas de FlowerPot
 *
 * @author Brocolitx
 * @version 1.4
 */
public class MagicFlowerPotListener implements Listener {

    private final Main plugin;
    private final MagicFlowerPot potFactory;
    private final MagicFlowerPotManager potManager;
    private final MagicFlowerFactory flowerFactory;
    private final NamespacedKey isMagicPotKey;
    private final NamespacedKey isMagicFlowerKey;

    // Permisos
    private static final String PERM_USE = "survivalcore.flowerpot.use";
    private static final String PERM_PLACE = "survivalcore.flowerpot.place";
    private static final String PERM_BREAK = "survivalcore.flowerpot.break";

    // Configuración de distancia mínima
    private static final int MIN_DISTANCE_BETWEEN_POTS = 2;

    public MagicFlowerPotListener(Main plugin) {
        this.plugin = plugin;
        this.potFactory = new MagicFlowerPot(plugin);
        this.potManager = plugin.getMagicFlowerPotManager();
        this.flowerFactory = new MagicFlowerFactory(plugin);
        this.isMagicPotKey = new NamespacedKey(plugin, "is_magic_flowerpot");
        this.isMagicFlowerKey = new NamespacedKey(plugin, "is_magic_flower");
    }

    /**
     * Maneja la colocación de macetas mágicas
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMagicFlowerPotPlace(BlockPlaceEvent event) {
        ItemStack placedItem = event.getItemInHand();
        Player player = event.getPlayer();

        // Verificar si es una maceta mágica
        if (!potFactory.isMagicFlowerPot(placedItem)) {
            return;
        }

        // Verificar permisos
        if (!player.hasPermission(PERM_PLACE)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "No tienes permisos para colocar Macetas Mágicas.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            return;
        }

        Block placedBlock = event.getBlock();
        Location location = placedBlock.getLocation();

        // Verificar que se puede colocar en esa ubicación
        if (!canPlacePotAt(location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "No puedes colocar una Maceta Mágica aquí.");
            player.sendMessage(ChatColor.GRAY + "Debe estar en una superficie sólida y tener espacio libre arriba.");
            playErrorEffects(location);
            return;
        }

        // 🔧 CORRECCIÓN PRINCIPAL: Verificación mejorada de distancia
        if (!isValidDistanceImproved(location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "No puedes colocar una Maceta Mágica tan cerca de otra.");
            player.sendMessage(ChatColor.GRAY + "Debe estar al menos a " + MIN_DISTANCE_BETWEEN_POTS + " bloques de distancia de otras macetas mágicas.");
            playErrorEffects(location);
            return;
        }

        // Verificar límite de macetas por jugador (si está configurado)
        if (hasReachedPotLimit(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Has alcanzado el límite de Macetas Mágicas que puedes colocar.");
            playErrorEffects(location);
            return;
        }

        // Permitir la colocación y registrar la maceta
        String potId = potFactory.getPotId(placedItem);
        int level = potFactory.getPotLevel(placedItem);
        String flowerId = potFactory.getContainedFlower(placedItem);

        // Copiar metadatos a la maceta colocada
        BlockState state = placedBlock.getState();
        if (state instanceof TileState tileState) {
            copyMetadataToBlock(placedItem, tileState);
            tileState.update(true);
        }

        // Registrar en el manager
        potManager.registerPot(location, potId, level, flowerId);

        // Efectos de colocación mejorados
        playEnhancedPlacementEffects(location, level, player);

        // Mensaje de confirmación
        player.sendMessage(ChatColor.GREEN + "✓ Maceta Mágica colocada correctamente.");
        player.sendMessage(ChatColor.GRAY + "Nivel: " + ChatColor.AQUA + level +
                ChatColor.GRAY + " | Rango: " + ChatColor.GREEN + (3 + (level - 1) * 2) + " bloques");

        if (!flowerId.equals("none")) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 Contiene: " + getFlowerDisplayName(flowerId));
        }
    }

    /**
     * 🔧 CORREGIDO: Maneja la rotura de macetas mágicas - SIN conservar metadatos
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMagicFlowerPotBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block.getType() != Material.FLOWER_POT) {
            return;
        }

        Location location = block.getLocation();
        Player player = event.getPlayer();

        if (!potManager.hasPotAt(location)) {
            return;
        }

        if (!player.hasPermission(PERM_BREAK)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "No tienes permisos para romper Macetas Mágicas.");
            return;
        }

        MagicFlowerPotData potData = potManager.getPotData(location);
        if (potData == null) {
            return;
        }

        // 🔧 CORRECCIÓN: Limpiar visual antes de desregistrar
        updatePotVisual(location, "none", false);

        // Desregistrar inmediatamente
        potManager.unregisterPot(location);
        cleanupPotLocation(location);

        // Cancelar drops vanilla
        event.setDropItems(false);

        // Crear maceta VACÍA
        ItemStack magicPot = potFactory.createMagicFlowerPot(potData.getLevel());
        block.getWorld().dropItemNaturally(location.add(0.5, 0.5, 0.5), magicPot);

        // Si tenía flor, dropear la flor MÁGICA por separado
        if (potData.hasFlower()) {
            ItemStack magicFlowerItem = createMagicFlowerFromId(potData.getFlowerId(), getStoredFlowerLevel(potData));
            if (magicFlowerItem != null) {
                block.getWorld().dropItemNaturally(location, magicFlowerItem);
            }
        }

        // Efectos y mensajes
        playBreakEffects(location, potData.hasFlower());

        player.sendMessage(ChatColor.YELLOW + "⚡ Maceta Mágica recogida correctamente.");
        if (potData.hasFlower()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 La flor mágica también ha sido devuelta por separado.");
        }

        plugin.getLogger().info(String.format("Maceta rota: %s (nivel %d) - ID: %s - %s",
                player.getName(), potData.getLevel(), potData.getPotId(),
                potData.hasFlower() ? "con " + potData.getFlowerId() : "vacía"));
    }

    /**
     * 🔧 CORREGIDO: Maneja la interacción - sin duplicación de flores
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMagicFlowerPotInteract(PlayerInteractEvent event) {
        // Solo mano principal y clic derecho
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FLOWER_POT) return;

        Location location = block.getLocation();
        Player player = event.getPlayer();

        // Verificar si es una maceta mágica
        if (!potManager.hasPotAt(location)) {
            return;
        }

        // Verificar permisos
        if (!player.hasPermission(PERM_USE)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "No tienes permisos para usar Macetas Mágicas.");
            return;
        }

        event.setCancelled(true);

        MagicFlowerPotData potData = potManager.getPotData(location);
        if (potData == null) return;

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Si no tiene nada en la mano, mostrar información
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            showPotInfo(player, potData);
            return;
        }

        // Verificación estricta: Solo flores mágicas
        if (isMagicFlower(itemInHand)) {
            handleFlowerPlanting(player, location, potData, itemInHand);
            return;
        }

        // Bloquear flores normales
        if (isNormalFlower(itemInHand)) {
            player.sendMessage(ChatColor.RED + "❌ Esta maceta solo acepta flores mágicas especiales.");
            player.sendMessage(ChatColor.GRAY + "Las flores normales no tienen propiedades mágicas.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            return;
        }

        // Si hace clic con otros ítems, mostrar información
        showPotInfo(player, potData);
    }

    /**
     * 🆕 NUEVO: Limpia ubicaciones inválidas de macetas fantasma
     */
    private void cleanupInvalidPotLocations() {
        Collection<MagicFlowerPotData> activePots = potManager.getAllActivePots();
        java.util.List<Location> toRemove = new java.util.ArrayList<>();

        for (MagicFlowerPotData potData : activePots) {
            Location location = potData.getLocation();
            Block block = location.getBlock();

            // Si el bloque ya no es una maceta, marcarlo para eliminación
            if (block.getType() != Material.FLOWER_POT) {
                toRemove.add(location);
            }
        }

        // Remover macetas fantasma
        for (Location location : toRemove) {
            potManager.unregisterPot(location);
            plugin.getLogger().info("Maceta fantasma removida de: " + formatLocation(location));
        }

        if (!toRemove.isEmpty()) {
            plugin.getLogger().info("Limpieza completada: " + toRemove.size() + " macetas fantasma eliminadas");
        }
    }

    /**
     * 🆕 NUEVO: Formatea una ubicación para logging
     */
    private String formatLocation(Location location) {
        return String.format("%s(%d,%d,%d)",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public void syncPotsInChunk(org.bukkit.Chunk chunk) {
        // Buscar todas las macetas mágicas en el chunk
        for (MagicFlowerPotData potData : potManager.getAllActivePots()) {
            Location potLocation = potData.getLocation();

            // Verificar si está en este chunk
            if (potLocation.getChunk().equals(chunk)) {
                // Sincronizar visual
                boolean isConfigurable = isFlowerConfigurable(potData.getFlowerId());
                updatePotVisual(potLocation, potData.getFlowerId(), isConfigurable);

                plugin.getLogger().info("Visual sincronizado para maceta ID: " + potData.getPotId() +
                        " en " + formatLocation(potLocation));
            }
        }
    }

    /**
     * Verifica si una flor es del sistema configurable
     */
    private boolean isFlowerConfigurable(String flowerId) {
        if (plugin.getFlowerIntegration() != null) {
            FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();
            return configManager.hasFlower(flowerId);
        }
        return false;
    }

    /**
     * Comando para forzar sincronización de visuales
     * Agregar esto al comando /flowerpot
     */
    private boolean handleSyncVisuals(CommandSender sender) {
        if (!sender.hasPermission("survivalcore.flowerpot.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos para usar este comando.");
            return true;
        }

        int syncedCount = 0;

        for (MagicFlowerPotData potData : potManager.getAllActivePots()) {
            Location potLocation = potData.getLocation();

            // Verificar que el chunk esté cargado
            if (!potLocation.getChunk().isLoaded()) {
                continue;
            }

            // Sincronizar visual
            boolean isConfigurable = isFlowerConfigurable(potData.getFlowerId());
            if (updatePotVisual(potLocation, potData.getFlowerId(), isConfigurable)) {
                syncedCount++;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "✓ Sincronizados " + syncedCount + " visuales de macetas.");

        return true;
    }

    /**
     * 🔧 MÉTODO CORREGIDO: Verificación mejorada de distancia
     * Soluciona el problema de detección de macetas fantasma
     */
    private boolean isValidDistanceImproved(Location newLocation) {
        // 🆕 NUEVO: Limpiar ubicaciones inválidas antes de verificar
        cleanupInvalidPotLocations();

        for (MagicFlowerPotData existingPot : potManager.getAllActivePots()) {
            Location existingLocation = existingPot.getLocation();

            // Solo verificar en el mismo mundo
            if (!newLocation.getWorld().equals(existingLocation.getWorld())) {
                continue;
            }

            // 🔧 CORRECCIÓN CRÍTICA: Verificar que realmente existe una maceta en esa ubicación
            Block existingBlock = existingLocation.getBlock();
            if (existingBlock.getType() != Material.FLOWER_POT) {
                // La maceta ya no existe físicamente, limpiar del manager
                plugin.getLogger().warning("Maceta fantasma detectada en " + formatLocation(existingLocation) + " - Limpiando...");
                potManager.unregisterPot(existingLocation);
                continue; // Saltar esta ubicación
            }

            // 🆕 NUEVO: Excluir la misma ubicación exacta (permite recolocar en el mismo lugar)
            if (existingLocation.equals(newLocation)) {
                plugin.getLogger().info("Permitiendo recolocación en la misma ubicación: " + formatLocation(newLocation));
                continue;
            }

            // Calcular distancia 3D
            double distance = newLocation.distance(existingLocation);

            if (distance < MIN_DISTANCE_BETWEEN_POTS) {
                plugin.getLogger().info(String.format("Maceta rechazada por proximidad: %.2f bloques de %s",
                        distance, formatLocation(existingLocation)));
                return false; // Muy cerca de otra maceta válida
            }
        }

        return true; // Distancia válida
    }

    /**
     * 🆕 NUEVO: Limpia una ubicación específica de maceta
     */
    private void cleanupPotLocation(Location location) {
        // Verificar si aún está registrada y limpiarla
        if (potManager.hasPotAt(location)) {
            potManager.unregisterPot(location);
            plugin.getLogger().info("Limpieza adicional realizada para: " + formatLocation(location));
        }
    }

    /**
     * Obtiene el material de Minecraft que se debe mostrar en la maceta
     * @param flowerId ID de la flor mágica
     * @param isConfigurable Si es una flor configurable
     * @return Material a mostrar en la maceta
     */
    private Material getVisualFlowerMaterial(String flowerId, boolean isConfigurable) {
        if (flowerId == null || flowerId.equals("none")) {
            return null; // Maceta vacía
        }

        // 🆕 NUEVO: Para flores configurables, obtener material de la configuración
        if (isConfigurable && plugin.getFlowerIntegration() != null) {
            FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();
            if (configManager.hasFlower(flowerId)) {
                FlowerDefinition flowerDef = configManager.getFlower(flowerId);
                if (flowerDef != null) {
                    Material configMaterial = flowerDef.getConfig().getType();
                    if (isValidFlowerMaterial(configMaterial)) {
                        return configMaterial;
                    }
                }
            }
        }

        // Para flores tradicionales o fallback
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return Material.POPPY;
            case "healing_flower":
                return Material.DANDELION;
            case "speed_flower":
                return Material.BLUE_ORCHID;
            case "strength_flower":
                return Material.ALLIUM;
            case "night_vision_flower":
                return Material.AZURE_BLUET;
            case "nature_flower":
                return Material.LILY_OF_THE_VALLEY;
            case "celestial_flower":
                return Material.CORNFLOWER;
            case "guardian_flower":
                return Material.SUNFLOWER;
            case "phoenix_flower":
                return Material.WITHER_ROSE;
            case "chaos_flower":
                return Material.CHORUS_FLOWER;
            default:
                plugin.getLogger().warning("Material visual no definido para flor: " + flowerId);
                return Material.DANDELION; // Fallback
        }
    }

    /**
     * Verifica si un material se puede mostrar en una maceta
     * @param material Material a verificar
     * @return true si es válido para macetas
     */
    private boolean isValidFlowerMaterial(Material material) {
        // Lista de materiales que se pueden mostrar en macetas
        Material[] validMaterials = {
                // Flores pequeñas
                Material.POPPY, Material.DANDELION, Material.BLUE_ORCHID, Material.ALLIUM,
                Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP,
                Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY,
                Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE,

                // Otros válidos
                Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
                Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
                Material.MANGROVE_PROPAGULE, Material.CHERRY_SAPLING,

                // Hongos
                Material.RED_MUSHROOM, Material.BROWN_MUSHROOM,

                // Cactus y plantas especiales
                Material.CACTUS, Material.DEAD_BUSH, Material.FERN, Material.BAMBOO,

                // Flores especiales
                Material.CHORUS_FLOWER
        };

        for (Material validMaterial : validMaterials) {
            if (material == validMaterial) {
                return true;
            }
        }

        return false;
    }

    /**
     * 🔧 CORREGIDO: Maneja el plantado sin duplicación
     */
    private void handleFlowerPlanting(Player player, Location location, MagicFlowerPotData potData, ItemStack flower) {
        String newFlowerId = null;
        int newFlowerLevel = 1;
        boolean isConfigurableFlower = false;

        // Verificar si es una flor configurable primero
        if (plugin.getFlowerIntegration() != null) {
            ConfigurableFlowerFactory factory = plugin.getFlowerIntegration().getFlowerFactory();
            if (factory.isConfigurableFlower(flower)) {
                newFlowerId = factory.getFlowerId(flower);
                newFlowerLevel = factory.getFlowerLevel(flower);
                isConfigurableFlower = true;
            }
        }

        // Si no es configurable, usar el sistema tradicional
        if (newFlowerId == null) {
            newFlowerId = getMagicFlowerId(flower);
            newFlowerLevel = getMagicFlowerLevel(flower);
            isConfigurableFlower = false;
        }

        if (newFlowerId == null) {
            player.sendMessage(ChatColor.RED + "Esta no es una flor mágica válida.");
            return;
        }

        boolean hadFlower = potData.hasFlower();
        String oldFlowerId = hadFlower ? potData.getFlowerId() : null;
        int oldFlowerLevel = hadFlower ? getStoredFlowerLevel(potData) : 1;

        // Verificar si es la misma flor antes de devolver
        if (hadFlower) {
            boolean isDifferentFlower = !newFlowerId.equals(oldFlowerId) || newFlowerLevel != oldFlowerLevel;

            if (isDifferentFlower) {
                ItemStack oldMagicFlower = createMagicFlowerFromId(oldFlowerId, oldFlowerLevel);
                if (oldMagicFlower != null) {
                    if (player.getInventory().addItem(oldMagicFlower).isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + "La " + getFlowerDisplayName(oldFlowerId) +
                                " anterior ha sido devuelta a tu inventario.");
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), oldMagicFlower);
                        player.sendMessage(ChatColor.YELLOW + "La " + getFlowerDisplayName(oldFlowerId) +
                                " anterior ha sido dropeada (inventario lleno).");
                    }
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "Esta maceta ya contiene la misma flor.");
                return;
            }
        }

        // 🔧 CORRECCIÓN PRINCIPAL: Actualizar el visual de la maceta
        if (updatePotVisual(location, newFlowerId, isConfigurableFlower)) {
            // Plantar la nueva flor en el sistema
            if (potManager.updateFlowerInPot(location, newFlowerId, newFlowerLevel)) {
                storeFlowerLevel(potData, newFlowerLevel);

                // Consumir la flor del inventario
                if (player.getGameMode() != GameMode.CREATIVE) {
                    flower.setAmount(flower.getAmount() - 1);
                }

                // Mensajes diferenciados
                String flowerDisplayName = getFlowerDisplayName(newFlowerId);
                String flowerTypeInfo = isConfigurableFlower ?
                        ChatColor.LIGHT_PURPLE + " [Configurable]" :
                        ChatColor.GRAY + " [Tradicional]";

                if (hadFlower && !newFlowerId.equals(oldFlowerId)) {
                    player.sendMessage(ChatColor.GREEN + "✓ " + flowerDisplayName +
                            flowerTypeInfo + ChatColor.GREEN + " plantada, reemplazando " +
                            getFlowerDisplayName(oldFlowerId) + "!");
                } else if (hadFlower) {
                    player.sendMessage(ChatColor.GREEN + "✓ " + flowerDisplayName +
                            flowerTypeInfo + ChatColor.GREEN + " mejorada de nivel " +
                            oldFlowerLevel + " a " + newFlowerLevel + "!");
                } else {
                    player.sendMessage(ChatColor.GREEN + "✓ " + flowerDisplayName +
                            flowerTypeInfo + ChatColor.GREEN + " plantada correctamente!");
                }

                player.sendMessage(ChatColor.AQUA + "La maceta ahora irradia efectos en " +
                        potData.getEffectRange() + " bloques de distancia.");

                // Efectos de plantado
                playFlowerPlantEffects(location, newFlowerId, newFlowerLevel);

                plugin.getLogger().info(String.format("Flor plantada: %s -> %s (nivel %d) %s en maceta ID: %s",
                        player.getName(), newFlowerId, newFlowerLevel,
                        isConfigurableFlower ? "[CONFIG]" : "[TRAD]", potData.getPotId()));
            } else {
                player.sendMessage(ChatColor.RED + "Error al plantar la flor. Inténtalo de nuevo.");
                // Revertir el visual si falló
                updatePotVisual(location, hadFlower ? oldFlowerId : "none", false);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Error actualizando el visual de la maceta.");
        }
    }

    /**
     * 🔧 MÉTODO COMPLETAMENTE CORREGIDO: Actualiza el visual de la maceta
     * Ahora usa el método correcto para establecer flores en macetas
     */
    private boolean updatePotVisual(Location location, String flowerId, boolean isConfigurable) {
        try {
            Block block = location.getBlock();

            // Verificar que sigue siendo una maceta
            if (block.getType() != Material.FLOWER_POT) {
                plugin.getLogger().warning("Bloque ya no es una maceta en: " + formatLocation(location));
                return false;
            }

            // Obtener el material de la flor a mostrar
            Material flowerMaterial = getVisualFlowerMaterial(flowerId, isConfigurable);

            if (flowerMaterial != null && flowerMaterial != Material.AIR && isValidFlowerMaterial(flowerMaterial)) {
                // 🔧 CORRECCIÓN PRINCIPAL: Usar setType directamente con el material de la flor
                // Esto coloca la flor en la maceta automáticamente
                Material potWithFlower = getMaterialForPottedFlower(flowerMaterial);
                if (potWithFlower != null) {
                    block.setType(potWithFlower, true);
                    plugin.getLogger().info("Flor visual establecida: " + flowerMaterial.name() + " en " + formatLocation(location));
                } else {
                    plugin.getLogger().warning("No se encontró material de maceta para: " + flowerMaterial.name());
                    return false;
                }
            } else {
                // Maceta vacía - establecer como maceta normal
                block.setType(Material.FLOWER_POT, true);
                plugin.getLogger().info("Maceta visual vaciada en: " + formatLocation(location));
            }

            // Forzar actualización para los clientes
            block.getState().update(true, true);

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error actualizando visual de maceta en " + formatLocation(location) + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 🆕 NUEVO: Obtiene el material de maceta con flor correspondiente
     */
    private Material getMaterialForPottedFlower(Material flowerMaterial) {
        switch (flowerMaterial) {
            case POPPY:
                return Material.POTTED_POPPY;
            case DANDELION:
                return Material.POTTED_DANDELION;
            case BLUE_ORCHID:
                return Material.POTTED_BLUE_ORCHID;
            case ALLIUM:
                return Material.POTTED_ALLIUM;
            case AZURE_BLUET:
                return Material.POTTED_AZURE_BLUET;
            case RED_TULIP:
                return Material.POTTED_RED_TULIP;
            case ORANGE_TULIP:
                return Material.POTTED_ORANGE_TULIP;
            case WHITE_TULIP:
                return Material.POTTED_WHITE_TULIP;
            case PINK_TULIP:
                return Material.POTTED_PINK_TULIP;
            case OXEYE_DAISY:
                return Material.POTTED_OXEYE_DAISY;
            case CORNFLOWER:
                return Material.POTTED_CORNFLOWER;
            case LILY_OF_THE_VALLEY:
                return Material.POTTED_LILY_OF_THE_VALLEY;
            case WITHER_ROSE:
                return Material.POTTED_WITHER_ROSE;

            // Arbustos
            case OAK_SAPLING:
                return Material.POTTED_OAK_SAPLING;
            case SPRUCE_SAPLING:
                return Material.POTTED_SPRUCE_SAPLING;
            case BIRCH_SAPLING:
                return Material.POTTED_BIRCH_SAPLING;
            case JUNGLE_SAPLING:
                return Material.POTTED_JUNGLE_SAPLING;
            case ACACIA_SAPLING:
                return Material.POTTED_ACACIA_SAPLING;
            case DARK_OAK_SAPLING:
                return Material.POTTED_DARK_OAK_SAPLING;
            case MANGROVE_PROPAGULE:
                return Material.POTTED_MANGROVE_PROPAGULE;
            case CHERRY_SAPLING:
                return Material.POTTED_CHERRY_SAPLING;

            // Hongos
            case RED_MUSHROOM:
                return Material.POTTED_RED_MUSHROOM;
            case BROWN_MUSHROOM:
                return Material.POTTED_BROWN_MUSHROOM;

            // Otros
            case CACTUS:
                return Material.POTTED_CACTUS;
            case DEAD_BUSH:
                return Material.POTTED_DEAD_BUSH;
            case FERN:
                return Material.POTTED_FERN;
            case BAMBOO:
                return Material.POTTED_BAMBOO;

            default:
                // Si no hay equivalente directo, usar POTTED_DANDELION como fallback
                plugin.getLogger().warning("Material sin equivalente en maceta: " + flowerMaterial.name() + ", usando DANDELION");
                return Material.POTTED_DANDELION;
        }
    }

    /**
     * 🆕 NUEVO: Crea una flor mágica a partir de su ID y nivel
     */
    private ItemStack createMagicFlowerFromId(String flowerId, int level) {
        // 🆕 NUEVO: Intentar crear flor configurable primero
        if (plugin.getFlowerIntegration() != null) {
            ConfigurableFlowerFactory factory = plugin.getFlowerIntegration().getFlowerFactory();
            FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();

            // Verificar si existe en la configuración
            if (configManager.hasFlower(flowerId)) {
                ItemStack configurableFlower = factory.createConfigurableFlower(flowerId, level);
                if (configurableFlower != null) {
                    return configurableFlower;
                }
            }
        }

        // Si no es configurable o falla, usar sistema tradicional
        MagicFlowerFactory.FlowerType flowerType = getFlowerTypeFromId(flowerId);
        if (flowerType == null) {
            plugin.getLogger().warning("No se pudo crear flor mágica para ID: " + flowerId);
            return null;
        }

        // Usar la factory tradicional para crear la flor mágica
        return flowerFactory.createMagicFlower(flowerType, level);
    }

    /**
     * Obtiene el tipo de flor a partir de su ID
     */
    private MagicFlowerFactory.FlowerType getFlowerTypeFromId(String flowerId) {
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return MagicFlowerFactory.FlowerType.LOVE_FLOWER;
            case "healing_flower":
                return MagicFlowerFactory.FlowerType.HEALING_FLOWER;
            case "speed_flower":
                return MagicFlowerFactory.FlowerType.SPEED_FLOWER;
            case "strength_flower":
                return MagicFlowerFactory.FlowerType.STRENGTH_FLOWER;
            case "night_vision_flower":
                return MagicFlowerFactory.FlowerType.NIGHT_VISION_FLOWER;
            default:
                return null;
        }
    }

    /**
     * Obtiene el nivel de una flor mágica
     */
    private int getMagicFlowerLevel(ItemStack flower) {
        if (!isMagicFlower(flower)) return 1;

        // 🆕 NUEVO: Intentar obtener nivel de flor configurable primero
        if (plugin.getFlowerIntegration() != null) {
            ConfigurableFlowerFactory factory = plugin.getFlowerIntegration().getFlowerFactory();
            if (factory.isConfigurableFlower(flower)) {
                return factory.getFlowerLevel(flower);
            }
        }

        // Obtener nivel de flor tradicional
        ItemMeta meta = flower.getItemMeta();
        if (meta == null) return 1;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey levelKey = new NamespacedKey(plugin, "flower_level");
        return container.getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    /**
     * 🔧 MEJORADO: Almacena el nivel de la flor en los datos de la maceta
     */
    private void storeFlowerLevel(MagicFlowerPotData potData, int level) {
        // NOTA: Esto requiere que MagicFlowerPotData soporte nivel de flor
        // En la implementación actual, MagicFlowerPotData ya tiene soporte para flowerLevel
        if (potData != null) {
            potData.setFlowerLevel(level);
        }
    }

    /**
     * Obtiene el nivel almacenado de la flor
     */
    private int getStoredFlowerLevel(MagicFlowerPotData potData) {
        // Usando el método ya existente en MagicFlowerPotData
        return potData != null ? potData.getFlowerLevel() : 1;
    }

    /**
     * Verificar distancia mínima entre macetas
     */
    private boolean isValidDistance(Location newLocation) {
        // Usar el método mejorado
        return isValidDistanceImproved(newLocation);
    }

    /**
     * Verificar si es una flor normal (prohibida)
     */
    private boolean isNormalFlower(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        // Lista de flores normales que NO se pueden usar
        Material[] normalFlowers = {
                Material.POPPY,
                Material.DANDELION,
                Material.BLUE_ORCHID,
                Material.ALLIUM,
                Material.AZURE_BLUET,
                Material.RED_TULIP,
                Material.ORANGE_TULIP,
                Material.WHITE_TULIP,
                Material.PINK_TULIP,
                Material.OXEYE_DAISY,
                Material.CORNFLOWER,
                Material.LILY_OF_THE_VALLEY,
                Material.WITHER_ROSE,
                Material.SUNFLOWER,
                Material.LILAC,
                Material.ROSE_BUSH,
                Material.PEONY,
                Material.SWEET_BERRY_BUSH
        };

        for (Material flower : normalFlowers) {
            if (item.getType() == flower) {
                return true;
            }
        }
        return false;
    }

    /**
     * Efectos de colocación mejorados
     */
    private void playEnhancedPlacementEffects(Location location, int level, Player player) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLocation = location.clone().add(0.5, 0.8, 0.5);

        // Sonidos iniciales
        world.playSound(effectLocation, Sound.BLOCK_GRASS_PLACE, 1.0f, 1.2f);

        // Animación de partículas en espiral ascendente
        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = 40; // 2 segundos

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    // Efectos finales
                    playFinalPlacementEffects(effectLocation, level);
                    cancel();
                    return;
                }

                // Espiral de partículas
                double angle = (ticks * 15) % 360; // Rotación
                double height = (double) ticks / maxTicks; // Altura progresiva
                double radius = 0.8 - (height * 0.3); // Radio decreciente

                double x = effectLocation.getX() + Math.cos(Math.toRadians(angle)) * radius;
                double y = effectLocation.getY() + height * 1.5;
                double z = effectLocation.getZ() + Math.sin(Math.toRadians(angle)) * radius;

                Location particleLocation = new Location(world, x, y, z);

                // Partículas según el nivel
                Particle particle = level >= 3 ? Particle.ENCHANTMENT_TABLE : Particle.VILLAGER_HAPPY;
                world.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0.02);

                // Partículas adicionales para niveles altos
                if (level >= 4 && ticks % 3 == 0) {
                    world.spawnParticle(Particle.CRIT_MAGIC, particleLocation, 1, 0.1, 0.1, 0.1, 0.05);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Efectos finales de colocación
     */
    private void playFinalPlacementEffects(Location location, int level) {
        World world = location.getWorld();
        if (world == null) return;

        // Sonido de finalización
        world.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
        world.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        // Explosión de partículas según el nivel
        int particleCount = 15 + (level * 5);
        Particle finalParticle = level >= 3 ? Particle.ENCHANTMENT_TABLE : Particle.VILLAGER_HAPPY;

        world.spawnParticle(finalParticle, location, particleCount, 0.6, 0.4, 0.6, 0.1);

        // Anillo de partículas en el suelo para niveles altos
        if (level >= 3) {
            createParticleRing(location.clone().subtract(0, 0.2, 0), level);
        }
    }

    /**
     * Crear anillo de partículas
     */
    private void createParticleRing(Location center, int level) {
        World world = center.getWorld();
        if (world == null) return;

        double radius = 1.5 + (level * 0.3);
        int points = 12 + (level * 2);

        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * 2 * Math.PI;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY();

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(Particle.WAX_OFF, particleLocation, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Efectos de error
     */
    private void playErrorEffects(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLocation = location.clone().add(0.5, 0.5, 0.5);

        // Sonido de error
        world.playSound(effectLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);

        // Partículas rojas de error
        world.spawnParticle(Particle.BLOCK_CRACK, effectLocation, 10, 0.3, 0.3, 0.3, 0.1,
                Material.REDSTONE_BLOCK.createBlockData());
    }

    /**
     * Muestra información sobre la maceta
     */
    private void showPotInfo(Player player, MagicFlowerPotData potData) {
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "✿ ═══════ MACETA MÁGICA ═══════ ✿");
        player.sendMessage(ChatColor.WHITE + "  📊 Nivel: " + ChatColor.AQUA + potData.getLevel());
        player.sendMessage(ChatColor.WHITE + "  📏 Rango: " + ChatColor.GREEN + potData.getEffectRange() + " bloques");

        if (potData.hasFlower()) {
            String flowerDesc = potData.getFlowerDescription(); // Incluye nivel de flor
            player.sendMessage(ChatColor.WHITE + "  🌸 Flor: " + ChatColor.LIGHT_PURPLE + flowerDesc);
            player.sendMessage(ChatColor.WHITE + "  ⚡ Estado: " + ChatColor.GREEN + "ACTIVA");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "▶ Click derecho con otra flor mágica para cambiar");
            player.sendMessage(ChatColor.GRAY + "▶ Plantar la misma flor no la duplicará");
        } else {
            player.sendMessage(ChatColor.WHITE + "  🌸 Flor: " + ChatColor.GRAY + "Vacía");
            player.sendMessage(ChatColor.WHITE + "  ⚡ Estado: " + ChatColor.YELLOW + "ESPERANDO FLOR");
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "▶ Click derecho con una flor mágica para plantar");
            player.sendMessage(ChatColor.RED + "▶ Las flores normales no funcionan");
        }

        player.sendMessage(ChatColor.WHITE + "  🕐 Activa desde: " + ChatColor.GRAY +
                formatTime(potData.getActiveTime()));
        player.sendMessage(ChatColor.WHITE + "  🆔 ID: " + ChatColor.GRAY + "#" + potData.getPotId());
        player.sendMessage(ChatColor.LIGHT_PURPLE + "✿ ═══════════════════════════════ ✿");

        // Sonido de información
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
    }

    /**
     * Verifica si se puede colocar una maceta en la ubicación
     */
    private boolean canPlacePotAt(Location location) {
        Block below = location.clone().subtract(0, 1, 0).getBlock();
        Block above = location.clone().add(0, 1, 0).getBlock();

        // Debe tener un bloque sólido debajo
        if (!below.getType().isSolid()) {
            return false;
        }

        // Debe tener espacio libre arriba
        if (above.getType().isSolid()) {
            return false;
        }

        return true;
    }

    /**
     * Verifica si el jugador ha alcanzado el límite de macetas
     */
    private boolean hasReachedPotLimit(Player player) {
        // Si es admin, no tiene límite
        if (player.hasPermission("survivalcore.flowerpot.unlimited")) {
            return false;
        }

        // Obtener límite de configuración
        int limit = plugin.getConfig().getInt("magic_flowerpot.max_pots_per_player", 10);
        if (limit <= 0) return false; // Sin límite

        // Contar macetas del jugador (esta implementación es básica)
        // En una implementación más avanzada, podrías trackear por UUID
        long playerPots = potManager.getAllActivePots().stream()
                .filter(pot -> pot.getLocation().getWorld().getName().equals(player.getWorld().getName()))
                .count();

        return playerPots >= limit;
    }

    /**
     * Copia metadatos del ítem al bloque
     */
    private void copyMetadataToBlock(ItemStack item, TileState tileState) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer itemContainer = meta.getPersistentDataContainer();
        PersistentDataContainer blockContainer = tileState.getPersistentDataContainer();

        // Copiar todas las claves relevantes
        if (itemContainer.has(isMagicPotKey, PersistentDataType.BYTE)) {
            blockContainer.set(isMagicPotKey, PersistentDataType.BYTE,
                    itemContainer.get(isMagicPotKey, PersistentDataType.BYTE));
        }
    }

    /**
     * Verifica si un ítem es una flor mágica
     */
    private boolean isMagicFlower(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        // 🆕 NUEVO: Verificar flor configurable primero
        if (plugin.getFlowerIntegration() != null) {
            ConfigurableFlowerFactory factory = plugin.getFlowerIntegration().getFlowerFactory();
            if (factory.isMagicFlower(item)) {
                return true;
            }
        }

        // Verificar flor tradicional
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(isMagicFlowerKey, PersistentDataType.BYTE);
    }

    /**
     * Obtiene el ID de una flor mágica
     */
    private String getMagicFlowerId(ItemStack item) {
        if (!isMagicFlower(item)) return null;

        // 🆕 NUEVO: Intentar obtener ID de flor configurable primero
        if (plugin.getFlowerIntegration() != null) {
            ConfigurableFlowerFactory factory = plugin.getFlowerIntegration().getFlowerFactory();
            if (factory.isConfigurableFlower(item)) {
                return factory.getFlowerId(item);
            }
        }

        // Obtener ID de flor tradicional
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(new NamespacedKey(plugin, "flower_id"), PersistentDataType.STRING);
    }

    /**
     * Obtiene el nombre de display de una flor
     */
    private String getFlowerDisplayName(String flowerId) {
        // 🆕 NUEVO: Intentar obtener nombre de configuración primero
        if (plugin.getFlowerIntegration() != null) {
            FlowerConfigManager configManager = plugin.getFlowerIntegration().getConfigManager();
            if (configManager.hasFlower(flowerId)) {
                FlowerDefinition flowerDef = configManager.getFlower(flowerId);
                if (flowerDef != null) {
                    return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                            flowerDef.getDisplay().getName()));
                }
            }
        }

        // Usar nombres tradicionales como fallback
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return "Flor del Amor";
            case "healing_flower":
                return "Flor Sanadora";
            case "speed_flower":
                return "Flor de Velocidad";
            case "strength_flower":
                return "Flor de Fuerza";
            case "night_vision_flower":
                return "Flor Nocturna";
            // 🆕 NUEVO: Soporte para flores configurables adicionales
            case "nature_flower":
                return "Flor de la Naturaleza";
            case "celestial_flower":
                return "Flor Celestial";
            case "guardian_flower":
                return "Flor Guardiana";
            case "phoenix_flower":
                return "Flor del Fénix";
            case "chaos_flower":
                return "Flor del Caos";
            default:
                return "Flor Desconocida";
        }
    }

    /**
     * Efectos al romper la maceta
     */
    private void playBreakEffects(Location location, boolean hadFlower) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLocation = location.clone().add(0.5, 0.8, 0.5);

        // Sonido
        world.playSound(effectLocation, Sound.BLOCK_GRASS_BREAK, 1.0f, 0.9f);

        // Partículas
        if (hadFlower) {
            world.spawnParticle(Particle.HEART, effectLocation, 8, 0.4, 0.3, 0.4, 0.1);
        }
        world.spawnParticle(Particle.BLOCK_CRACK, effectLocation, 15, 0.4, 0.2, 0.4, 0.1,
                Material.FLOWER_POT.createBlockData());
    }

    /**
     * 🔧 MEJORADO: Efectos al plantar una flor con nivel
     */
    private void playFlowerPlantEffects(Location location, String flowerId, int flowerLevel) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLocation = location.clone().add(0.5, 0.8, 0.5);

        // Sonido base
        world.playSound(effectLocation, Sound.BLOCK_GRASS_PLACE, 1.0f, 1.4f);
        world.playSound(effectLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);

        // Sonido específico por flor con pitch modificado por nivel
        Sound flowerSound = getFlowerSound(flowerId);
        if (flowerSound != null) {
            float pitch = 1.0f + (flowerLevel - 1) * 0.2f; // Pitch más alto para flores de mayor nivel
            world.playSound(effectLocation, flowerSound, 0.8f, pitch);
        }

        // 🆕 MEJORADO: Más partículas para flores de mayor nivel
        int particleCount = 20 + (flowerLevel - 1) * 10;
        double spread = 0.4 + (flowerLevel - 1) * 0.1;

        // Partículas específicas por flor
        Particle particle = getFlowerParticle(flowerId);
        world.spawnParticle(particle, effectLocation, particleCount, spread, 0.3, spread, 0.1);

        // 🆕 NUEVO: Efectos especiales para flores de alto nivel
        if (flowerLevel >= 4) {
            // Anillo de partículas especiales para flores de nivel 4+
            createSpecialFlowerRing(effectLocation, flowerLevel, particle);
        }
    }

    /**
     * 🆕 NUEVO: Crea un anillo especial para flores de alto nivel
     */
    private void createSpecialFlowerRing(Location center, int flowerLevel, Particle particle) {
        World world = center.getWorld();
        if (world == null) return;

        double radius = 1.0 + (flowerLevel - 4) * 0.5;
        int points = 12 + (flowerLevel - 4) * 4;

        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * 2 * Math.PI;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY();

            Location particleLocation = new Location(world, x, y, z);
            world.spawnParticle(particle, particleLocation, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * Obtiene partícula para una flor específica
     */
    private Particle getFlowerParticle(String flowerId) {
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return Particle.HEART;
            case "healing_flower":
                return Particle.VILLAGER_HAPPY;
            case "speed_flower":
                return Particle.CRIT;
            case "strength_flower":
                return Particle.CRIT_MAGIC;
            case "night_vision_flower":
                return Particle.ENCHANTMENT_TABLE;
            default:
                return Particle.VILLAGER_HAPPY;
        }
    }

    /**
     * Obtiene el sonido específico para una flor
     */
    private Sound getFlowerSound(String flowerId) {
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "healing_flower":
                return Sound.ENTITY_PLAYER_LEVELUP;
            case "speed_flower":
                return Sound.ENTITY_HORSE_GALLOP;
            case "strength_flower":
                return Sound.ENTITY_IRON_GOLEM_ATTACK;
            case "night_vision_flower":
                return Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            default:
                return Sound.BLOCK_GRASS_PLACE;
        }
    }

    /**
     * Formatea tiempo en formato legible
     */
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}