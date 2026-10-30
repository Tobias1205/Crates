package nl.crates;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Eén crate zoals die in crates.yml staat. */
public final class Crate {

    public enum Effect { CHOOSE, ROULETTE }

    public record Reward(int slot, ItemSpec icon, double chance, List<String> commands) {
    }

    public record Holo(double height, List<String> lines) {
    }

    public final String id;
    public final String title;
    public final Effect effect;
    public final int rows;
    public final boolean fillerEnabled;
    public final ItemSpec filler;
    public final boolean backEnabled;
    public final int backSlot;
    public final ItemSpec back;
    public final List<Holo> holograms;
    public final List<Reward> rewards;
    public final ItemSpec key;

    private Crate(String id, String title, Effect effect, int rows, boolean fillerEnabled, ItemSpec filler,
                  boolean backEnabled, int backSlot, ItemSpec back, List<Holo> holograms,
                  List<Reward> rewards, ItemSpec key) {
        this.id = id;
        this.title = title;
        this.effect = effect;
        this.rows = rows;
        this.fillerEnabled = fillerEnabled;
        this.filler = filler;
        this.backEnabled = backEnabled;
        this.backSlot = backSlot;
        this.back = back;
        this.holograms = holograms;
        this.rewards = rewards;
        this.key = key;
    }

    public static Crate load(String id, ConfigurationSection s, Logger log) {
        ConfigurationSection inv = s.getConfigurationSection("inventory");
        if (inv == null) {
            log.warning("Crate '" + id + "' has no inventory section, skipped.");
            return null;
        }
        String title = inv.getString("name", id);
        Effect effect;
        try {
            effect = Effect.valueOf(inv.getString("effect", "CHOOSE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("Crate '" + id + "' has an unknown effect, using CHOOSE.");
            effect = Effect.CHOOSE;
        }
        int rows = Math.max(1, Math.min(6, inv.getInt("rows", 3)));

        ConfigurationSection fs = inv.getConfigurationSection("filler-item");
        boolean fillerEnabled = fs != null && fs.getBoolean("enable", true);
        ItemSpec filler = fs != null ? ItemSpec.of(fs, Material.BLACK_STAINED_GLASS_PANE, log) : null;

        ConfigurationSection bs = s.getConfigurationSection("back-button");
        boolean backEnabled = bs != null && bs.getBoolean("enable", false);
        int backSlot = bs != null ? bs.getInt("slot", rows * 9 - 9) : rows * 9 - 9;
        ItemSpec back = bs != null ? ItemSpec.of(bs, Material.RED_STAINED_GLASS_PANE, log) : null;
        if (backEnabled && (backSlot < 0 || backSlot >= rows * 9)) backEnabled = false;

        List<Holo> holos = new ArrayList<>();
        ConfigurationSection hs = s.getConfigurationSection("hologram");
        if (hs != null) {
            for (String k : hs.getKeys(false)) {
                ConfigurationSection h = hs.getConfigurationSection(k);
                if (h == null) continue;
                List<String> lines = new ArrayList<>();
                for (String l : h.getStringList("lines")) {
                    if (l != null && !l.equalsIgnoreCase("null")) lines.add(l);
                }
                boolean anyText = lines.stream().anyMatch(l -> !l.isBlank());
                if (anyText) holos.add(new Holo(h.getDouble("height", 1.5), lines));
            }
        }

        List<Reward> rewards = new ArrayList<>();
        ConfigurationSection rs = s.getConfigurationSection("rewards");
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(k);
                if (r == null) continue;
                ConfigurationSection icon = r.getConfigurationSection("icon");
                int slot = r.getInt("slot", -1);
                if (icon == null || slot < 0 || slot >= rows * 9) {
                    log.warning("Crate '" + id + "' reward " + k + " has no icon or an invalid slot, skipped.");
                    continue;
                }
                List<String> commands = new ArrayList<>();
                for (String c : r.getStringList("commands")) {
                    if (c == null || c.isBlank()) continue;
                    commands.add(c.startsWith("/") ? c.substring(1) : c);
                }
                rewards.add(new Reward(slot, ItemSpec.of(icon, Material.STONE, log), parseChance(r.get("chance")), commands));
            }
        }
        if (rewards.isEmpty()) {
            log.warning("Crate '" + id + "' has no valid rewards, skipped.");
            return null;
        }

        ConfigurationSection ks = s.getConfigurationSection("key");
        ItemSpec key = ks != null
                ? ItemSpec.of(ks, Material.TRIPWIRE_HOOK, log)
                : ItemSpec.simple(Material.TRIPWIRE_HOOK, "&7" + id + " key");

        return new Crate(id, title, effect, rows, fillerEnabled, filler, backEnabled, backSlot, back, holos, rewards, key);
    }

    /** "100.0%", "40" of 12.5 allemaal toegestaan. */
    private static double parseChance(Object raw) {
        if (raw == null) return 100.0;
        String s = String.valueOf(raw).replace("%", "").trim();
        try {
            return Math.max(0.0, Double.parseDouble(s));
        } catch (NumberFormatException ex) {
            return 100.0;
        }
    }

    public Reward rewardAt(int slot) {
        for (Reward r : rewards) {
            if (r.slot() == slot) return r;
        }
        return null;
    }

    /** Willekeurige reward, gewogen op chance. */
    public Reward randomReward() {
        double total = 0;
        for (Reward r : rewards) total += r.chance();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (total <= 0) return rewards.get(rnd.nextInt(rewards.size()));
        double roll = rnd.nextDouble() * total;
        for (Reward r : rewards) {
            roll -= r.chance();
            if (roll < 0) return r;
        }
        return rewards.get(rewards.size() - 1);
    }

    public String keyName() {
        return key.name != null ? key.name : id;
    }

    public ItemStack fillerItem() {
        if (fillerEnabled && filler != null) return filler.build(1);
        return pane(Material.BLACK_STAINED_GLASS_PANE);
    }

    public static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }
}
