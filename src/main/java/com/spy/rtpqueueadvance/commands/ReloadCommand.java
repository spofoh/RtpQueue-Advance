package com.spy.rtpqueueadvance.commands;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.managers.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final RtpQueueAdvance plugin;

    public ReloadCommand(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rtpqueue.admin")) {
            sender.sendMessage(ConfigManager.colorize(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getNoPermissionMsg()));
            return true;
        }

        plugin.reload();
        sender.sendMessage(ConfigManager.colorize(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getConfigReloadedMsg()));
        return true;
    }
}
