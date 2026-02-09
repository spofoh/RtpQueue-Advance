package com.spy.rtpqueueadvance.utils;

import org.bukkit.Material;

import java.util.List;

public class WorldConfig {

    private final String worldName;
    private final String displayName;
    private final List<String> worldLore;
    private final Material material;
    private final int slot;
    private final int minRange;
    private final int maxRange;
    private final int centerX;
    private final int centerZ;

    public WorldConfig(String worldName, String displayName, List<String> worldLore,
                       Material material, int slot, int minRange, int maxRange,
                       int centerX, int centerZ) {
        this.worldName = worldName;
        this.displayName = displayName;
        this.worldLore = worldLore;
        this.material = material;
        this.slot = slot;
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getWorldLore() {
        return worldLore;
    }

    public Material getMaterial() {
        return material;
    }

    public int getSlot() {
        return slot;
    }

    public int getMinRange() {
        return minRange;
    }

    public int getMaxRange() {
        return maxRange;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }
}
