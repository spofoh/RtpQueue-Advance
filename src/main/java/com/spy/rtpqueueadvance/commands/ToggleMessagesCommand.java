package com.spy.rtpqueueadvance.commands;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.utils.MessageCache;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class ToggleMessagesCommand implements CommandExecutor {

    private final RtpQueueAdvance plugin;

    public ToggleMessagesCommand(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("rtpqueue.toggle")) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getNoPermissionMsg()));
            return true;
        }

        plugin.getDatabaseManager().toggleMessages(player.getUniqueId());

        boolean nowEnabled = plugin.getDatabaseManager().hasMessagesEnabled(player.getUniqueId());

        String msg = nowEnabled ?
                plugin.getConfigManager().getBroadcastMessageShown() :
                plugin.getConfigManager().getBroadcastMessageHidden();

        player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + msg));
        return true;
    }
}