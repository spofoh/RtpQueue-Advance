package com.spy.rtpqueueadvance.managers;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class RedisQueueManager {

    private final RtpQueueAdvance plugin;
    private final String keyPrefix;
    private final int lockSeconds;
    private final JedisPooled jedis;
    private final boolean enabled;

    public RedisQueueManager(RtpQueueAdvance plugin) {
        this.plugin = plugin;

        if (!plugin.getConfigManager().isRedisQueueMode()) {
            this.keyPrefix = "";
            this.lockSeconds = 2;
            this.jedis = null;
            this.enabled = false;
            return;
        }

        ConfigManager.RedisSettings settings = plugin.getConfigManager().getRedisSettings();
        this.keyPrefix = sanitizePrefix(settings.keyPrefix());
        this.lockSeconds = Math.max(1, settings.matchLockSeconds());

        JedisPooled created = null;
        boolean isEnabled = false;

        try {
            String uri = buildRedisUri(settings);
            created = new JedisPooled(uri);
            created.ping();
            isEnabled = true;
            plugin.getLogger().info("Redis queue mode enabled (" + settings.host() + ":" + settings.port() + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize Redis queue mode. Falling back to local queue mode.", e);
        }

        this.jedis = created;
        this.enabled = isEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void close() {
        if (jedis != null) {
            try {
                jedis.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error closing Redis connection", e);
            }
        }
    }

    public boolean enqueue(String worldName, UUID uuid) {
        if (!enabled) {
            return false;
        }

        String member = uuid.toString();
        String setKey = queueSetKey(worldName);
        String listKey = queueListKey(worldName);

        try {
            Long added = jedis.sadd(setKey, member);
            if (added != null && added > 0) {
                jedis.rpush(listKey, member);
                return true;
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis enqueue failed for world " + worldName, e);
            return false;
        }
    }

    public boolean remove(String worldName, UUID uuid) {
        if (!enabled) {
            return false;
        }

        String member = uuid.toString();

        try {
            Long listRemoved = jedis.lrem(queueListKey(worldName), 0, member);
            Long setRemoved = jedis.srem(queueSetKey(worldName), member);
            return (listRemoved != null && listRemoved > 0) || (setRemoved != null && setRemoved > 0);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis remove failed for world " + worldName, e);
            return false;
        }
    }

    public int getQueueSize(String worldName) {
        if (!enabled) {
            return 0;
        }

        try {
            Long size = jedis.llen(queueListKey(worldName));
            return size == null ? 0 : size.intValue();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis queue size read failed for world " + worldName, e);
            return 0;
        }
    }

    public List<UUID> tryPopMatch(String worldName, int count) {
        if (!enabled) {
            return List.of();
        }

        String lockKey = queueLockKey(worldName);
        String lockValue = UUID.randomUUID().toString();

        try {
            String result = jedis.set(lockKey, lockValue, SetParams.setParams().nx().ex(lockSeconds));
            if (!"OK".equalsIgnoreCase(result)) {
                return List.of();
            }

            List<UUID> popped = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                String next = jedis.lpop(queueListKey(worldName));
                if (next == null) {
                    break;
                }
                jedis.srem(queueSetKey(worldName), next);

                try {
                    popped.add(UUID.fromString(next));
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (popped.size() < count) {
                requeueFront(worldName, popped);
                return List.of();
            }

            return popped;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis match pop failed for world " + worldName, e);
            return List.of();
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    public void requeueFront(String worldName, List<UUID> players) {
        if (!enabled || players.isEmpty()) {
            return;
        }

        String listKey = queueListKey(worldName);
        String setKey = queueSetKey(worldName);

        try {
            List<UUID> reversed = new ArrayList<>(players);
            Collections.reverse(reversed);

            for (UUID uuid : reversed) {
                String value = uuid.toString();
                jedis.lpush(listKey, value);
                jedis.sadd(setKey, value);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis requeue failed for world " + worldName, e);
        }
    }

    public boolean storeTransferIntent(UUID uuid, String serializedIntent, int ttlSeconds) {
        if (!enabled || uuid == null || serializedIntent == null || serializedIntent.isBlank()) {
            return false;
        }

        try {
            String result = jedis.set(transferIntentKey(uuid), serializedIntent,
                    SetParams.setParams().ex(Math.max(5, ttlSeconds)));
            return "OK".equalsIgnoreCase(result);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis transfer intent store failed for player " + uuid, e);
            return false;
        }
    }

    public String consumeTransferIntent(UUID uuid) {
        if (!enabled || uuid == null) {
            return null;
        }

        String key = transferIntentKey(uuid);
        try {
            try {
                return jedis.getDel(key);
            } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
                String value = jedis.get(key);
                if (value != null) {
                    jedis.del(key);
                }
                return value;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis transfer intent read failed for player " + uuid, e);
            return null;
        }
    }

    public void clearTransferIntent(UUID uuid) {
        if (!enabled || uuid == null) {
            return;
        }

        try {
            jedis.del(transferIntentKey(uuid));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis transfer intent clear failed for player " + uuid, e);
        }
    }

    public boolean storeMatchAssignments(String matchId, String worldName, List<UUID> players, long createdAtEpochMs,
                                         int countdownSeconds, int totalPlayers, int ttlSeconds) {
        if (!enabled || matchId == null || matchId.isBlank() || worldName == null || worldName.isBlank()
                || players == null || players.isEmpty()) {
            return false;
        }

        String payload = "1|" + matchId + "|" + worldName + "|" + createdAtEpochMs + "|"
                + Math.max(0, countdownSeconds) + "|" + Math.max(1, totalPlayers);
        boolean success = true;

        for (UUID uuid : players) {
            if (uuid == null) {
                continue;
            }
            try {
                String result = jedis.set(matchAssignmentKey(uuid), payload,
                        SetParams.setParams().ex(Math.max(10, ttlSeconds)));
                if (!"OK".equalsIgnoreCase(result)) {
                    success = false;
                }
            } catch (Exception e) {
                success = false;
                plugin.getLogger().log(Level.WARNING, "Redis match assignment store failed for player " + uuid, e);
            }
        }

        return success;
    }

    public String consumeMatchAssignment(UUID uuid) {
        if (!enabled || uuid == null) {
            return null;
        }

        String key = matchAssignmentKey(uuid);
        try {
            try {
                return jedis.getDel(key);
            } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
                String value = jedis.get(key);
                if (value != null) {
                    jedis.del(key);
                }
                return value;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis match assignment read failed for player " + uuid, e);
            return null;
        }
    }

    public void publishQueueJoinEvent(String sourceServerId, String playerName, String worldName) {
        if (!enabled || sourceServerId == null || sourceServerId.isBlank() || playerName == null || playerName.isBlank()) {
            return;
        }

        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String payload = "1|" + eventId + "|" + now + "|" + sourceServerId + "|" + playerName + "|"
                + (worldName == null ? "" : worldName);

        try {
            jedis.setex(queueJoinEventKey(eventId), 45, payload);
            jedis.rpush(queueJoinIndexKey(), eventId);
            jedis.ltrim(queueJoinIndexKey(), -200, -1);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis queue join event publish failed for " + playerName, e);
        }
    }

    public List<QueueJoinEvent> fetchRecentQueueJoinEvents(int maxEvents) {
        if (!enabled || maxEvents <= 0) {
            return List.of();
        }

        List<QueueJoinEvent> events = new ArrayList<>();
        try {
            List<String> ids = jedis.lrange(queueJoinIndexKey(), -maxEvents, -1);
            for (String id : ids) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                String raw = jedis.get(queueJoinEventKey(id));
                QueueJoinEvent parsed = decodeQueueJoinEvent(raw);
                if (parsed != null) {
                    events.add(parsed);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis queue join event fetch failed", e);
        }
        return events;
    }

    private void releaseLock(String lockKey, String lockValue) {
        if (!enabled) {
            return;
        }

        try {
            String current = jedis.get(lockKey);
            if (lockValue.equals(current)) {
                jedis.del(lockKey);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis lock release failed for key " + lockKey, e);
        }
    }

    private String queueListKey(String worldName) {
        return keyPrefix + "queue:list:" + worldName;
    }

    private String queueSetKey(String worldName) {
        return keyPrefix + "queue:set:" + worldName;
    }

    private String queueLockKey(String worldName) {
        return keyPrefix + "queue:lock:" + worldName;
    }

    private String transferIntentKey(UUID uuid) {
        return keyPrefix + "transfer:intent:" + uuid;
    }

    private String matchAssignmentKey(UUID uuid) {
        return keyPrefix + "match:assignment:" + uuid;
    }

    private String queueJoinIndexKey() {
        return keyPrefix + "queue:join:index";
    }

    private String queueJoinEventKey(String eventId) {
        return keyPrefix + "queue:join:event:" + eventId;
    }

    private static QueueJoinEvent decodeQueueJoinEvent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|", 6);
        if (parts.length < 5 || !"1".equals(parts[0])) {
            return null;
        }
        try {
            long createdAt = Long.parseLong(parts[2]);
            String worldName = parts.length >= 6 ? parts[5] : "";
            return new QueueJoinEvent(parts[1], createdAt, parts[3], parts[4], worldName);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record QueueJoinEvent(String eventId, long createdAtEpochMs, String sourceServerId, String playerName,
                                 String worldName) {
    }

    private static String sanitizePrefix(String rawPrefix) {
        if (rawPrefix == null || rawPrefix.isBlank()) {
            return "rtpqueue:";
        }

        String prefix = rawPrefix.trim();
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }

    private static String buildRedisUri(ConfigManager.RedisSettings settings) {
        String authPart = "";
        if (!settings.password().isBlank()) {
            authPart = ":" + URLEncoder.encode(settings.password(), StandardCharsets.UTF_8) + "@";
        }
        return "redis://" + authPart + settings.host() + ":" + settings.port() + "/" + settings.database();
    }
}
