package com.minealphaproxy.network;

import com.minealphaproxy.compat.velocity.VelocityProxyServer;
import com.minealphaproxy.config.ProxyConfig;
import com.minealphaproxy.event.EventBus;
import com.minealphaproxy.util.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

public final class HandshakeHandler extends SimpleChannelInboundHandler<ByteBuf> {
    public static final AttributeKey<Integer> PROTOCOL_VERSION = AttributeKey.valueOf("protocol-version");
    public static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.valueOf("server-address");

    private final ProxyConfig config;
    private final Logger logger;
    private final EventBus eventBus;
    private final VelocityProxyServer velocityProxyServer;

    public HandshakeHandler(ProxyConfig config, Logger logger, EventBus eventBus, VelocityProxyServer velocityProxyServer) {
        this.config = config;
        this.logger = logger;
        this.eventBus = eventBus;
        this.velocityProxyServer = velocityProxyServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = VarInts.readVarInt(buf);
        if (packetId != 0x00) { ctx.close(); return; }

        int protocolVersion = VarInts.readVarInt(buf);
        String serverAddress = VarInts.readString(buf);
        int serverPort = buf.readUnsignedShort();
        int nextState = VarInts.readVarInt(buf);

        ctx.channel().attr(PROTOCOL_VERSION).set(protocolVersion);
        ctx.channel().attr(SERVER_ADDRESS).set(serverAddress);

        if (nextState == 1) {
            ctx.pipeline().replace(this, "status", new StatusHandler(config, logger));
        } else if (nextState == 2 || nextState == 3) { // 3 = Transfer
            ctx.pipeline().replace(this, "login", new LoginStartHandler(config, logger, eventBus, velocityProxyServer));
        } else {
            ctx.close();
        }
    }
}