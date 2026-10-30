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

        PluginCommand command = getCommand("crates");
        if (command != null) {
            CratesCommand handler = new CratesCommand(this, manager);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        // één tick later, zodat alle werelden zeker geladen zijn
        Bukkit.getScheduler().runTask(this, () -> manager.respawnAll());
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
