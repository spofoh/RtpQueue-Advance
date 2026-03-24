package com.spy.rtpqueueadvance.managers;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.utils.WorldConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final RtpQueueAdvance plugin;
    private final Map<String, WorldConfig> worldConfigs;

    private String guiTitle;
    private int guiSize;
    private int maxPlayers;
    private int countdownSeconds;

    private String prefix;
    private String leftQueueMsg;
    private String opponentFoundMsg;
    private String teleportationCountdownMsg;
    private String teleportedMsg;
    private String alreadyInQueueMsg;
    private String notInQueueMsg;
    private String opponentLeftMsg;
    private String worldNotFoundMsg;
    private String noPermissionMsg;
    private String broadcastMessageHidden;
    private String broadcastMessageShown;
    private String configReloadedMsg;
    private String noSpotMsg;

    private boolean broadcastEnabled;
    private String broadcastHeader;
    private List<String> broadcastLines;
    private String broadcastFooter;

    private boolean titleEnabled;
    private String titleJoinText;
    private String titleJoinSubtitle;
    private String titleFoundText;
    private String titleFoundSubtitle;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;

    private boolean actionbarEnabled;
    private String actionbarMessage;
    private int actionbarInterval;

    private boolean joinMessageEnabled;
    private String joinMessageText;

    private String guiStatusInQueue;
    private String guiStatusNotInQueue;

    public ConfigManager(RtpQueueAdvance plugin) {
        this.plugin = plugin;
        this.worldConfigs = new HashMap<>();
        loadConfig();
    }

    private void loadConfig() {
        guiTitle = plugin.getConfig().getString("gui.title", "&6&lRTP Queue &8| &fWorld Selection");
        guiSize = plugin.getConfig().getInt("gui.size", 27);

        if (guiSize % 9 != 0 || guiSize < 9 || guiSize > 54) {
            guiSize = 27;
        }

        maxPlayers = plugin.getConfig().getInt("queue.max-players", 2);
        countdownSeconds = plugin.getConfig().getInt("queue.countdown-seconds", 3);

        prefix = plugin.getConfig().getString("messages.prefix", "&aRTPQUEUE &8» &r");
        leftQueueMsg = plugin.getConfig().getString("messages.left-queue", "&cYou left the queue for &e%world%&c.");
        opponentFoundMsg = plugin.getConfig().getString("messages.opponent-found", "&aOpponent found, you will be teleported!");
        teleportationCountdownMsg = plugin.getConfig().getString("messages.teleportation-countdown", "&eTeleportation in &c%seconds%s");
        teleportedMsg = plugin.getConfig().getString("messages.teleported", "&aYou have been teleported!");
        alreadyInQueueMsg = plugin.getConfig().getString("messages.already-in-queue", "&cYou are already in a queue!");
        notInQueueMsg = plugin.getConfig().getString("messages.not-in-queue", "&cYou are not in any queue!");
        opponentLeftMsg = plugin.getConfig().getString("messages.opponent-left",
                "&cYour opponent left! You've been placed back at the front of the queue.");
        worldNotFoundMsg = plugin.getConfig().getString("messages.world-not-found", "&cWorld not found!");
        noPermissionMsg = plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to use this!");
        broadcastMessageHidden = plugin.getConfig().getString("messages.broadcast-message-hidden",
                "&cYou will no longer see RTP queue messages.");
        broadcastMessageShown = plugin.getConfig().getString("messages.broadcast-message-shown",
                "&aYou will now see RTP queue messages.");
        configReloadedMsg = plugin.getConfig().getString("messages.config-reloaded", "&aConfiguration reloaded successfully!");

        noSpotMsg = plugin.getConfig().getString("messages.no-spot", "&cNo safe spot found, try again in a bit");

        guiStatusInQueue = plugin.getConfig().getString("gui.status.in-queue", "&c→ CLICK to Leave Queue");
        guiStatusNotInQueue = plugin.getConfig().getString("gui.status.not-in-queue", "&a→ CLICK to Queue");

        broadcastEnabled = plugin.getConfig().getBoolean("broadcast.enabled", true);
        broadcastHeader = plugin.getConfig().getString("broadcast.header", "&a&l✦ RTPQUEUE ✦");
        broadcastLines = new ArrayList<>();
        List<String> rawLines = plugin.getConfig().getStringList("broadcast.lines");
        if (rawLines.isEmpty()) {
            broadcastLines.add("&6👑&e%player% &7is waiting for");
            broadcastLines.add("&7an opponent to fight!");
        } else {
            broadcastLines.addAll(rawLines);
        }
        broadcastFooter = plugin.getConfig().getString("broadcast.footer", "&a+ /rtpqueue +");

        titleEnabled = plugin.getConfig().getBoolean("title.enabled", true);
        titleJoinText = plugin.getConfig().getString("title.join-title", "&a&l+");
        titleJoinSubtitle = plugin.getConfig().getString("title.join-subtitle", "&aJoined &e&lRTPQueue&a!");
        titleFoundText = plugin.getConfig().getString("title.found-title", "&a&l✓");
        titleFoundSubtitle = plugin.getConfig().getString("title.found-subtitle", "&aOpponent found!");
        titleFadeIn = plugin.getConfig().getInt("title.fade-in", 10);
        titleStay = plugin.getConfig().getInt("title.stay", 40);
        titleFadeOut = plugin.getConfig().getInt("title.fade-out", 10);

        actionbarEnabled = plugin.getConfig().getBoolean("actionbar.enabled", true);
        actionbarMessage = plugin.getConfig().getString("actionbar.message",
                "&7Waiting for a &aplayer &7in &a/rtpqueue &7(%current%/%max%)");
        actionbarInterval = plugin.getConfig().getInt("actionbar.interval", 20);

        joinMessageEnabled = plugin.getConfig().getBoolean("join-message.enabled", true);
        joinMessageText = plugin.getConfig().getString("join-message.message",
                "&aRTPQUEUE &8» &7You joined the RTPQueue, wait for an opponent.");

        loadWorlds();
    }

    private void loadWorlds() {
        worldConfigs.clear();
        ConfigurationSection worldsSection = plugin.getConfig().getConfigurationSection("worlds");

        if (worldsSection == null) {
            plugin.getLogger().warning("No worlds configured in config.yml!");
            return;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
            if (worldSection == null)
                continue;

            boolean enabled = worldSection.getBoolean("enabled", true);
            if (!enabled)
                continue;

            String displayName = worldSection.getString("display-name", "&f" + worldName);

            List<String> guiWorldLore = worldSection.getStringList("world-item-lore");

            Material material;
            try {
                material = Material.valueOf(worldSection.getString("material", "GRASS_BLOCK").toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.GRASS_BLOCK;
                plugin.getLogger().warning("Invalid material for world " + worldName + ", using GRASS_BLOCK");
            }

            int slot = worldSection.getInt("slot", 0);
            int minRange = worldSection.getInt("spawn-range.min", 100);
            int maxRange = worldSection.getInt("spawn-range.max", 5000);
            int centerX = worldSection.getInt("center.x", 0);
            int centerZ = worldSection.getInt("center.z", 0);

            WorldConfig config = new WorldConfig(
                    worldName,
                    displayName,
                    guiWorldLore,
                    material,
                    slot,
                    minRange,
                    maxRange,
                    centerX,
                    centerZ);

            worldConfigs.put(worldName, config);
            plugin.getLogger().info(
                    "Loaded world: " + worldName + " (slot " + slot + ", range " + minRange + "-" + maxRange + ")");
        }
    }



    public Map<String, WorldConfig> getWorldConfigs() {
        return worldConfigs;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiSize() {
        return guiSize;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getLeftQueueMsg() {
        return leftQueueMsg;
    }

    public String getOpponentFoundMsg() {
        return opponentFoundMsg;
    }

    public String getTeleportationCountdownMsg() {
        return teleportationCountdownMsg;
    }

    public String getTeleportedMsg() {
        return teleportedMsg;
    }

    public String getAlreadyInQueueMsg() {
        return alreadyInQueueMsg;
    }

    public String getNotInQueueMsg() {
        return notInQueueMsg;
    }

    public String getOpponentLeftMsg() {
        return opponentLeftMsg;
    }

    public String getWorldNotFoundMsg() {
        return worldNotFoundMsg;
    }

    public String getNoPermissionMsg() {
        return noPermissionMsg;
    }

    public String getBroadcastMessageHidden() {
        return broadcastMessageHidden;
    }

    public String getBroadcastMessageShown() {
        return broadcastMessageShown;
    }

    public String getConfigReloadedMsg() {
        return configReloadedMsg;
    }

    public String getNoSpotMsg() {
        return noSpotMsg;
    }

    public boolean isBroadcastEnabled() {
        return broadcastEnabled;
    }

    public String getBroadcastHeader() {
        return broadcastHeader;
    }

    public List<String> getBroadcastLines() {
        return broadcastLines;
    }

    public String getBroadcastFooter() {
        return broadcastFooter;
    }

    public boolean isTitleEnabled() {
        return titleEnabled;
    }

    public String getTitleJoinText() {
        return titleJoinText;
    }

    public String getTitleJoinSubtitle() {
        return titleJoinSubtitle;
    }

    public String getTitleFoundText() {
        return titleFoundText;
    }

    public String getTitleFoundSubtitle() {
        return titleFoundSubtitle;
    }

    public int getTitleFadeIn() {
        return titleFadeIn;
    }

    public int getTitleStay() {
        return titleStay;
    }

    public int getTitleFadeOut() {
        return titleFadeOut;
    }

    public boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public String getActionbarMessage() {
        return actionbarMessage;
    }

    public int getActionbarInterval() {
        return actionbarInterval;
    }

    public boolean isJoinMessageEnabled() {
        return joinMessageEnabled;
    }

    public String getGuiStatusInQueue() {
        return guiStatusInQueue;
    }

    public String getGuiStatusNotInQueue() {
        return guiStatusNotInQueue;
    }

    public String getJoinMessageText() {
        return joinMessageText;
    }
}
