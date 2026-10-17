package com.thisislogan.railrider;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.bukkit.Location;
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
    assertEquals(0.4, cart.velocity.getX(), 1e-12);
    assertEquals(0.2, cart.velocity.getY());
  }

  @Test
  void missingTogglesAreDisabledAndDoNotQueryRegions() {
    controller(location -> { throw new AssertionError("Metro mode is disabled"); })
        .update(cart.entity, true);
    assertEquals(0.4, cart.maxSpeed);
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
    assertTrue(warnings.isEmpty());
    config.set("global-speed-boost.boost-factor", 0.9);
    controller(null).update(cart.entity, true);
    assertEquals(0.22, cart.velocity.getX(), 1e-12);
    assertEquals(1, warnings.size());
  }

  // A small interface fake avoids booting a server for speed-policy tests.
  private static final class Cart {
    Vector velocity = new Vector();
    double maxSpeed = -1;
    boolean occupied = true;
    int calls;
    int velocityWrites;
    final Minecart entity = (Minecart) Proxy.newProxyInstance(
        Minecart.class.getClassLoader(), new Class<?>[] {Minecart.class},
        (proxy, method, args) -> {
          calls++;
          return switch (method.getName()) {
            case "getPassengers" -> occupied ? List.of(proxy) : List.of();
            case "getLocation" -> null;
            case "getVelocity" -> velocity.clone();
            case "setMaxSpeed" -> { maxSpeed = (double) args[0]; yield null; }
            case "setVelocity" -> { velocity = ((Vector) args[0]).clone(); velocityWrites++; yield null; }
            default -> throw new AssertionError("Unexpected minecart call: " + method.getName());
          };
        });
  }
}
