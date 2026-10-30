package nl.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Markeert een inventory als crate-GUI, zodat clicks herkend en geblokkeerd kunnen worden. */
public final class CrateHolder implements InventoryHolder {

    public final Crate crate;
    /** true = alleen bekijken (linksklik op het blok of roulette zonder key). */
    public final boolean preview;
    public Inventory inv;
    public Spin spin;
    public boolean used;

    public CrateHolder(Crate crate, boolean preview) {
        this.crate = crate;
        this.preview = preview;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
