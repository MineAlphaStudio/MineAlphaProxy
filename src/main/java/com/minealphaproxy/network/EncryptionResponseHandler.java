package com.minealphaproxy.network;

import com.minealphaproxy.compat.velocity.VelocityProxyServer;
import com.minealphaproxy.config.ProxyConfig;
import com.minealphaproxy.event.EventBus;
import com.minealphaproxy.util.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.Arrays;

public final class EncryptionResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final ProxyConfig config;
    private final Logger logger;
    private final EventBus eventBus;
    private final VelocityProxyServer velocityProxyServer;
    private final String targetServer;

    public EncryptionResponseHandler(ProxyConfig config, Logger logger, EventBus eventBus, VelocityProxyServer velocityProxyServer, String targetServer) {
        this.config = config;
        this.logger = logger;
        this.eventBus = eventBus;
        this.velocityProxyServer = velocityProxyServer;
        this.targetServer = targetServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = VarInts.readVarInt(buf);
        if (packetId != 0x01) { ctx.close(); return; }

        int secretLen = VarInts.readVarInt(buf);
        byte[] encryptedSecret = new byte[secretLen];
        buf.readBytes(encryptedSecret);

        int tokenLen = VarInts.readVarInt(buf);
        byte[] encryptedToken = new byte[tokenLen];
        buf.readBytes(encryptedToken);

        byte[] sharedSecret, verifyToken;
        try {
            sharedSecret = EncryptionUtil.rsaDecrypt(encryptedSecret);
            verifyToken = EncryptionUtil.rsaDecrypt(encryptedToken);
        } catch (Exception e) { ctx.close(); return; }

        byte[] expectedToken = ctx.channel().attr(LoginStartHandler.VERIFY_TOKEN).get();
        if (expectedToken == null || !Arrays.equals(expectedToken, verifyToken)) { ctx.close(); return; }

        try {
            ctx.pipeline().addFirst("aes", new AesCodec(sharedSecret));
        } catch (Exception e) { ctx.close(); return; }

        String username = ctx.channel().attr(LoginStartHandler.USERNAME).get();
        String hash;
        try { hash = EncryptionUtil.serverHash(sharedSecret); } catch (Exception e) { ctx.close(); return; }

        logger.info("Authenticating {} with Mojang...", username);

        SessionService.hasJoined(username, hash).whenComplete((profile, error) -> {
            ctx.channel().eventLoop().execute(() -> {
                if (error != null || profile == null) {
                    disconnect(ctx, "§cFailed to verify session with Mojang.");
                    return;
                }
                BackendLoginFlow.connect(ctx, config, logger, profile.name(), profile.uuid(), profile.properties(), targetServer, eventBus, velocityProxyServer);
            });
        });
    }

    private void disconnect(ChannelHandlerContext ctx, String message) {
        try {
            String json = "{\"text\":\"" + message.replace("\"", "\\\"") + "\"}";
            ByteBuf out = ctx.alloc().buffer();
            VarInts.writeVarInt(out, 0x00);
            VarInts.writeString(out, json);
            ctx.writeAndFlush(out).addListener(f -> ctx.close());
        } catch (Exception e) { ctx.close(); }
    }
}