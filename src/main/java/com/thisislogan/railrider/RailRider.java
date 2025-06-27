package com.thisislogan.railrider;

import java.util.HashMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class RailRider extends JavaPlugin implements Listener {

  private final HashMap<UUID, Minecart> playerMinecarts = new HashMap<>();
  private final HashMap<UUID, Long> cooldowns = new HashMap<>();

  private final int cooldownSeconds = 5; // Cooldown duration

  @Override
  public void onEnable() {
    Bukkit.getPluginManager().registerEvents(this, this);
    getLogger().info("RailRider enabled!");
  }

  @Override
  public void onDisable() {
    getLogger().info("RailRider disabled.");
  }

  @EventHandler
  public void onRailRightClick(PlayerInteractEvent event) {
    if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
    if (!event.hasBlock() || event.getClickedBlock() == null) return;

    Player player = event.getPlayer();

    // Only trigger with empty main hand
    if (player.getInventory().getItemInMainHand().getType() != Material.AIR) return;

    // Only trigger when clicking a rail
    Block block = event.getClickedBlock();
    if (!block.getType().toString().contains("RAIL")) return;

    // Check permission
    if (!player.hasPermission("railrider.use")) {
      player.sendMessage("§cYou do not have permission to use RailRider.");
      return;
    }

    UUID uuid = player.getUniqueId();
    long now = System.currentTimeMillis();

    // Check cooldown
    if (cooldowns.containsKey(uuid)) {
      long lastUsed = cooldowns.get(uuid);
      long remaining = (lastUsed + (cooldownSeconds * 1000L)) - now;
      if (remaining > 0) {
        player.sendMessage("§ePlease wait " + (remaining / 1000.0) + "s before using this again.");
        return;
      }
    }

    // Prevent duplicate carts
    if (playerMinecarts.containsKey(uuid)) return;

    // Spawn cart and ride
    Minecart cart = block.getWorld().spawn(block.getLocation().add(0.5, 0.1, 0.5), Minecart.class);
    cart.setVelocity(new Vector(0, 0, 0));
    cart.addPassenger(player);

    playerMinecarts.put(uuid, cart);
    cooldowns.put(uuid, now);
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
