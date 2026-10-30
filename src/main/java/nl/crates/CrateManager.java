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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Houdt crates, gekoppelde blokken, hologrammen, keys (physical en virtual) en lopende roulettes bij. */
public final class CrateManager {

    /** Slots in het confirm-menu (3 rijen): rood links, item midden, lime rechts. */
    public static final int CANCEL_SLOT = 11;
    public static final int ITEM_SLOT = 13;
    public static final int CONFIRM_SLOT = 15;

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
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final Map<BlockPos, String> links = new LinkedHashMap<>();
    private final Map<BlockPos, List<UUID>> holos = new HashMap<>();
    private final File dataFile;

    public CrateManager(CratesPlugin plugin) {
        this.plugin = plugin;
        this.keyTag = new NamespacedKey(plugin, "crate_key");
        this.virtual = new VirtualKeys(plugin);
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public VirtualKeys virtual() {
        return virtual;
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
        loadAll();
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
    }

    public boolean unlink(Block b) {
        BlockPos p = BlockPos.of(b);
        if (links.remove(p) == null) return false;
        removeHolos(p);
        saveLinks();
        return true;
    }

    // ------------------------------------------------------------------ hologrammen

    public void respawnAll() {
        for (BlockPos p : new ArrayList<>(links.keySet())) spawnHolos(p);
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

    private void spawnHolos(BlockPos p) {
        removeHolos(p);
        String id = links.get(p);
        Crate c = id == null ? null : crates.get(id);
        if (c == null || c.holograms.isEmpty()) return;
        World w = Bukkit.getWorld(p.world());
        if (w == null || !w.isChunkLoaded(p.cx(), p.cz())) return;

        List<UUID> ids = new ArrayList<>();
        for (Crate.Holo h : c.holograms) {
            List<Component> lines = new ArrayList<>();
            for (String l : h.lines()) lines.add(Msg.c(l));
            Component text = Component.join(JoinConfiguration.newlines(), lines);
            Location loc = new Location(w, p.x() + 0.5, p.y() + h.height(), p.z() + 0.5);
            TextDisplay td = w.spawn(loc, TextDisplay.class, d -> {
                d.text(text);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setPersistent(false);
            });
            ids.add(td.getUniqueId());
        }
        if (!ids.isEmpty()) holos.put(p, ids);
    }

    private void removeHolos(BlockPos p) {
        List<UUID> ids = holos.remove(p);
        if (ids == null) return;
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
    }

    private void removeAllHolos() {
        for (BlockPos p : new ArrayList<>(holos.keySet())) removeHolos(p);
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
        meta.displayName(Msg.c(color + Msg.smallCaps(Msg.text(p, nameKey))));
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
        Msg.send(p, "reward", name);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        if (r.icon().broadcast) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                Msg.send(online, "broadcast", p.getName(), name, c.displayName());
            }
        }
    }

    public void shutdown() {
        for (Spin s : new ArrayList<>(spins.values())) s.abort();
        removeAllHolos();
        virtual.shutdown();
    }
}
