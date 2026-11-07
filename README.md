# MineAlphaProxy

A lightweight Minecraft proxy with its own API, plugin system, and built-in ViaVersion support.

## Features

- Own Netty-based proxy core (Handshake, Status, Login, Play)
- Modern forwarding v4 — works with both premium and cracked clients
- Own YAML config (core.yml)
- Own plugin API (com.minealphaproxy.api)
- Velocity plugin support
- Built-in ViaProxy — accepts clients from 1.7.2 to 26.3
- Console commands, localization (en/ru), ANSI colors
- Single fat JAR
<meta name="google-site-verification" content="q5zFXE3TT-FAfjYYmM4r0LB0SCnkHs58ysRCDhA7xeY" />
## Requirements

- Java 21+

## Building

    ./gradlew clean build

Output: build/libs/MineAlphaProxy-1.0.1-ALPHA.jar

## Plugin Development

    @MineAlphaPluginInfo(id = "myplugin", name = "MyPlugin", version = "1.0")
    public class MyPlugin extends MineAlphaPlugin {
        @Override
        public void onEnable() {
            getLogger().info("Enabled!");
        }
    }

## Credits

- ViaVersion / ViaBackwards / ViaProxy by ViaVersion Team
- Adventure API by Kyori
