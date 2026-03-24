package com.spy.rtpqueueadvance.utils;

import org.bukkit.Material;

import java.util.List;

public record WorldConfig(String worldName, String displayName, List<String> worldLore, Material material, int slot,
                          int minRange, int maxRange, int centerX, int centerZ) {

}
