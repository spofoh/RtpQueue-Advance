# RtpQueue Advance

## Overview

RtpQueue Advance is a Minecraft Spigot/Paper plugin that provides an advanced RTP (Random Teleport) queue system with a beautiful world selection GUI.

## Features

- **World Selection GUI**: Beautiful inventory-based GUI for selecting worlds
- **Queue System**: Players queue up and teleport together
- **Countdown System**: 3-2-1 Let's Go! countdown before teleportation
- **Configurable Worlds**: Add any world with custom settings in config.yml
- **Safe Teleportation**: Finds safe landing spots avoiding lava, fire, etc.
- **Configurable Range**: Set min/max teleport distance from center point (0-5000 blocks)
- **Customizable Messages**: All messages can be edited in config.yml

## Commands

- `/rtpqueue` (aliases: `/rtp`, `/rtpq`, `/queue`) - Open world selection GUI
- `/rtpqueueleave` (aliases: `/rtpleave`, `/leavequeue`) - Leave the current queue
- `/rtpqueuereload` - Reload configuration

## Permissions

- `rtpqueue.use` - Permission to use RTP Queue (default: true)
- `rtpqueue.admin` - Permission to reload plugin (default: op)
- `rtpqueue.bypass` - Bypass queue and teleport instantly (default: op)

## Project Structure

```
src/main/java/com/spy/rtpqueueadvance/
├── RtpQueueAdvance.java          # Main plugin class
├── commands/
│   ├── RtpQueueCommand.java      # /rtpqueue command
│   └── ReloadCommand.java        # /rtpqueuereload command
├── gui/
│   └── WorldSelectionGUI.java    # World selection inventory GUI
├── listeners/
│   ├── GUIListener.java          # Handles GUI clicks
│   └── PlayerQuitListener.java   # Removes players from queue on disconnect
├── managers/
│   ├── ConfigManager.java        # Configuration loading and management
│   └── QueueManager.java         # Queue and teleportation logic
└── utils/
    └── WorldConfig.java          # World configuration data class

src/main/resources/
├── plugin.yml                    # Plugin metadata and commands
└── config.yml                    # User configuration
```

## Configuration

Edit `config.yml` to:
- Set GUI title and size
- Configure queue settings (min players, countdown)
- Customize all messages
- Add/configure worlds with display names, materials, slots, and spawn ranges

## Building

```bash
mvn clean package
```

The compiled JAR will be in `target/rtpqueueadvance-1.0.0.jar`

## User Preferences

Preferred communication style: Simple, everyday language.

## Technology Stack

- Java 17
- Spigot API 1.21
- Maven for build management
