package com.spy.rtpqueueadvance.listeners;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final RtpQueueAdvance plugin;

    public PlayerQuitListener(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getQueueManager().isInQueue(player)) {
            plugin.getQueueManager().removeFromQueue(player);
        }
        plugin.getDatabaseManager().unloadPlayerData(player.getUniqueId());
    }
}
