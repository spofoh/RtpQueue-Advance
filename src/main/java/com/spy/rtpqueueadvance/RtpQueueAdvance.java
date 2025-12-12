package com.spy.rtpqueueadvance;

import com.spy.rtpqueueadvance.commands.RtpQueueCommand;
import com.spy.rtpqueueadvance.commands.LeaveQueueCommand;
import com.spy.rtpqueueadvance.commands.ReloadCommand;
import com.spy.rtpqueueadvance.gui.WorldSelectionGUI;
import com.spy.rtpqueueadvance.listeners.GUIListener;
import com.spy.rtpqueueadvance.listeners.PlayerQuitListener;
import com.spy.rtpqueueadvance.managers.ConfigManager;
import com.spy.rtpqueueadvance.managers.QueueManager;
import org.bukkit.plugin.java.JavaPlugin;

public class RtpQueueAdvance extends JavaPlugin {

    private static RtpQueueAdvance instance;
    private ConfigManager configManager;
    private QueueManager queueManager;
    private WorldSelectionGUI worldSelectionGUI;

    @Override
    public void onEnable() {
        instance = this;
        
        saveDefaultConfig();
        
        configManager = new ConfigManager(this);
        queueManager = new QueueManager(this);
        worldSelectionGUI = new WorldSelectionGUI(this);
        
        getCommand("rtpqueue").setExecutor(new RtpQueueCommand(this));
        getCommand("rtpqueueleave").setExecutor(new LeaveQueueCommand(this));
        getCommand("rtpqueuereload").setExecutor(new ReloadCommand(this));
        
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        
        getLogger().info("RtpQueue Advance has been enabled!");
        getLogger().info("Loaded " + configManager.getWorldConfigs().size() + " world(s)");
    }

    @Override
    public void onDisable() {
        if (queueManager != null) {
            queueManager.clearAllQueues();
        }
        getLogger().info("RtpQueue Advance has been disabled!");
    }

    public static RtpQueueAdvance getInstance() {
        return instance;
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
        configManager = new ConfigManager(this);
        queueManager.clearAllQueues();
        worldSelectionGUI = new WorldSelectionGUI(this);
    }
}
