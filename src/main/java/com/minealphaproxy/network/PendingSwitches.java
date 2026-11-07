package com.minealphaproxy.network;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class PendingSwitches {
    private static final Map<UUID, String> pending = new ConcurrentHashMap<>();

    public static void put(UUID uuid, String serverName) {
        pending.put(uuid, serverName);
    }

    public static String take(UUID uuid) {
        return pending.remove(uuid);
    }

    private PendingSwitches() {}
}