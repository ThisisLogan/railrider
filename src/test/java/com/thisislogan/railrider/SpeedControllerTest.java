package com.thisislogan.railrider;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
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

  private SpeedController controller() {
    return new SpeedController(config, warnings::add);
  }

  private void configure(double blocksPerSecond, double boostFactor) {
    config.set("max-speed-blocks-per-second", blocksPerSecond);
    config.set("powered-rail-boost-factor", boostFactor);
  }

  @Test
  void missingSettingsUseVanilla() {
    cart.velocity = new Vector(0.2, 0, 0);
    controller().update(cart.entity, true);
    assertEquals(0.4, cart.maxSpeed);
    assertEquals(0, cart.velocityWrites);
    assertTrue(warnings.isEmpty());
  }

  @Test
  void ordinaryCartsAreNotReadOrChanged() {
    configure(16, 2);
    controller().update(cart.entity, false);
    assertEquals(0, cart.calls);
  }

  @Test
  void emptyTrackedCartsAreNotChanged() {
    configure(16, 2);
    cart.occupied = false;
    controller().update(cart.entity, true);
    assertEquals(-1, cart.maxSpeed);
    assertEquals(0, cart.velocityWrites);
  }

  @Test
  void boostPreservesHorizontalDirectionAndVerticalVelocity() {
    configure(12, 3);
    cart.velocity = new Vector(-0.3, -0.2, 0.4);
    controller().update(cart.entity, true);
    assertEquals(-0.36, cart.velocity.getX(), 1e-12);
    assertEquals(0.48, cart.velocity.getZ(), 1e-12);
    assertEquals(-0.2, cart.velocity.getY());
    assertEquals(0.6, Math.hypot(cart.velocity.getX(), cart.velocity.getZ()), 1e-12);
  }

  @Test
  void movingCartAcceleratesBelowCap() {
    configure(12, 1.5);
    cart.velocity = new Vector(0.2, 0, 0);
    controller().update(cart.entity, true);
    assertEquals(0.23, cart.velocity.getX(), 1e-12);
  }

  @Test
  void stationaryAndVerticalOnlyCartsAreNotPropelled() {
    configure(12, 2);
    SpeedController controller = controller();
    controller.update(cart.entity, true);
    cart.velocity = new Vector(0, 0.2, 0);
    controller.update(cart.entity, true);
    assertEquals(0, cart.velocityWrites);
    assertEquals(new Vector(0, 0.2, 0), cart.velocity);
  }

  @Test
  void invalidNumbersUseVanillaAndWarnOncePerSetting() {
    for (Object invalid : List.of(-1, 0, Double.NaN, Double.POSITIVE_INFINITY, "fast")) {
      warnings.clear();
      config.set("max-speed-blocks-per-second", invalid);
      config.set("powered-rail-boost-factor", invalid);
      cart.velocity = new Vector(0.2, 0, 0);
      SpeedController controller = controller();
      controller.update(cart.entity, true);
      assertEquals(0.4, cart.maxSpeed);
      assertEquals(0.2, cart.velocity.getX());
      controller.update(cart.entity, true);
      assertEquals(2, warnings.size());
    }
  }

  @Test
  void factorOneIsAllowedButFactorsBelowOneAreRejected() {
    configure(18, 1);
    cart.velocity = new Vector(0.2, 0, 0);
    controller().update(cart.entity, true);
    assertEquals(0.2, cart.velocity.getX());
    assertEquals(0, cart.velocityWrites);
    assertTrue(warnings.isEmpty());
    config.set("powered-rail-boost-factor", 0.9);
    controller().update(cart.entity, true);
    assertEquals(0.2, cart.velocity.getX());
    assertEquals(0, cart.velocityWrites);
    assertEquals(1, warnings.size());
  }

  @Test
  void boostsOnlyOnEnergizedPoweredRails() {
    configure(16, 1.5);
    SpeedController controller = controller();
    for (Material material : List.of(Material.RAIL, Material.POWERED_RAIL,
        Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL, Material.AIR)) {
      for (boolean powered : List.of(false, true)) {
        cart.rail.material = material;
        cart.rail.powered = powered;
        cart.velocity = new Vector(0.2, 0.1, 0);
        cart.velocityWrites = 0;
        controller.update(cart.entity, true);
        boolean boosts = material == Material.POWERED_RAIL && powered;
        assertEquals(boosts ? 0.23 : 0.2, cart.velocity.getX(), 1e-12,
            material + ", powered=" + powered);
        assertEquals(boosts ? 1 : 0, cart.velocityWrites);
        assertEquals(0.8, cart.maxSpeed);
        assertEquals(0.1, cart.velocity.getY());
      }
    }
  }

  @Test
  void slopeRailOneBlockBelowCartCanBoost() {
    configure(16, 1.5);
    cart.rail.material = Material.AIR;
    cart.below.material = Material.POWERED_RAIL;
    cart.below.powered = true;
    cart.velocity = new Vector(0.2, 0.2, 0);
    controller().update(cart.entity, true);
    assertEquals(0.23, cart.velocity.getX(), 1e-12);
    assertEquals(0.2, cart.velocity.getY());
    assertEquals(1, cart.rail.belowReads);
  }

  @Test
  void firstRailPreventsBoostFromUnrelatedPoweredRailBelow() {
    configure(16, 1.5);
    cart.below.material = Material.POWERED_RAIL;
    cart.below.powered = true;
    SpeedController controller = controller();
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
    configure(16, 1.5);
    cart.rail.material = Material.AIR;
    cart.velocity = new Vector(0.2, 0, 0);
    controller().update(cart.entity, true);
    assertEquals(0, cart.velocityWrites);
    assertEquals(1, cart.rail.belowReads);
    assertEquals(0, cart.below.belowReads);
  }

  @Test
  void leavingPoweredRailStopsBoostWithoutChangingCapOrMomentum() {
    configure(16, 2);
    SpeedController controller = controller();
    cart.velocity = new Vector(0.5, 0, 0);
    controller.update(cart.entity, true);
    assertEquals(0.56, cart.velocity.getX(), 1e-12);
    cart.rail.material = Material.RAIL;
    controller.update(cart.entity, true);
    cart.rail.material = Material.POWERED_RAIL;
    cart.rail.powered = false;
    controller.update(cart.entity, true);
    assertEquals(0.8, cart.maxSpeed);
    assertEquals(0.56, cart.velocity.getX(), 1e-12);
    assertEquals(1, cart.velocityWrites);
    // The fake has no physics; an unchanged vector proves the plugin leaves
    // natural friction and braking to the server on these sections.
  }

  @Test
  void ordinaryRailDoesNotClampStoredVelocityToMovementCap() {
    configure(16, 1.5);
    cart.rail.material = Material.RAIL;
    cart.velocity = new Vector(1.0, 0.2, 0);
    controller().update(cart.entity, true);
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
