package com.spy.rtpqueueadvance.managers;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.utils.WorldConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConfigManager {

    private final RtpQueueAdvance plugin;
    private final Map<String, WorldConfig> worldConfigs;

    private String guiTitle;
    private int guiSize;
    private int maxPlayers;
    private int countdownSeconds;
    private String queueMode;
    private RedisSettings redisSettings;

    private List<String> blacklistedBiomes;

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
    private String transferPreparingMsg;
    private String transferFailedMsg;

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

    private boolean crossServerEnabled;
    private String localServerId;
    private int transferIntentTtlSeconds;
    private final Map<String, ServerRoute> serverRoutes;
    private final Set<String> localServerRegions;

    public ConfigManager(RtpQueueAdvance plugin) {
        this.plugin = plugin;
        this.worldConfigs = new HashMap<>();
        this.serverRoutes = new HashMap<>();
        this.localServerRegions = new HashSet<>();
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration mainConfig = plugin.getConfig();
        FileConfiguration queueConfig = loadExternalConfig("queue.yml");
        FileConfiguration crossServerConfig = loadExternalConfig("cross-server.yml");

        guiTitle = mainConfig.getString("gui.title", "&6&lRTP Queue &8| &fWorld Selection");
        guiSize = mainConfig.getInt("gui.size", 27);

        if (guiSize % 9 != 0 || guiSize < 9 || guiSize > 54) {
            guiSize = 27;
        }

        blacklistedBiomes = mainConfig.getStringList("rtp-settings.blacklisted-biomes");

        maxPlayers = queueConfig.getInt("queue.max-players", 2);
        countdownSeconds = queueConfig.getInt("queue.countdown-seconds", 3);
        queueMode = queueConfig.getString("queue.mode", "local").toLowerCase(Locale.ROOT);

        String redisHost = queueConfig.getString("queue.redis.host", "127.0.0.1");
        int redisPort = queueConfig.getInt("queue.redis.port", 6379);
        String redisPassword = queueConfig.getString("queue.redis.password", "");
        int redisDatabase = queueConfig.getInt("queue.redis.database", 0);
        String redisKeyPrefix = queueConfig.getString("queue.redis.key-prefix", "rtpqueue");
        int redisMatchLockSeconds = queueConfig.getInt("queue.redis.match-lock-seconds", 2);
        redisSettings = new RedisSettings(redisHost, redisPort, redisPassword, redisDatabase, redisKeyPrefix,
                redisMatchLockSeconds);

        prefix = mainConfig.getString("messages.prefix", "&aRTPQUEUE &8> &r");
        leftQueueMsg = mainConfig.getString("messages.left-queue", "&cYou left the queue for &e%world%&c.");
        opponentFoundMsg = mainConfig.getString("messages.opponent-found", "&aOpponent found, you will be teleported!");
        teleportationCountdownMsg = mainConfig.getString("messages.teleportation-countdown", "&eTeleportation in &c%seconds%s");
        teleportedMsg = mainConfig.getString("messages.teleported", "&aYou have been teleported!");
        alreadyInQueueMsg = mainConfig.getString("messages.already-in-queue", "&cYou are already in a queue!");
        notInQueueMsg = mainConfig.getString("messages.not-in-queue", "&cYou are not in any queue!");
        opponentLeftMsg = mainConfig.getString("messages.opponent-left",
                "&cYour opponent left! You've been placed back at the front of the queue.");
        worldNotFoundMsg = mainConfig.getString("messages.world-not-found", "&cWorld not found!");
        noPermissionMsg = mainConfig.getString("messages.no-permission", "&cYou don't have permission to use this!");
        broadcastMessageHidden = mainConfig.getString("messages.broadcast-message-hidden",
                "&cYou will no longer see RTP queue messages.");
        broadcastMessageShown = mainConfig.getString("messages.broadcast-message-shown",
                "&aYou will now see RTP queue messages.");
        configReloadedMsg = mainConfig.getString("messages.config-reloaded", "&aConfiguration reloaded successfully!");
        noSpotMsg = mainConfig.getString("messages.no-spot", "&cNo safe spot found, try again in a bit");
        transferPreparingMsg = mainConfig.getString("messages.transfer-preparing",
                "&eTransferring you to &6%server% &7[%region%] &e(%host%:%port%)...");
        transferFailedMsg = mainConfig.getString("messages.transfer-failed",
                "&cCould not transfer you to &e%server% &7[%region%]&c. Please try again.");

        guiStatusInQueue = mainConfig.getString("gui.status.in-queue", "&c-> CLICK to Leave Queue");
        guiStatusNotInQueue = mainConfig.getString("gui.status.not-in-queue", "&a-> CLICK to Queue");

        broadcastEnabled = mainConfig.getBoolean("broadcast.enabled", true);
        broadcastHeader = mainConfig.getString("broadcast.header", "&a&l* RTPQUEUE *");
        broadcastLines = new ArrayList<>();
        List<String> rawLines = mainConfig.getStringList("broadcast.lines");
        if (rawLines.isEmpty()) {
            broadcastLines.add("&e%player% &7is waiting for");
            broadcastLines.add("&7an opponent to fight!");
        } else {
            broadcastLines.addAll(rawLines);
        }
        broadcastFooter = mainConfig.getString("broadcast.footer", "&a+ /rtpqueue +");

        titleEnabled = mainConfig.getBoolean("title.enabled", true);
        titleJoinText = mainConfig.getString("title.join-title", "&a&l+");
        titleJoinSubtitle = mainConfig.getString("title.join-subtitle", "&aJoined &e&lRTPQueue&a!");
        titleFoundText = mainConfig.getString("title.found-title", "&a&l+");
        titleFoundSubtitle = mainConfig.getString("title.found-subtitle", "&aOpponent found!");
        titleFadeIn = mainConfig.getInt("title.fade-in", 10);
        titleStay = mainConfig.getInt("title.stay", 40);
        titleFadeOut = mainConfig.getInt("title.fade-out", 10);

        actionbarEnabled = mainConfig.getBoolean("actionbar.enabled", true);
        actionbarMessage = mainConfig.getString("actionbar.message",
                "&7Waiting for a &aplayer &7in &a/rtpqueue &7(%current%/%max%)");
        actionbarInterval = mainConfig.getInt("actionbar.interval", 20);

        joinMessageEnabled = mainConfig.getBoolean("join-message.enabled", true);
        joinMessageText = mainConfig.getString("join-message.message",
                "&aRTPQUEUE &8> &7You joined the RTPQueue, wait for an opponent.");

        crossServerEnabled = crossServerConfig.getBoolean("cross-server.enabled", false);
        if (crossServerEnabled && !isRedisQueueMode()) {
            plugin.getLogger().warning("Cross-server requires queue.mode=redis. Disabling cross-server until redis mode is enabled.");
            crossServerEnabled = false;
        }

        localServerId = normalizeId(crossServerConfig.getString("cross-server.local-server-id", "local"), "local");
        transferIntentTtlSeconds = Math.max(5,
                crossServerConfig.getInt("cross-server.transfer-intent-ttl-seconds", 30));

        loadServerRoutes(crossServerConfig);
        loadWorlds(mainConfig);
    }

    private FileConfiguration loadExternalConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void loadServerRoutes(FileConfiguration crossServerConfig) {
        serverRoutes.clear();

        ConfigurationSection routesSection = crossServerConfig.getConfigurationSection("cross-server.server-routes");
        if (routesSection == null) {
            return;
        }

        for (String routeId : routesSection.getKeys(false)) {
            ConfigurationSection serverSection = routesSection.getConfigurationSection(routeId);
            if (serverSection == null) {
                continue;
            }

            String host = serverSection.getString("host", "").trim();
            int port = serverSection.getInt("port", 25565);
            String normalizedRouteId = normalizeId(routeId, "");

            if (host.isEmpty()) {
                plugin.getLogger().warning("Cross-server route " + routeId + " has no host, skipping.");
                continue;
            }

            if (normalizedRouteId.isEmpty()) {
                plugin.getLogger().warning("Cross-server route " + routeId + " is invalid, skipping.");
                continue;
            }

            serverRoutes.put(normalizedRouteId, new ServerRoute(normalizedRouteId, host, port));
        }
    }

    private void loadWorlds(FileConfiguration mainConfig) {
        worldConfigs.clear();
        localServerRegions.clear();
        ConfigurationSection worldsSection = mainConfig.getConfigurationSection("worlds");

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
            String serverId = normalizeId(worldSection.getString("server", localServerId), localServerId);
            String regionId = normalizeId(worldSection.getString("region", ""), "");

            WorldConfig config = new WorldConfig(
                    worldName,
                    displayName,
                    guiWorldLore,
                    material,
                    slot,
                    minRange,
                    maxRange,
                    centerX,
                    centerZ,
                    serverId,
                    regionId);

            worldConfigs.put(worldName, config);
            if (localServerId.equals(serverId) && !regionId.isBlank()) {
                localServerRegions.add(regionId);
            }
            plugin.getLogger().info(
                    "Loaded world: " + worldName + " (slot " + slot + ", range " + minRange + "-" + maxRange
                            + ", server " + serverId + ", region " + regionId + ")");
        }

        if (localServerRegions.isEmpty()) {
            plugin.getLogger().warning("No local regions inferred for local-server-id '" + localServerId
                    + "'. Region-based transfer decisions may be inaccurate. Ensure at least one world uses this server id.");
        }
    }

    public record ServerRoute(String serverId, String host, int port) {
    }

    public record RedisSettings(String host, int port, String password, int database, String keyPrefix,
                                int matchLockSeconds) {
    }

    public Map<String, WorldConfig> getWorldConfigs() {
        return worldConfigs;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public List<String> getBlacklistedBiomes() {
        return blacklistedBiomes;
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

    public String getQueueMode() {
        return queueMode;
    }

    public boolean isRedisQueueMode() {
        return "redis".equalsIgnoreCase(queueMode);
    }

    public RedisSettings getRedisSettings() {
        return redisSettings;
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

    public String getTransferPreparingMsg() {
        return transferPreparingMsg;
    }

    public String getTransferFailedMsg() {
        return transferFailedMsg;
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

    public boolean isCrossServerEnabled() {
        return crossServerEnabled;
    }

    public String getLocalServerId() {
        return localServerId;
    }

    public boolean isLocalServerId(String serverId) {
        return serverId != null && serverId.equalsIgnoreCase(localServerId);
    }

    public boolean isLocalRegion(String regionId) {
        return regionId != null && localServerRegions.contains(regionId.toLowerCase(Locale.ROOT));
    }

    public Set<String> getLocalServerRegions() {
        return Collections.unmodifiableSet(localServerRegions);
    }

    public int getTransferIntentTtlSeconds() {
        return transferIntentTtlSeconds;
    }

    public Optional<ServerRoute> getServerRoute(String serverId) {
        if (serverId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(serverRoutes.get(serverId.toLowerCase(Locale.ROOT)));
    }

    private static String normalizeId(String value, String fallback) {
        String candidate = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!candidate.isEmpty()) {
            return candidate;
        }
        return fallback == null ? "" : fallback.trim().toLowerCase(Locale.ROOT);
    }
}
