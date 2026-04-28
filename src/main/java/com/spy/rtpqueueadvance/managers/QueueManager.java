package com.spy.rtpqueueadvance.managers;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.utils.WorldConfig;
import com.spy.rtpqueueadvance.utils.MessageCache;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class QueueManager {
    private static final String TRANSFER_INTENT_VERSION = "1";
    private static final String TRANSFER_INTENT_SEPARATOR = "\\|";
    private static final String MATCH_ASSIGNMENT_VERSION = "1";
    private static final String MATCH_ASSIGNMENT_SEPARATOR = "\\|";
    private static final int REDIS_SYNC_INTERVAL_TICKS = 5;
    private static final int SAME_REGION_CONNECT_MAX_ATTEMPTS = 5;
    private static final long SAME_REGION_CONNECT_RETRY_DELAY_TICKS = 10L;

    private static final EnumSet<Material> UNSAFE_MATERIALS = EnumSet.of(
            Material.LAVA, Material.WATER, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.COBWEB,
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
            Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES);

    private final RtpQueueAdvance plugin;
    private final Map<String, LinkedList<UUID>> waitingQueues;
    private final Map<UUID, Match> activeMatches;
    private final Map<UUID, ScheduledTask> actionbarTasks;
    private final Set<UUID> playersInQueue;
    private final Map<UUID, String> playerQueueWorlds;
    private final NamespacedKey transferIntentCookieKey;
    private final RedisQueueManager redisQueueManager;
    private final boolean redisQueueMode;
    private final Map<String, PendingRedisMatch> pendingRedisMatches;
    private final Map<String, Long> processedQueueJoinEvents;
    private ScheduledTask redisSyncTask;

    private enum RoutingMode {
        LOCAL,
        SAME_REGION_PROXY_SWITCH,
        CROSS_REGION_TRANSFER
    }

    public static class Match {
        private final Set<UUID> players;
        private final String worldName;
        private int countdown;
        private ScheduledTask task;

        public Match(Set<UUID> players, String worldName, int countdown) {
            this.players = players;
            this.worldName = worldName;
            this.countdown = countdown;
        }

        public Set<UUID> getPlayers() {
            return players;
        }

        public String getWorldName() {
            return worldName;
        }

        public int getCountdown() {
            return countdown;
        }

        public void decrementCountdown() {
            this.countdown--;
        }

        public ScheduledTask getTask() {
            return task;
        }

        public void setTask(ScheduledTask task) {
            this.task = task;
        }
    }

    public QueueManager(RtpQueueAdvance plugin, RedisQueueManager redisQueueManager) {
        this.plugin = plugin;
        this.redisQueueManager = redisQueueManager;
        this.redisQueueMode = plugin.getConfigManager().isRedisQueueMode() && redisQueueManager != null
                && redisQueueManager.isEnabled();
        this.waitingQueues = new ConcurrentHashMap<>();
        this.activeMatches = new ConcurrentHashMap<>();
        this.actionbarTasks = new ConcurrentHashMap<>();
        this.playersInQueue = ConcurrentHashMap.newKeySet();
        this.playerQueueWorlds = new ConcurrentHashMap<>();
        this.transferIntentCookieKey = new NamespacedKey(plugin, "transfer-intent");
        this.pendingRedisMatches = new ConcurrentHashMap<>();
        this.processedQueueJoinEvents = new ConcurrentHashMap<>();
        this.redisSyncTask = null;

        if (redisQueueMode) {
            this.redisSyncTask = Bukkit.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
                tickRedisNetworkState();
            }, REDIS_SYNC_INTERVAL_TICKS, REDIS_SYNC_INTERVAL_TICKS);
        }
    }

    private static class PendingRedisMatch {
        private final String matchId;
        private final String worldName;
        private final int countdownSeconds;
        private final int expectedPlayers;
        private final long firstSeenEpochMs;
        private final Set<UUID> players = ConcurrentHashMap.newKeySet();

        private PendingRedisMatch(String matchId, String worldName, int countdownSeconds, int expectedPlayers,
                                  long firstSeenEpochMs) {
            this.matchId = matchId;
            this.worldName = worldName;
            this.countdownSeconds = countdownSeconds;
            this.expectedPlayers = expectedPlayers;
            this.firstSeenEpochMs = firstSeenEpochMs;
        }
    }

    public void addToQueue(Player player, String worldName) {
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getWorldNotFoundMsg()));
            return;
        }

        if (player.hasPermission("rtpqueue.bypass")) {
            instantTeleport(player, config);
            return;
        }

        if (playersInQueue.contains(player.getUniqueId())) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getAlreadyInQueueMsg()));
            return;
        }

        playersInQueue.add(player.getUniqueId());
        playerQueueWorlds.put(player.getUniqueId(), worldName);

        if (redisQueueMode) {
            boolean added = redisQueueManager.enqueue(worldName, player.getUniqueId());
            if (!added) {
                playersInQueue.remove(player.getUniqueId());
                playerQueueWorlds.remove(player.getUniqueId());
                player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getAlreadyInQueueMsg()));
                return;
            }
        } else {
            LinkedList<UUID> queue = waitingQueues.computeIfAbsent(worldName, k -> new LinkedList<>());
            synchronized (queue) {
                queue.add(player.getUniqueId());
            }
        }

        int current = getQueueSize(worldName);

        if (plugin.getConfigManager().isJoinMessageEnabled()) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getJoinMessageText()));
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        if (plugin.getConfigManager().isTitleEnabled()) {
            sendJoinTitle(player);
        }

        if (plugin.getConfigManager().isActionbarEnabled()) {
            sendActionBar(player, current);
        }

        if (plugin.getConfigManager().isBroadcastEnabled()) {
            broadcastQueueJoin(player);
            if (redisQueueMode && redisQueueManager != null && redisQueueManager.isEnabled()) {
                redisQueueManager.publishQueueJoinEvent(plugin.getConfigManager().getLocalServerId(),
                        player.getName(), worldName);
            }
        }

        if (redisQueueMode) {
            checkAndFormRedisMatch(worldName);
        } else {
            checkAndFormMatch(worldName);
        }
    }

    private void checkAndFormMatch(String worldName) {
        LinkedList<UUID> queue = waitingQueues.get(worldName);
        if (queue == null)
            return;

        int max = plugin.getConfigManager().getMaxPlayers();

        synchronized (queue) {
            while (queue.size() >= max) {
                Set<UUID> matchPlayers = new HashSet<>();
                for (int i = 0; i < max; i++) {
                    matchPlayers.add(queue.poll());
                }

                Match match = new Match(matchPlayers, worldName, plugin.getConfigManager().getCountdownSeconds());
                for (UUID uuid : matchPlayers) {
                    activeMatches.put(uuid, match);
                }

                startMatchCountdown(match);
            }
        }
    }

    private void checkAndFormRedisMatch(String worldName) {
        int max = plugin.getConfigManager().getMaxPlayers();
        List<UUID> matchedPlayers = redisQueueManager.tryPopMatch(worldName, max);
        if (matchedPlayers.isEmpty()) {
            return;
        }

        String matchId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        int countdown = plugin.getConfigManager().getCountdownSeconds();
        int ttlSeconds = Math.max(30, countdown + plugin.getConfigManager().getTransferIntentTtlSeconds() + 15);

        boolean assigned = redisQueueManager.storeMatchAssignments(matchId, worldName, matchedPlayers, createdAt,
                countdown, matchedPlayers.size(), ttlSeconds);
        if (!assigned) {
            redisQueueManager.requeueFront(worldName, matchedPlayers);
            plugin.getLogger().warning("Failed to assign redis match " + matchId + " for world " + worldName
                    + ". Re-queued players.");
            return;
        }

        for (UUID uuid : matchedPlayers) {
            if (playersInQueue.contains(uuid) && worldName.equals(playerQueueWorlds.get(uuid))) {
                registerPendingRedisMatch(matchId, worldName, countdown, matchedPlayers.size(), createdAt, uuid);
            }
        }

        // Handle local players immediately to reduce queue-size flicker before the next redis sync tick.
        pollRedisMatchAssignments();
        startPendingRedisMatches();
    }

    private void tickRedisNetworkState() {
        if (!redisQueueMode || redisQueueManager == null || !redisQueueManager.isEnabled()) {
            return;
        }

        try {
            pollRedisQueueJoinEvents();
            pollRedisMatchAssignments();
            startPendingRedisMatches();
            cleanupProcessedQueueJoinEvents();
        } catch (Exception e) {
            plugin.getLogger().warning("Redis network sync tick failed: " + e.getMessage());
        }
    }

    private void pollRedisMatchAssignments() {
        for (UUID uuid : new ArrayList<>(playersInQueue)) {
            if (activeMatches.containsKey(uuid)) {
                continue;
            }

            String queuedWorld = playerQueueWorlds.get(uuid);
            if (queuedWorld == null || queuedWorld.isBlank()) {
                continue;
            }

            String rawAssignment = redisQueueManager.consumeMatchAssignment(uuid);
            if (rawAssignment == null || rawAssignment.isBlank()) {
                continue;
            }

            RedisMatchAssignment assignment = decodeRedisMatchAssignment(rawAssignment);
            if (assignment == null) {
                continue;
            }

            if (!queuedWorld.equalsIgnoreCase(assignment.worldName())) {
                plugin.getLogger().warning("Ignoring redis match assignment for " + uuid + " (queued in "
                        + queuedWorld + ", assignment world " + assignment.worldName() + ")");
                continue;
            }

            registerPendingRedisMatch(assignment.matchId(), assignment.worldName(), assignment.countdownSeconds(),
                    assignment.expectedPlayers(), assignment.createdAtEpochMs(), uuid);
        }
    }

    private void startPendingRedisMatches() {
        long now = System.currentTimeMillis();
        List<String> completedMatchIds = new ArrayList<>();

        for (PendingRedisMatch pending : pendingRedisMatches.values()) {
            boolean readyByCount = pending.players.size() >= pending.expectedPlayers;
            boolean readyByTime = (now - pending.firstSeenEpochMs) >= 400L;
            if (!readyByCount && !readyByTime) {
                continue;
            }

            Set<UUID> localPlayers = new HashSet<>();
            for (UUID uuid : pending.players) {
                if (!playersInQueue.contains(uuid) || activeMatches.containsKey(uuid)) {
                    continue;
                }
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                String queuedWorld = playerQueueWorlds.get(uuid);
                if (queuedWorld == null || !queuedWorld.equalsIgnoreCase(pending.worldName)) {
                    continue;
                }
                localPlayers.add(uuid);
            }

            if (!localPlayers.isEmpty()) {
                Match match = new Match(localPlayers, pending.worldName, pending.countdownSeconds);
                for (UUID uuid : localPlayers) {
                    activeMatches.put(uuid, match);
                }
                startMatchCountdown(match);
                plugin.getLogger().info("Started redis distributed match " + pending.matchId + " for world "
                        + pending.worldName + " with " + localPlayers.size() + " local player(s).");
            }

            completedMatchIds.add(pending.matchId);
        }

        for (String matchId : completedMatchIds) {
            pendingRedisMatches.remove(matchId);
        }
    }

    private void registerPendingRedisMatch(String matchId, String worldName, int countdownSeconds, int expectedPlayers,
                                           long createdAtEpochMs, UUID uuid) {
        PendingRedisMatch pending = pendingRedisMatches.computeIfAbsent(matchId, id -> new PendingRedisMatch(
                id,
                worldName,
                Math.max(0, countdownSeconds),
                Math.max(1, expectedPlayers),
                createdAtEpochMs
        ));
        pending.players.add(uuid);
    }

    private void pollRedisQueueJoinEvents() {
        if (!plugin.getConfigManager().isBroadcastEnabled()) {
            return;
        }

        String localServerId = plugin.getConfigManager().getLocalServerId();
        long now = System.currentTimeMillis();
        for (RedisQueueManager.QueueJoinEvent event : redisQueueManager.fetchRecentQueueJoinEvents(100)) {
            if (event == null || event.eventId() == null || event.eventId().isBlank()) {
                continue;
            }
            if (localServerId.equalsIgnoreCase(event.sourceServerId())) {
                continue;
            }
            if (processedQueueJoinEvents.putIfAbsent(event.eventId(), now) != null) {
                continue;
            }

            broadcastQueueJoin(event.playerName());
        }
    }

    private void cleanupProcessedQueueJoinEvents() {
        long threshold = System.currentTimeMillis() - 120_000L;
        processedQueueJoinEvents.entrySet().removeIf(entry -> entry.getValue() < threshold);
        if (processedQueueJoinEvents.size() > 4000) {
            processedQueueJoinEvents.clear();
        }
    }

    private void removeFromPendingRedisMatches(UUID uuid) {
        if (uuid == null) {
            return;
        }
        for (PendingRedisMatch pending : pendingRedisMatches.values()) {
            pending.players.remove(uuid);
        }
        pendingRedisMatches.entrySet().removeIf(entry -> entry.getValue().players.isEmpty());
    }

    private int getPendingRedisMatchPlayerCount(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return 0;
        }

        int count = 0;
        for (PendingRedisMatch pending : pendingRedisMatches.values()) {
            if (worldName.equalsIgnoreCase(pending.worldName)) {
                count += pending.players.size();
            }
        }
        return count;
    }

    private RedisMatchAssignment decodeRedisMatchAssignment(String raw) {
        String[] parts = raw.split(MATCH_ASSIGNMENT_SEPARATOR, 6);
        if (parts.length < 6 || !MATCH_ASSIGNMENT_VERSION.equals(parts[0])) {
            return null;
        }

        try {
            long createdAt = Long.parseLong(parts[3]);
            int countdown = Integer.parseInt(parts[4]);
            int expectedPlayers = Integer.parseInt(parts[5]);
            String matchId = parts[1];
            String worldName = parts[2];
            if (matchId.isBlank() || worldName.isBlank()) {
                return null;
            }
            return new RedisMatchAssignment(matchId, worldName, createdAt, countdown, expectedPlayers);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RedisMatchAssignment(String matchId, String worldName, long createdAtEpochMs, int countdownSeconds,
                                        int expectedPlayers) {
    }

    public void instantTeleport(Player player, WorldConfig config) {
        RoutingMode routingMode = getRoutingMode(config);
        if (routingMode == RoutingMode.CROSS_REGION_TRANSFER) {
            startCrossRegionTransfer(player, config);
            return;
        }
        if (routingMode == RoutingMode.SAME_REGION_PROXY_SWITCH) {
            startSameRegionProxySwitch(player, config);
            return;
        }

        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            player.sendMessage(MessageCache.getComponent(
                    plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getWorldNotFoundMsg()));
            return;
        }

        findAndTeleport(player, world, config, 0);
    }

    public void processInboundTransfer(Player player) {
        if (!redisQueueMode || !plugin.getConfigManager().isCrossServerEnabled()) {
            return;
        }

        TransferIntent redisIntent = consumeRedisTransferIntent(player);
        if (redisIntent != null) {
            plugin.getLogger().info("Inbound transfer intent (redis) for " + player.getName()
                    + " -> world " + redisIntent.worldName());
            handleInboundTransferIntent(player, redisIntent);
            return;
        }

        player.retrieveCookie(transferIntentCookieKey).thenAccept(cookieBytes -> {
            if (cookieBytes == null || cookieBytes.length == 0) {
                return;
            }

            TransferIntent intent = decodeTransferIntent(cookieBytes);
            if (intent == null) {
                return;
            }

            plugin.getLogger().info("Inbound transfer intent (cookie fallback) for " + player.getName()
                    + " -> world " + intent.worldName());
            handleInboundTransferIntent(player, intent);
        });
    }

    private TransferIntent consumeRedisTransferIntent(Player player) {
        if (!redisQueueMode || redisQueueManager == null || !redisQueueManager.isEnabled()) {
            return null;
        }

        String encoded = redisQueueManager.consumeTransferIntent(player.getUniqueId());
        if (encoded == null || encoded.isBlank()) {
            return null;
        }

        return decodeTransferIntent(encoded);
    }

    private void handleInboundTransferIntent(Player player, TransferIntent intent) {
        if (intent.expiresAtEpochMs() < System.currentTimeMillis()) {
            plugin.getLogger().warning("Discarding expired transfer intent for " + player.getName()
                    + " -> world " + intent.worldName());
            clearTransferIntent(player);
            return;
        }

        WorldConfig worldConfig = plugin.getConfigManager().getWorldConfigs().get(intent.worldName());
        if (worldConfig == null) {
            plugin.getLogger().warning("Discarding transfer intent for " + player.getName()
                    + " because world config is missing: " + intent.worldName());
            clearTransferIntent(player);
            return;
        }

        if (!plugin.getConfigManager().isLocalServerId(worldConfig.serverId())) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                startSameRegionProxySwitch(player, worldConfig);
            });
            return;
        }

        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            clearTransferIntent(player);
            instantTeleport(player, worldConfig);
        });
    }

    private boolean isCrossServerTarget(WorldConfig config) {
        return getRoutingMode(config) != RoutingMode.LOCAL;
    }

    private RoutingMode getRoutingMode(WorldConfig config) {
        if (!redisQueueMode || !plugin.getConfigManager().isCrossServerEnabled()) {
            return RoutingMode.LOCAL;
        }

        String targetServerId = config.serverId();
        if (targetServerId == null || targetServerId.isBlank()) {
            return RoutingMode.LOCAL;
        }

        if (plugin.getConfigManager().isLocalServerId(targetServerId)) {
            return RoutingMode.LOCAL;
        }

        String targetRegionId = config.regionId();
        if (!targetRegionId.isBlank() && plugin.getConfigManager().isLocalRegion(targetRegionId)) {
            return RoutingMode.SAME_REGION_PROXY_SWITCH;
        }

        return RoutingMode.CROSS_REGION_TRANSFER;
    }

    private void startCrossRegionTransfer(Player player, WorldConfig worldConfig) {
        String targetServerId = worldConfig.serverId();
        String targetRegionId = worldConfig.regionId();
        if (targetServerId == null || targetServerId.isBlank()) {
            player.sendMessage(MessageCache.getComponent(
                    plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getWorldNotFoundMsg()));
            return;
        }
        if (targetRegionId == null || targetRegionId.isBlank()) {
            player.sendMessage(MessageCache.getComponent(
                    plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getTransferFailedMsg()
                            .replace("%server%", targetServerId)
                            .replace("%region%", "unknown")));
            return;
        }

        Optional<ConfigManager.ServerRoute> routeOptional = plugin.getConfigManager().getServerRoute(targetRegionId);
        if (routeOptional.isEmpty()) {
            player.sendMessage(MessageCache.getComponent(
                    plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getTransferFailedMsg()
                            .replace("%server%", targetServerId)
                            .replace("%region%", targetRegionId)));
            plugin.getLogger().warning("No cross-server route defined for region id: " + targetRegionId
                    + " (world " + worldConfig.worldName() + ", target server " + targetServerId + ")");
            return;
        }

        ConfigManager.ServerRoute route = routeOptional.get();
        if (!storeTransferIntent(player, worldConfig.worldName())) {
            player.sendMessage(MessageCache.getComponent(
                    plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getTransferFailedMsg()
                            .replace("%server%", targetServerId)
                            .replace("%region%", targetRegionId)));
            return;
        }

        player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getTransferPreparingMsg()
                .replace("%server%", targetServerId)
                .replace("%region%", targetRegionId)
                .replace("%host%", route.host())
                .replace("%port%", String.valueOf(route.port()))));
        plugin.getLogger().info("Routing " + player.getName() + " to region " + targetRegionId
                + " via transfer " + route.host() + ":" + route.port()
                + " for world " + worldConfig.worldName() + " targeting server " + targetServerId + ".");

        try {
            player.transfer(route.host(), route.port());
        } catch (IllegalStateException e) {
            player.sendMessage(MessageCache.getComponent(
                    plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getTransferFailedMsg()
                            .replace("%server%", targetServerId)
                            .replace("%region%", targetRegionId)));
            plugin.getLogger().warning("Transfer failed for " + player.getName() + " to " + route.host() + ":"
                    + route.port() + " (target " + targetServerId + "@" + targetRegionId + ", "
                    + e.getMessage() + ")");
        }
    }

    private void startSameRegionProxySwitch(Player player, WorldConfig worldConfig) {
        String targetServerId = worldConfig.serverId();
        if (targetServerId == null || targetServerId.isBlank()) {
            player.sendMessage(MessageCache.getComponent(
                    plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getWorldNotFoundMsg()));
            return;
        }

        if (!storeTransferIntent(player, worldConfig.worldName())) {
            player.sendMessage(MessageCache.getComponent(
                    plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getTransferFailedMsg()
                            .replace("%server%", targetServerId)
                            .replace("%region%", worldConfig.regionId().isBlank() ? "local" : worldConfig.regionId())));
            return;
        }

        player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix()
                + plugin.getConfigManager().getTransferPreparingMsg()
                .replace("%server%", targetServerId)
                .replace("%region%", worldConfig.regionId().isBlank() ? "local" : worldConfig.regionId())
                .replace("%host%", "proxy")
                .replace("%port%", "-")));
        plugin.getLogger().info("Routing " + player.getName() + " to backend " + targetServerId
                + " via proxy Connect for world " + worldConfig.worldName() + ".");

        // Initial connect + retries because immediately after a region transfer, a single Connect can be flaky.
        sendProxyConnect(player, targetServerId);
        scheduleSameRegionProxyConnectRetry(player, targetServerId, 2);
    }

    private void scheduleSameRegionProxyConnectRetry(Player player, String targetServerId, int attempt) {
        if (attempt > SAME_REGION_CONNECT_MAX_ATTEMPTS) {
            return;
        }

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                return;
            }

            plugin.getLogger().info("Retrying proxy Connect for " + player.getName() + " -> " + targetServerId
                    + " (attempt " + attempt + "/" + SAME_REGION_CONNECT_MAX_ATTEMPTS + ").");
            sendProxyConnect(player, targetServerId);
            scheduleSameRegionProxyConnectRetry(player, targetServerId, attempt + 1);
        }, SAME_REGION_CONNECT_RETRY_DELAY_TICKS);
    }

    private void sendProxyConnect(Player player, String targetServerId) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(targetServerId);

        try {
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to send proxy Connect request for " + player.getName()
                    + " -> " + targetServerId + " (" + ex.getMessage() + ")");
        }
    }

    private boolean storeTransferIntent(Player player, String worldName) {
        long expiry = System.currentTimeMillis() + plugin.getConfigManager().getTransferIntentTtlSeconds() * 1000L;
        String serialized = TRANSFER_INTENT_VERSION + "|" + worldName + "|" + expiry + "|" + UUID.randomUUID();

        boolean redisStored = false;
        if (redisQueueMode && redisQueueManager != null && redisQueueManager.isEnabled()) {
            redisStored = redisQueueManager.storeTransferIntent(player.getUniqueId(), serialized,
                    plugin.getConfigManager().getTransferIntentTtlSeconds());
            if (!redisStored) {
                plugin.getLogger().warning("Could not store transfer intent in Redis for " + player.getName() + ".");
            }
        }

        boolean cookieStored = false;
        try {
            player.storeCookie(transferIntentCookieKey, serialized.getBytes(StandardCharsets.UTF_8));
            cookieStored = true;
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("Could not store transfer intent cookie for " + player.getName() + ": "
                    + e.getMessage());
        }

        return redisStored || cookieStored;
    }

    private void clearTransferIntent(Player player) {
        if (redisQueueMode && redisQueueManager != null && redisQueueManager.isEnabled()) {
            redisQueueManager.clearTransferIntent(player.getUniqueId());
        }
        try {
            player.storeCookie(transferIntentCookieKey, new byte[0]);
        } catch (IllegalStateException ignored) {
        }
    }

    private TransferIntent decodeTransferIntent(byte[] rawCookie) {
        return decodeTransferIntent(new String(rawCookie, StandardCharsets.UTF_8));
    }

    private TransferIntent decodeTransferIntent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String[] parts = raw.split(TRANSFER_INTENT_SEPARATOR, 4);
        if (parts.length < 3 || !TRANSFER_INTENT_VERSION.equals(parts[0])) {
            return null;
        }

        String worldName = parts[1];
        if (worldName.isBlank()) {
            return null;
        }

        try {
            long expiry = Long.parseLong(parts[2]);
            return new TransferIntent(worldName, expiry);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record TransferIntent(String worldName, long expiresAtEpochMs) {
    }

    private Location findSafeLocation(World world, int x, int z) {
        Biome biome = world.getBiome(x, 64, z);
        if (isBiomeBlacklisted(biome)) {
            return null;
        }

        int y = world.getHighestBlockYAt(x, z);

        for (int i = 0; i < 10; i++) {
            int currentY = y - i;
            if (currentY < world.getMinHeight()) break;

            Location checkLoc = new Location(world, x + 0.5, currentY, z + 0.5);
            org.bukkit.block.Block ground = checkLoc.getBlock();

            if (ground.isLiquid()) {
                return null;
            }

            org.bukkit.block.Block feet = ground.getRelative(org.bukkit.block.BlockFace.UP);
            org.bukkit.block.Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);

            Material groundType = ground.getType();

            boolean materialSafe = groundType.isSolid() && !UNSAFE_MATERIALS.contains(groundType);

            boolean spaceSafe = feet.isPassable() && head.isPassable() && !feet.isLiquid();

            if (materialSafe && spaceSafe) {
                int SAFE_RADIUS = 20;
                boolean playerNearby = world.getNearbyEntities(checkLoc, SAFE_RADIUS, SAFE_RADIUS, SAFE_RADIUS)
                        .stream()
                        .anyMatch(entity -> entity instanceof Player);

                if (!playerNearby) {
                    if (world.getEnvironment() == World.Environment.THE_END) {
                        if (!checkLoc.clone().add(2, -1, 0).getBlock().getType().isSolid()) return null;
                    }
                    return checkLoc.add(0, 1, 0);
                }
            }
        }
        return null;
    }

    private boolean isBiomeBlacklisted(Biome biome) {
        List<String> blacklisted = plugin.getConfigManager().getBlacklistedBiomes();
        if (blacklisted == null || blacklisted.isEmpty()) return false;

        NamespacedKey key = biome.getKey();
        String fullKey = key.toString();
        String shortKey = key.getKey();

        for (String s : blacklisted) {
            if (s.equalsIgnoreCase(fullKey) || s.equalsIgnoreCase(shortKey)) {
                return true;
            }
        }
        return false;
    }

    public void removeFromQueue(Player player) {
        UUID uuid = player.getUniqueId();
        cancelActionbarTask(uuid);
        removeFromPendingRedisMatches(uuid);
        String queuedWorld = playerQueueWorlds.remove(uuid);

        if (!playersInQueue.remove(uuid))
            return;

        Match match = activeMatches.remove(uuid);
        if (match != null) {
            String worldName = match.getWorldName();
            if (match.getTask() != null)
                match.getTask().cancel();

            match.getPlayers().remove(uuid);

            WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
            if (config != null) {
                player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getLeftQueueMsg().replace("%world%", config.displayName())));
            }

            if (redisQueueMode) {
                List<UUID> remainingPlayers = new ArrayList<>(match.getPlayers());
                for (UUID remainingUuid : remainingPlayers) {
                    activeMatches.remove(remainingUuid);
                    Player remainingPlayer = Bukkit.getPlayer(remainingUuid);
                    if (remainingPlayer != null) {
                        remainingPlayer.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getOpponentLeftMsg()));
                    }
                }
                redisQueueManager.requeueFront(worldName, remainingPlayers);
            } else {
                LinkedList<UUID> queue = waitingQueues.computeIfAbsent(worldName, k -> new LinkedList<>());
                synchronized (queue) {
                    for (UUID remainingUuid : match.getPlayers()) {
                        activeMatches.remove(remainingUuid);
                        queue.addFirst(remainingUuid);
                        Player remainingPlayer = Bukkit.getPlayer(remainingUuid);
                        if (remainingPlayer != null) {
                            remainingPlayer.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix()
                                    + plugin.getConfigManager().getOpponentLeftMsg()));
                        }
                    }
                }
            }

            if (redisQueueMode) {
                checkAndFormRedisMatch(worldName);
            } else {
                checkAndFormMatch(worldName);
            }
            return;
        }

        if (redisQueueMode) {
            if (queuedWorld != null) {
                redisQueueManager.remove(queuedWorld, uuid);
                WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(queuedWorld);
                if (config != null) {
                    player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getLeftQueueMsg().replace("%world%", config.displayName())));
                }
                checkAndFormRedisMatch(queuedWorld);
            }
            return;
        }

        for (Map.Entry<String, LinkedList<UUID>> entry : waitingQueues.entrySet()) {
            LinkedList<UUID> queue = entry.getValue();
            synchronized (queue) {
                if (queue.remove(uuid)) {
                    WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(entry.getKey());
                    if (config != null) {
                        player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() +
                                plugin.getConfigManager().getLeftQueueMsg().replace("%world%",
                                        config.displayName())));
                    }
                    break;
                }
            }
        }
    }

    public boolean isInQueue(Player player) {
        return playersInQueue.contains(player.getUniqueId());
    }

    public String getQueueWorld(Player player) {
        UUID uuid = player.getUniqueId();

        Match match = activeMatches.get(uuid);
        if (match != null)
            return match.getWorldName();

        if (redisQueueMode) {
            return playerQueueWorlds.get(uuid);
        }

        for (Map.Entry<String, LinkedList<UUID>> entry : waitingQueues.entrySet()) {
            if (entry.getValue().contains(uuid))
                return entry.getKey();
        }
        return null;
    }

    public int getQueueSize(String worldName) {
        int count = redisQueueMode ? redisQueueManager.getQueueSize(worldName) : 0;
        if (!redisQueueMode) {
            LinkedList<UUID> queue = waitingQueues.get(worldName);
            if (queue != null)
                count += queue.size();
        } else {
            count += getPendingRedisMatchPlayerCount(worldName);
        }

        for (Match match : activeMatches.values()) {
            if (match.getWorldName().equals(worldName)) {
                count++;
            }
        }
        return count;
    }

    private void startMatchCountdown(Match match) {
        ScheduledTask task = Bukkit.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (scheduledTask) -> {

            List<Player> players = new ArrayList<>();
            for (UUID uuid : match.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline())
                    players.add(p);
            }

            int currentCount = match.getCountdown();

            if (currentCount > 0) {
                String countdownMsg = plugin.getConfigManager().getTeleportationCountdownMsg().replace("%seconds%",
                        String.valueOf(currentCount));
                Sound sound = Sound.BLOCK_NOTE_BLOCK_HAT;
                float pitch = currentCount == 1 ? 1.5f : 1.0f;

                for (Player p : players) {
                    if (currentCount == plugin.getConfigManager().getCountdownSeconds()) {
                        if (plugin.getConfigManager().isTitleEnabled())
                            sendFoundTitle(p);
                        p.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getOpponentFoundMsg()));
                    }
                    p.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + countdownMsg));
                    p.playSound(p.getLocation(), sound, 1.0f, pitch);
                }
                match.decrementCountdown();
            } else {
                teleportMatch(match, players);
                for (UUID uuid : match.getPlayers()) {
                    activeMatches.remove(uuid);
                    playersInQueue.remove(uuid);
                    playerQueueWorlds.remove(uuid);
                }
                scheduledTask.cancel();
            }
        }, 1L, 20L);

        match.setTask(task);
    }

    private void teleportMatch(Match match, List<Player> players) {
        if (players.isEmpty()) {
            return;
        }

        String worldName = match.getWorldName();
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null)
            return;

        RoutingMode routingMode = getRoutingMode(config);
        if (routingMode == RoutingMode.CROSS_REGION_TRANSFER) {
            for (Player player : players) {
                if (player.isOnline()) {
                    startCrossRegionTransfer(player, config);
                }
            }
            return;
        }

        if (routingMode == RoutingMode.SAME_REGION_PROXY_SWITCH) {
            for (Player player : players) {
                if (player.isOnline()) {
                    startSameRegionProxySwitch(player, config);
                }
            }
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            for (Player player : players) {
                player.sendMessage(MessageCache.getComponent(
                        plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getWorldNotFoundMsg()));
            }
            return;
        }

        findAndTeleportGroup(players, world, config, 0);
    }

    private void findAndTeleport(Player player, World world, WorldConfig config, int attempts) {
        if (attempts >= 10) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getNoSpotMsg()));
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int range = random.nextInt(config.minRange(), config.maxRange() + 1);
        double angle = random.nextDouble() * 2 * Math.PI;
        int x = config.centerX() + (int) (range * Math.cos(angle));
        int z = config.centerZ() + (int) (range * Math.sin(angle));

        Bukkit.getServer().getRegionScheduler().execute(plugin, world, x >> 4, z >> 4, () -> {
            Location safeLoc = findSafeLocation(world, x, z);

            if (safeLoc != null) {
                player.teleportAsync(safeLoc).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(
                                MessageCache.getComponent(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getTeleportedMsg()));
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                });
            } else {
                findAndTeleport(player, world, config, attempts + 1);
            }
        });
    }

    private void findAndTeleportGroup(List<Player> players, World world, WorldConfig config,
                                      int attempts) {
        if (attempts >= 10) {
            for (Player p : players) {
                p.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getNoSpotMsg()));
            }
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int range = random.nextInt(config.minRange(), config.maxRange() + 1);
        double angle = random.nextDouble() * 2 * Math.PI;
        int x = config.centerX() + (int) (range * Math.cos(angle));
        int z = config.centerZ() + (int) (range * Math.sin(angle));

        Bukkit.getServer().getRegionScheduler().execute(plugin, world, x >> 4, z >> 4, () -> {
            Location sharedLocation = findSafeLocation(world, x, z);

            if (sharedLocation != null) {
                for (Player player : players) {
                    player.teleportAsync(sharedLocation).thenAccept(success -> {
                        if (success) {
                            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix()
                                    + plugin.getConfigManager().getTeleportedMsg()));
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        }
                    });
                    cancelActionbarTask(player.getUniqueId());
                }
            } else {
                findAndTeleportGroup(players, world, config, attempts + 1);
            }
        });
    }

    public void clearAllQueues() {
        if (redisSyncTask != null) {
            redisSyncTask.cancel();
            redisSyncTask = null;
        }
        actionbarTasks.values().forEach(ScheduledTask::cancel);
        for (Match match : activeMatches.values()) {
            if (match.getTask() != null)
                match.getTask().cancel();
        }
        if (redisQueueMode) {
            for (Map.Entry<UUID, String> entry : playerQueueWorlds.entrySet()) {
                redisQueueManager.remove(entry.getValue(), entry.getKey());
            }
        }
        actionbarTasks.clear();
        waitingQueues.clear();
        activeMatches.clear();
        playersInQueue.clear();
        playerQueueWorlds.clear();
        pendingRedisMatches.clear();
        processedQueueJoinEvents.clear();
    }

    private void sendActionBar(Player player, int initialSize) {
        UUID uuid = player.getUniqueId();
        cancelActionbarTask(uuid);

        int interval = plugin.getConfigManager().getActionbarInterval();

        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, (t) -> {
            if (!player.isOnline() || !playersInQueue.contains(uuid)) {
                t.cancel();
                actionbarTasks.remove(uuid);
                return;
            }

            String worldName = getQueueWorld(player);
            int currentSize = worldName != null ? getQueueSize(worldName) : initialSize;

            String message = plugin.getConfigManager().getActionbarMessage()
                    .replace("%current%", String.valueOf(currentSize))
                    .replace("%max%", String.valueOf(plugin.getConfigManager().getMaxPlayers()));

            player.sendActionBar(MessageCache.getComponent(message));
        }, null, 1L, interval);

        actionbarTasks.put(player.getUniqueId(), task);
    }

    private void cancelActionbarTask(UUID uuid) {
        ScheduledTask task = actionbarTasks.remove(uuid);
        if (task != null)
            task.cancel();
    }

    private void broadcastQueueJoin(Player player) {
        broadcastQueueJoin(player.getName());
    }

    private void broadcastQueueJoin(String playerName) {
        String header = plugin.getConfigManager().getBroadcastHeader();
        List<String> lines = plugin.getConfigManager().getBroadcastLines();
        String footer = plugin.getConfigManager().getBroadcastFooter();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!plugin.getDatabaseManager().hasMessagesEnabled(onlinePlayer.getUniqueId())) {
                continue;
            }

            onlinePlayer.sendMessage(MessageCache.getComponent(header));
            for (String line : lines) {
                onlinePlayer.sendMessage(MessageCache.getComponent(line.replace("%player%", playerName)));
            }
            onlinePlayer.sendMessage(MessageCache.getComponent(footer));
        }
    }

    private void sendJoinTitle(Player player) {
        Title title = Title.title(
                MessageCache.getComponent(plugin.getConfigManager().getTitleJoinText()),
                MessageCache.getComponent(plugin.getConfigManager().getTitleJoinSubtitle()),
                Title.Times.times(Ticks.duration(plugin.getConfigManager().getTitleFadeIn()),
                                  Ticks.duration(plugin.getConfigManager().getTitleStay()),
                                  Ticks.duration(plugin.getConfigManager().getTitleFadeOut()))
        );
        player.showTitle(title);
    }

    private void sendFoundTitle(Player player) {
        Title title = Title.title(
                MessageCache.getComponent(plugin.getConfigManager().getTitleFoundText()),
                MessageCache.getComponent(plugin.getConfigManager().getTitleFoundSubtitle()),
                Title.Times.times(Ticks.duration(plugin.getConfigManager().getTitleFadeIn()),
                                  Ticks.duration(plugin.getConfigManager().getTitleStay()),
                                  Ticks.duration(plugin.getConfigManager().getTitleFadeOut()))
        );
        player.showTitle(title);
    }
}
