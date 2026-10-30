package nl.crates;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Roulette-animatie. De key is al ingenomen en de winnaar staat bij de start vast;
 * sluit de speler de GUI of verlaat hij de server, dan wordt de reward direct gegeven.
 */
public final class Spin {

    private final CratesPlugin plugin;
    private final CrateManager manager;
    private final Player player;
    private final Crate crate;
    private final Crate.Reward winner;
    private final List<Crate.Reward> seq = new ArrayList<>();
    private final int steps;

    private CrateHolder holder;
    private Inventory inv;
    private BukkitTask task;
    private int step;
    private boolean finished;

    public Spin(CratesPlugin plugin, CrateManager manager, Player player, Crate crate) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.crate = crate;
        this.winner = crate.randomReward();
        this.steps = 28 + ThreadLocalRandom.current().nextInt(8);
        for (int i = 0; i < steps + 9; i++) seq.add(crate.randomReward());
        // in de laatste stand staat de winnaar in het midden van de rij (slot 13)
        seq.set(steps + 4, winner);
    }

    public void start() {
        holder = new CrateHolder(crate, true);
        holder.spin = this;
        inv = Bukkit.createInventory(holder, 27, Msg.c(crate.title));
        holder.inv = inv;

        ItemStack glass = crate.fillerItem();
        for (int i = 0; i < 27; i++) inv.setItem(i, glass.clone());
        ItemStack marker = Crate.pane(Material.LIME_STAINED_GLASS_PANE);
        inv.setItem(4, marker.clone());
        inv.setItem(22, marker.clone());

        manager.spins.put(player.getUniqueId(), this);
        player.openInventory(inv);
        run();
    }

    private void run() {
        if (finished) return;
        for (int i = 0; i < 9; i++) {
            inv.setItem(9 + i, seq.get(step + i).icon().build(1));
        }
        if (step >= steps) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            task = Bukkit.getScheduler().runTaskLater(plugin, () -> finish(true), 30L);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
        int delay = 1 + (step * 6) / steps;
        step++;
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> run(), delay);
    }

    /** Geeft de reward (één keer). closeInv = false als dit vanuit een close/quit event komt. */
    public void finish(boolean closeInv) {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        manager.spins.remove(player.getUniqueId());
        manager.give(player, winner);
        if (closeInv && player.isOnline()) player.closeInventory();
    }

    /** Bij het uitzetten van de plugin: key terug in plaats van een reward. */
    public void abort() {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        manager.spins.remove(player.getUniqueId());
        manager.giveKeys(player, crate, 1);
        if (player.isOnline()) player.closeInventory();
    }
}
