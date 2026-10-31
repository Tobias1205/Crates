package nl.crates;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Beschrijving van een item uit de config (materiaal, naam, lore, enchantments, trim, aantal). */
public final class ItemSpec {

    public final Material material;
    public final String name;
    public final List<String> lore;
    public final Map<Enchantment, Integer> enchants;
    public final boolean glow;
    public final int amount;
    public final String trimMaterial;
    public final String trimPattern;
    /** Als dit item gewonnen wordt, krijgt de hele server een bericht. */
    public final boolean broadcast;
    /** Vliegduur van een firework rocket (1-3 in vanilla, 0 = niet instellen). */
    public final int flightDuration;
    /** Verbergt de extra tooltip-regels die Minecraft zelf toevoegt (bij een rocket: "Flight Duration"). */
    public final boolean hideExtraTooltip;

    public ItemSpec(Material material, String name, List<String> lore, Map<Enchantment, Integer> enchants,
                    boolean glow, int amount, String trimMaterial, String trimPattern, boolean broadcast,
                    int flightDuration, boolean hideExtraTooltip) {
        this.flightDuration = flightDuration;
        this.hideExtraTooltip = hideExtraTooltip;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.enchants = enchants;
        this.glow = glow;
        this.amount = amount;
        this.trimMaterial = trimMaterial;
        this.trimPattern = trimPattern;
        this.broadcast = broadcast;
    }

    public static ItemSpec simple(Material material, String name) {
        return new ItemSpec(material, name, List.of(), Map.of(), false, 1, null, null, false, 0, false);
    }

    public static ItemSpec of(ConfigurationSection s, Material fallback, Logger log) {
        String matName = s.getString("material", fallback.name());
        Material mat = Material.matchMaterial(matName == null ? fallback.name() : matName.toUpperCase(Locale.ROOT));
        if (mat == null || mat.isAir() || !mat.isItem()) {
            log.warning("Unknown material '" + matName + "' in " + s.getCurrentPath() + ", using " + fallback);
            mat = fallback;
        }
        // het back-button blok gebruikt "displayname", de rest "name"
        String name = s.getString("name", s.getString("displayname"));
        List<String> lore = new ArrayList<>(s.getStringList("lore"));

        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        ConfigurationSection es = s.getConfigurationSection("enchantments");
        if (es != null) {
            for (String key : es.getKeys(false)) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (e == null) {
                    log.warning("Unknown enchantment '" + key + "' in " + s.getCurrentPath());
                    continue;
                }
                enchants.put(e, Math.max(1, Math.min(255, es.getInt(key))));
            }
        }

        String tm = null;
        String tp = null;
        ConfigurationSection ts = s.getConfigurationSection("trim");
        if (ts != null) {
            tm = ts.getString("material");
            tp = ts.getString("pattern");
            if (tm == null || tp == null || lookupMaterial(tm) == null || lookupPattern(tp) == null) {
                log.warning("Invalid armor trim in " + s.getCurrentPath() + " (material/pattern), ignored.");
                tm = null;
                tp = null;
            }
        }

        int amount = Math.max(1, Math.min(64, s.getInt("amount", 1)));
        int flight = Math.max(0, Math.min(127, s.getInt("flight-duration", 0)));
        return new ItemSpec(mat, name, lore, enchants, s.getBoolean("glow", false), amount, tm, tp,
                s.getBoolean("broadcast", false), flight, s.getBoolean("hide-flight-duration", false));
    }

    private static TrimMaterial lookupMaterial(String n) {
        return Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(n.toLowerCase(Locale.ROOT)));
    }

    private static TrimPattern lookupPattern(String n) {
        return Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(n.toLowerCase(Locale.ROOT)));
    }

    /** Item met het aantal uit de config. */
    public ItemStack build() {
        return build(amount);
    }

    public ItemStack build(int count) {
        ItemStack item = new ItemStack(material, Math.max(1, count));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (name != null) meta.displayName(Msg.c(name));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Msg.c(l));
            meta.lore(lines);
        }
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        if (trimMaterial != null && trimPattern != null && meta instanceof ArmorMeta armor) {
            TrimMaterial tm = lookupMaterial(trimMaterial);
            TrimPattern tp = lookupPattern(trimPattern);
            if (tm != null && tp != null) armor.setTrim(new ArmorTrim(tm, tp));
        }
        if (flightDuration > 0 && meta instanceof FireworkMeta rocket) rocket.setPower(flightDuration);
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        // hide-flight-duration: true verbergt de regel "Flight Duration: N" die Minecraft zelf onder elke rocket zet;
        // de lore uit de config (met de echte duur) blijft staan.
        if (hideExtraTooltip) meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
