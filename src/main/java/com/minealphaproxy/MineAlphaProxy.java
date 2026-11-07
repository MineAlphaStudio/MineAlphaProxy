package com.minealphaproxy;

import com.minealphaproxy.api.event.ProxyStartEvent;
import com.minealphaproxy.api.event.ProxyStopEvent;
import com.minealphaproxy.api.impl.MineAlphaApiHolder;
import com.minealphaproxy.api.impl.MineAlphaProxyImpl;
import com.minealphaproxy.command.CommandManager;
import com.minealphaproxy.command.ConsoleCommandSource;
import com.minealphaproxy.command.commands.*;
import com.minealphaproxy.compat.velocity.VelocityCommandManager;
import com.minealphaproxy.compat.velocity.VelocityEventManager;
import com.minealphaproxy.compat.velocity.VelocityProxyServer;
import com.minealphaproxy.compat.velocity.VelocityScheduler;
import com.minealphaproxy.config.*;
import com.minealphaproxy.event.EventBus;
import com.minealphaproxy.lang.Colors;
import com.minealphaproxy.lang.Messages;
import com.minealphaproxy.network.NetworkServer;
import com.minealphaproxy.plugin.MineAlphaPluginLoader;
import com.minealphaproxy.plugin.VelocityPluginManager;
import com.minealphaproxy.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class MineAlphaProxy {

    private static final Logger logger = Logger.get(MineAlphaProxy.class);

    private ProxyConfig config;
    private EventBus eventBus;
    private VelocityEventManager velocityEventManager;
    private VelocityCommandManager velocityCommandManager;
    private VelocityScheduler velocityScheduler;
    private VelocityProxyServer velocityProxyServer;
    private VelocityPluginManager velocityPluginManager;
    private MineAlphaProxyImpl mineAlphaApi;
    private MineAlphaPluginLoader mineAlphaPluginLoader;
    private NetworkServer networkServer;
    private CommandManager commandManager;

    public static void main(String[] args) {
        MineAlphaProxy core = new MineAlphaProxy();
        try {
            core.bootstrap();
            Runtime.getRuntime().addShutdownHook(new Thread(core::shutdown));
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to start proxy", e);
            System.exit(1);
        }
    }

    public void bootstrap() throws Exception {
        config = bootstrapConfig();
        ensureForwardingSecret(config);
        ConfigValidator.validate(config);

        Messages.init(Path.of("lang"), config.language, logger);

        ProxyBrand brand = config.brand();

        eventBus = new EventBus(logger);
        velocityEventManager = new VelocityEventManager(eventBus);
        velocityCommandManager = new VelocityCommandManager(logger);
        velocityScheduler = new VelocityScheduler(logger);

        velocityProxyServer = new VelocityProxyServer(
                velocityEventManager,
                velocityCommandManager,
                velocityScheduler,
                new InetSocketAddress(config.bind.host, config.bind.port));

        velocityPluginManager = new VelocityPluginManager(
                Path.of(config.plugins.velocityDir),
                logger, eventBus, velocityProxyServer);
        velocityPluginManager.loadPlugins();

        logger.info("[{}] MineAlphaProxy v{} started on {}:{}",
                brand.displayName(), BuildInfo.VERSION,
                config.bind.host, config.bind.port);
        logger.info("[{}] MOTD: {}", brand.displayName(),
                Colors.toAnsi(config.resolveMotd()));

        // ==== Р РЋР Р†Р С•Р в„– MineAlpha API ====
        mineAlphaApi = new MineAlphaProxyImpl(
                velocityProxyServer,
                () -> config,
                logger);
        MineAlphaApiHolder.set(mineAlphaApi);

        // ==== Р РЋР Р†Р С•Р С‘ Р С—Р В»Р В°Р С–Р С‘Р Р…РЎвЂ№ ====
        mineAlphaPluginLoader = new MineAlphaPluginLoader(
                Path.of("MineAlphaPlugins"),
                logger, mineAlphaApi);
        mineAlphaPluginLoader.loadPlugins();

        try {
            mineAlphaApi.eventBusImpl().fire(new ProxyStartEvent());
        } catch (Throwable t) {
            logger.warn("ProxyStartEvent fire failed: {}", t.getMessage());
        }

        commandManager = new CommandManager(logger);
        registerBuiltins(commandManager);
        startConsoleReader();

        networkServer = new NetworkServer(config, logger, eventBus, velocityProxyServer);
        networkServer.start();

        // ==== Р—Р°РїСѓСЃРє ViaProxy (РІСЃС‚СЂРѕРµРЅРЅС‹Р№) ====
    }

    private void registerBuiltins(CommandManager cm) {
        cm.register(new HelpCommand(cm));
        cm.register(new StopCommand());
        cm.register(new ReloadCommand(this));
        cm.register(new ServersCommand(this));
        cm.register(new PluginsCommand(this));
        cm.register(new LangCommand(this));
        cm.register(new ProxyCommand(this));
        cm.register(new VersionCommand());
    }

    private void startConsoleReader() {
        ConsoleCommandSource console = new ConsoleCommandSource(commandManager, logger);
        Thread t = new Thread(console, "console-reader");
        t.setDaemon(true);
        t.start();
    }

    public void reload() throws IOException {
        config = new ConfigLoader(Path.of("core.yml")).load();
        ConfigValidator.validate(config);
        Messages.init(Path.of("lang"), config.language, logger);
        logger.info("Config and language reloaded");
    }

    public void switchLanguage(String code) throws IOException {
        config.language = code;
        new ConfigLoader(Path.of("core.yml")).save(config);
        Messages.init(Path.of("lang"), code, logger);
    }

    public ProxyConfig config() { return config; }
    public VelocityPluginManager velocityPluginManager() { return velocityPluginManager; }

    private ProxyConfig bootstrapConfig() throws IOException {
        Path own = Path.of("core.yml");
        Path velocity = Path.of("velocity.toml");
        Path bungee = Path.of("config.yml");

        ConfigLoader loader = new ConfigLoader(own);

        if (Files.exists(own)) {
            logger.info("Loading config from core.yml");
            return loader.load();
        }

        ProxyConfig cfg;
        if (Files.exists(velocity)) {
            logger.info("Importing config from velocity.toml -> core.yml");
            cfg = new VelocityTomlImporter().importFrom(velocity);

            Path parent = velocity.toAbsolutePath().getParent();
            Path secret = (parent != null)
                    ? parent.resolve("forwarding.secret")
                    : Path.of("forwarding.secret");

            if (Files.exists(secret)) {
                Files.copy(secret, Path.of("forwarding.secret"),
                        StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied forwarding.secret from velocity.toml directory");
            }
            logger.warn("Fields forced-hosts / ping-passthrough are not imported.");
        } else if (Files.exists(bungee)) {
            logger.info("Importing config from Bungee config.yml -> core.yml");
            cfg = new BungeeYamlImporter().importFrom(bungee);
        } else {
            logger.info("Creating default core.yml from template");
            cfg = loader.defaultFromResource();
        }

        loader.save(cfg);
        return cfg;
    }

    private void ensureForwardingSecret(ProxyConfig cfg) throws IOException {
        if (!"modern".equalsIgnoreCase(cfg.forwarding.mode)) return;
        Path secret = Path.of(cfg.forwarding.secretFile);
        if (Files.exists(secret)) {
            logger.info("Using existing forwarding secret: {}", secret);
            return;
        }
        String generated = UUID.randomUUID().toString().replace("-", "");
        Files.writeString(secret, generated);
        logger.info("Generated new forwarding.secret: {}", secret);
        logger.warn("Make sure backend servers use the same secret for modern forwarding!");
    }

    private void shutdown() {
        logger.info("Stopping proxy...");

        if (mineAlphaApi != null) {
            try { mineAlphaApi.eventBusImpl().fire(new ProxyStopEvent()); }
            catch (Throwable ignored) {}
        }

        if (mineAlphaPluginLoader != null) mineAlphaPluginLoader.disableAll();
        if (mineAlphaApi != null) mineAlphaApi.schedulerImpl().shutdown();

        if (networkServer != null) networkServer.stop();
        if (velocityPluginManager != null) velocityPluginManager.disableAll();
        if (velocityScheduler != null) velocityScheduler.shutdown();
    }
}