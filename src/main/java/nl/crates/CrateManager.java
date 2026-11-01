package nl.crates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Houdt crates, gekoppelde blokken, hologrammen, keys (physical en virtual) en lopende roulettes bij. */
public final class CrateManager {

    /** Slots in het confirm-menu (3 rijen): rood links, item midden, lime rechts. */
    public static final int CANCEL_SLOT = 11;
    public static final int ITEM_SLOT = 13;
    public static final int CONFIRM_SLOT = 15;

    /** Spelers binnen deze afstand (in blokken) van een crate zien hun eigen hologram. */
    private static final double VIEW_RANGE_SQ = 32.0 * 32.0;
    private static final Pattern CRATE_KEYS = Pattern.compile("%crate_keys_([A-Za-z0-9_-]+)%");

    private static final String[] DEFAULT_CRATES = {"amethyst", "common", "gold", "crimson", "prime"};

    public record BlockPos(String world, int x, int y, int z) {
        static BlockPos of(Block b) {
            return new BlockPos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }

        int cx() {
            return x >> 4;
        }

        int cz() {
            return z >> 4;
        }
    }

    public final Map<UUID, Spin> spins = new HashMap<>();

    private final CratesPlugin plugin;
    private final NamespacedKey keyTag;
    private final VirtualKeys virtual;
    private final KeyAll keyAll;
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final Map<BlockPos, String> links = new LinkedHashMap<>();
    /** Hologrammen die voor iedereen hetzelfde zijn: één entity per hologram. */
    private final Map<BlockPos, List<UUID>> holos = new HashMap<>();
    /** Hologrammen met een key-teller: per blok, per speler een eigen entity (alleen voor die speler zichtbaar). */
    private final Map<BlockPos, Map<UUID, List<UUID>>> personal = new HashMap<>();
    /** Laatst getoonde tekst per hologram-entity, zodat we alleen bijwerken als het getal echt veranderd is. */
    private final Map<UUID, String> lastText = new HashMap<>();
    /** /crates hologram <crate> on|off: wint van hologram.enable in de config. Staat in data.yml. */
    private final Map<String, Boolean> holoOverride = new HashMap<>();
    private final File dataFile;
    private BukkitTask personalTask;

    public CrateManager(CratesPlugin plugin) {
        this.plugin = plugin;
        this.keyTag = new NamespacedKey(plugin, "crate_key");
        this.virtual = new VirtualKeys(plugin);
        this.keyAll = new KeyAll(plugin, this);
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public VirtualKeys virtual() {
        return virtual;
    }

    public KeyAll keyAll() {
        return keyAll;
    }

    /** Start de taak die de hologrammen per speler bijhoudt (elke 0,5 seconde) en de keyall-timer. */
    public void start() {
        personalTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> refreshPersonal(), 20L, 10L);
        keyAll.start();
    }

    // ------------------------------------------------------------------ laden

    public void loadAll() {
        loadCrates();
        loadLinks();
    }

    public void reload() {
        removeAllHolos();
        crates.clear();
        links.clear();
        holoOverride.clear();
        loadAll();
        keyAll.reload();
        Msg.setTheme(plugin.getConfig().getStringList("theme-colors"));
        respawnAll();
    }

    /** Leest alle bestanden in plugins/Crates/crates/. Elke sectie "<id>-crate" is een crate. */
    private void loadCrates() {
        File dir = new File(plugin.getDataFolder(), "crates");
        if (!dir.exists()) {
            dir.mkdirs();
            for (String name : DEFAULT_CRATES) plugin.saveResource("crates/" + name + ".yml", false);
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String key : yml.getKeys(false)) {
                if (!key.endsWith("-crate")) continue;
                ConfigurationSection s = yml.getConfigurationSection(key);
                if (s == null) continue;
                String id = key.substring(0, key.length() - "-crate".length()).toLowerCase(Locale.ROOT);
                Crate c = Crate.load(id, s, plugin.getLogger());
                if (c != null) crates.put(id, c);
            }
        }
        plugin.getLogger().info("Loaded " + crates.size() + " crate(s): " + String.join(", ", crates.keySet()));
    }

    private void loadLinks() {
        if (!dataFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        for (Map<?, ?> m : yml.getMapList("links")) {
            try {
                String world = String.valueOf(m.get("world"));
                int x = ((Number) m.get("x")).intValue();
                int y = ((Number) m.get("y")).intValue();
                int z = ((Number) m.get("z")).intValue();
                String crate = String.valueOf(m.get("crate")).toLowerCase(Locale.ROOT);
                links.put(new BlockPos(world, x, y, z), crate);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped a broken entry in data.yml: " + m);
            }
        }
        ConfigurationSection hs = yml.getConfigurationSection("holograms");
        if (hs != null) {
            for (String k : hs.getKeys(false)) {
                holoOverride.put(k.toLowerCase(Locale.ROOT), hs.getBoolean(k));
            }
        }
    }

    private void saveLinks() {
        YamlConfiguration yml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<BlockPos, String> e : links.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", e.getKey().world());
            m.put("x", e.getKey().x());
            m.put("y", e.getKey().y());
            m.put("z", e.getKey().z());
            m.put("crate", e.getValue());
            list.add(m);
        }
        yml.set("links", list);
        for (Map.Entry<String, Boolean> e : holoOverride.entrySet()) {
            yml.set("holograms." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ crates en links

    public Crate crate(String id) {
        return id == null ? null : crates.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> crateIds() {
        return new ArrayList<>(crates.keySet());
    }

    public boolean isLinked(Block b) {
        return !links.isEmpty() && links.containsKey(BlockPos.of(b));
    }

    public Crate crateAt(Block b) {
        if (links.isEmpty()) return null;
        String id = links.get(BlockPos.of(b));
        return id == null ? null : crates.get(id);
    }

    public Map<BlockPos, String> links() {
        return links;
    }

    public void link(Block b, Crate c) {
        BlockPos p = BlockPos.of(b);
        links.put(p, c.id);
        saveLinks();
        spawnHolos(p);
        refreshPersonal();
    }

    public boolean unlink(Block b) {
        BlockPos p = BlockPos.of(b);
        if (links.remove(p) == null) return false;
        removeHolos(p);
        saveLinks();
        return true;
    }

    // ------------------------------------------------------------------ hologrammen

    /** Bij height "auto": zoveel staat het midden van de onderste regel boven de bovenkant van het blok. */
    private static final double AUTO_GAP = 0.28;

    /** Hoogte van de bovenkant van het blok boven zijn onderkant (chest 0.875, volle blok 1.0). */
    private static double blockTop(World w, BlockPos p) {
        BoundingBox bb = w.getBlockAt(p.x(), p.y(), p.z()).getBoundingBox();
        double top = bb.getMaxY() - p.y();
        if (bb.getHeight() <= 0 || top <= 0.1) return 1.0;
        return Math.min(top, 2.0);
    }

    /** Hoogte boven de onderkant van het blok waarop het midden van deze regel komt. */
    private static double lineHeight(Crate.HoloLine l, double top) {
        return (l.base() != null ? l.base() : top + AUTO_GAP) + l.up();
    }

    /** Staat het hologram van deze crate aan? Het commando wint van hologram.enable in de config. */
    public boolean hologramOn(Crate c) {
        return holoOverride.getOrDefault(c.id, c.hologramEnabled);
    }

    /** /crates hologram <crate> on|off. Blijft bewaard na een herstart. */
    public void setHologram(Crate c, boolean on) {
        holoOverride.put(c.id, on);
        saveLinks();
        for (Map.Entry<BlockPos, String> e : links.entrySet()) {
            if (e.getValue().equals(c.id)) spawnHolos(e.getKey());
        }
        refreshPersonal();
    }

    public void respawnAll() {
        for (BlockPos p : new ArrayList<>(links.keySet())) spawnHolos(p);
        refreshPersonal();
    }

    public void chunkLoaded(Chunk ch) {
        String w = ch.getWorld().getName();
        for (BlockPos p : links.keySet()) {
            if (p.world().equals(w) && p.cx() == ch.getX() && p.cz() == ch.getZ()) {
                Bukkit.getScheduler().runTask(plugin, () -> spawnHolos(p));
            }
        }
    }

    public void chunkUnloaded(Chunk ch) {
        String w = ch.getWorld().getName();
        for (BlockPos p : links.keySet()) {
            if (p.world().equals(w) && p.cx() == ch.getX() && p.cz() == ch.getZ()) removeHolos(p);
        }
    }

    /**
     * Zet de regels neer die voor iedereen gelijk zijn. Elke regel is een eigen text display op een vaste
     * hoogte. Regels met een key-teller doet refreshPersonal.
     */
    private void spawnHolos(BlockPos p) {
        removeHolos(p);
        String id = links.get(p);
        Crate c = id == null ? null : crates.get(id);
        if (c == null || !hologramOn(c)) return;
        World w = Bukkit.getWorld(p.world());
        if (w == null || !w.isChunkLoaded(p.cx(), p.cz())) return;

        double top = blockTop(w, p);
        List<UUID> ids = new ArrayList<>();
        for (Crate.HoloLine l : c.holoLines) {
            if (l.dynamic()) continue;
            ids.add(spawnDisplay(w, p, lineHeight(l, top), l.text(), false).getUniqueId());
        }
        if (!ids.isEmpty()) holos.put(p, ids);
    }

    private TextDisplay spawnDisplay(World w, BlockPos p, double height, String raw, boolean hidden) {
        Component text = component(raw);
        Location loc = new Location(w, p.x() + 0.5, p.y() + height, p.z() + 0.5);
        return w.spawn(loc, TextDisplay.class, d -> {
            // verborgen vanaf het begin, zodat alleen de speler voor wie het bedoeld is het ooit te zien krijgt
            if (hidden) d.setVisibleByDefault(false);
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            // volle helderheid: anders dimt het licht om het blok (nacht, schaduw) de tekst en lijkt de kleur anders
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
        });
    }

    private static Component component(String raw) {
        List<Component> lines = new ArrayList<>();
        for (String l : raw.split("\n", -1)) lines.add(Msg.c(l));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /**
     * Elke 0,5 seconde: spelers binnen 32 blokken van een crate krijgen hun eigen regels met hun eigen
     * aantal keys. Spelers die weglopen, uitloggen of van wereld wisselen raken ze weer kwijt.
     */
    public void refreshPersonal() {
        for (Map.Entry<BlockPos, String> e : links.entrySet()) {
            BlockPos p = e.getKey();
            Crate c = crates.get(e.getValue());
            World w = Bukkit.getWorld(p.world());

            List<Crate.HoloLine> dyn = new ArrayList<>();
            if (c != null && hologramOn(c)) {
                for (Crate.HoloLine l : c.holoLines) {
                    if (l.dynamic()) dyn.add(l);
                }
            }
            if (dyn.isEmpty() || w == null || !w.isChunkLoaded(p.cx(), p.cz())) {
                removePersonal(p);
                continue;
            }

            double top = blockTop(w, p);
            Map<UUID, List<UUID>> views = personal.computeIfAbsent(p, k -> new HashMap<>());
            Location center = new Location(w, p.x() + 0.5, p.y() + 1.0, p.z() + 0.5);
            Set<UUID> near = new HashSet<>();
            for (Player pl : w.getPlayers()) {
                if (pl.getLocation().distanceSquared(center) > VIEW_RANGE_SQ) continue;
                near.add(pl.getUniqueId());
                updateView(pl, w, p, c, dyn, top, views);
            }

            Iterator<Map.Entry<UUID, List<UUID>>> it = views.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, List<UUID>> v = it.next();
                if (!near.contains(v.getKey())) {
                    removeEntities(v.getValue());
                    it.remove();
                }
            }
        }
    }

    private void updateView(Player pl, World w, BlockPos p, Crate c, List<Crate.HoloLine> dyn, double top,
                            Map<UUID, List<UUID>> views) {
        List<UUID> ids = views.get(pl.getUniqueId());
        boolean ok = ids != null && ids.size() == dyn.size();
        if (ok) {
            for (UUID id : ids) {
                Entity en = Bukkit.getEntity(id);
                if (en == null || !en.isValid()) {
                    ok = false;
                    break;
                }
            }
        }

        if (!ok) {
            if (ids != null) removeEntities(ids);
            ids = new ArrayList<>();
            for (Crate.HoloLine l : dyn) {
                String raw = renderRaw(pl, c, l.text());
                TextDisplay td = spawnDisplay(w, p, lineHeight(l, top), raw, true);
                pl.showEntity(plugin, td);
                lastText.put(td.getUniqueId(), raw);
                ids.add(td.getUniqueId());
            }
            views.put(pl.getUniqueId(), ids);
            return;
        }

        for (int i = 0; i < dyn.size(); i++) {
            String raw = renderRaw(pl, c, dyn.get(i).text());
            UUID id = ids.get(i);
            if (raw.equals(lastText.get(id))) continue;
            if (Bukkit.getEntity(id) instanceof TextDisplay td) {
                td.text(component(raw));
                lastText.put(id, raw);
            }
        }
    }

    /** Vult %keys%, %virtual%, %physical% en %crate_keys_<crate>% in met de aantallen van deze speler. */
    private String renderRaw(Player viewer, Crate c, String text) {
        int virt = virtual.get(viewer.getUniqueId(), c.id);
        int phys = physicalKeys(viewer, c);
        String line = text
                .replace("%keys%", String.valueOf(virt + phys))
                .replace("%virtual%", String.valueOf(virt))
                .replace("%physical%", String.valueOf(phys));
        return CRATE_KEYS.matcher(line).replaceAll(m -> {
            Crate other = crates.get(m.group(1).toLowerCase(Locale.ROOT));
            return other == null ? "0" : String.valueOf(totalKeys(viewer, other));
        });
    }

    private void removeEntities(List<UUID> ids) {
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
            lastText.remove(id);
        }
    }

    private void removePersonal(BlockPos p) {
        Map<UUID, List<UUID>> views = personal.remove(p);
        if (views == null) return;
        for (List<UUID> ids : views.values()) removeEntities(ids);
    }

    /** Haalt de persoonlijke hologrammen van één speler weg (bij uitloggen). */
    public void removePlayerViews(UUID player) {
        for (Map<UUID, List<UUID>> views : personal.values()) {
            List<UUID> ids = views.remove(player);
            if (ids != null) removeEntities(ids);
        }
    }

    private void removeHolos(BlockPos p) {
        List<UUID> ids = holos.remove(p);
        if (ids != null) removeEntities(ids);
        removePersonal(p);
    }

    private void removeAllHolos() {
        Set<BlockPos> all = new HashSet<>(holos.keySet());
        all.addAll(personal.keySet());
        for (BlockPos p : all) removeHolos(p);
    }

    // ------------------------------------------------------------------ keys (physical + virtual)

    public ItemStack createKey(Crate c, int amount) {
        ItemStack it = c.key.build(amount);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, c.id);
        it.setItemMeta(meta);
        return it;
    }

    /** Crate-id van een key-item, of null als het geen key is. */
    public String keyId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
    }

    /** Aantal physical keys voor deze crate in de inventory (inclusief offhand). */
    public int physicalKeys(Player p, Crate c) {
        int n = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && c.id.equals(keyId(it))) n += it.getAmount();
        }
        ItemStack off = inv.getItemInOffHand();
        if (c.id.equals(keyId(off))) n += off.getAmount();
        return n;
    }

    /** Virtual + physical keys. */
    public int totalKeys(Player p, Crate c) {
        return virtual.get(p.getUniqueId(), c.id) + physicalKeys(p, c);
    }

    /** Heeft de speler een virtual of physical key voor deze crate? */
    public boolean hasKey(Player p, Crate c) {
        if (virtual.get(p.getUniqueId(), c.id) > 0) return true;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (c.id.equals(keyId(inv.getItem(i)))) return true;
        }
        return c.id.equals(keyId(inv.getItemInOffHand()));
    }

    /** Neemt één key in: eerst een virtual key, anders een physical. false als de speler er geen heeft. */
    public boolean takeKey(Player p, Crate c) {
        if (virtual.take(p.getUniqueId(), c.id)) return true;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && c.id.equals(keyId(it))) {
                inv.setItem(i, shrink(it));
                return true;
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (c.id.equals(keyId(off))) {
            inv.setItemInOffHand(shrink(off));
            return true;
        }
        return false;
    }

    private static ItemStack shrink(ItemStack it) {
        if (it.getAmount() <= 1) return null;
        ItemStack copy = it.clone();
        copy.setAmount(it.getAmount() - 1);
        return copy;
    }

    public void giveKeys(Player p, Crate c, int amount) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(createKey(c, amount));
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ------------------------------------------------------------------ GUI en rewards

    public boolean isBusy(Player p) {
        return spins.containsKey(p.getUniqueId());
    }

    public void open(Player p, Crate c, boolean preview) {
        CrateHolder h = new CrateHolder(c, preview);
        Inventory inv = Bukkit.createInventory(h, c.rows * 9, Msg.c(c.title));
        h.inv = inv;

        if (c.fillerEnabled) {
            ItemStack glass = c.fillerItem();
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass.clone());
        }
        for (Crate.Reward r : c.rewards) {
            inv.setItem(r.slot(), r.icon().build());
        }
        if (c.backEnabled && c.back != null) {
            inv.setItem(c.backSlot, c.back.build(1));
        }
        p.openInventory(inv);
    }

    /** Confirm-menu: de gekozen reward in het midden, rode pane links (terug), lime pane rechts (bevestigen). */
    public void openConfirm(Player p, Crate c, Crate.Reward r) {
        ConfirmHolder h = new ConfirmHolder(c, r);
        String title = "&8" + Msg.smallCaps(Msg.text(p, "confirm_title"));
        Inventory inv = Bukkit.createInventory(h, 27, Msg.c(title));
        h.inv = inv;

        ItemStack bg = Crate.pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg.clone());

        inv.setItem(ITEM_SLOT, r.icon().build());
        inv.setItem(CANCEL_SLOT, button(Material.RED_STAINED_GLASS_PANE, "&c", p, "btn_cancel", "cancel_lore"));
        inv.setItem(CONFIRM_SLOT, button(Material.LIME_STAINED_GLASS_PANE, "&a", p, "btn_confirm", "confirm_lore"));
        p.openInventory(inv);
    }

    private ItemStack button(Material material, String color, Player p, String nameKey, String loreKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.c("<g>" + Msg.smallCaps(Msg.text(p, nameKey)) + "</g>"));
        meta.lore(List.of(Msg.c("&7" + Msg.text(p, loreKey))));
        item.setItemMeta(meta);
        return item;
    }

    /** Neemt een key in en start de roulette. */
    public void startSpin(Player p, Crate c) {
        if (!takeKey(p, c)) {
            Msg.send(p, "no_key", c.keyName());
            return;
        }
        new Spin(plugin, this, p, c).start();
    }

    /**
     * Geeft de reward: de commands uit de config, of (zonder commands) het item zelf.
     * Altijd op de main thread aanroepen.
     */
    public void give(Player p, Crate c, Crate.Reward r) {
        if (r.commands().isEmpty()) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(r.icon().build());
            for (ItemStack rest : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        } else {
            for (String cmd : r.commands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            }
        }
        String name = r.icon().name != null ? r.icon().name : r.icon().material.name();
        // Geeft het command (bijvoorbeeld "tools give") zelf al een melding aan de speler, dan sturen wij er geen
        // tweede. Welke commands dat zijn staat bij silent-commands in config.yml.
        if (!selfAnnouncing(r)) Msg.send(p, "reward", name);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        if (r.icon().broadcast) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                Msg.send(online, "broadcast", p.getName(), name, c.displayName());
            }
        }
    }

    /** true als alle commands van deze reward van een plugin zijn die zelf een melding aan de speler stuurt. */
    private boolean selfAnnouncing(Crate.Reward r) {
        if (r.commands().isEmpty()) return false;
        List<String> silent = plugin.getConfig().contains("silent-commands")
                ? plugin.getConfig().getStringList("silent-commands")
                : List.of("tools");
        for (String cmd : r.commands()) {
            String label = cmd.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            int colon = label.indexOf(':');
            if (colon >= 0) label = label.substring(colon + 1);
            boolean found = false;
            for (String s : silent) {
                if (s != null && s.trim().equalsIgnoreCase(label)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    public void shutdown() {
        if (personalTask != null) personalTask.cancel();
        for (Spin s : new ArrayList<>(spins.values())) s.abort();
        removeAllHolos();
        keyAll.shutdown();
        virtual.shutdown();
    }
}
