package com.minealphaproxy.api;

import net.kyori.adventure.text.Component;

import java.net.InetSocketAddress;
import java.util.UUID;

public interface MineAlphaPlayer {
    String getName();
    UUID getUniqueId();
    InetSocketAddress getAddress();
    boolean isOnline();

    void sendMessage(String message);
    void sendMessage(Component message);
    void sendActionBar(String message);
    void kick(String reason);

    String getCurrentServer();
    boolean hasPermission(String permission);

    void connectToServer(String serverName);
}