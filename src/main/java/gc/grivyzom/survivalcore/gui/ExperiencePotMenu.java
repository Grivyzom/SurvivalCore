package gc.grivyzom.survivalcore.gui;

import gc.grivyzom.survivalcore.Main;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

public class ExperiencePotMenu implements Listener {

    private static final Main plugin = Main.getPlugin(Main.class);

    private static final NamespacedKey keyLevels   = new NamespacedKey(plugin, "levels");      // para los botones
    private static final NamespacedKey keyAction   = new NamespacedKey(plugin, "action-type"); // tipo de acción (depositar/retirar)
    private static final NamespacedKey keyUpgrade  = new NamespacedKey(plugin, "upgrade-btn"); // señalamos el botón de upgrade
    private static final NamespacedKey keyIsPot    = new NamespacedKey(plugin, "is_xp_pot");
    private static final NamespacedKey keyAllXp    = new NamespacedKey(plugin, "all-xp-btn");  // botón para retirar/depositar todo

    private static final String TITLE = ChatColor.DARK_GREEN + "⚗ Banco de Experiencia ⚗";
    private static final Map<UUID, Location> potLocations = new HashMap<>();

    /* ================================= MENÚ ================================= */

    public static void open(Player p, org.bukkit.block.Block potBlock) {
        BlockState bs = potBlock.getState();
        if (!(bs instanceof TileState ts)) return;
        if (!ts.getPersistentDataContainer().has(keyIsPot, PersistentDataType.BYTE)) return;

        potLocations.put(p.getUniqueId(), potBlock.getLocation());
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Llenar bordes con cristal verde
        ItemStack borderItem = createBorderItem();
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, borderItem);
            }
        }

        // Espacio central para botones
        // Retirar
        inv.setItem(10, createButton(Material.RED_DYE, ChatColor.RED + "Retirar 1 nivel", 1, "withdraw"));
        inv.setItem(11, createButton(Material.RED_DYE, ChatColor.RED + "Retirar 5 niveles", 5, "withdraw"));
        inv.setItem(12, createButton(Material.RED_DYE, ChatColor.RED + "Retirar 10 niveles", 10, "withdraw"));

        // Botón para retirar todo
        inv.setItem(13, createAllButton(false));

        // Info del banco
        inv.setItem(4, updateInfoItem(p));

        // Guardar
        inv.setItem(14, createButton(Material.LIME_DYE, ChatColor.GREEN + "Guardar 1 nivel", 1, "deposit"));
        inv.setItem(15, createButton(Material.LIME_DYE, ChatColor.GREEN + "Guardar 5 niveles", 5, "deposit"));
        inv.setItem(16, createButton(Material.LIME_DYE, ChatColor.GREEN + "Guardar 10 niveles", 10, "deposit"));

        // Botón de mejora en el centro abajo
        inv.setItem(22, createUpgradeButton(p));

        // Botón para depositar todo
        inv.setItem(21, createAllButton(true));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.2f);
    }

    private static ItemStack createBorderItem() {
        ItemStack border = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);
        return border;
    }

    private static ItemStack createButton(Material mat, String name, int levels, String actionType) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        String action = actionType.equals("withdraw") ? "retirar" : "depositar";
        lore.add(ChatColor.GRAY + "Haz clic para " + action + " " + levels + (levels == 1 ? " nivel" : " niveles"));
        lore.add(ChatColor.GRAY + "Shift+Clic para " + action + " x5");
        m.setLore(lore);

        m.getPersistentDataContainer().set(keyLevels, PersistentDataType.INTEGER, levels);
        m.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, actionType);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack createAllButton(boolean isDeposit) {
        ItemStack it = new ItemStack(isDeposit ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName((isDeposit ? ChatColor.GREEN + "Depositar TODOS los niveles" : ChatColor.RED + "Retirar TODA la experiencia"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Haz clic para " + (isDeposit ? "depositar" : "retirar") + " toda tu experiencia");
        m.setLore(lore);

        m.getPersistentDataContainer().set(keyAllXp, PersistentDataType.BYTE, (byte)1);
        m.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, isDeposit ? "deposit" : "withdraw");
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack createUpgradeButton(Player p) {
        long capXp   = plugin.getDatabaseManager().getBankCapacity(p.getUniqueId().toString());
        int  capLv   = (int)(capXp / 68L);
        int  bankLvl = capLv / 2500;

        ItemStack up = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
        ItemMeta  im = up.getItemMeta();
        im.setDisplayName(ChatColor.GOLD + "⬆ " + ChatColor.BOLD + "Mejorar tú Banco de Exp" + ChatColor.GOLD + " ⬆");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Capacidad: " + ChatColor.YELLOW + capLv + " niveles");
        if (bankLvl < 20) {
            int costLv = 500 + 250 * bankLvl;
            lore.add("");
            lore.add(ChatColor.WHITE + "Coste mejora: " + ChatColor.GOLD + costLv + ChatColor.GREEN +" niveles");
            lore.add(ChatColor.GREEN.BOLD + "Haz clic para mejorar");
        } else {
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "¡Nivel máximo alcanzado!");
        }
        im.setLore(lore);
        im.getPersistentDataContainer().set(keyUpgrade, PersistentDataType.BYTE, (byte)1);
        up.setItemMeta(im);
        return up;
    }

    private static ItemStack updateInfoItem(Player p) {
        long storedXp = plugin.getDatabaseManager().getBankedXp(p.getUniqueId().toString());
        long storedLv = storedXp / 68L;

        long capXp   = plugin.getDatabaseManager().getBankCapacity(p.getUniqueId().toString());
        long capLv   = capXp / 68L;
        int  bankLvl = (int)(capLv / 2500);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta  im   = info.getItemMeta();
        im.setDisplayName(ChatColor.GOLD + "✦ Información del Banco ✦");

        double fillPercentage = (double) storedXp / capXp;
        String progressBar = getProgressBar(storedXp, capXp, 20, '█', ChatColor.YELLOW, ChatColor.GRAY);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.WHITE + "Capacidad: " + ChatColor.YELLOW + capLv + " niveles" + ChatColor.WHITE + " (" + capXp + " XP)");
        lore.add(ChatColor.WHITE + "Nivel Banco: " + ChatColor.GOLD + bankLvl + "/20");
        lore.add("");
        lore.add(ChatColor.WHITE + "Almacenado: " + ChatColor.YELLOW + storedLv + " niveles" + ChatColor.WHITE + " (" + storedXp + " XP)");
        lore.add("");
        lore.add(ChatColor.WHITE + "Progreso: " + progressBar + " " + String.format("%.1f%%", fillPercentage * 100));
        im.setLore(lore);
        info.setItemMeta(im);
        return info;
    }

    private static String getProgressBar(long current, long max, int barLength, char symbol, ChatColor completedColor, ChatColor remainingColor) {
        StringBuilder bar = new StringBuilder();
        int completed = Math.min((int) ((current * barLength) / max), barLength);

        bar.append(completedColor);
        for (int i = 0; i < completed; i++) {
            bar.append(symbol);
        }

        bar.append(remainingColor);
        for (int i = completed; i < barLength; i++) {
            bar.append(symbol);
        }

        return bar.toString();
    }

    private static String getProgressBar(int current, int max, int barLength, char symbol, ChatColor completedColor, ChatColor remainingColor) {
        return getProgressBar((long)current, (long)max, barLength, symbol, completedColor, remainingColor);
    }

    /* ============================= EVENTOS ============================= */

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player p = (Player) e.getWhoClicked();
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1) ¿Es botón de upgrade?
        if (pdc.has(keyUpgrade, PersistentDataType.BYTE)) {
            plugin.getCommand("score").execute(p, "score", new String[]{"xpbank","upgrade"});

            // Efecto visual y sonoro de mejora
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            p.spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

            // Refrescar botones
            e.getInventory().setItem(22, createUpgradeButton(p));
            e.getInventory().setItem(4, updateInfoItem(p));
            return;
        }

        // 2) ¿Es botón para retirar/depositar todo?
        if (pdc.has(keyAllXp, PersistentDataType.BYTE)) {
            String action = pdc.get(keyAction, PersistentDataType.STRING);
            Location potLoc = potLocations.get(p.getUniqueId());
            World w = p.getWorld();

// Depositar TODO
            if ("deposit".equals(action)) {
                int playerLv = p.getLevel();
                if (playerLv <= 0) {
                    p.sendMessage(ChatColor.RED + "No tienes niveles para guardar.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                    return;
                }

                long xpPlayer  = playerLv * 68L;
                long storedXp  = plugin.getDatabaseManager()
                        .addXpCapped(p.getUniqueId().toString(), xpPlayer);

                if (storedXp == 0) {               // no quedó espacio
                    p.sendMessage(ChatColor.RED + "Has alcanzado el límite de tu banco.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                    return;
                }

                int storedLv   = (int) (storedXp / 68L);
                p.setLevel(playerLv - storedLv);   // restamos sólo lo que se guardó
                p.setExp(0);

                playEffect(w, potLoc, p, true);
                p.sendMessage(ChatColor.GREEN + "Has guardado " + storedLv +
                        " niveles (" + storedXp + " XP).");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            }else {
                // Retirar todo
                long storedXp = plugin.getDatabaseManager().getBankedXp(p.getUniqueId().toString());
                if (storedXp <= 0) {
                    p.sendMessage(ChatColor.RED + "No tienes experiencia almacenada.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                    return;
                }

                if (!plugin.getDatabaseManager().withdrawBankedXp(p.getUniqueId().toString(), storedXp)) {
                    p.sendMessage(ChatColor.RED + "Error al retirar experiencia. Intenta de nuevo.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                    return;
                }

                long levels = storedXp / 68L;
                int leftover = (int) (storedXp % 68L);

                if (levels > 0) p.giveExpLevels((int) levels);
                if (leftover > 0) p.giveExp(leftover);

                playEffect(w, potLoc, p, false);
                p.sendMessage(ChatColor.GREEN + "Has retirado " + storedXp + " puntos de XP (" + levels + " niveles).");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            }

            // Refrescar información
            e.getInventory().setItem(4, updateInfoItem(p));
            return;
        }

        // 3) Botones guardar / retirar niveles específicos
        Integer lv = pdc.get(keyLevels, PersistentDataType.INTEGER);
        String action = pdc.get(keyAction, PersistentDataType.STRING);
        if (lv == null || action == null) return;

        boolean shift = e.isShiftClick();
        int levelsToProcess = shift ? lv * 5 : lv;

        Location potLoc = potLocations.get(p.getUniqueId());
        World w = p.getWorld();

        if ("withdraw".equals(action)) {
            long xpAmount = levelsToProcess * 68L;
            boolean ok = plugin.getDatabaseManager()
                    .withdrawBankedXp(p.getUniqueId().toString(), xpAmount);
            if (!ok) {
                p.sendMessage(ChatColor.RED + "No tienes suficiente XP almacenada.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
            p.giveExpLevels(levelsToProcess);
            playEffect(w, potLoc, p, false);
            p.sendMessage(ChatColor.GREEN + "Has retirado " + levelsToProcess + " niveles.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        } else if ("deposit".equals(action)) {
            if (p.getLevel() < levelsToProcess) {
                p.sendMessage(ChatColor.RED + "No tienes suficientes niveles.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
            long xpAmount = levelsToProcess * 68L;
            boolean ok = plugin.getDatabaseManager()
                    .updateBankedXp(p.getUniqueId().toString(), xpAmount);
            if (!ok) {
                p.sendMessage(ChatColor.RED + "Has alcanzado el límite de tu banco.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
            p.setLevel(p.getLevel() - levelsToProcess);
            playEffect(w, potLoc, p, true);
            p.sendMessage(ChatColor.GREEN + "Has guardado " + levelsToProcess + " niveles.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }

        // Refrescar info
        e.getInventory().setItem(4, updateInfoItem(p));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        potLocations.remove(e.getPlayer().getUniqueId());

        // Casteamos a Player antes de llamar a playSound
        if (e.getPlayer() instanceof Player) {
            Player p = (Player) e.getPlayer();
            p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 1.2f);
        }
    }


    /* ======================= UTILIDADES INTERNAS ======================= */

    private static void playEffect(World w, Location potLoc, Player p, boolean deposit) {
        if (potLoc == null) return;
        Location potCenter = potLoc.clone().add(0.5, 0.7, 0.5);
        Location playerLoc = p.getLocation().add(0, 1.0, 0);
        Vector dir = deposit
                ? potCenter.toVector().subtract(playerLoc.toVector())
                : playerLoc.toVector().subtract(potCenter.toVector());
        int steps = 10; // Más pasos para un efecto más fluido

        // Programar la animación para que se ejecute a lo largo del tiempo
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location point = (deposit ? playerLoc : potCenter).clone()
                        .add(dir.clone().multiply((double) step / steps));
                w.spawnParticle(
                        deposit ? Particle.VILLAGER_HAPPY : Particle.ENCHANTMENT_TABLE,
                        point, 2, 0.05, 0.05, 0.05, 0.01
                );
            }, i);
        }

        // Efecto final en destino
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            w.spawnParticle(
                    deposit ? Particle.CLOUD : Particle.ENCHANTMENT_TABLE,
                    deposit ? potCenter : playerLoc,
                    15, 0.2, 0.1, 0.2, 0.01
            );
            w.playSound(
                    deposit ? potCenter : playerLoc,
                    deposit ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_PLAYER_LEVELUP,
                    0.8f, 1.0f
            );
        }, steps + 1);
    }
}