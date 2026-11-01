package nl.crates;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

public final class CrateListener implements Listener {

    private final CratesPlugin plugin;
    private final CrateManager manager;

    public CrateListener(CratesPlugin plugin, CrateManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ------------------------------------------------------------------ blok gebruiken

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || !manager.isLinked(b)) return;

        // het blok is een crate: nooit de normale actie laten doorgaan (plaatsen, openen, breken)
        e.setCancelled(true);

        Crate c = manager.crateAt(b);
        if (c == null) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("crates.use")) {
            Msg.send(p, "no_permission");
            return;
        }
        if (manager.isBusy(p)) {
            Msg.send(p, "busy");
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            manager.open(p, c, true);
            return;
        }

        if (c.effect == Crate.Effect.CHOOSE) {
            manager.open(p, c, false);
        } else if (manager.hasKey(p, c)) {
            manager.startSpin(p, c);
        } else {
            Msg.send(p, "no_key", c.keyName());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            manager.open(p, c, true);
        }
    }

    // ------------------------------------------------------------------ GUI

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();

        // --- confirm-menu
        if (holder instanceof ConfirmHolder ch) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player clicker)) return;
            if (e.getClickedInventory() != e.getInventory() || ch.used) return;
            int slot = e.getSlot();
            if (slot == CrateManager.CONFIRM_SLOT) {
                confirm(clicker, ch);
            } else if (slot == CrateManager.CANCEL_SLOT) {
                ch.used = true;
                Bukkit.getScheduler().runTask(plugin, () -> manager.open(clicker, ch.crate, false));
            }
            return;
        }

        // --- crate-GUI
        if (!(holder instanceof CrateHolder h)) return;
        // alles in en rond de crate-GUI is vergrendeld (ook shift-click vanuit de eigen inventory)
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (h.spin != null) return;

        int slot = e.getSlot();
        Crate c = h.crate;

        if (c.backEnabled && slot == c.backSlot) {
            Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            return;
        }
        if (h.preview) return;

        Crate.Reward r = c.rewardAt(slot);
        if (r == null) return;

        // eerst controleren of de speler een key heeft; de key wordt pas bij "bevestigen" ingenomen
        if (!manager.hasKey(p, c)) {
            Msg.send(p, "no_key", c.keyName());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> manager.openConfirm(p, c, r));
    }

    private void confirm(Player p, ConfirmHolder ch) {
        ch.used = true;
        if (manager.takeKey(p, ch.crate)) {
            manager.give(p, ch.crate, ch.reward);
        } else {
            // de key is ondertussen weg (bijvoorbeeld weggegooid)
            Msg.send(p, "no_key", ch.crate.keyName());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        }
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof CrateHolder || holder instanceof ConfirmHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof CrateHolder h && h.spin != null) {
            h.spin.finish(false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Spin s = manager.spins.get(e.getPlayer().getUniqueId());
        if (s != null) s.finish(false);
        manager.removePlayerViews(e.getPlayer().getUniqueId());
        manager.keyAll().quit(e.getPlayer());
        manager.virtual().evict(e.getPlayer().getUniqueId());
    }

    /** De keyall-timer van deze speler gaat verder waar hij bleef toen de speler wegging. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.keyAll().join(e.getPlayer());
    }

    // ------------------------------------------------------------------ keys en bescherming

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (manager.keyId(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (!manager.isLinked(e.getBlock())) return;
        e.setCancelled(true);
        if (e.getPlayer().hasPermission("crates.admin")) Msg.send(e.getPlayer(), "break_denied");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(manager::isLinked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(manager::isLinked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(manager::isLinked)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(manager::isLinked)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ hologrammen

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        manager.chunkLoaded(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        manager.chunkUnloaded(e.getChunk());
    }
}
