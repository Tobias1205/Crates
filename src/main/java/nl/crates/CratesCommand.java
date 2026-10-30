package nl.crates;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CratesCommand implements TabExecutor {

    private final CratesPlugin plugin;
    private final CrateManager manager;

    public CratesCommand(CratesPlugin plugin, CrateManager manager) {
        this.plugin = plugin;
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

    private void key(CommandSender s, String[] a) {
        if (a.length < 4 || !a[1].equalsIgnoreCase("give")) {
            Msg.send(s, "usage_key");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[2]);
        if (target == null) {
            Msg.send(s, "player_not_found", a[2]);
            return;
        }
        Crate c = manager.crate(a[3]);
        if (c == null) {
            Msg.send(s, "unknown_crate", a[3]);
            return;
        }
        int amount = 1;
        if (a.length > 4) {
            try {
                amount = Integer.parseInt(a[4]);
            } catch (NumberFormatException ex) {
                amount = -1;
            }
            if (amount < 1 || amount > 2304) {
                Msg.send(s, "invalid_amount");
                return;
            }
        }
        manager.giveKeys(target, c, amount);
        Msg.send(s, "key_given", String.valueOf(amount), c.keyName(), target.getName());
        Msg.send(target, "key_received", String.valueOf(amount), c.keyName());
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
            if (a.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, a[2]);
            }
            if (a.length == 4) return filter(manager.crateIds(), a[3]);
            if (a.length == 5) return filter(List.of("1", "8", "16", "64"), a[4]);
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
