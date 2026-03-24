package com.spy.rtpqueueadvance;

import com.spy.rtpqueueadvance.commands.RtpQueueCommand;
import com.spy.rtpqueueadvance.commands.LeaveQueueCommand;
import com.spy.rtpqueueadvance.commands.ReloadCommand;
import com.spy.rtpqueueadvance.commands.ToggleMessagesCommand;
import com.spy.rtpqueueadvance.gui.WorldSelectionGUI;
import com.spy.rtpqueueadvance.listeners.GUIListener;
import com.spy.rtpqueueadvance.listeners.PlayerJoinListener;
import com.spy.rtpqueueadvance.listeners.PlayerQuitListener;
import com.spy.rtpqueueadvance.managers.ConfigManager;
import com.spy.rtpqueueadvance.managers.DatabaseManager;
import com.spy.rtpqueueadvance.managers.QueueManager;
import com.spy.rtpqueueadvance.utils.MessageCache;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class RtpQueueAdvance extends JavaPlugin {

    private ConfigManager configManager;
    private QueueManager queueManager;
    private DatabaseManager databaseManager;
    private WorldSelectionGUI worldSelectionGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        queueManager = new QueueManager(this);
        worldSelectionGUI = new WorldSelectionGUI(this);

        PluginCommand rtpqueue = getCommand("rtpqueue");
        if (rtpqueue != null) {
            rtpqueue.setExecutor(new RtpQueueCommand(this));
        }
        PluginCommand rtpqueueleave = getCommand("rtpqueueleave");
        if (rtpqueueleave != null) {
            rtpqueueleave.setExecutor(new LeaveQueueCommand(this));
        }
        PluginCommand rtpqueuereload  = getCommand("rtpqueuereload");
        if (rtpqueuereload != null) {
            rtpqueuereload.setExecutor(new ReloadCommand(this));
        }
        PluginCommand rtpqueuetoggle = getCommand("rtpqueuetoggle");
        if (rtpqueuetoggle != null) {
            rtpqueuetoggle.setExecutor(new ToggleMessagesCommand(this));
        }
        
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            databaseManager.loadPlayerData(player.getUniqueId());
        }
        
        getLogger().info("RtpQueue Advance has been enabled!");
        getLogger().info("Loaded " + configManager.getWorldConfigs().size() + " world(s)");
    }

    @Override
    public void onDisable() {
        if (queueManager != null) {
            queueManager.clearAllQueues();
        }
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        getLogger().info("RtpQueue Advance has been disabled!");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public WorldSelectionGUI getWorldSelectionGUI() {
        return worldSelectionGUI;
    }

    public void reload() {
        reloadConfig();
        if (queueManager != null) {
            queueManager.clearAllQueues();
        }
        MessageCache.clear();
        configManager = new ConfigManager(this);
        worldSelectionGUI = new WorldSelectionGUI(this);
    }
}
