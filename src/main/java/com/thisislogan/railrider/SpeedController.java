package com.thisislogan.railrider;

import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

final class SpeedController {
  private record Settings(boolean enabled, double maxSpeed, double boostFactor) {}

  private static final Settings DEFAULT = new Settings(true, 0.4, 1.0);
  private final Settings global;
  private final Settings metro;
  private final Predicate<Location> metroRegion;

  SpeedController(ConfigurationSection config, Consumer<String> warning,
      Predicate<Location> metroRegion) {
    global = readSettings(config, "global-speed-boost", warning);
    metro = readSettings(config, "metro-speed-boost", warning);
    this.metroRegion = metroRegion;
    if (metro.enabled() && metroRegion == null) {
      warning.accept("Metro speed boost is enabled but WorldGuard is unavailable; skipping metro boosts.");
    }
  }

  private static Settings readSettings(ConfigurationSection config, String path,
      Consumer<String> warning) {
    return new Settings(config.getBoolean(path + ".enabled", false),
        readNumber(config, path + ".max-speed", 0.6, true, warning),
        readNumber(config, path + ".boost-factor", 1.1, false, warning));
  }

  private static double readNumber(ConfigurationSection config, String path, double fallback,
      boolean speed, Consumer<String> warning) {
    Object raw = config.get(path);
    if (raw == null) return fallback;
    if (raw instanceof Number number) {
      double value = number.doubleValue();
      if (Double.isFinite(value) && (speed ? value > 0 : value >= 1)) return value;
    }
    warning.accept("Invalid " + path + "; using " + fallback + ".");
    return fallback;
  }

  void update(Minecart cart, boolean tracked) {
    if (!tracked || cart.getPassengers().isEmpty()) return;

    Settings settings = DEFAULT;
    if (global.enabled()) {
      settings = global;
    } else if (metro.enabled() && metroRegion != null && metroRegion.test(cart.getLocation())) {
      settings = metro;
    }

    cart.setMaxSpeed(settings.maxSpeed());
    Vector velocity = cart.getVelocity();
    double horizontalSpeed = Math.hypot(velocity.getX(), velocity.getZ());
    if (horizontalSpeed == 0) return;

    double boostedSpeed = Math.min(horizontalSpeed * settings.boostFactor(), settings.maxSpeed());
    double scale = boostedSpeed / horizontalSpeed;
    cart.setVelocity(new Vector(velocity.getX() * scale, velocity.getY(), velocity.getZ() * scale));
  }
}
