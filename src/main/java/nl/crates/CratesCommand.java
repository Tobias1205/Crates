package nl.crates;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CratesCommand implements TabExecutor {

    private final CrateManager manager;

    public CratesCommand(CrateManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("crates.admin")) {
            Msg.send(s, "no_permission");
            return true;
        }
        if (a.length == 0) {
            Msg.send(s, "usage");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "link" -> link(s, a);
            case "unlink" -> unlink(s);
            case "list" -> list(s);
            case "key" -> key(s, a);
            case "open" -> open(s, a);
            case "reload" -> {
                manager.reload();
                Msg.send(s, "reloaded");
            }
            default -> Msg.send(s, "usage");
        }
        return true;
    }

    private Block target(Player p) {
        Block b = p.getTargetBlockExact(6);
        return (b == null || b.getType().isAir()) ? null : b;
    }

    private void link(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "players_only");
            return;
        }
        if (a.length < 2) {
            Msg.send(s, "usage");
            return;
        }
        Crate c = manager.crate(a[1]);
        if (c == null) {
            Msg.send(s, "unknown_crate", a[1]);
            return;
        }
        Block b = target(p);
        if (b == null) {
            Msg.send(s, "not_looking");
            return;
        }
        manager.link(b, c);
        Msg.send(s, "linked", c.id);
    }

    private void unlink(CommandSender s) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "players_only");
            return;
        }
        Block b = target(p);
        if (b == null) {
            Msg.send(s, "not_looking");
            return;
        }
        if (manager.unlink(b)) {
            Msg.send(s, "unlinked");
        } else {
            Msg.send(s, "not_linked");
        }
    }

    private void list(CommandSender s) {
        if (manager.links().isEmpty()) {
            Msg.send(s, "list_empty");
            return;
        }
        Msg.send(s, "list_header");
        manager.links().forEach((pos, crate) -> s.sendMessage(Msg.c(Msg.text(s, "list_entry",
                crate, pos.world(), String.valueOf(pos.x()), String.valueOf(pos.y()), String.valueOf(pos.z())))));
    }

    /** /crates key give <virtual|physical> <speler> <crate> [aantal] */
    private void key(CommandSender s, String[] a) {
        if (a.length < 5 || !a[1].equalsIgnoreCase("give")) {
            Msg.send(s, "usage_key");
            return;
        }
        boolean virtual;
        if (a[2].equalsIgnoreCase("virtual")) {
            virtual = true;
        } else if (a[2].equalsIgnoreCase("physical")) {
            virtual = false;
        } else {
            Msg.send(s, "usage_key");
            return;
        }

        Crate c = manager.crate(a[4]);
        if (c == null) {
            Msg.send(s, "unknown_crate", a[4]);
            return;
        }
        int amount = 1;
        if (a.length > 5) {
            try {
                amount = Integer.parseInt(a[5]);
            } catch (NumberFormatException ex) {
                amount = -1;
            }
            if (amount < 1 || amount > 2304) {
                Msg.send(s, "invalid_amount");
                return;
            }
        }

        Player online = Bukkit.getPlayerExact(a[3]);
        String amountText = String.valueOf(amount);

        if (virtual) {
            // virtual keys kunnen ook naar spelers die offline zijn (zolang de server ze kent)
            OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(a[3]);
            if (target == null) {
                Msg.send(s, "player_not_found", a[3]);
                return;
            }
            manager.virtual().add(target.getUniqueId(), c.id, amount);
            Msg.send(s, "key_given_virtual", amountText, c.keyName(), a[3]);
            if (online != null) Msg.send(online, "key_received_virtual", amountText, c.keyName());
        } else {
            if (online == null) {
                Msg.send(s, "player_not_found", a[3]);
                return;
            }
            manager.giveKeys(online, c, amount);
            Msg.send(s, "key_given_physical", amountText, c.keyName(), online.getName());
            Msg.send(online, "key_received_physical", amountText, c.keyName());
        }
    }

    private void open(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "players_only");
            return;
        }
        if (a.length < 2) {
            Msg.send(s, "usage");
            return;
        }
        Crate c = manager.crate(a[1]);
        if (c == null) {
            Msg.send(s, "unknown_crate", a[1]);
            return;
        }
        manager.open(p, c, true);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("crates.admin")) return List.of();
        if (a.length == 1) return filter(List.of("link", "unlink", "list", "key", "open", "reload"), a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && (sub.equals("link") || sub.equals("open"))) return filter(manager.crateIds(), a[1]);
        if (sub.equals("key")) {
            if (a.length == 2) return filter(List.of("give"), a[1]);
            if (a.length == 3) return filter(List.of("virtual", "physical"), a[2]);
            if (a.length == 4) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, a[3]);
            }
            if (a.length == 5) return filter(manager.crateIds(), a[4]);
            if (a.length == 6) return filter(List.of("1", "8", "16", "64"), a[5]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) out.add(o);
        }
        return out;
    }
}
