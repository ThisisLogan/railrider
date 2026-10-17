package com.thisislogan.railrider;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Locale;
import java.util.function.Predicate;
import org.bukkit.Location;

final class WorldGuardRegions implements Predicate<Location> {
  @Override
  public boolean test(Location location) {
    if (location == null || location.getWorld() == null) return false;
    RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
        .get(BukkitAdapter.adapt(location.getWorld()));
    if (manager == null) return false;
    BlockVector3 position = BlockVector3.at(
        location.getBlockX(), location.getBlockY(), location.getBlockZ());
    for (ProtectedRegion region : manager.getApplicableRegions(position)) {
      if (region.getId().toLowerCase(Locale.ROOT).startsWith("metro")) return true;
    }
    return false;
  }
}
