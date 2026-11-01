package nl.crates;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI: %keyall_&lt;crate&gt;% geeft de tijd tot de volgende keyall-key van die crate,
 * bijvoorbeeld %keyall_common% -> 19m 13s. Werkt in tab, scoreboard en overal waar PlaceholderAPI werkt.
 * Deze klasse wordt alleen geladen als PlaceholderAPI op de server staat.
 */
public final class KeyAllExpansion extends PlaceholderExpansion {

    private final KeyAll keyAll;

    private KeyAllExpansion(KeyAll keyAll) {
        this.keyAll = keyAll;
    }

    /** Registreert de placeholder bij PlaceholderAPI. */
    public static void hook(KeyAll keyAll) {
        new KeyAllExpansion(keyAll).register();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "keyall";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Crates";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getRequiredPlugin() {
        return "Crates";
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        return keyAll.placeholder(player.getUniqueId(), params);
    }
}
