package com.thisislogan.railrider;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.HashMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class RailRider extends JavaPlugin implements Listener {

  private final HashMap<UUID, Minecart> playerMinecarts = new HashMap<>();

  @Override
  public void onEnable() {
    saveDefaultConfig();
    Bukkit.getPluginManager().registerEvents(this, this);
    getLogger().info("RailRider enabled!");
  }

  @Override
  public void onDisable() {
    for (Minecart cart : playerMinecarts.values()) {
      if (cart != null && !cart.isDead()) {
        cart.remove();
      }
    }
    playerMinecarts.clear();
    getLogger().info("RailRider disabled.");
  }

  public boolean isInMetroRegion(Location location) {
    if (location == null || location.getWorld() == null) return false;

    RegionManager regionManager =
        WorldGuard.getInstance()
            .getPlatform()
            .getRegionContainer()
            .get(BukkitAdapter.adapt(location.getWorld()));

    if (regionManager == null) return false;

    BlockVector3 position =
        BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());

    ApplicableRegionSet regions = regionManager.getApplicableRegions(position);

    for (ProtectedRegion region : regions) {
      if (region.getId().toLowerCase().startsWith("metro")) {
        return true;
      }
    }

    return false;
  }

  @EventHandler
  public void onRailRightClick(PlayerInteractEvent event) {
    if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
    if (!event.hasBlock() || event.getClickedBlock() == null) return;

    Player player = event.getPlayer();

    // Only with empty hand
    if (player.getInventory().getItemInMainHand().getType() != Material.AIR) return;

    // Only if not already inside a vehicle
    if (player.isInsideVehicle()) return;

    Block block = event.getClickedBlock();

    // Check if it's a valid rail type
    switch (block.getType()) {
      case RAIL:
      case POWERED_RAIL:
      case DETECTOR_RAIL:
      case ACTIVATOR_RAIL:
        break;
      default:
        return;
    }

    UUID uuid = player.getUniqueId();

    // Prevent duplicate carts
    if (playerMinecarts.containsKey(uuid)) return;

    // Spawn cart and ride
    Minecart cart = block.getWorld().spawn(block.getLocation().add(0.5, 0.1, 0.5), Minecart.class);
    cart.setVelocity(new Vector(0, 0, 0));
    cart.addPassenger(player);

    playerMinecarts.put(uuid, cart);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();

    Minecart cart = playerMinecarts.remove(uuid);
    if (cart != null && !cart.isDead()) {
      cart.remove();
    }
  }

  @EventHandler
  public void onVehicleUpdate(VehicleUpdateEvent event) {
    if (!(event.getVehicle() instanceof Minecart)) return;
    Minecart cart = (Minecart) event.getVehicle();

    if (cart.getPassengers().isEmpty()) return;

    if (isInMetroRegion(cart.getLocation())) {
      if (!getConfig().getBoolean("metro-speed-boost.enabled", true)) return;

      double maxSpeed = getConfig().getDouble("metro-speed-boost.max-speed", 0.6);
      double boostFactor = getConfig().getDouble("metro-speed-boost.boost-factor", 1.1);

      cart.setMaxSpeed(maxSpeed);

      Vector velocity = cart.getVelocity();
      if (velocity.length() > 0) {
        cart.setVelocity(velocity.multiply(boostFactor));
      }
    } else {
      cart.setMaxSpeed(0.4); // reset outside metro
    }
  }

  @EventHandler
  public void onVehicleExit(VehicleExitEvent event) {
    if (!(event.getExited() instanceof Player)) return;

    Player player = (Player) event.getExited();
    UUID uuid = player.getUniqueId();

    Minecart cart = playerMinecarts.remove(uuid);
    if (cart != null && !cart.isDead()) {
      // Delay cart removal by 1 tick to avoid sinking into the floor
      Bukkit.getScheduler().runTaskLater(this, cart::remove, 1L);
    }
  }
}
