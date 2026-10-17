package com.thisislogan.railrider;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

final class SpeedController {
  private static final double TICKS_PER_SECOND = 20;
  // Speed vanilla adds each tick a cart spends on an energized powered rail, in blocks per tick.
  private static final double VANILLA_POWERED_RAIL_BOOST = 0.06;
  private static final double VANILLA_MAX_SPEED_BLOCKS_PER_SECOND = 8;

  private final double maxSpeed;
  private final double extraBoost;

  SpeedController(ConfigurationSection config, Consumer<String> warning) {
    if (config.contains("global-speed-boost") || config.contains("metro-speed-boost")) {
      warning.accept("global-speed-boost and metro-speed-boost are no longer supported; use "
          + "max-speed-blocks-per-second and powered-rail-boost-factor.");
    }
    maxSpeed = readNumber(config, "max-speed-blocks-per-second",
        VANILLA_MAX_SPEED_BLOCKS_PER_SECOND, true, warning) / TICKS_PER_SECOND;
    extraBoost = (readNumber(config, "powered-rail-boost-factor", 1, false, warning) - 1)
        * VANILLA_POWERED_RAIL_BOOST;
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

    cart.setMaxSpeed(maxSpeed);
    if (extraBoost == 0 || !isOnPoweredRail(cart.getLocation())) return;

    Vector velocity = cart.getVelocity();
    double horizontalSpeed = Math.hypot(velocity.getX(), velocity.getZ());
    if (horizontalSpeed == 0) return;

    double boostedSpeed = Math.min(horizontalSpeed + extraBoost, maxSpeed);
    double scale = boostedSpeed / horizontalSpeed;
    cart.setVelocity(new Vector(velocity.getX() * scale, velocity.getY(), velocity.getZ() * scale));
  }

  private static boolean isOnPoweredRail(Location location) {
    if (location == null || location.getWorld() == null) return false;

    Block rail = location.getBlock();
    // A cart on a slope can occupy the block immediately above its rail.
    // Do not look below a rail already found at the cart's position.
    if (!isRail(rail.getType())) rail = rail.getRelative(BlockFace.DOWN);

    return rail.getType() == Material.POWERED_RAIL
        && rail.getBlockData() instanceof Powerable powerable
        && powerable.isPowered();
  }

  private static boolean isRail(Material material) {
    return switch (material) {
      case RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL -> true;
      default -> false;
    };
  }
}
