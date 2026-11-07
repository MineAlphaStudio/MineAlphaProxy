package com.minealphaproxy.network;

import com.minealphaproxy.compat.velocity.VelocityProxyServer;
import com.minealphaproxy.config.ProxyConfig;
import com.minealphaproxy.event.EventBus;
import com.minealphaproxy.util.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class BackendLoginFlow {
    private static final int TIMEOUT = 5000;

    public static void connect(ChannelHandlerContext ctx, ProxyConfig config, Logger logger,
                               String username, UUID uuid, List<PlayerProfile.Property> properties,
                               String targetServer, EventBus eventBus, VelocityProxyServer velocityProxyServer) {
        String address = config.servers.list.get(targetServer);
        if (address == null) {
            logger.error("Server '{}' not found in config", targetServer);
            ctx.close();
            return;
        }

        String host; int port;
        int colon = address.lastIndexOf(':');
        if (colon <= 0) { host = address; port = 25565; }
        else { host = address.substring(0, colon); port = Integer.parseInt(address.substring(colon + 1)); }

        String ip = extractIp(ctx);
        String secret = readSecret(config);
        byte[] extraAttr = ctx.channel().attr(LoginStartHandler.LOGIN_START_EXTRA).get();
        final byte[] extra = extraAttr != null ? extraAttr : new byte[0];

        logger.info("Connecting to backend '{}' ({}:{})...", targetServer, host, port);

        BackendConnector.connect(ctx.channel().eventLoop(), host, port, TIMEOUT,
                backendChannel -> {
                    sendHandshake(ctx, config, backendChannel);
                    sendLoginStart(ctx, backendChannel, username, extra);
                    backendChannel.pipeline().addLast("backend-login",
                            new BackendLoginHandler(ctx.channel(), config, logger, ip, uuid, username, secret, properties, null, eventBus, velocityProxyServer));
                },
                (h, p, cause) -> {
                    logger.warn("Backend '{}' unavailable", targetServer);
                    ctx.close();
                });
    }

    private static void sendHandshake(ChannelHandlerContext ctx, ProxyConfig config, Channel backend) {
        Integer protocol = ctx.channel().attr(HandshakeHandler.PROTOCOL_VERSION).get();
        String serverAddress = ctx.channel().attr(HandshakeHandler.SERVER_ADDRESS).get();
        if (protocol == null) protocol = 0;
        if (serverAddress == null) serverAddress = "minecraft";

        ByteBuf out = ctx.alloc().buffer();
        VarInts.writeVarInt(out, 0x00);
        VarInts.writeVarInt(out, protocol);
        VarInts.writeString(out, serverAddress);
        out.writeShort(config.bind.port);
        VarInts.writeVarInt(out, 2);
        backend.writeAndFlush(out);
    }

    private static void sendLoginStart(ChannelHandlerContext ctx, Channel backend, String username, byte[] extra) {
        ByteBuf out = ctx.alloc().buffer();
        VarInts.writeVarInt(out, 0x00);
        VarInts.writeString(out, username);
        if (extra.length > 0) out.writeBytes(extra);
        backend.writeAndFlush(out);
    }

    private static String extractIp(ChannelHandlerContext ctx) {
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress isa) return isa.getAddress().getHostAddress();
        return "127.0.0.1";
    }

    private static String readSecret(ProxyConfig config) {
        try {
            Path p = Path.of(config.forwarding.secretFile);
            if (Files.exists(p)) return Files.readString(p).trim();
        } catch (Exception ignored) {}
        return "";
    }
}