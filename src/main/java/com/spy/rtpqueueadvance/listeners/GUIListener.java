package com.spy.rtpqueueadvance.listeners;

import com.spy.rtpqueueadvance.RtpQueueAdvance;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {

    private final RtpQueueAdvance plugin;

    public GUIListener(RtpQueueAdvance plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        String title = event.getView().getTitle();
        if (!plugin.getWorldSelectionGUI().isGUI(title)) return;
        
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        
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
}
