package com.minealphaproxy.network;

import com.minealphaproxy.api.impl.MineAlphaApiHolder;
import com.minealphaproxy.api.impl.PlayerSource;
import com.minealphaproxy.compat.velocity.PlayerCommandSource;
import com.minealphaproxy.compat.velocity.VelocityCommandManager;
import com.minealphaproxy.util.Logger;
import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public final class PacketRelayHandler extends ChannelInboundHandlerAdapter {

    private final Channel target;
    private final boolean interceptCommands;
    private final VelocityCommandManager commandManager;
    private final Player player;
    private final Logger logger;

    public PacketRelayHandler(Channel target, boolean interceptCommands,
                              VelocityCommandManager commandManager,
                              Player player, Logger logger) {
        this.target = target;
        this.interceptCommands = interceptCommands;
        this.commandManager = commandManager;
        this.player = player;
        this.logger = logger;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            ReferenceCountUtil.release(msg);
            return;
        }

        if (interceptCommands && player != null) {
            buf.markReaderIndex();
            try {
                int packetId = VarInts.readVarInt(buf);

                // ID команд для 1.19+ - 0x04, для 1.20.5+ - 0x05, для 26.3 - 0x09
                // Мы ловим 0x04 и 0x09 для совместимости
                if (packetId == 0x04 || packetId == 0x05 || packetId == 0x09) {
                    String command = VarInts.readString(buf);
                    buf.resetReaderIndex();

                    // 1. MineAlpha команды
                    if (MineAlphaApiHolder.isAvailable()) {
                        var api = MineAlphaApiHolder.get();
                        var found = api.getPlayer(player.getUsername());
                        if (found.isPresent()) {
                            var source = new PlayerSource(found.get());
                            if (api.getCommandManager().dispatch(source, command)) {
                                buf.release();
                                return;
                            }
                        }
                    }

                    // 2. Velocity команды
                    if (commandManager != null) {
                        var vSource = new PlayerCommandSource(player);
                        if (commandManager.execute(vSource, command)) {
                            buf.release();
                            return;
                        }
                    }

                    // 3. Отправляем на бэкенд
                    if (target.isActive()) target.writeAndFlush(buf);
                    else buf.release();
                    return;
                }

                buf.resetReaderIndex();
            } catch (Exception e) {
                buf.resetReaderIndex();
            }
        }

        if (target.isActive()) target.writeAndFlush(buf);
        else { buf.release(); ctx.close(); }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (target.isActive()) target.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}