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
3. For metro-region boosts, also install WorldGuard **7.0.19** and a Minecraft 26.3-compatible
   WorldEdit Bukkit release. Both are optional for spawning carts and using global speed.

## Speed configuration

Edit `plugins/RailRider/config.yml` and restart the server to apply changes:

```yaml
global-speed-boost:
  enabled: false
  max-speed: 0.6
  boost-factor: 1.1

metro-speed-boost:
  enabled: false
  max-speed: 0.6
  boost-factor: 1.1
```

Set `global-speed-boost.enabled: true` to use its custom speed throughout **every world**,
including metro regions. It takes priority over metro settings. Otherwise, enabled metro
settings apply inside WorldGuard regions whose names start with `metro` (case insensitive).
Outside those regions, or with both toggles disabled, the cap is `0.4` with no added boost.
Without WorldGuard, metro boosts are skipped with one startup warning when enabled.

Speed settings affect only occupied carts spawned by RailRider; ordinary player-placed carts
are unaffected. `max-speed` is a finite positive horizontal speed in **blocks per tick**:
`0.4` is 8 blocks/second and `0.6` is 12 blocks/second at 20 ticks/second.
`boost-factor` multiplies moving carts' horizontal speed each update, capped at `max-speed`.
Use `1.0` for no additional acceleration. Stationary carts stay stationary, and vertical
velocity is preserved. Normal rail physics still affect travel; the cap is not a constant
cruising speed.

Maximum speeds must be positive and boost factors at least `1.0`; both must be finite.
Invalid numeric settings produce one warning per setting at startup and use the defaults
above. Existing metro settings are preserved; missing toggles default to disabled. Existing
configuration files are not overwritten: add the global section above to enable it.

## Validation

`mvn clean verify` runs focused speed-policy tests and builds the plugin. Before deploying,
smoke-test on a 26.3 server both with and without WorldGuard: spawn and ride carts, cross
metro boundaries, test slopes and stationary carts, and verify cleanup on dismount,
disconnect, and server shutdown. Check that player-placed carts are unaffected.
