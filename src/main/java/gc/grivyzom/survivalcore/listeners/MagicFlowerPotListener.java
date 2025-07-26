package gc.grivyzom.survivalcore.listeners;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPot;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotManager;
import gc.grivyzom.survivalcore.flowerpot.MagicFlowerPotData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
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

/**
 * Listener que maneja todos los eventos relacionados con las Macetas Mágicas
 *
 * @author Brocolitx
 * @version 1.0
 */
public class MagicFlowerPotListener implements Listener {

    private final Main plugin;
    private final MagicFlowerPot potFactory;
    private final MagicFlowerPotManager potManager;
    private final NamespacedKey isMagicPotKey;
    private final NamespacedKey isMagicFlowerKey;

    // Permisos
    private static final String PERM_USE = "survivalcore.flowerpot.use";
    private static final String PERM_PLACE = "survivalcore.flowerpot.place";
    private static final String PERM_BREAK = "survivalcore.flowerpot.break";

    public MagicFlowerPotListener(Main plugin) {
        this.plugin = plugin;
        this.potFactory = new MagicFlowerPot(plugin);
        this.potManager = plugin.getMagicFlowerPotManager();
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
            return;
        }

        // Verificar límite de macetas por jugador (si está configurado)
        if (hasReachedPotLimit(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Has alcanzado el límite de Macetas Mágicas que puedes colocar.");
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

        // Efectos de colocación
        playPlacementEffects(location, level);

        // Mensaje de confirmación
        player.sendMessage(ChatColor.GREEN + "✓ Maceta Mágica colocada correctamente.");
        player.sendMessage(ChatColor.GRAY + "Nivel: " + ChatColor.AQUA + level +
                ChatColor.GRAY + " | Rango: " + ChatColor.GREEN + (3 + (level - 1) * 2) + " bloques");

        if (!flowerId.equals("none")) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 Contiene: " + getFlowerDisplayName(flowerId));
        }
    }

    /**
     * Maneja la rotura de macetas mágicas
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMagicFlowerPotBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Solo procesar macetas
        if (block.getType() != Material.FLOWER_POT) {
            return;
        }

        Location location = block.getLocation();
        Player player = event.getPlayer();

        // Verificar si es una maceta mágica registrada
        if (!potManager.hasPotAt(location)) {
            return;
        }

        // Verificar permisos
        if (!player.hasPermission(PERM_BREAK)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "No tienes permisos para romper Macetas Mágicas.");
            return;
        }

        // Obtener datos de la maceta
        MagicFlowerPotData potData = potManager.getPotData(location);
        if (potData == null) {
            return;
        }

        // Cancelar drops vanilla
        event.setDropItems(false);

        // Crear ítem de maceta mágica con sus metadatos originales
        ItemStack magicPot = potFactory.createMagicFlowerPot(potData.getLevel());

        // Si tenía una flor, mantenerla
        if (potData.hasFlower()) {
            magicPot = potFactory.setContainedFlower(magicPot, potData.getFlowerId());
        }

        // Dropear la maceta mágica
        block.getWorld().dropItemNaturally(location.add(0.5, 0.5, 0.5), magicPot);

        // Si tenía flor, también dropear la flor como ítem separado
        if (potData.hasFlower()) {
            ItemStack flowerItem = createMagicFlowerItem(potData.getFlowerId());
            if (flowerItem != null) {
                block.getWorld().dropItemNaturally(location, flowerItem);
            }
        }

        // Desregistrar la maceta
        potManager.unregisterPot(location);

        // Efectos de rotura
        playBreakEffects(location, potData.hasFlower());

        // Mensaje al jugador
        player.sendMessage(ChatColor.YELLOW + "⚡ Maceta Mágica recogida correctamente.");
        if (potData.hasFlower()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "🌸 La flor también ha sido devuelta.");
        }
    }

    /**
     * Maneja la interacción con macetas mágicas (plantar/quitar flores)
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

        // Si tiene una flor mágica en la mano
        if (isMagicFlower(itemInHand)) {
            handleFlowerPlanting(player, location, potData, itemInHand);
            return;
        }

        // Si hace clic con otros ítems, mostrar información
        showPotInfo(player, potData);
    }

    /**
     * Maneja el plantado de flores mágicas
     */
    private void handleFlowerPlanting(Player player, Location location, MagicFlowerPotData potData, ItemStack flower) {
        String flowerId = getMagicFlowerId(flower);

        if (flowerId == null) {
            player.sendMessage(ChatColor.RED + "Esta no es una flor mágica válida.");
            return;
        }

        // Si ya tiene una flor, primero removerla
        if (potData.hasFlower()) {
            // Devolver la flor anterior
            ItemStack oldFlower = createMagicFlowerItem(potData.getFlowerId());
            if (oldFlower != null) {
                player.getInventory().addItem(oldFlower);
                player.sendMessage(ChatColor.YELLOW + "La flor anterior ha sido devuelta a tu inventario.");
            }
        }

        // Plantar la nueva flor
        if (potManager.updateFlowerInPot(location, flowerId)) {
            // Consumir la flor del inventario (si no está en creativo)
            if (player.getGameMode() != GameMode.CREATIVE) {
                flower.setAmount(flower.getAmount() - 1);
            }

            player.sendMessage(ChatColor.GREEN + "✓ " + getFlowerDisplayName(flowerId) +
                    ChatColor.GREEN + " plantada correctamente!");
            player.sendMessage(ChatColor.AQUA + "La maceta ahora irradia efectos en " +
                    potData.getEffectRange() + " bloques de distancia.");

            // Efectos de plantado
            playFlowerPlantEffects(location, flowerId);
        } else {
            player.sendMessage(ChatColor.RED + "Error al plantar la flor. Inténtalo de nuevo.");
        }
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
            player.sendMessage(ChatColor.WHITE + "  🌸 Flor: " + ChatColor.LIGHT_PURPLE +
                    getFlowerDisplayName(potData.getFlowerId()));
            player.sendMessage(ChatColor.WHITE + "  ⚡ Estado: " + ChatColor.GREEN + "ACTIVA");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "▶ Click derecho con otra flor para cambiar");
        } else {
            player.sendMessage(ChatColor.WHITE + "  🌸 Flor: " + ChatColor.GRAY + "Vacía");
            player.sendMessage(ChatColor.WHITE + "  ⚡ Estado: " + ChatColor.YELLOW + "ESPERANDO FLOR");
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "▶ Click derecho con una flor mágica para plantar");
        }

        player.sendMessage(ChatColor.WHITE + "  🕐 Activa desde: " + ChatColor.GRAY +
                formatTime(potData.getActiveTime()));
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

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(isMagicFlowerKey, PersistentDataType.BYTE);
    }

    /**
     * Obtiene el ID de una flor mágica
     */
    private String getMagicFlowerId(ItemStack item) {
        if (!isMagicFlower(item)) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(new NamespacedKey(plugin, "flower_id"), PersistentDataType.STRING);
    }

    /**
     * Crea un ítem de flor mágica (placeholder para futuras flores)
     */
    private ItemStack createMagicFlowerItem(String flowerId) {
        // Esto será reemplazado cuando implementemos las flores mágicas
        switch (flowerId.toLowerCase()) {
            case "love_flower":
                return new ItemStack(Material.POPPY);
            case "healing_flower":
                return new ItemStack(Material.DANDELION);
            case "speed_flower":
                return new ItemStack(Material.BLUE_ORCHID);
            case "strength_flower":
                return new ItemStack(Material.ALLIUM);
            case "night_vision_flower":
                return new ItemStack(Material.AZURE_BLUET);
            default:
                return new ItemStack(Material.POPPY);
        }
    }

    /**
     * Obtiene el nombre de display de una flor
     */
    private String getFlowerDisplayName(String flowerId) {
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
            default:
                return "Flor Desconocida";
        }
    }

    /**
     * Efectos al colocar la maceta
     */
    private void playPlacementEffects(Location location, int level) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLocation = location.clone().add(0.5, 0.8, 0.5);

        // Sonido
        world.playSound(effectLocation, Sound.BLOCK_GRASS_PLACE, 1.0f, 1.2f);

        // Partículas según el nivel
        Particle particle = level >= 3 ? Particle.ENCHANTMENT_TABLE : Particle.VILLAGER_HAPPY;
        world.spawnParticle(particle, effectLocation, 10 + (level * 3), 0.3, 0.2, 0.3, 0.05);
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
     * Efectos al plantar una flor
     */
    private void playFlowerPlantEffects(Location location, String flowerId) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLocation = location.clone().add(0.5, 0.8, 0.5);

        // Sonido
        world.playSound(effectLocation, Sound.BLOCK_GRASS_PLACE, 1.0f, 1.4f);
        world.playSound(effectLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);

        // Partículas específicas por flor
        Particle particle = getFlowerParticle(flowerId);
        world.spawnParticle(particle, effectLocation, 20, 0.4, 0.3, 0.4, 0.1);
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