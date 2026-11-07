package com.minealphaproxy.api.impl;

import com.minealphaproxy.api.MineAlphaPlayer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.net.InetSocketAddress;
import java.util.UUID;

public final class MineAlphaPlayerImpl implements MineAlphaPlayer {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final Player vplayer;

    public MineAlphaPlayerImpl(Player vplayer) {
        this.vplayer = vplayer;
    }

    public Player velocity() { return vplayer; }

    @Override public String getName() { return vplayer.getUsername(); }
    @Override public UUID getUniqueId() { return vplayer.getUniqueId(); }
    @Override public InetSocketAddress getAddress() { return vplayer.getRemoteAddress(); }
    @Override public boolean isOnline() { return vplayer.isActive(); }

    @Override
    public void sendMessage(String message) {
        vplayer.sendMessage(LEGACY.deserialize(message));
    }

    @Override
    public void sendMessage(Component message) {
        vplayer.sendMessage(message);
    }

    @Override
    public void sendActionBar(String message) {
        vplayer.sendActionBar(LEGACY.deserialize(message));
    }

    @Override
    public void kick(String reason) {
        vplayer.disconnect(LEGACY.deserialize(reason));
    }

    @Override
    public String getCurrentServer() { return "unknown"; }

    @Override
    public boolean hasPermission(String permission) {
        return vplayer.hasPermission(permission);
    }

    @Override
    public void connectToServer(String serverName) {
        MineAlphaProxyImpl api = MineAlphaApiHolder.get();
        if (api != null) api.switchServer(this, serverName);
    }
}