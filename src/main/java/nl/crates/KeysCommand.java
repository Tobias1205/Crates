package nl.crates;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /keys: spelers zien hoeveel virtual keys ze hebben. */
public final class KeysCommand implements CommandExecutor {

    private final CrateManager manager;

    public KeysCommand(CrateManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "players_only");
            return true;
        }
        boolean any = false;
        for (String id : manager.crateIds()) {
            int n = manager.virtual().get(p.getUniqueId(), id);
            if (n <= 0) continue;
            if (!any) {
                Msg.send(p, "keys_header");
                any = true;
            }
            p.sendMessage(Msg.c(Msg.text(p, "keys_entry", manager.crate(id).keyName(), String.valueOf(n))));
        }
        if (!any) Msg.send(p, "keys_none");
        return true;
    }
}
