package com.spy.rtpqueueadvance.managers;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.utils.WorldConfig;
import com.spy.rtpqueueadvance.utils.MessageCache;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;

public class QueueManager {

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

    public QueueManager(RtpQueueAdvance plugin) {
        this.plugin = plugin;
        this.waitingQueues = new ConcurrentHashMap<>();
        this.activeMatches = new ConcurrentHashMap<>();
        this.actionbarTasks = new ConcurrentHashMap<>();
        this.playersInQueue = ConcurrentHashMap.newKeySet();
    }

    public void addToQueue(Player player, String worldName) {
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getWorldNotFoundMsg()));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getWorldNotFoundMsg()));
            return;
        }

        if (player.hasPermission("rtpqueue.bypass")) {
            instantTeleport(player, worldName);
            return;
        }

        if (playersInQueue.contains(player.getUniqueId())) {
            player.sendMessage(MessageCache.getComponent(plugin.getConfigManager().getPrefix() + plugin.getConfigManager().getAlreadyInQueueMsg()));
            return;
        }

        playersInQueue.add(player.getUniqueId());

        LinkedList<UUID> queue = waitingQueues.computeIfAbsent(worldName, k -> new LinkedList<>());
        synchronized (queue) {
            queue.add(player.getUniqueId());
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
        }

        checkAndFormMatch(worldName);
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

    public void instantTeleport(Player player, String worldName) {
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null)
            return;
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return;

        findAndTeleport(player, world, config, 0);
    }

    private Location findSafeLocation(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        int SAFE_RADIUS = 20;

        for (int i = 0; i < 15; i++) {
            Location checkLoc = new Location(world, x + 0.5, y - i, z + 0.5);
            org.bukkit.block.Block ground = checkLoc.getBlock();
            org.bukkit.block.Block feet = ground.getRelative(org.bukkit.block.BlockFace.UP);
            org.bukkit.block.Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);

            Material groundType = ground.getType();

            boolean materialSafe = groundType.isSolid() && !UNSAFE_MATERIALS.contains(groundType);

            boolean spaceSafe = feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid();

            if (materialSafe && spaceSafe) {
                boolean playerNearby = world.getNearbyEntities(checkLoc, SAFE_RADIUS, SAFE_RADIUS, SAFE_RADIUS)
                        .stream()
                        .anyMatch(entity -> entity instanceof Player);

                if (!playerNearby) {
                    return checkLoc.add(0, 1, 0);
                }
            }
        }
        return null;
    }

    public void removeFromQueue(Player player) {
        UUID uuid = player.getUniqueId();
        cancelActionbarTask(uuid);

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

            checkAndFormMatch(worldName);
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

        for (Map.Entry<String, LinkedList<UUID>> entry : waitingQueues.entrySet()) {
            if (entry.getValue().contains(uuid))
                return entry.getKey();
        }
        return null;
    }

    public int getQueueSize(String worldName) {
        int count = 0;
        LinkedList<UUID> queue = waitingQueues.get(worldName);
        if (queue != null)
            count += queue.size();

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
                }
                scheduledTask.cancel();
            }
        }, 1L, 20L);

        match.setTask(task);
    }

    private void teleportMatch(Match match, List<Player> players) {
        String worldName = match.getWorldName();
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null)
            return;
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return;

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
        actionbarTasks.values().forEach(ScheduledTask::cancel);
        for (Match match : activeMatches.values()) {
            if (match.getTask() != null)
                match.getTask().cancel();
        }
        actionbarTasks.clear();
        waitingQueues.clear();
        activeMatches.clear();
        playersInQueue.clear();
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
        String header = plugin.getConfigManager().getBroadcastHeader();
        List<String> lines = plugin.getConfigManager().getBroadcastLines();
        String footer = plugin.getConfigManager().getBroadcastFooter();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!plugin.getDatabaseManager().hasMessagesEnabled(onlinePlayer.getUniqueId())) {
                continue;
            }

            onlinePlayer.sendMessage(MessageCache.getComponent(header));
            for (String line : lines) {
                onlinePlayer.sendMessage(MessageCache.getComponent(line.replace("%player%", player.getName())));
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