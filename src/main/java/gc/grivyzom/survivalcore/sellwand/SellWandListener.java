package gc.grivyzom.survivalcore.sellwand;

import gc.grivyzom.survivalcore.Main;
import gc.grivyzom.survivalcore.api.events.PlayerSellWandUseEvent;
import gc.grivyzom.survivalcore.data.UserData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Listener para el sistema SellWand
 */
public class SellWandListener implements Listener {

    private final Main plugin;
    private final SellWandManager manager;
    private final DecimalFormat priceFormat;

    public SellWandListener(Main plugin, SellWandManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.priceFormat = new DecimalFormat("#,##0.00");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Solo procesar clicks derechos con la mano principal
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK ||
                event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Verificar si es una SellWand
        if (!manager.isSellWand(item)) return;

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Container)) {
            player.sendMessage(ChatColor.RED + "⚠ Debes hacer clic en un contenedor (cofre, barril, etc.)");
            return;
        }

        event.setCancelled(true);

        // Verificar permisos básicos
        if (!player.hasPermission("survivalcore.sellwand.use")) {
            player.sendMessage(ChatColor.RED + "⚠ No tienes permisos para usar SellWands.");
            return;
        }

        // Verificar cooldown
        if (manager.isOnCooldown(player)) {
            long remainingMs = manager.getRemainingCooldown(player);
            double remainingSeconds = remainingMs / 1000.0;
            player.sendMessage(ChatColor.RED + "⏰ Debes esperar " +
                    String.format("%.1f", remainingSeconds) + " segundos antes de usar la SellWand nuevamente.");
            return;
        }

        // Verificar permisos del contenedor
        if (!canAccessContainer(player, block)) {
            player.sendMessage(ChatColor.RED + "⚠ No tienes permisos para acceder a este contenedor.");
            return;
        }

        // Obtener inventario del contenedor
        Container container = (Container) block.getState();
        Inventory inventory = container.getInventory();

        // Procesar venta
        processSale(player, inventory, item);
    }

    /**
     * Restaura items al inventario (usado cuando se cancela el evento)
     */
    private void restoreItemsToInventory(Inventory inventory, Map<Material, Integer> itemsToRestore) {
        for (Map.Entry<Material, Integer> entry : itemsToRestore.entrySet()) {
            Material material = entry.getKey();
            int amountToRestore = entry.getValue();

            while (amountToRestore > 0) {
                int stackSize = Math.min(amountToRestore, material.getMaxStackSize());
                ItemStack itemStack = new ItemStack(material, stackSize);

                // Intentar agregar al inventario
                Map<Integer, ItemStack> leftover = inventory.addItem(itemStack);
                if (!leftover.isEmpty()) {
                    // Si no hay espacio, romper el bucle
                    break;
                }

                amountToRestore -= stackSize;
            }
        }
    }

    /**
     * Procesa la venta de items en el contenedor
     */
    private void processSale(Player player, Inventory inventory, ItemStack sellWand) {
        Map<Material, Integer> itemsToSell = new HashMap<>();
        Map<Material, Double> itemPrices = new HashMap<>();
        double totalEarnings = 0.0;
        int totalItemsSold = 0;

        // Analizar contenido del contenedor
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            Material material = item.getType();
            int amount = item.getAmount();

            // Verificar si el item se puede vender
            if (!manager.canSellItem(material)) continue;

            double unitPrice = manager.getItemPrice(material);
            if (unitPrice <= 0) continue;

            // Verificar límites de venta
            if (!manager.checkSellLimit(player, material, amount)) {
                player.sendMessage(ChatColor.YELLOW + "⚠ Has alcanzado el límite de venta para " +
                        getItemDisplayName(material) + " por hoy.");
                continue;
            }

            // Agregar a la lista de venta
            itemsToSell.put(material, itemsToSell.getOrDefault(material, 0) + amount);
            itemPrices.put(material, unitPrice);
            totalEarnings += unitPrice * amount;
            totalItemsSold += amount;
        }

        // Verificar si hay algo que vender
        if (itemsToSell.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "📦 No hay items vendibles en este contenedor.");

            // Reproducir sonido de fallo
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        // Verificar usos de la SellWand
        if (!manager.reduceUses(sellWand)) {
            player.sendMessage(ChatColor.RED + "💥 Tu SellWand se ha agotado completamente.");
            player.getInventory().setItemInMainHand(null);

            // Efecto visual de destrucción
            player.getWorld().spawnParticle(Particle.SMOKE_LARGE,
                    player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            return;
        }

        // Remover items del contenedor
        removeItemsFromInventory(inventory, itemsToSell);

        // Procesar ganancias de experiencia
        long experienceGained = processExperience(player, totalEarnings, itemsToSell);

        // Disparar evento personalizado
        String xpType = determineExperienceType(itemsToSell);
        PlayerSellWandUseEvent sellEvent = new PlayerSellWandUseEvent(
                player, itemsToSell, totalEarnings, experienceGained, xpType);
        Bukkit.getPluginManager().callEvent(sellEvent);

        // Verificar si el evento fue cancelado
        if (sellEvent.isCancelled()) {
            // Restaurar items al contenedor
            restoreItemsToInventory(inventory, itemsToSell);
            player.sendMessage(ChatColor.RED + "❌ La venta fue cancelada por otro plugin.");
            return;
        }

        // Establecer cooldown
        manager.setCooldown(player);

        // Enviar resumen de venta
        sendSaleReport(player, itemsToSell, itemPrices, totalEarnings, experienceGained, totalItemsSold);

        // Efectos visuales y sonoros
        playSuccessEffects(player, totalEarnings);

        // Log de la transacción
        plugin.getLogger().info(String.format("SellWand: %s vendió %d items por %.2f (%.0f XP) usando SellWand",
                player.getName(), totalItemsSold, totalEarnings, (double) experienceGained));
    }

    /**
     * Procesa la experiencia ganada y la distribuye según la configuración
     */
    private long processExperience(Player player, double totalEarnings, Map<Material, Integer> itemsSold) {
        // Determinar el tipo de experiencia basado en los items vendidos
        String xpType = determineExperienceType(itemsSold);

        // Convertir ganancias a experiencia
        long totalXpGained = convertEarningsToExperience(totalEarnings);

        if (totalXpGained <= 0) {
            return 0;
        }

        // Distribución de experiencia según configuración
        String distribution = plugin.getConfig().getString("sellwand.xp_distribution", "mixed");

        switch (distribution.toLowerCase()) {
            case "farming_only":
                // Solo dar XP de farming
                addFarmingExperience(player, totalXpGained);
                break;

            case "mining_only":
                // Solo dar XP de mining
                addMiningExperience(player, totalXpGained);
                break;

            case "automatic":
                // Dar XP según el tipo de items vendidos
                if ("farming".equals(xpType)) {
                    addFarmingExperience(player, totalXpGained);
                } else if ("mining".equals(xpType)) {
                    addMiningExperience(player, totalXpGained);
                } else {
                    // Items mixtos - distribuir proporcionalmente
                    long farmingXp = totalXpGained / 2;
                    long miningXp = totalXpGained - farmingXp;
                    addFarmingExperience(player, farmingXp);
                    addMiningExperience(player, miningXp);
                }
                break;

            case "vanilla":
                // Dar XP vanilla de Minecraft
                player.giveExp((int) Math.min(totalXpGained, Integer.MAX_VALUE));
                break;

            default: // "mixed"
                // Distribuir entre farming, mining y vanilla
                long farmingXp = totalXpGained / 3;
                long miningXp = totalXpGained / 3;
                long vanillaXp = totalXpGained - farmingXp - miningXp;

                addFarmingExperience(player, farmingXp);
                addMiningExperience(player, miningXp);
                if (vanillaXp > 0) {
                    player.giveExp((int) Math.min(vanillaXp, Integer.MAX_VALUE));
                }
                break;
        }

        return totalXpGained;
    }

    /**
     * Determina el tipo de experiencia según los items vendidos
     */
    private String determineExperienceType(Map<Material, Integer> itemsSold) {
        int farmingItems = 0;
        int miningItems = 0;

        for (Material material : itemsSold.keySet()) {
            if (isFarmingItem(material)) {
                farmingItems++;
            } else if (isMiningItem(material)) {
                miningItems++;
            }
        }

        if (farmingItems > miningItems) {
            return "farming";
        } else if (miningItems > farmingItems) {
            return "mining";
        } else {
            return "mixed";
        }
    }

    /**
     * Verifica si un material es relacionado con farming
     */
    private boolean isFarmingItem(Material material) {
        return material.name().contains("SEEDS") ||
                material.name().contains("WHEAT") ||
                material.name().contains("CARROT") ||
                material.name().contains("POTATO") ||
                material.name().contains("BEETROOT") ||
                material.name().contains("PUMPKIN") ||
                material.name().contains("MELON") ||
                material.name().contains("SUGAR_CANE") ||
                material.name().contains("BAMBOO") ||
                material.name().contains("SWEET_BERRIES") ||
                material.name().contains("APPLE") ||
                material == Material.BREAD ||
                material == Material.COOKIE ||
                material == Material.CAKE;
    }

    /**
     * Verifica si un material es relacionado con mining
     */
    private boolean isMiningItem(Material material) {
        return material.name().contains("ORE") ||
                material.name().contains("INGOT") ||
                material.name().contains("GEM") ||
                material.name().contains("STONE") ||
                material.name().contains("COBBLESTONE") ||
                material.name().contains("COAL") ||
                material == Material.DIAMOND ||
                material == Material.EMERALD ||
                material == Material.QUARTZ ||
                material == Material.REDSTONE ||
                material == Material.LAPIS_LAZULI ||
                material.name().contains("DEEPSLATE") ||
                material.name().contains("GRANITE") ||
                material.name().contains("DIORITE") ||
                material.name().contains("ANDESITE");
    }

    /**
     * Añade experiencia de farming al jugador
     */
    private void addFarmingExperience(Player player, long xpAmount) {
        if (xpAmount <= 0) return;

        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            long currentXp = userData.getFarmingXP();
            int currentLevel = userData.getFarmingLevel();

            userData.setFarmingXP(currentXp + xpAmount);

            // Verificar si sube de nivel
            int newLevel = calculateLevel(currentXp + xpAmount);
            if (newLevel > currentLevel) {
                userData.setFarmingLevel(newLevel);
                player.sendMessage(ChatColor.GREEN + "🌾 ¡Has subido al nivel " + newLevel + " de Farming!");

                // Disparar evento de level up
                plugin.firePlayerProfessionLevelUpEvent(player, "farming", currentLevel, newLevel);
            }

            plugin.getDatabaseManager().saveUserData(userData);

            // Disparar evento de ganancia de XP
            plugin.firePlayerProfessionXPGainEvent(player, "farming", xpAmount, currentXp + xpAmount);

        } catch (Exception e) {
            plugin.getLogger().warning("Error al añadir experiencia de farming: " + e.getMessage());
        }
    }

    /**
     * Añade experiencia de mining al jugador
     */
    private void addMiningExperience(Player player, long xpAmount) {
        if (xpAmount <= 0) return;

        try {
            UserData userData = plugin.getDatabaseManager().getUserData(player.getUniqueId().toString());
            long currentXp = userData.getMiningXP();
            int currentLevel = userData.getMiningLevel();

            userData.setMiningXP(currentXp + xpAmount);

            // Verificar si sube de nivel
            int newLevel = calculateLevel(currentXp + xpAmount);
            if (newLevel > currentLevel) {
                userData.setMiningLevel(newLevel);
                player.sendMessage(ChatColor.GREEN + "⛏️ ¡Has subido al nivel " + newLevel + " de Mining!");

                // Disparar evento de level up
                plugin.firePlayerProfessionLevelUpEvent(player, "mining", currentLevel, newLevel);
            }

            plugin.getDatabaseManager().saveUserData(userData);

            // Disparar evento de ganancia de XP
            plugin.firePlayerProfessionXPGainEvent(player, "mining", xpAmount, currentXp + xpAmount);

        } catch (Exception e) {
            plugin.getLogger().warning("Error al añadir experiencia de mining: " + e.getMessage());
        }
    }

    /**
     * Calcula el nivel basado en la experiencia total
     */
    private int calculateLevel(long totalXp) {
        // Fórmula básica para calcular nivel: nivel = sqrt(xp/1000) + 1
        // Puedes ajustar esta fórmula según tu sistema de niveles
        return (int) Math.floor(Math.sqrt(totalXp / 1000.0)) + 1;
    }

    /**
     * Convierte ganancias monetarias a experiencia
     */
    private long convertEarningsToExperience(double earnings) {
        // Configuración de conversión de la config
        double conversionRate = plugin.getConfig().getDouble("sellwand.xp_conversion_rate", 0.1);
        long baseXp = Math.round(earnings * conversionRate);

        // Aplicar multiplicador si está configurado
        double multiplier = plugin.getConfig().getDouble("sellwand.xp_multiplier", 1.0);
        return Math.round(baseXp * multiplier);
    }

    /**
     * Remueve items específicos del inventario
     */
    private void removeItemsFromInventory(Inventory inventory, Map<Material, Integer> itemsToRemove) {
        for (Map.Entry<Material, Integer> entry : itemsToRemove.entrySet()) {
            Material material = entry.getKey();
            int amountToRemove = entry.getValue();

            for (int i = 0; i < inventory.getSize() && amountToRemove > 0; i++) {
                ItemStack item = inventory.getItem(i);
                if (item == null || item.getType() != material) continue;

                int itemAmount = item.getAmount();
                if (itemAmount <= amountToRemove) {
                    // Remover item completamente
                    inventory.setItem(i, null);
                    amountToRemove -= itemAmount;
                } else {
                    // Reducir cantidad del item
                    item.setAmount(itemAmount - amountToRemove);
                    amountToRemove = 0;
                }
            }
        }
    }

    /**
     * Envía el reporte de venta al jugador
     */
    private void sendSaleReport(Player player, Map<Material, Integer> itemsSold,
                                Map<Material, Double> itemPrices, double totalEarnings,
                                long experienceGained, int totalItemsSold) {

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        player.sendMessage(ChatColor.GOLD + "         💰 VENTA COMPLETADA 💰");
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════");

        // Mostrar items vendidos (máximo 5 líneas)
        int count = 0;
        for (Map.Entry<Material, Integer> entry : itemsSold.entrySet()) {
            if (count >= 5) {
                int remaining = itemsSold.size() - 5;
                player.sendMessage(ChatColor.GRAY + "... y " + remaining + " tipo(s) más");
                break;
            }

            Material material = entry.getKey();
            int amount = entry.getValue();
            double unitPrice = itemPrices.get(material);
            double totalPrice = unitPrice * amount;

            player.sendMessage(ChatColor.WHITE + "• " + ChatColor.YELLOW + getItemDisplayName(material) +
                    ChatColor.GRAY + " x" + amount + ChatColor.WHITE + " → " +
                    ChatColor.GREEN + priceFormat.format(totalPrice) + " pts");
            count++;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "📊 Total items vendidos: " + ChatColor.AQUA + totalItemsSold);
        player.sendMessage(ChatColor.WHITE + "💎 Valor total: " + ChatColor.GREEN + priceFormat.format(totalEarnings) + " puntos");
        player.sendMessage(ChatColor.WHITE + "⭐ Experiencia ganada: " + ChatColor.YELLOW + experienceGained + " XP");

        // Mostrar distribución de XP si está configurado
        String distribution = plugin.getConfig().getString("sellwand.xp_distribution", "mixed");
        if (!distribution.equals("vanilla")) {
            player.sendMessage(ChatColor.GRAY + "   Distribución: " + getDistributionDisplay(distribution));
        }

        // Mostrar usos restantes de la SellWand
        int usesRemaining = manager.getUsesRemaining(player.getInventory().getItemInMainHand());
        if (usesRemaining > 0) {
            String usesColor = usesRemaining <= 10 ? ChatColor.RED.toString() :
                    usesRemaining <= 25 ? ChatColor.YELLOW.toString() : ChatColor.GREEN.toString();
            player.sendMessage(ChatColor.WHITE + "🪄 Usos restantes: " + usesColor + usesRemaining);
        }

        player.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        player.sendMessage("");
    }

    /**
     * Obtiene el texto de visualización para el tipo de distribución
     */
    private String getDistributionDisplay(String distribution) {
        switch (distribution.toLowerCase()) {
            case "farming_only": return "Solo Farming";
            case "mining_only": return "Solo Mining";
            case "automatic": return "Automática según items";
            case "vanilla": return "Experiencia vanilla";
            default: return "Mixta (Farming + Mining + Vanilla)";
        }
    }

    /**
     * Reproduce efectos visuales y sonoros de éxito
     */
    private void playSuccessEffects(Player player, double totalEarnings) {
        // Reproducir sonido basado en el valor de la venta
        manager.playSound(player, totalEarnings);

        // Efectos de partículas
        if (totalEarnings >= 1000) {
            // Venta muy valiosa - efectos épicos
            player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK,
                    player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                    player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
        } else if (totalEarnings >= 100) {
            // Venta valiosa - efectos buenos
            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                    player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        } else {
            // Venta normal - efectos básicos
            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                    player.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /**
     * Verifica si el jugador puede acceder al contenedor
     */
    private boolean canAccessContainer(Player player, Block block) {
        // Aquí puedes integrar con plugins de protección como WorldGuard, GriefPrevention, etc.
        // Por ahora, implementaremos una verificación básica

        // Verificar si el jugador tiene permisos básicos
        if (!player.hasPermission("survivalcore.sellwand.use")) {
            return false;
        }

        // TODO: Integrar con sistemas de protección de terrenos
        // Ejemplos de integraciones que podrías añadir:

        // WorldGuard integration
        /*
        if (plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            WorldGuardPlugin worldGuard = WorldGuardPlugin.inst();
            LocalPlayer localPlayer = worldGuard.wrapPlayer(player);
            com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(block.getLocation());

            if (!worldGuard.getPlatform().getRegionContainer().createQuery()
                    .testState(loc, localPlayer, Flags.CHEST_ACCESS)) {
                return false;
            }
        }
        */

        // GriefPrevention integration
        /*
        if (plugin.getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
            GriefPrevention griefPrevention = GriefPrevention.instance;
            Claim claim = griefPrevention.dataStore.getClaimAt(block.getLocation(), false, null);

            if (claim != null && claim.allowContainers(player) != null) {
                return false;
            }
        }
        */

        return true;
    }

    /**
     * Obtiene el nombre de visualización de un material
     */
    private String getItemDisplayName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }

        return result.toString();
    }
}