# RailRider Plugin

A simple Minecraft plugin that allows players to right-click rails with an empty hand to spawn a minecart and automatically ride it. When the player exits the minecart, it gets destroyed.

## Features

- Spawn and ride a minecart by right-clicking on rails with an empty hand.
- Automatically removes the minecart when the player exits it to keep the world clean.

## Installation

Requires Minecraft **26.3** (Spigot or Paper), **Java 25**, and Maven to build.

1. Build the plugin using Maven with Java 25:

   ```bash
   mvn clean verify
   ```
2. Copy `target/railrider-3.1.jar` into your server's `plugins` folder and restart.

## Speed configuration

Edit `plugins/RailRider/config.yml` and restart the server to apply changes:

```yaml
max-speed-blocks-per-second: 8
powered-rail-boost-factor: 1
```

Both settings default to vanilla and affect only occupied carts spawned by RailRider;
ordinary player-placed carts are unaffected.

`max-speed-blocks-per-second` caps horizontal speed. Vanilla is `8`; it must be positive.

`powered-rail-boost-factor` is a multiple of vanilla's acceleration on powered rails receiving
redstone power: `2` accelerates twice as hard as vanilla, `1` is vanilla. It must be at least
`1`. Elsewhere the plugin leaves velocity untouched, so vanilla coasting, friction, and
braking apply. It does not propel stationary carts and preserves vertical velocity.

Invalid values produce one warning per setting at startup and fall back to vanilla. Existing
configuration files are not overwritten; the old `global-speed-boost` and `metro-speed-boost`
sections are ignored with a startup warning.

## Validation

`mvn clean verify` runs focused speed-policy tests and builds the plugin. Before deploying,
smoke-test on a 26.3 server: spawn and ride carts, test energized powered-rail acceleration,
ordinary-rail coasting, unpowered powered-rail braking, slopes, and stationary carts, and
verify cleanup on dismount, disconnect, and server shutdown. Check that player-placed carts
are unaffected.
