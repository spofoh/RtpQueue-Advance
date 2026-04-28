package com.spy.rtpqueueadvance.listeners;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final RtpQueueAdvance plugin;

    public PlayerJoinListener(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getDatabaseManager().loadPlayerData(event.getPlayer().getUniqueId());
        plugin.getQueueManager().processInboundTransfer(event.getPlayer());
    }
}
