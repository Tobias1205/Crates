package nl.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Het confirm-menu: gekozen reward in het midden, rood = terug, lime = bevestigen. */
public final class ConfirmHolder implements InventoryHolder {

    public final Crate crate;
    public final Crate.Reward reward;
    public Inventory inv;
    public boolean used;

    public ConfirmHolder(Crate crate, Crate.Reward reward) {
        this.crate = crate;
        this.reward = reward;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
