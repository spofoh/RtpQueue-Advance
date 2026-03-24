package com.spy.rtpqueueadvance.commands;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.utils.MessageCache;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class RtpQueueCommand implements CommandExecutor {

    private final RtpQueueAdvance plugin;

    public RtpQueueCommand(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("rtpqueue.use")) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getNoPermissionMsg()));
            return true;
        }

        plugin.getWorldSelectionGUI().openGUI(player);
        return true;
    }
}
