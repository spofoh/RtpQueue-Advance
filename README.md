# RtpQueue Advance

A powerful Minecraft plugin for random teleportation with queue-based matchmaking. Players can join a queue and wait for opponents before being teleported together to a random location.

## Features

- **World Selection GUI** - Beautiful interface to select which world to queue for
- **Queue System** - Wait for other players before teleporting together
- **Multi-World Support** - Configure multiple worlds with custom settings
- **Safe Teleportation** - Finds safe locations avoiding lava, fire, water, and dangerous blocks
- **Customizable Messages** - Full control over all plugin messages with color codes
- **Action Bar Display** - Persistent action bar showing queue status until player is found
- **Title Notifications** - On-screen titles when joining queue and finding opponents
- **Broadcast System** - Announce to all players when someone joins the queue
- **Bypass Permission** - Allow staff to skip the queue and teleport instantly

## Commands

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/rtpqueue` | `/rtpq` | Opens the world selection GUI to join a queue | `rtpqueue.use` |
| `/rtpqueueleave` | `/rtpleave`, `/leavequeue` | Leave your current queue | `rtpqueue.use` |
| `/rtpqueuereload` | - | Reload the plugin configuration | `rtpqueue.admin` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `rtpqueue.use` | Allows players to use the RTP queue system | `true` (all players) |
| `rtpqueue.admin` | Allows reloading the plugin configuration | `op` |
| `rtpqueue.bypass` | Skip the queue and teleport instantly | `op` |

## Configuration

### Queue Settings

```yaml
queue:
  min-players: 2           # Minimum players needed to start teleport
  countdown-seconds: 3     # Countdown before teleport (3-2-1)
  teleport-together: true  # Teleport all queued players together
```

### GUI Settings

```yaml
gui:
  title: "&6&lRTP Queue &8| &fWorld Selection"
  size: 27  # Options: 9, 18, 27, 36, 45, 54
```

### Action Bar Settings

```yaml
actionbar:
  enabled: true
  message: "&7Waiting for a &aplayer &7in &a/rtpqueue &7(%current%/%max%)"
  interval: 20  # Ticks between refresh (20 = 1 second, keeps visible until found)
```

### Title Settings

```yaml
title:
  enabled: true
  join-title: "&a&l+"
  join-subtitle: "&aJoined &e&lRTPQueue&a!"
  found-title: "&a&l✓"
  found-subtitle: "&aOpponent found!"
  fade-in: 10
  stay: 40
  fade-out: 10
```

### Broadcast Settings

```yaml
broadcast:
  enabled: true
  header: "&a✦ &a&lRTPQUEUE &a✦"
  lines:
    - "&e%player% &7is waiting for"
    - "&7an opponent to fight!"
  footer: "&a+ /rtpqueue +"
```

### World Configuration

Each world can be configured individually:

```yaml
worlds:
  world:
    enabled: true
    display-name: "&a&lOVERWORLD"
    description: "&7Classic survival world"
    material: GRASS_BLOCK
    slot: 11
    spawn-range:
      min: 100    # Minimum distance from center
      max: 5000   # Maximum distance from center
    center:
      x: 0
      z: 0
```

### Messages

All messages are fully customizable with color code support:

```yaml
messages:
  prefix: "&aRTPQUEUE &8» &r"
  joined-queue: "&aYou joined the queue for &e%world%&a! &7(%current%/%max% players)"
  left-queue: "&cYou left the queue for &e%world%&c."
  opponent-found: "&aOpponent found, you will be teleported!"
  teleportation-countdown: "&eTeleportation in &c%seconds%s"
  teleported: "&aYou have been teleported!"
  already-in-queue: "&cYou are already in a queue!"
  not-in-queue: "&cYou are not in any queue!"
  world-not-found: "&cWorld not found!"
  no-permission: "&cYou don't have permission to use this!"
  config-reloaded: "&aConfiguration reloaded successfully!"
```

## Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%player%` | Player's name |
| `%world%` | World display name |
| `%current%` | Current players in queue |
| `%max%` | Minimum players needed |
| `%seconds%` | Countdown seconds remaining |

## How It Works

1. Player runs `/rtpqueue` to open the world selection GUI
2. Player clicks on a world to join that world's queue
3. Action bar shows waiting status (persists until opponent found)
4. When enough players join (default: 2), countdown begins
5. All queued players are teleported together to a safe random location
6. Players can leave queue anytime with `/rtpqueueleave`

## Safe Location Finding

The plugin automatically finds safe teleport locations by:
- Checking for solid ground (no lava, fire, water, etc.)
- Ensuring 2 blocks of air space for the player
- Avoiding dangerous blocks nearby (lava, fire, cactus, magma)
- Multiple attempts to find the perfect spot

## Installation

1. Download the plugin JAR file
2. Place it in your server's `plugins` folder
3. Restart or reload your server
4. Configure the plugin in `plugins/RtpQueueAdvance/config.yml`
5. Use `/rtpqueuereload` to apply changes

## Requirements

- Minecraft Server: Paper/Spigot 1.21+
- Java: 21+

## Author

Created by **Spy**

## Version

1.0.0
