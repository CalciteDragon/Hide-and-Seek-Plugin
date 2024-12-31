package com.calcite.commands;

import java.io.FileWriter;
import java.io.IOException;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.calcite.Main;

public class PlacePowerup implements CommandExecutor {
    private Main plugin;

    //command '/placepowerup' places a powerup spawner at the player's location

    public PlacePowerup(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command arg1, @NotNull String arg2, @NotNull String[] arg3) {
        if (!(sender instanceof Player)) {
            return false;
        }
        Player player = (Player) sender;
        Location location = player.getLocation();
        location = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        World world = player.getWorld();
        // Add location to poweruplocations.txt
        String powerupLocation = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        try {
            FileWriter writer = new FileWriter("plugins/hide_and_seek_data/poweruplocations.txt", true);
            writer.write(powerupLocation + "\n");
            writer.close();
            world.getBlockAt(location).setType(Material.DIAMOND_BLOCK);
            plugin.addPowerupLocation(location);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
}
