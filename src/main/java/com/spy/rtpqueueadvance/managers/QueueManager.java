package com.spy.rtpqueueadvance.managers;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.utils.WorldConfig;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class QueueManager {

    private final RtpQueueAdvance plugin;
    private final Map<String, Set<UUID>> worldQueues;
    private final Map<String, BukkitTask> countdownTasks;
    private final Map<UUID, BukkitTask> actionbarTasks;
    private final Set<UUID> playersInQueue;

    public QueueManager(RtpQueueAdvance plugin) {
        this.plugin = plugin;
        this.worldQueues = new ConcurrentHashMap<>();
        this.countdownTasks = new ConcurrentHashMap<>();
        this.actionbarTasks = new ConcurrentHashMap<>();
        this.playersInQueue = ConcurrentHashMap.newKeySet();
    }

    public boolean addToQueue(Player player, String worldName) {
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + 
                plugin.getConfigManager().getWorldNotFoundMsg());
            return false;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + 
                plugin.getConfigManager().getWorldNotFoundMsg());
            return false;
        }

        if (player.hasPermission("rtpqueue.bypass")) {
            instantTeleport(player, worldName);
            return true;
        }

        if (playersInQueue.contains(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + 
                plugin.getConfigManager().getAlreadyInQueueMsg());
            return false;
        }

        worldQueues.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        playersInQueue.add(player.getUniqueId());

        int current = getQueueSize(worldName);
        int max = plugin.getConfigManager().getMinPlayers();
        
        if (plugin.getConfigManager().isJoinMessageEnabled()) {
            player.sendMessage(plugin.getConfigManager().getJoinMessageText());
        }
        
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        
        if (plugin.getConfigManager().isTitleEnabled()) {
            sendJoinTitle(player);
        }
        
        if (plugin.getConfigManager().isActionbarEnabled()) {
            sendActionBar(player, current, max);
        }
        
        if (plugin.getConfigManager().isBroadcastEnabled()) {
            broadcastQueueJoin(player);
        }

        if (current >= max && !countdownTasks.containsKey(worldName)) {
            startCountdown(worldName);
        }

        return true;
    }

    public void instantTeleport(Player player, String worldName) {
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location safeLoc = findSafeLocation(world, config);
        if (safeLoc != null) {
            player.teleport(safeLoc);
            player.sendMessage(plugin.getConfigManager().getPrefix() + 
                plugin.getConfigManager().getTeleportedMsg());
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }
    }

    public void removeFromQueue(Player player) {
        UUID uuid = player.getUniqueId();
        
        cancelActionbarTask(uuid);
        
        for (Map.Entry<String, Set<UUID>> entry : worldQueues.entrySet()) {
            if (entry.getValue().remove(uuid)) {
                playersInQueue.remove(uuid);
                
                WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(entry.getKey());
                if (config != null) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + 
                        plugin.getConfigManager().getLeftQueueMsg()
                            .replace("%world%", config.getDisplayName()));
                }
                
                if (entry.getValue().size() < plugin.getConfigManager().getMinPlayers()) {
                    BukkitTask task = countdownTasks.remove(entry.getKey());
                    if (task != null) {
                        task.cancel();
                    }
                }
                break;
            }
        }
    }

    public boolean isInQueue(Player player) {
        return playersInQueue.contains(player.getUniqueId());
    }

    public String getQueueWorld(Player player) {
        for (Map.Entry<String, Set<UUID>> entry : worldQueues.entrySet()) {
            if (entry.getValue().contains(player.getUniqueId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public int getQueueSize(String worldName) {
        Set<UUID> queue = worldQueues.get(worldName);
        return queue == null ? 0 : queue.size();
    }

    private void startCountdown(String worldName) {
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null) return;

        int countdown = plugin.getConfigManager().getCountdownSeconds();

        BukkitTask task = new BukkitRunnable() {
            int secondsLeft = countdown;

            @Override
            public void run() {
                Set<UUID> queue = worldQueues.get(worldName);
                if (queue == null || queue.isEmpty()) {
                    countdownTasks.remove(worldName);
                    cancel();
                    return;
                }

                List<Player> players = new ArrayList<>();
                for (UUID uuid : queue) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        players.add(p);
                    }
                }

                if (players.size() < plugin.getConfigManager().getMinPlayers()) {
                    countdownTasks.remove(worldName);
                    cancel();
                    return;
                }

                if (secondsLeft > 0) {
                    String countdownMsg = plugin.getConfigManager().getTeleportationCountdownMsg()
                        .replace("%seconds%", String.valueOf(secondsLeft));
                    Sound sound = Sound.BLOCK_NOTE_BLOCK_HAT;
                    float pitch = secondsLeft == 1 ? 1.5f : 1.0f;
                    
                    if (secondsLeft == countdown) {
                        for (Player p : players) {
                            if (plugin.getConfigManager().isTitleEnabled()) {
                                sendFoundTitle(p);
                            }
                            p.sendMessage(plugin.getConfigManager().getPrefix() + countdownMsg);
                            p.sendMessage(plugin.getConfigManager().getPrefix() + 
                                plugin.getConfigManager().getOpponentFoundMsg());
                            p.playSound(p.getLocation(), sound, 1.0f, pitch);
                        }
                    } else {
                        for (Player p : players) {
                            p.sendMessage(plugin.getConfigManager().getPrefix() + countdownMsg);
                            p.playSound(p.getLocation(), sound, 1.0f, pitch);
                        }
                    }

                    secondsLeft--;
                } else {
                    teleportPlayers(worldName, players);
                    
                    countdownTasks.remove(worldName);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        countdownTasks.put(worldName, task);
    }

    private void teleportPlayers(String worldName, List<Player> players) {
        WorldConfig config = plugin.getConfigManager().getWorldConfigs().get(worldName);
        if (config == null) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Set<UUID> queue = worldQueues.get(worldName);
        
        Location sharedLocation = findSafeLocation(world, config);
        
        for (Player player : players) {
            if (sharedLocation != null) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.teleport(sharedLocation);
                        player.sendMessage(plugin.getConfigManager().getPrefix() + 
                            plugin.getConfigManager().getTeleportedMsg());
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                }.runTask(plugin);
            }
            
            if (queue != null) {
                queue.remove(player.getUniqueId());
            }
            playersInQueue.remove(player.getUniqueId());
            cancelActionbarTask(player.getUniqueId());
        }
    }

    private Location findSafeLocation(World world, WorldConfig config) {
        int attempts = 100;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < attempts; i++) {
            int range = random.nextInt(config.getMinRange(), config.getMaxRange() + 1);
            double angle = random.nextDouble() * 2 * Math.PI;
            
            int x = config.getCenterX() + (int) (range * Math.cos(angle));
            int z = config.getCenterZ() + (int) (range * Math.sin(angle));
            
            Location safeLoc = findSafeYAt(world, x, z);
            if (safeLoc != null) {
                return safeLoc;
            }
        }

        int fallbackX = config.getCenterX();
        int fallbackZ = config.getCenterZ();
        Location fallback = findSafeYAt(world, fallbackX, fallbackZ);
        if (fallback != null) {
            return fallback;
        }
        
        return new Location(world, fallbackX + 0.5, 
            world.getHighestBlockYAt(fallbackX, fallbackZ) + 1, 
            fallbackZ + 0.5);
    }

    private Location findSafeYAt(World world, int x, int z) {
        int maxY = world.getHighestBlockYAt(x, z);
        
        if (maxY < 1) return null;
        
        for (int y = maxY; y >= 1; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            
            if (!isSafeGround(ground)) continue;
            if (!isSafeAir(feet)) continue;
            if (!isSafeAir(head)) continue;
            
            if (hasDangerNearby(world, x, y + 1, z)) continue;
            
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        
        return null;
    }

    private boolean isSafeGround(Block block) {
        if (block == null) return false;
        
        switch (block.getType()) {
            case LAVA:
            case FIRE:
            case CACTUS:
            case MAGMA_BLOCK:
            case CAMPFIRE:
            case SOUL_CAMPFIRE:
            case SWEET_BERRY_BUSH:
            case WITHER_ROSE:
            case POWDER_SNOW:
            case WATER:
            case BUBBLE_COLUMN:
            case COBWEB:
            case AIR:
            case CAVE_AIR:
            case VOID_AIR:
                return false;
            default:
                return block.getType().isSolid();
        }
    }

    private boolean isSafeAir(Block block) {
        if (block == null) return false;
        
        switch (block.getType()) {
            case LAVA:
            case FIRE:
            case CACTUS:
            case SWEET_BERRY_BUSH:
            case WITHER_ROSE:
            case COBWEB:
                return false;
            default:
                return block.isPassable() || block.getType() == org.bukkit.Material.AIR;
        }
    }

    private boolean hasDangerNearby(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block nearby = world.getBlockAt(x + dx, y, z + dz);
                Block nearbyBelow = world.getBlockAt(x + dx, y - 1, z + dz);
                
                if (isDangerousBlock(nearby) || isDangerousBlock(nearbyBelow)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDangerousBlock(Block block) {
        if (block == null) return false;
        
        switch (block.getType()) {
            case LAVA:
            case FIRE:
            case CACTUS:
            case MAGMA_BLOCK:
            case CAMPFIRE:
            case SOUL_CAMPFIRE:
                return true;
            default:
                return false;
        }
    }

    public void clearAllQueues() {
        for (BukkitTask task : countdownTasks.values()) {
            task.cancel();
        }
        for (BukkitTask task : actionbarTasks.values()) {
            task.cancel();
        }
        countdownTasks.clear();
        actionbarTasks.clear();
        worldQueues.clear();
        playersInQueue.clear();
    }
    
    private void broadcastQueueJoin(Player player) {
        String header = plugin.getConfigManager().getBroadcastHeader();
        List<String> lines = plugin.getConfigManager().getBroadcastLines();
        String footer = plugin.getConfigManager().getBroadcastFooter();
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(header);
            for (String line : lines) {
                onlinePlayer.sendMessage(line.replace("%player%", player.getName()));
            }
            onlinePlayer.sendMessage(footer);
        }
    }
    
    private void sendJoinTitle(Player player) {
        String title = plugin.getConfigManager().getTitleJoinText();
        String subtitle = plugin.getConfigManager().getTitleJoinSubtitle();
        int fadeIn = plugin.getConfigManager().getTitleFadeIn();
        int stay = plugin.getConfigManager().getTitleStay();
        int fadeOut = plugin.getConfigManager().getTitleFadeOut();
        
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }
    
    private void sendFoundTitle(Player player) {
        String title = plugin.getConfigManager().getTitleFoundText();
        String subtitle = plugin.getConfigManager().getTitleFoundSubtitle();
        int fadeIn = plugin.getConfigManager().getTitleFadeIn();
        int stay = plugin.getConfigManager().getTitleStay();
        int fadeOut = plugin.getConfigManager().getTitleFadeOut();
        
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }
    
    private void sendActionBar(Player player, int current, int max) {
        cancelActionbarTask(player.getUniqueId());
        
        int interval = plugin.getConfigManager().getActionbarInterval();
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !playersInQueue.contains(player.getUniqueId())) {
                    cancelActionbarTask(player.getUniqueId());
                    return;
                }
                
                String worldName = getQueueWorld(player);
                int currentSize = worldName != null ? getQueueSize(worldName) : current;
                int maxSize = plugin.getConfigManager().getMinPlayers();
                
                String message = plugin.getConfigManager().getActionbarMessage()
                    .replace("%current%", String.valueOf(currentSize))
                    .replace("%max%", String.valueOf(maxSize));
                
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
            }
        }.runTaskTimer(plugin, 0L, interval);
        
        actionbarTasks.put(player.getUniqueId(), task);
    }
    
    private void cancelActionbarTask(UUID uuid) {
        BukkitTask task = actionbarTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
}
