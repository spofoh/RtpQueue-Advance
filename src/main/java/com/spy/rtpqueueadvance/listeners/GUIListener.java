package com.spy.rtpqueueadvance.listeners;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import com.spy.rtpqueueadvance.gui.WorldSelectionGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {

    private final RtpQueueAdvance plugin;

    public GUIListener(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WorldSelectionGUI.RtpQueueHolder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        String worldName = plugin.getWorldSelectionGUI().getWorldFromSlot(slot);

        if (worldName != null) {
            player.closeInventory();
            String currentQueue = plugin.getQueueManager().getQueueWorld(player);

            if (currentQueue != null && currentQueue.equals(worldName)) {
                plugin.getQueueManager().removeFromQueue(player);
            } else {
                if (currentQueue != null) {
                    plugin.getQueueManager().removeFromQueue(player);
                }
                plugin.getQueueManager().addToQueue(player, worldName);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WorldSelectionGUI.RtpQueueHolder) {
            event.setCancelled(true);
        }
    }
}