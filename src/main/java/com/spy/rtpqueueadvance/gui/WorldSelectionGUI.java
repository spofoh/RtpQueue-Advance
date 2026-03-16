package com.spy.rtpqueueadvance.gui;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.managers.ConfigManager;
import com.spy.rtpqueueadvance.utils.WorldConfig;
import com.spy.rtpqueueadvance.utils.MessageCache;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class WorldSelectionGUI {

    private final RtpQueueAdvance plugin;
    private final ItemStack fillerItem;

    public static class RtpQueueHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }

    public WorldSelectionGUI(RtpQueueAdvance plugin) {
        this.plugin = plugin;
        this.fillerItem = createFillerItem();
    }

    public void openGUI(Player player) {
        ConfigManager config = plugin.getConfigManager();
        Inventory inventory = Bukkit.createInventory(new RtpQueueHolder(), config.getGuiSize(), MessageCache.getComponent(config.getGuiTitle()));

        fillBorders(inventory, config.getGuiSize());

        for (WorldConfig worldConfig : config.getWorldConfigs().values()) {
            if (worldConfig.getSlot() >= 0 && worldConfig.getSlot() < config.getGuiSize()) {
                inventory.setItem(worldConfig.getSlot(), createWorldItem(worldConfig, player));
            }
        }
        player.openInventory(inventory);
    }

    private void fillBorders(Inventory inventory, int size) {
        int rows = size / 9;
        for (int i = 0; i < 9; i++) inventory.setItem(i, fillerItem);
        for (int i = size - 9; i < size; i++) inventory.setItem(i, fillerItem);
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, fillerItem);
            inventory.setItem(row * 9 + 8, fillerItem);
        }
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWorldItem(WorldConfig worldConfig, Player player) {
        ItemStack item = new ItemStack(worldConfig.getMaterial());
        item.editMeta(meta -> {
            meta.displayName(MessageCache.getComponent(worldConfig.getDisplayName()));
            List<Component> lore = new ArrayList<>();
            List<String> layout = worldConfig.getWorldLore();

            if (layout != null) {
                int queueSize = plugin.getQueueManager().getQueueSize(worldConfig.getWorldName());
                int minPlayers = plugin.getConfigManager().getMinPlayers();
                String playerQueue = plugin.getQueueManager().getQueueWorld(player);
                boolean inThisQueue = playerQueue != null && playerQueue.equals(worldConfig.getWorldName());

                String statusText = inThisQueue ? plugin.getConfigManager().getGuiStatusInQueue() : plugin.getConfigManager().getGuiStatusNotInQueue();

                for (String line : layout) {
                    lore.add(MessageCache.getComponent(line
                            .replace("%world%", worldConfig.getDisplayName())
                            .replace("%current%", String.valueOf(queueSize))
                            .replace("%max%", String.valueOf(minPlayers))
                            .replace("%status%", statusText)));
                }
                meta.lore(lore);
            }
        });
        return item;
    }

    public String getWorldFromSlot(int slot) {
        for (WorldConfig config : plugin.getConfigManager().getWorldConfigs().values()) {
            if (config.getSlot() == slot) return config.getWorldName();
        }
        return null;
    }
}