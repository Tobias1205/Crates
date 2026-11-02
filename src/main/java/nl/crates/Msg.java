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
        text = HEX.matcher(text).replaceAll("");
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
                "&cYou don't have permission to do that.",
                "&cJe hebt hier geen toestemming voor.",
                "&cDafür hast du keine Berechtigung.",
                "&cTu n'as pas la permission de faire ça.",
                "&cNo tienes permiso para hacer eso.");
        m("players_only",
                "&cOnly players can use this command.",
                "&cAlleen spelers kunnen dit commando gebruiken.",
                "&cNur Spieler können diesen Befehl benutzen.",
                "&cSeuls les joueurs peuvent utiliser cette commande.",
                "&cSolo los jugadores pueden usar este comando.");
        m("usage",
                "&cUsage: &f/crates <link|unlink|list|key|open|hologram|reload>",
                "&cGebruik: &f/crates <link|unlink|list|key|open|hologram|reload>",
                "&cVerwendung: &f/crates <link|unlink|list|key|open|hologram|reload>",
                "&cUtilisation : &f/crates <link|unlink|list|key|open|hologram|reload>",
                "&cUso: &f/crates <link|unlink|list|key|open|hologram|reload>");
        m("usage_key",
                "&cUsage: &f/crates key <give|take> <virtual|physical> <player> <crate> [amount]",
                "&cGebruik: &f/crates key <give|take> <virtual|physical> <speler> <crate> [aantal]",
                "&cVerwendung: &f/crates key <give|take> <virtual|physical> <Spieler> <Crate> [Anzahl]",
                "&cUtilisation : &f/crates key <give|take> <virtual|physical> <joueur> <crate> [quantité]",
                "&cUso: &f/crates key <give|take> <virtual|physical> <jugador> <crate> [cantidad]");
        m("usage_hologram",
                "&cUsage: &f/crates hologram <crate> <on|off>",
                "&cGebruik: &f/crates hologram <crate> <on|off>",
                "&cVerwendung: &f/crates hologram <Crate> <on|off>",
                "&cUtilisation : &f/crates hologram <crate> <on|off>",
                "&cUso: &f/crates hologram <crate> <on|off>");
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
        m("key_taken_virtual",
                "Took <g>{0}x</g> {1}&7 (virtual) from <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 (virtueel) afgenomen van <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 (virtuell) von <g>{2}</g> entfernt.",
                "<g>{0}x</g> {1}&7 (virtuelle) retiré à <g>{2}</g>.",
                "Se quitaron <g>{0}x</g> {1}&7 (virtual) a <g>{2}</g>.");
        m("key_taken_physical",
                "Took <g>{0}x</g> {1}&7 (physical) from <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 (fysiek) afgenomen van <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 (physisch) von <g>{2}</g> entfernt.",
                "<g>{0}x</g> {1}&7 (physique) retiré à <g>{2}</g>.",
                "Se quitaron <g>{0}x</g> {1}&7 (físico) a <g>{2}</g>.");
        m("key_lost_virtual",
                "<g>{0}x</g> {1}&7 (virtual) was removed from your keys.",
                "<g>{0}x</g> {1}&7 (virtueel) is van je keys afgenomen.",
                "<g>{0}x</g> {1}&7 (virtuell) wurde aus deinen Keys entfernt.",
                "<g>{0}x</g> {1}&7 (virtuelle) a été retiré de tes clés.",
                "Se quitaron <g>{0}x</g> {1}&7 (virtual) de tus llaves.");
        m("key_lost_physical",
                "<g>{0}x</g> {1}&7 (physical) was removed from your inventory.",
                "<g>{0}x</g> {1}&7 (fysiek) is uit je inventory gehaald.",
                "<g>{0}x</g> {1}&7 (physisch) wurde aus deinem Inventar entfernt.",
                "<g>{0}x</g> {1}&7 (physique) a été retiré de ton inventaire.",
                "Se quitaron <g>{0}x</g> {1}&7 (físico) de tu inventario.");
        m("no_keys_to_take",
                "&c{0} has no {1}&c ({2}).",
                "&c{0} heeft geen {1}&c ({2}).",
                "&c{0} hat keinen {1}&c ({2}).",
                "&c{0} n'a pas de {1}&c ({2}).",
                "&c{0} no tiene {1}&c ({2}).");
        m("kind_virtual", "virtual", "virtueel", "virtuell", "virtuelle", "virtual");
        m("kind_physical", "physical", "fysiek", "physisch", "physique", "físico");
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
                "You received <g>{0}x</g> <g>{1}</g> &7(virtual).",
                "Je hebt <g>{0}x</g> <g>{1}</g> &7(virtueel) ontvangen.",
                "Du hast <g>{0}x</g> <g>{1}</g> &7(virtuell) erhalten.",
                "Tu as reçu <g>{0}x</g> <g>{1}</g> &7(virtuelle).",
                "Has recibido <g>{0}x</g> <g>{1}</g> &7(virtual).");
        m("key_received_physical",
                "You received <g>{0}x</g> <g>{1}</g> &7(physical).",
                "Je hebt <g>{0}x</g> <g>{1}</g> &7(fysiek) ontvangen.",
                "Du hast <g>{0}x</g> <g>{1}</g> &7(physisch) erhalten.",
                "Tu as reçu <g>{0}x</g> <g>{1}</g> &7(physique).",
                "Has recibido <g>{0}x</g> <g>{1}</g> &7(físico).");
        m("keys_header",
                "<g>Your virtual keys:</g>",
                "<g>Jouw virtuele keys:</g>",
                "<g>Deine virtuellen Keys:</g>",
                "<g>Tes clés virtuelles :</g>",
                "<g>Tus llaves virtuales:</g>");
        m("keys_none",
                "&cYou have no virtual keys.",
                "&cJe hebt geen virtuele keys.",
                "&cDu hast keine virtuellen Keys.",
                "&cTu n'as aucune clé virtuelle.",
                "&cNo tienes llaves virtuales.");
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
                "<g>{0} just won</g> {1}&7 <g>from the</g> {2}&7<g>!</g>",
                "<g>{0} heeft zojuist</g> {1}&7 <g>gewonnen uit de</g> {2}&7<g>!</g>",
                "<g>{0} hat gerade</g> {1}&7 <g>aus der</g> {2}&7 <g>gewonnen!</g>",
                "<g>{0} vient de gagner</g> {1}&7 <g>dans la</g> {2}&7 <g>!</g>",
                "<g>{0} acaba de ganar</g> {1}&7 <g>de la</g> {2}&7<g>!</g>");
        m("unknown_crate",
                "&cUnknown crate: &f{0}",
                "&cOnbekende crate: &f{0}",
                "&cUnbekannte Crate: &f{0}",
                "&cCrate inconnue : &f{0}",
                "&cCrate desconocida: &f{0}");
        m("not_looking",
                "&cLook at a block first.",
                "&cKijk eerst naar een blok.",
                "&cSieh zuerst einen Block an.",
                "&cRegarde d'abord un bloc.",
                "&cMira primero un bloque.");
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
                "&cThis block is not linked to a crate.",
                "&cDit blok is niet gekoppeld aan een crate.",
                "&cDieser Block ist mit keiner Crate verknüpft.",
                "&cCe bloc n'est lié à aucune crate.",
                "&cEste bloque no está vinculado a ninguna crate.");
        m("no_key",
                "<g>You need a</g> {0}&7 <g>to use this crate.</g>",
                "<g>Je hebt een</g> {0}&7 <g>nodig voor deze crate.</g>",
                "<g>Du brauchst einen</g> {0}&7 <g>für diese Crate.</g>",
                "<g>Il te faut une</g> {0}&7 <g>pour cette crate.</g>",
                "<g>Necesitas una</g> {0}&7 <g>para esta crate.</g>");
        m("reward",
                "<g>You received</g> {0}&7.",
                "<g>Je hebt</g> {0}&7 <g>ontvangen.</g>",
                "<g>Du hast</g> {0}&7 <g>erhalten.</g>",
                "<g>Tu as reçu</g> {0}&7.",
                "<g>Has recibido</g> {0}&7.");
        m("key_given",
                "Gave <g>{0}x</g> {1}&7 to <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 gegeven aan <g>{2}</g>.",
                "<g>{0}x</g> {1}&7 an <g>{2}</g> gegeben.",
                "<g>{0}x</g> {1}&7 donné à <g>{2}</g>.",
                "Se dieron <g>{0}x</g> {1}&7 a <g>{2}</g>.");
        m("key_received",
                "You received <g>{0}x</g> <g>{1}</g>&7.",
                "Je hebt <g>{0}x</g> <g>{1}</g> &7ontvangen.",
                "Du hast <g>{0}x</g> <g>{1}</g> &7erhalten.",
                "Tu as reçu <g>{0}x</g> <g>{1}</g>&7.",
                "Has recibido <g>{0}x</g> <g>{1}</g>&7.");
        m("reloaded",
                "Configuration reloaded.",
                "Configuratie opnieuw geladen.",
                "Konfiguration neu geladen.",
                "Configuration rechargée.",
                "Configuración recargada.");
        m("break_denied",
                "&cThis block is a crate. Use &f/crates unlink &cto remove it.",
                "&cDit blok is een crate. Gebruik &f/crates unlink &com hem te verwijderen.",
                "&cDieser Block ist eine Crate. Benutze &f/crates unlink&c, um ihn zu entfernen.",
                "&cCe bloc est une crate. Utilise &f/crates unlink &cpour le retirer.",
                "&cEste bloque es una crate. Usa &f/crates unlink &cpara quitarlo.");
        m("busy",
                "&cYou are already opening a crate.",
                "&cJe bent al een crate aan het openen.",
                "&cDu öffnest bereits eine Crate.",
                "&cTu es déjà en train d'ouvrir une crate.",
                "&cYa estás abriendo una crate.");
        m("player_not_found",
                "&cPlayer not found: &f{0}",
                "&cSpeler niet gevonden: &f{0}",
                "&cSpieler nicht gefunden: &f{0}",
                "&cJoueur introuvable : &f{0}",
                "&cJugador no encontrado: &f{0}");
        m("invalid_amount",
                "&cInvalid amount.",
                "&cOngeldig aantal.",
                "&cUngültige Anzahl.",
                "&cQuantité invalide.",
                "&cCantidad no válida.");
        m("list_empty",
                "&cNo blocks are linked to a crate yet.",
                "&cEr zijn nog geen blokken aan een crate gekoppeld.",
                "&cEs sind noch keine Blöcke mit einer Crate verknüpft.",
                "&cAucun bloc n'est encore lié à une crate.",
                "&cTodavía no hay bloques vinculados a una crate.");
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
