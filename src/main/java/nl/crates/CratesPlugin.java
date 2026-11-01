package nl.crates;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CratesPlugin extends JavaPlugin {

    private CrateManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // config.yml (keyall) wordt alleen aangemaakt als hij er nog niet is
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

        // %keyall_<crate>% bestaat alleen als PlaceholderAPI op de server staat
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            KeyAllExpansion.hook(manager.keyAll());
            getLogger().info("PlaceholderAPI found: %keyall_<crate>% is available.");
        } else {
            getLogger().info("PlaceholderAPI not found: %keyall_<crate>% is not available (keyall itself works).");
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
