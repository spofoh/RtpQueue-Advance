package com.spy.rtpqueueadvance.managers;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final RtpQueueAdvance plugin;
    private Connection connection;
    private final Set<UUID> disabledMessagesCache = new HashSet<>();

    public DatabaseManager(RtpQueueAdvance plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder + "/player_data.db");

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL;");
                statement.execute("PRAGMA synchronous = NORMAL;");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_settings (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "messages_enabled BOOLEAN DEFAULT 1)");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize SQLite database", e);
        }
    }

    public void loadPlayerData(UUID uuid) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, (task) -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT messages_enabled FROM player_settings WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    boolean enabled = rs.getBoolean("messages_enabled");
                    if (!enabled) {
                        disabledMessagesCache.add(uuid);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void unloadPlayerData(UUID uuid) {
        disabledMessagesCache.remove(uuid);
    }

    public boolean hasMessagesEnabled(UUID uuid) {
        return !disabledMessagesCache.contains(uuid);
    }

    public void toggleMessages(UUID uuid) {
        boolean nowDisabled;
        if (disabledMessagesCache.contains(uuid)) {
            disabledMessagesCache.remove(uuid);
            nowDisabled = false;
        } else {
            disabledMessagesCache.add(uuid);
            nowDisabled = true;
        }

        plugin.getServer().getAsyncScheduler().runNow(plugin, (task) -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_settings (uuid, messages_enabled) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, !nowDisabled);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save toggle state for " + uuid);
            }
        });
    }


    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}