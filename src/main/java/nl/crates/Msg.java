package nl.crates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Kleuren (&, § en &#RRGGBB, nooit bold) en meertalige berichten (taal van de client van de speler). */
public final class Msg {

    private static final Pattern HEX = Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final Pattern LEGACY = Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final Pattern BOLD = Pattern.compile("(?i)§l");
    private static final LegacyComponentSerializer SER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .build();

    private static final String PREFIX = "<g>Crates</g> &8» &7";
    private static final Pattern GRAD = Pattern.compile("<g>(.*?)</g>");
    private static final String[] DEFAULT_THEME = {"#FFB347", "#FFA531", "#FF981B", "#FF8C00"};
    /** Themakleuren (kleurverloop van licht- naar donkeroranje), instelbaar met theme-colors in config.yml. */
    private static volatile int[][] theme = parseTheme(java.util.List.of(DEFAULT_THEME));
    private static final String[] LANGS = {"en", "nl", "de", "fr", "es"};
    private static final Map<String, String[]> M = new HashMap<>();

    private Msg() {
    }

    /** Stelt de kleuren van het kleurverloop in (hex, bv. #FFB347). Ongeldige lijst = standaard oranje. */
    public static void setTheme(java.util.List<String> hexes) {
        theme = parseTheme(hexes);
    }

    private static int[][] parseTheme(java.util.List<String> hexes) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        if (hexes != null) {
            for (String h : hexes) {
                if (h != null && h.trim().matches("#[0-9a-fA-F]{6}")) {
                    int v = Integer.parseInt(h.trim().substring(1), 16);
                    out.add(new int[]{(v >> 16) & 255, (v >> 8) & 255, v & 255});
                }
            }
        }
        if (out.isEmpty()) return parseTheme(java.util.List.of(DEFAULT_THEME));
        return out.toArray(new int[0][]);
    }

    /** Geeft elke letter een eigen kleur uit het kleurverloop; daarna terug naar grijs (&7). */
    static String gradient(String text) {
        text = LEGACY.matcher(text).replaceAll("");
        int[][] cols = theme;
        int n = text.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                sb.append(ch);
                continue;
            }
            double t = n <= 1 ? 0 : (double) i / (n - 1);
            double pos = t * (cols.length - 1);
            int a = (int) Math.floor(pos);
            int b = Math.min(a + 1, cols.length - 1);
            double f = pos - a;
            int r = (int) Math.round(cols[a][0] + (cols[b][0] - cols[a][0]) * f);
            int g = (int) Math.round(cols[a][1] + (cols[b][1] - cols[a][1]) * f);
            int bl = (int) Math.round(cols[a][2] + (cols[b][2] - cols[a][2]) * f);
            sb.append(String.format("&#%02X%02X%02X", r, g, bl)).append(ch);
        }
        return sb.append("&7").toString();
    }

    /** Zet &-codes en hex om naar een Component, zonder bold en zonder cursieve standaardstijl. */
    public static Component c(String s) {
        if (s == null) return Component.empty();
        s = GRAD.matcher(s).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(gradient(m.group(1))));
        s = HEX.matcher(s).replaceAll("§#$1");
        s = LEGACY.matcher(s).replaceAll("§$1");
        s = BOLD.matcher(s).replaceAll("");
        return SER.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static String text(CommandSender who, String key, String... args) {
        String[] arr = M.get(key);
        if (arr == null) return key;
        int idx = 0;
        if (who instanceof Player p) {
            String lang = p.locale().getLanguage();
            for (int i = 0; i < LANGS.length; i++) {
                if (LANGS[i].equals(lang)) {
                    idx = i;
                    break;
                }
            }
        }
        String out = arr[idx];
        for (int i = 0; i < args.length; i++) {
            out = out.replace("{" + i + "}", args[i]);
        }
        return out;
    }

    public static void send(CommandSender who, String key, String... args) {
        who.sendMessage(c(PREFIX + text(who, key, args)));
    }

    // a t/m z in small caps (de s en x blijven gewoon, zoals in jullie eigen menu's)
    private static final String SMALL_CAPS =
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍ"
                    + "ɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";

    /** Zet gewone tekst om naar small caps. Alleen op tekst zonder kleurcodes gebruiken. */
    public static String smallCaps(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            sb.append(ch >= 'a' && ch <= 'z' ? SMALL_CAPS.charAt(ch - 'a') : ch);
        }
        return sb.toString();
    }

    private static void m(String key, String en, String nl, String de, String fr, String es) {
        M.put(key, new String[]{en, nl, de, fr, es});
    }

    static {
        m("no_permission",
                "You don't have permission to do that.",
                "Je hebt hier geen toestemming voor.",
                "Dafür hast du keine Berechtigung.",
                "Tu n'as pas la permission de faire ça.",
                "No tienes permiso para hacer eso.");
        m("players_only",
                "Only players can use this command.",
                "Alleen spelers kunnen dit commando gebruiken.",
                "Nur Spieler können diesen Befehl benutzen.",
                "Seuls les joueurs peuvent utiliser cette commande.",
                "Solo los jugadores pueden usar este comando.");
        m("usage",
                "Usage: /crates <link|unlink|list|key|open|hologram|reload>",
                "Gebruik: /crates <link|unlink|list|key|open|hologram|reload>",
                "Verwendung: /crates <link|unlink|list|key|open|hologram|reload>",
                "Utilisation : /crates <link|unlink|list|key|open|hologram|reload>",
                "Uso: /crates <link|unlink|list|key|open|hologram|reload>");
        m("usage_key",
                "Usage: /crates key give <virtual|physical> <player> <crate> [amount]",
                "Gebruik: /crates key give <virtual|physical> <speler> <crate> [aantal]",
                "Verwendung: /crates key give <virtual|physical> <Spieler> <Crate> [Anzahl]",
                "Utilisation : /crates key give <virtual|physical> <joueur> <crate> [quantité]",
                "Uso: /crates key give <virtual|physical> <jugador> <crate> [cantidad]");
        m("usage_hologram",
                "Usage: /crates hologram <crate> <on|off>",
                "Gebruik: /crates hologram <crate> <on|off>",
                "Verwendung: /crates hologram <Crate> <on|off>",
                "Utilisation : /crates hologram <crate> <on|off>",
                "Uso: /crates hologram <crate> <on|off>");
        m("hologram_on",
                "The hologram of the <g>{0}</g> crate is now on.",
                "Het hologram van de <g>{0}</g> crate staat nu aan.",
                "Das Hologramm der <g>{0}</g>-Crate ist jetzt an.",
                "L'hologramme de la crate <g>{0}</g> est maintenant activé.",
                "El holograma de la crate <g>{0}</g> ahora está activado.");
        m("hologram_off",
                "The hologram of the <g>{0}</g> crate is now off.",
                "Het hologram van de <g>{0}</g> crate staat nu uit.",
                "Das Hologramm der <g>{0}</g>-Crate ist jetzt aus.",
                "L'hologramme de la crate <g>{0}</g> est maintenant désactivé.",
                "El holograma de la crate <g>{0}</g> ahora está desactivado.");
        m("keyall_next",
                "&7next key in <g>{0}</g>",
                "&7volgende key over <g>{0}</g>",
                "&7nächster Key in <g>{0}</g>",
                "&7prochaine key dans <g>{0}</g>",
                "&7próxima key en <g>{0}</g>");
        m("key_given_virtual",
                "Gave <g>{0}x</g> {1}&7 (virtual) to <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 (virtueel) gegeven aan <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 (virtuell) an <g>{2}</g> gegeben.",
                "<g>{0}x</g> {1}&7 (virtuelle) donné à <g>{2}</g>.",
                "Se dieron <g>{0}x</g> {1}&7 (virtual) a <g>{2}</g>.");
        m("key_given_physical",
                "Gave <g>{0}x</g> {1}&7 (physical) to <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 (fysiek) gegeven aan <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 (physisch) an <g>{2}</g> gegeben.",
                "<g>{0}x</g> {1}&7 (physique) donné à <g>{2}</g>.",
                "Se dieron <g>{0}x</g> {1}&7 (físico) a <g>{2}</g>.");
        m("key_received_virtual",
                "You received <g>{0}x</g> {1}&7 (virtual).",
                "Je hebt <g>{0}x</g> {1}&7 (virtueel) ontvangen.",
                "Du hast <g>{0}x</g> {1}&7 (virtuell) erhalten.",
                "Tu as reçu <g>{0}x</g> {1}&7 (virtuelle).",
                "Has recibido <g>{0}x</g> {1}&7 (virtual).");
        m("key_received_physical",
                "You received <g>{0}x</g> {1}&7 (physical).",
                "Je hebt <g>{0}x</g> {1}&7 (fysiek) ontvangen.",
                "Du hast <g>{0}x</g> {1}&7 (physisch) erhalten.",
                "Tu as reçu <g>{0}x</g> {1}&7 (physique).",
                "Has recibido <g>{0}x</g> {1}&7 (físico).");
        m("keys_header",
                "<g>Your virtual keys:</g>",
                "<g>Jouw virtuele keys:</g>",
                "<g>Deine virtuellen Keys:</g>",
                "<g>Tes clés virtuelles :</g>",
                "<g>Tus llaves virtuales:</g>");
        m("keys_none",
                "You have no virtual keys.",
                "Je hebt geen virtuele keys.",
                "Du hast keine virtuellen Keys.",
                "Tu n'as aucune clé virtuelle.",
                "No tienes llaves virtuales.");
        String keysEntry = "&8- {0}&7: <g>{1}</g>";
        m("keys_entry", keysEntry, keysEntry, keysEntry, keysEntry, keysEntry);
        m("confirm_title",
                "Confirm reward",
                "Beloning bevestigen",
                "Belohnung bestätigen",
                "Confirmer la récompense",
                "Confirmar recompensa");
        m("btn_confirm",
                "Confirm",
                "Bevestigen",
                "Bestätigen",
                "Confirmer",
                "Confirmar");
        m("btn_cancel",
                "Cancel",
                "Annuleren",
                "Abbrechen",
                "Annuler",
                "Cancelar");
        m("confirm_lore",
                "Click to claim this reward.",
                "Klik om deze beloning te claimen.",
                "Klicke, um diese Belohnung zu erhalten.",
                "Clique pour obtenir cette récompense.",
                "Haz clic para reclamar esta recompensa.");
        m("cancel_lore",
                "Click to go back.",
                "Klik om terug te gaan.",
                "Klicke, um zurückzugehen.",
                "Clique pour revenir.",
                "Haz clic para volver.");
        m("broadcast",
                "<g>{0}</g> &7just won {1}&7 from the {2}&7!",
                "<g>{0}</g> &7heeft zojuist {1}&7 gewonnen uit de {2}&7!",
                "<g>{0}</g> &7hat gerade {1}&7 aus der {2}&7 gewonnen!",
                "<g>{0}</g> &7vient de gagner {1}&7 dans la {2}&7 !",
                "<g>{0}</g> &7acaba de ganar {1}&7 de la {2}&7!");
        m("unknown_crate",
                "Unknown crate: <g>{0}</g>",
                "Onbekende crate: <g>{0}</g>",
                "Unbekannte Crate: <g>{0}</g>",
                "Crate inconnue : <g>{0}</g>",
                "Crate desconocida: <g>{0}</g>");
        m("not_looking",
                "Look at a block first.",
                "Kijk eerst naar een blok.",
                "Sieh zuerst einen Block an.",
                "Regarde d'abord un bloc.",
                "Mira primero un bloque.");
        m("linked",
                "This block is now linked to the <g>{0}</g> crate.",
                "Dit blok is nu gekoppeld aan de <g>{0}</g> crate.",
                "Dieser Block ist jetzt mit der <g>{0}</g>-Crate verknüpft.",
                "Ce bloc est maintenant lié à la crate <g>{0}</g>.",
                "Este bloque ahora está vinculado a la crate <g>{0}</g>.");
        m("unlinked",
                "This block is no longer linked to a crate.",
                "Dit blok is niet meer gekoppeld aan een crate.",
                "Dieser Block ist nicht mehr mit einer Crate verknüpft.",
                "Ce bloc n'est plus lié à une crate.",
                "Este bloque ya no está vinculado a una crate.");
        m("not_linked",
                "This block is not linked to a crate.",
                "Dit blok is niet gekoppeld aan een crate.",
                "Dieser Block ist mit keiner Crate verknüpft.",
                "Ce bloc n'est lié à aucune crate.",
                "Este bloque no está vinculado a ninguna crate.");
        m("no_key",
                "You need a {0}&7 to use this crate.",
                "Je hebt een {0}&7 nodig voor deze crate.",
                "Du brauchst einen {0}&7 für diese Crate.",
                "Il te faut une {0}&7 pour cette crate.",
                "Necesitas una {0}&7 para esta crate.");
        m("reward",
                "You received {0}&7.",
                "Je hebt {0}&7 ontvangen.",
                "Du hast {0}&7 erhalten.",
                "Tu as reçu {0}&7.",
                "Has recibido {0}&7.");
        m("key_given",
                "Gave <g>{0}x</g> {1}&7 to <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 gegeven aan <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 an <g>{2}</g> gegeben.",
                "<g>{0}x</g> {1}&7 donné à <g>{2}</g>.",
                "Se dieron <g>{0}x</g> {1}&7 a <g>{2}</g>.");
        m("key_received",
                "You received <g>{0}x</g> {1}&7.",
                "Je hebt <g>{0}x</g> {1}&7 ontvangen.",
                "Du hast <g>{0}x</g> {1}&7 erhalten.",
                "Tu as reçu <g>{0}x</g> {1}&7.",
                "Has recibido <g>{0}x</g> {1}&7.");
        m("reloaded",
                "Configuration reloaded.",
                "Configuratie opnieuw geladen.",
                "Konfiguration neu geladen.",
                "Configuration rechargée.",
                "Configuración recargada.");
        m("break_denied",
                "This block is a crate. Use /crates unlink to remove it.",
                "Dit blok is een crate. Gebruik /crates unlink om hem te verwijderen.",
                "Dieser Block ist eine Crate. Benutze /crates unlink, um ihn zu entfernen.",
                "Ce bloc est une crate. Utilise /crates unlink pour le retirer.",
                "Este bloque es una crate. Usa /crates unlink para quitarlo.");
        m("busy",
                "You are already opening a crate.",
                "Je bent al een crate aan het openen.",
                "Du öffnest bereits eine Crate.",
                "Tu es déjà en train d'ouvrir une crate.",
                "Ya estás abriendo una crate.");
        m("player_not_found",
                "Player not found: <g>{0}</g>",
                "Speler niet gevonden: <g>{0}</g>",
                "Spieler nicht gefunden: <g>{0}</g>",
                "Joueur introuvable : <g>{0}</g>",
                "Jugador no encontrado: <g>{0}</g>");
        m("invalid_amount",
                "Invalid amount.",
                "Ongeldig aantal.",
                "Ungültige Anzahl.",
                "Quantité invalide.",
                "Cantidad no válida.");
        m("list_empty",
                "No blocks are linked to a crate yet.",
                "Er zijn nog geen blokken aan een crate gekoppeld.",
                "Es sind noch keine Blöcke mit einer Crate verknüpft.",
                "Aucun bloc n'est encore lié à une crate.",
                "Todavía no hay bloques vinculados a una crate.");
        m("list_header",
                "<g>Linked crates:</g>",
                "<g>Gekoppelde crates:</g>",
                "<g>Verknüpfte Crates:</g>",
                "<g>Crates liées :</g>",
                "<g>Crates vinculadas:</g>");
        String entry = "&8- <g>{0}</g> &7({1}: {2}, {3}, {4})";
        m("list_entry", entry, entry, entry, entry, entry);
    }
}
