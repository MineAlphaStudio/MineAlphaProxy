package com.minealphaproxy.network;

import com.minealphaproxy.compat.velocity.VelocityProxyServer;
import com.minealphaproxy.config.ProxyConfig;
import com.minealphaproxy.event.EventBus;
import com.minealphaproxy.util.Logger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public final class NetworkServer {
    private final ProxyConfig config;
    private final Logger logger;
    private final EventBus eventBus;
    private final VelocityProxyServer velocityProxyServer;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NetworkServer(ProxyConfig config, Logger logger, EventBus eventBus, VelocityProxyServer velocityProxyServer) {
        this.config = config;
        this.logger = logger;
        this.eventBus = eventBus;
        this.velocityProxyServer = velocityProxyServer;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("frame-decoder", new MinecraftVarintFrameDecoder())
                                .addLast("frame-encoder", new MinecraftFrameEncoder())
                                .addLast("handshake", new HandshakeHandler(config, logger, eventBus, velocityProxyServer))
                                .addLast("error-handler", new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        logger.debug("Connection error ({}): {}", ctx.channel().remoteAddress(), cause.getMessage());
                                        ctx.close();
                                    }
                                });
                    }
                });
        serverChannel = b.bind(config.bind.host, config.bind.port).sync().channel();
        logger.info("Network server bound to {}:{}", config.bind.host, config.bind.port);
    }

    public void stop() {
        logger.info("Stopping network server...");
        if (serverChannel != null) { serverChannel.close(); serverChannel = null; }
        if (bossGroup != null) { bossGroup.shutdownGracefully(); bossGroup = null; }
        if (workerGroup != null) { workerGroup.shutdownGracefully(); workerGroup = null; }
    }
}