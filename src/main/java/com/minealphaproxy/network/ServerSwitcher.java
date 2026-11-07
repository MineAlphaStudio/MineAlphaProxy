package com.minealphaproxy.network;

import com.minealphaproxy.config.ProxyConfig;
import com.minealphaproxy.util.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.UUID;

public final class ServerSwitcher {
    private ServerSwitcher() {}

    public static void switchTo(Channel clientChannel, UUID uuid, String targetServer, ProxyConfig config, Logger logger) {
        try {
            int protocol = clientChannel.attr(HandshakeHandler.PROTOCOL_VERSION).get() != null 
                    ? clientChannel.attr(HandshakeHandler.PROTOCOL_VERSION).get() : 777;
            
            // Берем адрес, к которому подключался клиент
            String host = clientChannel.attr(HandshakeHandler.SERVER_ADDRESS).get();
            if (host == null || host.isBlank()) host = "localhost";
            int port = config.bind.port;

            PendingSwitches.put(uuid, targetServer);

            int packetId = getTransferPacketId(protocol);
            if (packetId == -1) {
                logger.warn("Transfer not supported for protocol {}", protocol);
                return;
            }

            ByteBuf out = clientChannel.alloc().buffer();
            VarInts.writeVarInt(out, packetId);
            VarInts.writeString(out, host);
            VarInts.writeVarInt(out, port);

            clientChannel.writeAndFlush(out).addListener(f -> clientChannel.close());

            logger.info("Sent Transfer to {}:{} for server '{}'", host, port, targetServer);
        } catch (Exception e) {
            logger.error("Failed to send Transfer packet", e);
        }
    }

    private static int getTransferPacketId(int protocol) {
        if (protocol >= 777) return 0x84; // 26.3
        if (protocol >= 776) return 0x81; // 26.1 - 26.2
        if (protocol >= 773) return 0x7F; // 1.21.9+
        if (protocol >= 768) return 0x7A; // 1.21.2+
        if (protocol >= 766) return 0x73; // 1.20.5+
        return -1;
    }
}