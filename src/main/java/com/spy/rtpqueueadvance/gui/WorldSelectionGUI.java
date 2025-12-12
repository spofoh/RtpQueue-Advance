package com.spy.rtpqueueadvance.gui;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.managers.ConfigManager;
import com.spy.rtpqueueadvance.utils.WorldConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class WorldSelectionGUI {

    private final RtpQueueAdvance plugin;
    public static final String GUI_IDENTIFIER = "RtpQueueAdvance";

    public WorldSelectionGUI(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    public void openGUI(Player player) {
        ConfigManager config = plugin.getConfigManager();
        Inventory inventory = Bukkit.createInventory(null, config.getGuiSize(), config.getGuiTitle());

        fillBorders(inventory, config.getGuiSize());

        for (WorldConfig worldConfig : config.getWorldConfigs().values()) {
            if (worldConfig.getSlot() >= 0 && worldConfig.getSlot() < config.getGuiSize()) {
                ItemStack item = createWorldItem(worldConfig, player);
                inventory.setItem(worldConfig.getSlot(), item);
            }
        }

        player.openInventory(inventory);
    }

    private void fillBorders(Inventory inventory, int size) {
        ItemStack filler = createFillerItem();
        int rows = size / 9;
        
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, filler);
        }
        
        for (int i = size - 9; i < size; i++) {
            inventory.setItem(i, filler);
        }
        
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, filler);
            inventory.setItem(row * 9 + 8, filler);
        }
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWorldItem(WorldConfig worldConfig, Player player) {
        ItemStack item = new ItemStack(worldConfig.getMaterial());
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(worldConfig.getDisplayName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ConfigManager.colorize("&7" + worldConfig.getDescription()));
            lore.add("");
            lore.add(ConfigManager.colorize("&e&lInformation:"));
            lore.add(ConfigManager.colorize("&7Click here to queue for"));
            lore.add(ConfigManager.colorize("&7the &f" + worldConfig.getDisplayName() + " &7world!"));
            lore.add("");
            
            int queueSize = plugin.getQueueManager().getQueueSize(worldConfig.getWorldName());
            int minPlayers = plugin.getConfigManager().getMinPlayers();
            lore.add(ConfigManager.colorize("&6★ &fPlayers in queue: &a" + queueSize + "&7/&e" + minPlayers));
            lore.add("");
            
            boolean inThisQueue = false;
            String playerQueue = plugin.getQueueManager().getQueueWorld(player);
            if (playerQueue != null && playerQueue.equals(worldConfig.getWorldName())) {
                inThisQueue = true;
            }
            
            if (inThisQueue) {
                lore.add(ConfigManager.colorize("&c→ &cCLICK &cto Leave Queue"));
            } else {
                lore.add(ConfigManager.colorize("&a→ &aCLICK &ato Queue"));
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    public String getWorldFromSlot(int slot) {
        for (WorldConfig config : plugin.getConfigManager().getWorldConfigs().values()) {
            if (config.getSlot() == slot) {
                return config.getWorldName();
            }
        }
        return null;
    }

    public boolean isGUI(String title) {
        return title != null && title.equals(plugin.getConfigManager().getGuiTitle());
    }
}
