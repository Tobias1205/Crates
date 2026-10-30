package nl.crates;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Virtual keys, één bestand per speler (keys/&lt;uuid&gt;.yml). Online spelers staan in het geheugen,
 * wijzigingen worden op een aparte thread (in volgorde) weggeschreven, zodat de server er niet door vertraagt.
 */
public final class VirtualKeys {

    private final CratesPlugin plugin;
    private final File dir;
    /** Alleen op de main thread aanraken. */
    private final Map<UUID, Map<String, Integer>> cache = new HashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "Crates-IO"));

    public VirtualKeys(CratesPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "keys");
        this.dir.mkdirs();
    }

    public int get(UUID id, String crate) {
        return data(id).getOrDefault(crate, 0);
    }

    /** Past het aantal aan (negatief = afnemen). Werkt ook voor spelers die offline zijn. */
    public void add(UUID id, String crate, int amount) {
        Map<String, Integer> m = data(id);
        int n = m.getOrDefault(crate, 0) + amount;
        if (n <= 0) m.remove(crate);
        else m.put(crate, n);
        Map<String, Integer> snapshot = new HashMap<>(m);
        io.execute(() -> write(id, snapshot));
        if (Bukkit.getPlayer(id) == null) cache.remove(id);
    }

    /** Neemt één virtual key in. false als de speler er geen heeft. */
    public boolean take(UUID id, String crate) {
        if (get(id, crate) <= 0) return false;
        add(id, crate, -1);
        return true;
    }

    /** Haalt een speler uit het geheugen (bij uitloggen). */
    public void evict(UUID id) {
        cache.remove(id);
    }

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Saving virtual keys took too long.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Integer> data(UUID id) {
        return cache.computeIfAbsent(id, this::read);
    }

    private Map<String, Integer> read(UUID id) {
        flush();
        Map<String, Integer> out = new HashMap<>();
        File file = new File(dir, id + ".yml");
        if (!file.exists()) return out;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("keys");
        if (s == null) return out;
        for (String key : s.getKeys(false)) {
            int n = s.getInt(key);
            if (n > 0) out.put(key, n);
        }
        return out;
    }

    /** Wacht tot alle wijzigingen die nog onderweg zijn naar schijf geschreven zijn. */
    private void flush() {
        try {
            io.submit(() -> {
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // niet kritiek: we lezen dan hooguit een iets oudere stand
        }
    }

    private void write(UUID id, Map<String, Integer> snapshot) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
            y.set("keys." + e.getKey(), e.getValue());
        }
        try {
            y.save(new File(dir, id + ".yml"));
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save virtual keys of " + id + ": " + ex.getMessage());
        }
    }
}
