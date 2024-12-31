package com.calcite.commands;

import java.io.*;
import java.util.List;
import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.calcite.Main;

public class RemovePowerup implements CommandExecutor {
    private Main plugin;

    //command '/removepowerup' removes a powerup spawner at the player's location (minus y by 1 so you can stand on top of it)

    public RemovePowerup(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }
        Player player = (Player) sender;
        Location playerLocation = player.getLocation();
        Location targetLocation = new Location(playerLocation.getWorld(), playerLocation.getBlockX(), playerLocation.getBlockY() - 1, playerLocation.getBlockZ());
        World world = player.getWorld();

        List<Location> powerupLocations = plugin.getPowerupLocations();
        boolean isLocationRemoved = false;

        Iterator<Location> iterator = powerupLocations.iterator();
        while (iterator.hasNext()) {
            Location loc = iterator.next();
            if (loc.equals(targetLocation)) {
                world.getBlockAt(targetLocation).setType(Material.AIR);
                iterator.remove();
                isLocationRemoved = true;
            }
        }

        if (isLocationRemoved) {
            File file = new File("plugins/hide_and_seek_data/poweruplocations.txt");

            try {
                // Delete the file to ensure no leftover content
                if (file.exists() && !file.delete()) {
                    throw new IOException("Failed to delete existing file.");
                }

                // Create a new file and write the updated locations
                try (FileWriter writer = new FileWriter(file, false)) {
                    for (Location powerupLocation : powerupLocations) {
                        writer.write(String.format("%d,%d,%d%n", powerupLocation.getBlockX(), powerupLocation.getBlockY(), powerupLocation.getBlockZ()));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        return false;
    }
}
