package com.spy.rtpqueueadvance.commands;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.utils.MessageCache;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

public class ReloadCommand implements CommandExecutor {

    private final RtpQueueAdvance plugin;

    public ReloadCommand(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("rtpqueue.admin")) {
            sender.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getNoPermissionMsg()));
            return true;
        }

        plugin.reload();
        sender.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getConfigReloadedMsg()));
        return true;
    }
}
