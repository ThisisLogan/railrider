package com.thisislogan.railrider;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class SpeedControllerTest {
  private final MemoryConfiguration config = new MemoryConfiguration();
  private final List<String> warnings = new ArrayList<>();
  private final Cart cart = new Cart();

  private SpeedController controller(Predicate<Location> regions) {
    return new SpeedController(config, warnings::add, regions);
  }

  private void enable(String mode, double speed, double factor) {
    config.set(mode + ".enabled", true);
    config.set(mode + ".max-speed", speed);
    config.set(mode + ".boost-factor", factor);
  }

  @Test
  void globalOverridesMetroAndSkipsRegionLookup() {
    enable("global-speed-boost", 0.8, 2);
    enable("metro-speed-boost", 0.5, 1.1);
    cart.velocity = new Vector(0.5, 0, 0);
    controller(location -> { throw new AssertionError("Global mode must skip region lookup"); })
        .update(cart.entity, true);
    assertEquals(0.8, cart.maxSpeed);
    assertEquals(0.8, cart.velocity.getX(), 1e-12);
  }

  @Test
  void metroAppliesInsideRegionAndResetsOutside() {
    enable("metro-speed-boost", 0.7, 2);
    AtomicBoolean inside = new AtomicBoolean(true);
    SpeedController controller = controller(location -> inside.get());
    cart.velocity = new Vector(0.3, 0.2, 0);
    controller.update(cart.entity, true);
    assertEquals(0.7, cart.maxSpeed);
    assertEquals(0.6, cart.velocity.getX(), 1e-12);
    inside.set(false);
    controller.update(cart.entity, true);
    assertEquals(0.4, cart.maxSpeed);
    assertEquals(0.6, cart.velocity.getX(), 1e-12);
    assertEquals(0.2, cart.velocity.getY());
    assertEquals(1, cart.velocityWrites);
  }

  @Test
  void missingTogglesAreDisabledAndDoNotQueryRegions() {
    controller(location -> { throw new AssertionError("Metro mode is disabled"); })
        .update(cart.entity, true);
    assertEquals(0.4, cart.maxSpeed);
    assertEquals(0, cart.velocityWrites);
    assertTrue(warnings.isEmpty());
  }

  @Test
  void explicitlyDisabledMetroUsesDefaultEvenInsideRegion() {
    config.set("metro-speed-boost.enabled", false);
    controller(location -> true).update(cart.entity, true);
    assertEquals(0.4, cart.maxSpeed);
  }

  @Test
  void ordinaryCartsAreNotReadOrChanged() {
    enable("global-speed-boost", 0.8, 2);
    controller(null).update(cart.entity, false);
    assertEquals(0, cart.calls);
  }

  @Test
  void emptyTrackedCartsAreNotChanged() {
    enable("global-speed-boost", 0.8, 2);
    cart.occupied = false;
    controller(null).update(cart.entity, true);
    assertEquals(-1, cart.maxSpeed);
    assertEquals(0, cart.velocityWrites);
  }

  @Test
  void boostPreservesHorizontalDirectionAndVerticalVelocity() {
    enable("global-speed-boost", 0.6, 2);
    cart.velocity = new Vector(-0.3, -0.2, 0.4);
    controller(null).update(cart.entity, true);
    assertEquals(-0.36, cart.velocity.getX(), 1e-12);
    assertEquals(0.48, cart.velocity.getZ(), 1e-12);
    assertEquals(-0.2, cart.velocity.getY());
    assertEquals(0.6, Math.hypot(cart.velocity.getX(), cart.velocity.getZ()), 1e-12);
  }

  @Test
  void movingCartAcceleratesBelowCap() {
    enable("global-speed-boost", 0.6, 1.1);
    cart.velocity = new Vector(0.2, 0, 0);
    controller(null).update(cart.entity, true);
    assertEquals(0.22, cart.velocity.getX(), 1e-12);
  }

  @Test
  void stationaryAndVerticalOnlyCartsAreNotPropelled() {
    enable("global-speed-boost", 0.6, 2);
    SpeedController controller = controller(null);
    controller.update(cart.entity, true);
    cart.velocity = new Vector(0, 0.2, 0);
    controller.update(cart.entity, true);
    assertEquals(0, cart.velocityWrites);
    assertEquals(new Vector(0, 0.2, 0), cart.velocity);
  }

  @Test
  void globalWorksWithoutWorldGuardAndMissingMetroWarnsOnlyAtStartup() {
    enable("global-speed-boost", 0.9, 1.1);
    enable("metro-speed-boost", 0.7, 1.1);
    SpeedController controller = controller(null);
    controller.update(cart.entity, true);
    controller.update(cart.entity, true);
    assertEquals(0.9, cart.maxSpeed);
    assertEquals(1, warnings.size());
  }

  @Test
  void unavailableMetroFallsBackToDefault() {
    enable("metro-speed-boost", 0.7, 1.1);
    controller(null).update(cart.entity, true);
    assertEquals(0.4, cart.maxSpeed);
    assertEquals(1, warnings.size());
  }

  @Test
  void invalidNumbersUseDefaultsAndWarnOncePerSetting() {
    for (Object invalid : List.of(-1, 0, Double.NaN, Double.POSITIVE_INFINITY, "fast")) {
      warnings.clear();
      enable("global-speed-boost", 0.9, 2);
      config.set("global-speed-boost.max-speed", invalid);
      config.set("global-speed-boost.boost-factor", invalid);
      cart.velocity = new Vector(0.2, 0, 0);
      SpeedController controller = controller(null);
      controller.update(cart.entity, true);
      assertEquals(0.6, cart.maxSpeed);
      assertEquals(0.22, cart.velocity.getX(), 1e-12);
      controller.update(cart.entity, true);
      assertEquals(2, warnings.size());
    }
  }

  @Test
  void factorOneIsAllowedButFactorsBelowOneAreRejected() {
    enable("global-speed-boost", 0.9, 1);
    cart.velocity = new Vector(0.2, 0, 0);
    controller(null).update(cart.entity, true);
    assertEquals(0.2, cart.velocity.getX());
    assertEquals(0, cart.velocityWrites);
    assertTrue(warnings.isEmpty());
    config.set("global-speed-boost.boost-factor", 0.9);
    controller(null).update(cart.entity, true);
    assertEquals(0.22, cart.velocity.getX(), 1e-12);
    assertEquals(1, warnings.size());
  }

  @Test
  void bothModesBoostOnlyOnEnergizedPoweredRails() {
    for (String mode : List.of("global-speed-boost", "metro-speed-boost")) {
      config.set("global-speed-boost.enabled", false);
      config.set("metro-speed-boost.enabled", false);
      enable(mode, 0.8, 1.2);
      SpeedController controller = controller(location -> true);
      for (Material material : List.of(Material.RAIL, Material.POWERED_RAIL,
          Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL, Material.AIR)) {
        for (boolean powered : List.of(false, true)) {
          cart.rail.material = material;
          cart.rail.powered = powered;
          cart.velocity = new Vector(0.2, 0.1, 0);
          cart.velocityWrites = 0;
          controller.update(cart.entity, true);
          boolean boosts = material == Material.POWERED_RAIL && powered;
          assertEquals(boosts ? 0.24 : 0.2, cart.velocity.getX(), 1e-12,
              mode + ": " + material + ", powered=" + powered);
          assertEquals(boosts ? 1 : 0, cart.velocityWrites);
          assertEquals(0.8, cart.maxSpeed);
          assertEquals(0.1, cart.velocity.getY());
        }
      }
    }
  }

  @Test
  void slopeRailOneBlockBelowCartCanBoost() {
    enable("global-speed-boost", 0.8, 1.2);
    cart.rail.material = Material.AIR;
    cart.below.material = Material.POWERED_RAIL;
    cart.below.powered = true;
    cart.velocity = new Vector(0.2, 0.2, 0);
    controller(null).update(cart.entity, true);
    assertEquals(0.24, cart.velocity.getX(), 1e-12);
    assertEquals(0.2, cart.velocity.getY());
    assertEquals(1, cart.rail.belowReads);
  }

  @Test
  void firstRailPreventsBoostFromUnrelatedPoweredRailBelow() {
    enable("global-speed-boost", 0.8, 1.2);
    cart.below.material = Material.POWERED_RAIL;
    cart.below.powered = true;
    SpeedController controller = controller(null);
    for (Material material : List.of(Material.RAIL, Material.POWERED_RAIL,
        Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL)) {
      cart.rail.material = material;
      cart.rail.powered = false;
      cart.velocity = new Vector(0.2, 0, 0);
      controller.update(cart.entity, true);
      assertEquals(0, cart.velocityWrites);
      assertEquals(0, cart.rail.belowReads);
    }
  }

  @Test
  void onlyOneBlockBelowIsChecked() {
    enable("global-speed-boost", 0.8, 1.2);
    cart.rail.material = Material.AIR;
    cart.velocity = new Vector(0.2, 0, 0);
    controller(null).update(cart.entity, true);
    assertEquals(0, cart.velocityWrites);
    assertEquals(1, cart.rail.belowReads);
    assertEquals(0, cart.below.belowReads);
  }

  @Test
  void leavingPoweredRailStopsBoostWithoutChangingCapOrMomentum() {
    enable("global-speed-boost", 0.8, 1.2);
    SpeedController controller = controller(null);
    cart.velocity = new Vector(0.5, 0, 0);
    controller.update(cart.entity, true);
    assertEquals(0.6, cart.velocity.getX(), 1e-12);
    cart.rail.material = Material.RAIL;
    controller.update(cart.entity, true);
    cart.rail.material = Material.POWERED_RAIL;
    cart.rail.powered = false;
    controller.update(cart.entity, true);
    assertEquals(0.8, cart.maxSpeed);
    assertEquals(0.6, cart.velocity.getX(), 1e-12);
    assertEquals(1, cart.velocityWrites);
    // The fake has no physics; an unchanged vector proves the plugin leaves
    // natural friction and braking to the server on these sections.
  }

  @Test
  void ordinaryRailDoesNotClampStoredVelocityToMovementCap() {
    enable("global-speed-boost", 0.8, 1.2);
    cart.rail.material = Material.RAIL;
    cart.velocity = new Vector(1.0, 0.2, 0);
    controller(null).update(cart.entity, true);
    assertEquals(0.8, cart.maxSpeed);
    assertEquals(new Vector(1.0, 0.2, 0), cart.velocity);
    assertEquals(0, cart.velocityWrites);
  }

  // A small interface fake avoids booting a server for speed-policy tests.
  private static final class Cart {
    Vector velocity = new Vector();
    double maxSpeed = -1;
    boolean occupied = true;
    int calls;
    int velocityWrites;
    final RailBlock below = new RailBlock(Material.AIR, false, null);
    final RailBlock rail = new RailBlock(Material.POWERED_RAIL, true, below);
    final World world = (World) Proxy.newProxyInstance(
        World.class.getClassLoader(), new Class<?>[] {World.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getBlockAt")) return rail.block;
          throw new AssertionError("Unexpected world call: " + method.getName());
        });
    final Location location = new Location(world, 0.5, 64.1, 0.5);
    final Minecart entity = (Minecart) Proxy.newProxyInstance(
        Minecart.class.getClassLoader(), new Class<?>[] {Minecart.class},
        (proxy, method, args) -> {
          calls++;
          return switch (method.getName()) {
            case "getPassengers" -> occupied ? List.of(proxy) : List.of();
            case "getLocation" -> location.clone();
            case "getVelocity" -> velocity.clone();
            case "setMaxSpeed" -> { maxSpeed = (double) args[0]; yield null; }
            case "setVelocity" -> { velocity = ((Vector) args[0]).clone(); velocityWrites++; yield null; }
            default -> throw new AssertionError("Unexpected minecart call: " + method.getName());
          };
        });
  }

  private static final class RailBlock {
    Material material;
    boolean powered;
    int belowReads;
    final Block block;

    RailBlock(Material material, boolean powered, RailBlock below) {
      this.material = material;
      this.powered = powered;
      Powerable data = (Powerable) Proxy.newProxyInstance(
          Powerable.class.getClassLoader(), new Class<?>[] {Powerable.class},
          (proxy, method, args) -> {
            if (method.getName().equals("isPowered")) return this.powered;
            throw new AssertionError("Unexpected block-data call: " + method.getName());
          });
      block = (Block) Proxy.newProxyInstance(
          Block.class.getClassLoader(), new Class<?>[] {Block.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "getType" -> this.material;
            case "getBlockData" -> data;
            case "getRelative" -> {
              assertEquals(BlockFace.DOWN, args[0]);
              belowReads++;
              if (below == null) throw new AssertionError("Looked more than one block below cart");
              yield below.block;
            }
            default -> throw new AssertionError("Unexpected block call: " + method.getName());
          });
    }
  }
}
