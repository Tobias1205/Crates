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

    private static final String PREFIX = "&#A20AD6crates &8» &7";
    private static final String[] LANGS = {"en", "nl", "de", "fr", "es"};
    private static final Map<String, String[]> M = new HashMap<>();

    private Msg() {
    }

    /** Zet &-codes en hex om naar een Component, zonder bold en zonder cursieve standaardstijl. */
    public static Component c(String s) {
        if (s == null) return Component.empty();
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
                "The hologram of the {0} crate is now on.",
                "Het hologram van de {0} crate staat nu aan.",
                "Das Hologramm der {0}-Crate ist jetzt an.",
                "L'hologramme de la crate {0} est maintenant activé.",
                "El holograma de la crate {0} ahora está activado.");
        m("hologram_off",
                "The hologram of the {0} crate is now off.",
                "Het hologram van de {0} crate staat nu uit.",
                "Das Hologramm der {0}-Crate ist jetzt aus.",
                "L'hologramme de la crate {0} est maintenant désactivé.",
                "El holograma de la crate {0} ahora está desactivado.");
        m("key_given_virtual",
                "Gave {0}x {1}&7 (virtual) to {2}.",
                "{0}x {1}&7 (virtueel) gegeven aan {2}.",
                "{0}x {1}&7 (virtuell) an {2} gegeben.",
                "{0}x {1}&7 (virtuelle) donné à {2}.",
                "Se dieron {0}x {1}&7 (virtual) a {2}.");
        m("key_given_physical",
                "Gave {0}x {1}&7 (physical) to {2}.",
                "{0}x {1}&7 (fysiek) gegeven aan {2}.",
                "{0}x {1}&7 (physisch) an {2} gegeben.",
                "{0}x {1}&7 (physique) donné à {2}.",
                "Se dieron {0}x {1}&7 (físico) a {2}.");
        m("key_received_virtual",
                "You received {0}x {1}&7 (virtual).",
                "Je hebt {0}x {1}&7 (virtueel) ontvangen.",
                "Du hast {0}x {1}&7 (virtuell) erhalten.",
                "Tu as reçu {0}x {1}&7 (virtuelle).",
                "Has recibido {0}x {1}&7 (virtual).");
        m("key_received_physical",
                "You received {0}x {1}&7 (physical).",
                "Je hebt {0}x {1}&7 (fysiek) ontvangen.",
                "Du hast {0}x {1}&7 (physisch) erhalten.",
                "Tu as reçu {0}x {1}&7 (physique).",
                "Has recibido {0}x {1}&7 (físico).");
        m("keys_header",
                "Your virtual keys:",
                "Jouw virtuele keys:",
                "Deine virtuellen Keys:",
                "Tes clés virtuelles :",
                "Tus llaves virtuales:");
        m("keys_none",
                "You have no virtual keys.",
                "Je hebt geen virtuele keys.",
                "Du hast keine virtuellen Keys.",
                "Tu n'as aucune clé virtuelle.",
                "No tienes llaves virtuales.");
        String keysEntry = "&8- {0}&7: &f{1}";
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
                "&f{0} &7just won {1}&7 from the {2}&7!",
                "&f{0} &7heeft zojuist {1}&7 gewonnen uit de {2}&7!",
                "&f{0} &7hat gerade {1}&7 aus der {2}&7 gewonnen!",
                "&f{0} &7vient de gagner {1}&7 dans la {2}&7 !",
                "&f{0} &7acaba de ganar {1}&7 de la {2}&7!");
        m("unknown_crate",
                "Unknown crate: {0}",
                "Onbekende crate: {0}",
                "Unbekannte Crate: {0}",
                "Crate inconnue : {0}",
                "Crate desconocida: {0}");
        m("not_looking",
                "Look at a block first.",
                "Kijk eerst naar een blok.",
                "Sieh zuerst einen Block an.",
                "Regarde d'abord un bloc.",
                "Mira primero un bloque.");
        m("linked",
                "This block is now linked to the {0} crate.",
                "Dit blok is nu gekoppeld aan de {0} crate.",
                "Dieser Block ist jetzt mit der {0}-Crate verknüpft.",
                "Ce bloc est maintenant lié à la crate {0}.",
                "Este bloque ahora está vinculado a la crate {0}.");
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
                "Gave {0}x {1}&7 to {2}.",
                "{0}x {1}&7 gegeven aan {2}.",
                "{0}x {1}&7 an {2} gegeben.",
                "{0}x {1}&7 donné à {2}.",
                "Se dieron {0}x {1}&7 a {2}.");
        m("key_received",
                "You received {0}x {1}&7.",
                "Je hebt {0}x {1}&7 ontvangen.",
                "Du hast {0}x {1}&7 erhalten.",
                "Tu as reçu {0}x {1}&7.",
                "Has recibido {0}x {1}&7.");
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
                "Player not found: {0}",
                "Speler niet gevonden: {0}",
                "Spieler nicht gefunden: {0}",
                "Joueur introuvable : {0}",
                "Jugador no encontrado: {0}");
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
                "Linked crates:",
                "Gekoppelde crates:",
                "Verknüpfte Crates:",
                "Crates liées :",
                "Crates vinculadas:");
        String entry = "&8- &f{0} &7({1}: {2}, {3}, {4})";
        m("list_entry", entry, entry, entry, entry, entry);
    }
}
