package nl.crates;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyall: spelers krijgen keys voor de tijd dat ze online zijn. De timer is per speler en per key.
 * Hij loopt alleen als de speler online is: bij weggaan stopt hij, bij terugkomen gaat hij verder waar hij was.
 * De voortgang staat per speler in keyall/&lt;uuid&gt;.yml (op een aparte thread weggeschreven).
 */
public final class KeyAll {

    /** Eén key die uitgedeeld wordt: welke crate, hoe vaak, hoeveel en virtual of physical. */
    public record Entry(String crate, long intervalMs, int amount, boolean virtual) {
    }

    private static final class Data {
        /** crate-id -> milliseconden online sinds de vorige key. Wordt ook door andere threads gelezen (PlaceholderAPI). */
        final Map<String, Long> progress = new ConcurrentHashMap<>();
        /** Tijdstip van de vorige controle. Alleen op de main thread aanraken. */
        long last;
    }

    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);
    /** Hoeveel tijd een enkele controle hoogstens meetelt (beschermt tegen een verstelde klok). */
    private static final long MAX_STEP_MS = 30_000L;
    private static final long MIN_INTERVAL_MS = 10_000L;

    private final CratesPlugin plugin;
    private final CrateManager manager;
    private final File dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "Crates-KeyAll-IO"));
    private final Map<UUID, Data> data = new ConcurrentHashMap<>();
    private volatile Map<String, Entry> entries = Map.of();
    private BukkitTask task;
    private int seconds;

    public KeyAll(CratesPlugin plugin, CrateManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.dir = new File(plugin.getDataFolder(), "keyall");
        this.dir.mkdirs();
    }

    // ------------------------------------------------------------------ starten en config

    public void start() {
        loadEntries();
        for (Player p : Bukkit.getOnlinePlayers()) join(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    /** Leest config.yml opnieuw (en de crates moeten dan al geladen zijn). */
    public void reload() {
        plugin.reloadConfig();
        loadEntries();
    }

    private void loadEntries() {
        FileConfiguration cfg = plugin.getConfig();
        Map<String, Entry> out = new LinkedHashMap<>();
        if (cfg.getBoolean("keyall.enabled", true)) {
            ConfigurationSection keys = cfg.getConfigurationSection("keyall.keys");
            if (keys != null) {
                for (String id : keys.getKeys(false)) {
                    ConfigurationSection s = keys.getConfigurationSection(id);
                    if (s == null) continue;
                    String crateId = id.toLowerCase(Locale.ROOT);
                    if (manager.crate(crateId) == null) {
                        plugin.getLogger().warning("keyall: unknown crate '" + id + "', skipped.");
                        continue;
                    }
                    long interval = parseDuration(s.getString("interval", "1h"));
                    if (interval < MIN_INTERVAL_MS) {
                        plugin.getLogger().warning("keyall: invalid interval for '" + id
                                + "' (use for example 30m, 1h or 2h30m, at least 10s), skipped.");
                        continue;
                    }
                    int amount = Math.max(1, Math.min(2304, s.getInt("amount", 1)));
                    boolean virtual = !"physical".equalsIgnoreCase(s.getString("type", "virtual"));
                    out.put(crateId, new Entry(crateId, interval, amount, virtual));
                }
            }
        }
        entries = out;
        plugin.getLogger().info("keyall: " + out.size() + " key timer(s) active"
                + (out.isEmpty() ? "" : ": " + String.join(", ", out.keySet())));
    }

    // ------------------------------------------------------------------ spelers

    public void join(Player p) {
        data.put(p.getUniqueId(), load(p.getUniqueId()));
    }

    public void quit(Player p) {
        Data d = data.remove(p.getUniqueId());
        if (d == null) return;
        // de tijd sinds de laatste controle telt nog mee
        advance(d, System.currentTimeMillis(), entries.values(), null);
        save(p.getUniqueId(), d);
    }

    /** Elke seconde: tijd bijtellen voor elke online speler en keys geven als het zover is. */
    private void tick() {
        Map<String, Entry> es = entries;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Data d = data.get(p.getUniqueId());
            if (d == null) {
                d = load(p.getUniqueId());
                data.put(p.getUniqueId(), d);
            }
            advance(d, now, es.values(), p);
        }
        seconds++;
        if (seconds % 60 == 0) saveAll();
    }

    /** Telt de tijd sinds de vorige controle bij. Met een speler erbij worden de verdiende keys ook gegeven. */
    private void advance(Data d, long now, Iterable<Entry> es, Player award) {
        long delta = Math.max(0L, Math.min(now - d.last, MAX_STEP_MS));
        d.last = now;
        for (Entry e : es) {
            long prog = d.progress.getOrDefault(e.crate(), 0L) + delta;
            long times = 0;
            if (award != null && prog >= e.intervalMs()) {
                times = Math.min(prog / e.intervalMs(), 1000L);
                prog -= times * e.intervalMs();
            }
            d.progress.put(e.crate(), prog);
            if (times > 0) give(award, e, (int) Math.min(times * e.amount(), 100_000L));
        }
    }

    private void give(Player p, Entry e, int total) {
        Crate c = manager.crate(e.crate());
        if (c == null) return;
        if (e.virtual()) {
            manager.virtual().add(p.getUniqueId(), c.id, total);
        } else {
            manager.giveKeys(p, c, total);
        }
        // melding: een klein kader in de chat, plus dezelfde regel kort in de actionbar
        String line = "<g>+" + total + "x</g> " + c.keyName();
        p.sendMessage(Msg.c("&8&m      &r <g>" + Msg.smallCaps("keyall") + "</g> &8&m      "));
        p.sendMessage(Msg.c("  " + line));
        p.sendMessage(Msg.c("  " + Msg.text(p, "keyall_next", formatInterval(e.intervalMs()))));
        p.sendMessage(Msg.c("&8&m                    "));
        p.sendActionBar(Msg.c(line));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    /** Korte schrijfwijze van het interval: 1h, 30m, 2h 30m. */
    static String formatInterval(long ms) {
        long s = ms / 1000L;
        StringBuilder sb = new StringBuilder();
        long d = s / 86400L, h = (s % 86400L) / 3600L, m = (s % 3600L) / 60L, sec = s % 60L;
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0) sb.append(sec).append("s ");
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ placeholder

    /**
     * Tijd tot de volgende key van deze crate, bijvoorbeeld "19m 13s". Wordt door PlaceholderAPI aangeroepen
     * (ook vanaf andere threads). null als er geen keyall voor deze crate is.
     */
    public String placeholder(UUID player, String crate) {
        Entry e = entries.get(crate.toLowerCase(Locale.ROOT));
        if (e == null) return null;
        Data d = data.get(player);
        long done = d == null ? 0L : d.progress.getOrDefault(e.crate(), 0L);
        return formatTime(Math.max(0L, e.intervalMs() - done));
    }

    /** 1h 5m 3s / 19m 13s / 42s. Rondt naar boven af, zodat het nooit op 0s blijft staan. */
    static String formatTime(long ms) {
        long s = (ms + 999L) / 1000L;
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        if (h > 0) return h + "h " + m + "m " + sec + "s";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    /** "1h", "30m", "2h30m", "90s", "1d" -> milliseconden. -1 als het niet klopt. */
    static long parseDuration(String raw) {
        if (raw == null) return -1L;
        String t = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (t.isEmpty()) return -1L;
        long total = 0L;
        int pos = 0;
        Matcher m = DURATION_PART.matcher(t);
        try {
            while (m.find()) {
                if (m.start() != pos) return -1L;
                long n = Long.parseLong(m.group(1));
                long unit = switch (m.group(2).charAt(0)) {
                    case 'd' -> 86_400_000L;
                    case 'h' -> 3_600_000L;
                    case 'm' -> 60_000L;
                    default -> 1_000L;
                };
                total += n * unit;
                pos = m.end();
            }
        } catch (NumberFormatException ex) {
            return -1L;
        }
        return pos == t.length() ? total : -1L;
    }

    // ------------------------------------------------------------------ opslaan

    private Data load(UUID id) {
        flush();
        Data d = new Data();
        d.last = System.currentTimeMillis();
        File f = new File(dir, id + ".yml");
        if (f.exists()) {
            ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection("progress");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    long v = s.getLong(k);
                    if (v > 0) d.progress.put(k.toLowerCase(Locale.ROOT), v);
                }
            }
        }
        return d;
    }

    private void saveAll() {
        for (Map.Entry<UUID, Data> e : data.entrySet()) save(e.getKey(), e.getValue());
    }

    private void save(UUID id, Data d) {
        Map<String, Long> snapshot = new HashMap<>(d.progress);
        io.execute(() -> write(id, snapshot));
    }

    private void write(UUID id, Map<String, Long> snapshot) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Long> e : snapshot.entrySet()) {
            y.set("progress." + e.getKey(), e.getValue());
        }
        try {
            y.save(new File(dir, id + ".yml"));
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save keyall progress of " + id + ": " + ex.getMessage());
        }
    }

    /** Wacht tot alles wat nog naar schijf moet geschreven is, zodat we nooit een oude stand lezen. */
    private void flush() {
        try {
            io.submit(() -> {
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // niet kritiek
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        // laatste tijd bijtellen (zonder keys te geven) en alles opslaan
        long now = System.currentTimeMillis();
        for (Data d : data.values()) advance(d, now, entries.values(), null);
        saveAll();
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Saving keyall progress took too long.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
