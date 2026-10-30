package nl.crates;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Beschrijving van een item uit de config (materiaal, naam, lore, enchantments). */
public final class ItemSpec {

    public final Material material;
    public final String name;
    public final List<String> lore;
    public final Map<Enchantment, Integer> enchants;
    public final boolean glow;

    public ItemSpec(Material material, String name, List<String> lore, Map<Enchantment, Integer> enchants, boolean glow) {
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.enchants = enchants;
        this.glow = glow;
    }

    public static ItemSpec simple(Material material, String name) {
        return new ItemSpec(material, name, List.of(), Map.of(), false);
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
        return new ItemSpec(mat, name, lore, enchants, s.getBoolean("glow", false));
    }

    public ItemStack build(int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
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
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
