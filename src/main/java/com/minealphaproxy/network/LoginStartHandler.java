package com.minealphaproxy.network;

import com.minealphaproxy.compat.velocity.VelocityProxyServer;
import com.minealphaproxy.config.ProxyConfig;
import com.minealphaproxy.event.EventBus;
import com.minealphaproxy.util.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LoginStartHandler extends SimpleChannelInboundHandler<ByteBuf> {
    public static final AttributeKey<String> USERNAME = AttributeKey.valueOf("username");
    public static final AttributeKey<byte[]> VERIFY_TOKEN = AttributeKey.valueOf("verify-token");
    public static final AttributeKey<byte[]> LOGIN_START_EXTRA = AttributeKey.valueOf("login-start-extra");
    public static final AttributeKey<UUID> CLIENT_UUID = AttributeKey.valueOf("client-uuid");
    public static final AttributeKey<PlayerProfile.ProfileKey> CLIENT_KEY = AttributeKey.valueOf("client-key");

    private final ProxyConfig config;
    private final Logger logger;
    private final EventBus eventBus;
    private final VelocityProxyServer velocityProxyServer;

    public LoginStartHandler(ProxyConfig config, Logger logger, EventBus eventBus, VelocityProxyServer velocityProxyServer) {
        this.config = config;
        this.logger = logger;
        this.eventBus = eventBus;
        this.velocityProxyServer = velocityProxyServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = VarInts.readVarInt(buf);
        if (packetId != 0x00) { ctx.close(); return; }

        String username = VarInts.readString(buf);
        byte[] extra = new byte[buf.readableBytes()];
        buf.readBytes(extra);

        UUID clientUuid = extractClientUuid(extra);
        if (clientUuid != null) ctx.channel().attr(CLIENT_UUID).set(clientUuid);

        PlayerProfile.ProfileKey clientKey = extractProfileKey(extra);
        if (clientKey != null) ctx.channel().attr(CLIENT_KEY).set(clientKey);

        ctx.channel().attr(USERNAME).set(username);
        ctx.channel().attr(LOGIN_START_EXTRA).set(extra);

        // ГЛАВНОЕ ИСПРАВЛЕНИЕ: Выбираем UUID в зависимости от режима
        UUID finalUuid;
        if (config.bind.onlineMode) {
            // В онлайн-режиме используем UUID от клиента (позже он заменится на Mojang-UUID)
            finalUuid = clientUuid != null ? clientUuid : VelocityForwarding.offlineUuid(username);
        } else {
            // В ОФЛАЙН-РЕЖИМЕ всегда генерируем стандартный OfflinePlayer:<ник>,
            // чтобы UUID совпадал с Velocity и BungeeCord.
            finalUuid = VelocityForwarding.offlineUuid(username);
        }

        // Проверяем, есть ли ожидающий сервер для этого UUID
        String targetServer = PendingSwitches.take(finalUuid);
        if (targetServer == null) targetServer = config.defaultServer;

        logger.info("Login Start: {} (uuid={}). Routing to '{}'", username, finalUuid, targetServer);

        if (config.bind.onlineMode) {
            byte[] verifyToken = new byte[4];
            ThreadLocalRandom.current().nextBytes(verifyToken);
            ctx.channel().attr(VERIFY_TOKEN).set(verifyToken);

            sendEncryptionRequest(ctx, verifyToken);
            ctx.pipeline().replace(this, "encryption-response", new EncryptionResponseHandler(config, logger, eventBus, velocityProxyServer, targetServer));
        } else {
            BackendLoginFlow.connect(ctx, config, logger, username, finalUuid, List.of(), targetServer, eventBus, velocityProxyServer);
        }
    }

    private static UUID extractClientUuid(byte[] extra) {
        if (extra.length < 16) return null;
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (extra[i] & 0xFF);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (extra[i] & 0xFF);
        return new UUID(msb, lsb);
    }

    private static PlayerProfile.ProfileKey extractProfileKey(byte[] extra) {
        if (extra.length <= 16) return null;
        try {
            ByteBuf buf = Unpooled.wrappedBuffer(extra);
            buf.skipBytes(16);
            long expire = buf.readLong();
            int keyLen = VarInts.readVarInt(buf);
            byte[] keyBytes = new byte[keyLen];
            buf.readBytes(keyBytes);
            int sigLen = VarInts.readVarInt(buf);
            byte[] sig = new byte[sigLen];
            buf.readBytes(sig);
            buf.release();
            return new PlayerProfile.ProfileKey(expire, keyBytes, sig);
        } catch (Exception e) { return null; }
    }

    private void sendEncryptionRequest(ChannelHandlerContext ctx, byte[] verifyToken) {
        byte[] publicKey = EncryptionUtil.publicKey().getEncoded();
        ByteBuf out = ctx.alloc().buffer();
        VarInts.writeVarInt(out, 0x01);
        VarInts.writeString(out, "");
        VarInts.writeVarInt(out, publicKey.length);
        out.writeBytes(publicKey);
        VarInts.writeVarInt(out, verifyToken.length);
        out.writeBytes(verifyToken);
        out.writeBoolean(true);
        ctx.writeAndFlush(out);
    }
}