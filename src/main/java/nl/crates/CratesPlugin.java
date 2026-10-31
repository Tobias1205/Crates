package nl.crates;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CratesPlugin extends JavaPlugin {

    private CrateManager manager;

    @Override
    public void onEnable() {
        manager = new CrateManager(this);
        manager.loadAll();

        Bukkit.getPluginManager().registerEvents(new CrateListener(this, manager), this);

        PluginCommand crates = getCommand("crates");
        if (crates != null) {
            CratesCommand handler = new CratesCommand(manager);
            crates.setExecutor(handler);
            crates.setTabCompleter(handler);
        }
        PluginCommand keys = getCommand("keys");
        if (keys != null) keys.setExecutor(new KeysCommand(manager));

        // één tick later, zodat alle werelden zeker geladen zijn
        Bukkit.getScheduler().runTask(this, () -> manager.respawnAll());
        manager.start();
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
