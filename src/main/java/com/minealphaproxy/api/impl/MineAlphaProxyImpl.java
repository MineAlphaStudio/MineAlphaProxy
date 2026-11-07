package com.minealphaproxy.api.impl;

import com.minealphaproxy.BuildInfo;
import com.minealphaproxy.api.*;
import com.minealphaproxy.api.command.MineAlphaCommandManager;
import com.minealphaproxy.config.ProxyConfig;
import com.minealphaproxy.util.Logger;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class MineAlphaProxyImpl implements MineAlphaProxy {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final ProxyServer vserver;
    private final Supplier<ProxyConfig> configSupplier;
    private final Logger logger;
    private final MineAlphaCommandManagerImpl commandManager;
    private final MineAlphaSchedulerImpl scheduler;
    private final MineAlphaEventBusImpl eventBus;

    public MineAlphaProxyImpl(ProxyServer vserver,
                              Supplier<ProxyConfig> configSupplier,
                              Logger logger) {
        this.vserver = vserver;
        this.configSupplier = configSupplier;
        this.logger = logger;
        this.commandManager = new MineAlphaCommandManagerImpl(logger);
        this.scheduler = new MineAlphaSchedulerImpl(logger);
        this.eventBus = new MineAlphaEventBusImpl(logger);
    }

    @Override public String getVersion() { return BuildInfo.VERSION; }

    @Override public String getBrand() {
        try {
            ProxyConfig cfg = configSupplier.get();
            return cfg != null ? cfg.proxyName : "MineAlphaProxy";
        } catch (Throwable t) { return "MineAlphaProxy"; }
    }

    @Override public Optional<MineAlphaPlayer> getPlayer(String name) {
        try { return vserver.getPlayer(name).map(MineAlphaPlayerImpl::new); }
        catch (Throwable t) { return Optional.empty(); }
    }

    @Override public MineAlphaPlayer getPlayer(UUID uuid) {
        try {
            Player p = vserver.getPlayer(uuid);
            return p != null ? new MineAlphaPlayerImpl(p) : null;
        } catch (Throwable t) { return null; }
    }

    @Override public Collection<MineAlphaPlayer> getPlayers() {
        try {
            return vserver.getAllPlayers().stream()
                    .map(MineAlphaPlayerImpl::new)
                    .collect(Collectors.toList());
        } catch (Throwable t) { return Collections.emptyList(); }
    }

    @Override public int getPlayerCount() {
        try { return vserver.getPlayerCount(); }
        catch (Throwable t) { return 0; }
    }

    @Override public Optional<MineAlphaServer> getServer(String name) {
        try {
            ProxyConfig cfg = configSupplier.get();
            if (cfg == null || cfg.servers == null) return Optional.empty();
            String addr = cfg.servers.list.get(name);
            if (addr == null) return Optional.empty();
            return Optional.of(new MineAlphaServerImpl(name, addr));
        } catch (Throwable t) { return Optional.empty(); }
    }

    @Override public Collection<MineAlphaServer> getServers() {
        try {
            ProxyConfig cfg = configSupplier.get();
            if (cfg == null || cfg.servers == null) return Collections.emptyList();
            return cfg.servers.list.entrySet().stream()
                    .map(e -> new MineAlphaServerImpl(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        } catch (Throwable t) { return Collections.emptyList(); }
    }

    @Override public String getDefaultServer() {
        try {
            ProxyConfig cfg = configSupplier.get();
            return cfg != null ? cfg.defaultServer : "lobby";
        } catch (Throwable t) { return "lobby"; }
    }

    @Override public MineAlphaCommandManager getCommandManager() { return commandManager; }
    @Override public MineAlphaScheduler getScheduler() { return scheduler; }
    @Override public MineAlphaEventBus getEventBus() { return eventBus; }

    public MineAlphaSchedulerImpl schedulerImpl() { return scheduler; }
    public MineAlphaEventBusImpl eventBusImpl() { return eventBus; }

    @Override public void broadcastMessage(String message) {
        Component c = LEGACY.deserialize(message);
        for (Player p : vserver.getAllPlayers()) p.sendMessage(c);
    }

    @Override public void broadcastMessage(Component message) {
        for (Player p : vserver.getAllPlayers()) p.sendMessage(message);
    }

    public void switchServer(MineAlphaPlayerImpl player, String serverName) {
        var vplayer = player.velocity();
        if (!(vplayer instanceof com.minealphaproxy.compat.velocity.VelocityPlayer vp)) return;
        var channel = vp.channel();
        if (channel == null || !channel.isActive()) return;
        com.minealphaproxy.network.ServerSwitcher.switchTo(channel, player.getUniqueId(), serverName, configSupplier.get(), logger);
    }}