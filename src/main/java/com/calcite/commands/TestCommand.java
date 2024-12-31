package com.calcite.commands;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import com.calcite.Main;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;

//this command is for debugging purposes
public class TestCommand implements CommandExecutor {

    private Main plugin;

    public TestCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender arg0, @NotNull Command arg1, @NotNull String arg2, @NotNull String[] arg3) {
        /* plugin.getPowerupLocations().forEach((location) -> {
            Location spawnLocation = new Location(location.getWorld(), location.getX() + 0.5, location.getY() + 1.1, location.getZ() + 0.5);
            ItemStack powerup = new ItemStack(Material.GOLDEN_APPLE);
            ItemMeta powerupMeta = powerup.getItemMeta();

            powerup = new ItemStack(Material.GOLDEN_APPLE);
                powerupMeta = powerup.getItemMeta();
                powerupMeta.setCustomModelData(4);
                powerupMeta.displayName(Component.text(ChatColor.RED + "Insta Heal"));
                powerup.setItemMeta(powerupMeta);

            spawnLocation.getWorld().dropItem(spawnLocation, powerup).setVelocity(new Vector(0, 0, 0));
        }); */
        plugin.spawnRandomPowerup(new Location(Bukkit.getWorld("world"), -8.5, 67, -67.5));
        return true;
    }
}
