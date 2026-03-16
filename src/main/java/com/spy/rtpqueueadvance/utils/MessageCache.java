package com.spy.rtpqueueadvance.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.LinkedHashMap;
import java.util.Map;

public class MessageCache {

    private static final int MAX_CACHE_SIZE = 500;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private static final Map<String, Component> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public static Component getComponent(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        synchronized (cache) {
            return cache.computeIfAbsent(text, t -> {
                Component legacy = LEGACY_SERIALIZER.deserialize(t);

                String serialized = MINI_MESSAGE.serialize(legacy);
                return MINI_MESSAGE.deserialize("<!italic>" + serialized);
            });
        }
    }

    public static void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }
}