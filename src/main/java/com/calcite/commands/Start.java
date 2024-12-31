package com.calcite.commands;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import com.calcite.Main;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatColor;

public class Start implements CommandExecutor {
    private Main plugin;

    //command '/start' starts the game

    public Start(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender arg0, @NotNull Command arg1, @NotNull String arg2, @NotNull String[] arg3) {

        Collection<? extends Player> onlinePlayers;
        Player seeker;

        //select random player to be seeker:
        {
            onlinePlayers = Bukkit.getServer().getOnlinePlayers();
            int players = onlinePlayers.size();
            Random Random = new Random();
            int randomPlayer = Random.nextInt(players) + 1;
            Iterator<? extends Player> itr = onlinePlayers.iterator();
            seeker = itr.next();
            for(int i = 1; i < randomPlayer; i++) {
                if(itr.hasNext()) {
                    seeker = itr.next();
                }
            }
        }
        
        //join the correct teams and close the starting cage:
        {
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "team join hiders @a");
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "team join seeker " + seeker.getName());
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "fill " + plugin.SPAWN_CAGE_GLASS + " minecraft:tinted_glass");
        }
        
        //more game initializing (inventory, titles, health, etc):
        for(Player player : onlinePlayers) {
            player.setHealth(20);
            player.getInventory().clear();
            if(player.equals(seeker)) {
                player.showTitle(Title.title(Component.text(ChatColor.RED + "" + ChatColor.BOLD + "YOU ARE THE SEEKER"), Component.text(ChatColor.RED + "Hiders have 30 seconds to hide!")));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 30 * 20, 0));
                player.teleport(plugin.SEEK_SPAWN);
            }
            else {
                player.teleport(plugin.HIDE_SPAWN);
                player.showTitle(Title.title(Component.text(ChatColor.GREEN + "" + ChatColor.BOLD + "YOU ARE A HIDER"), Component.text(ChatColor.GREEN + "You have 30 seconds to hide from the seeker!")));
            }
        }
        
        //give the seeker their sword
        {
            ItemStack seekerSword = new ItemStack(Material.NETHERITE_SWORD);
            ItemMeta seekerSwordMeta = seekerSword.getItemMeta();
            seekerSwordMeta.displayName(Component.text(ChatColor.RED + "" + ChatColor.BOLD + "SEEKER WEAPON"));
            seekerSword.setItemMeta(seekerSwordMeta);
            seeker.getInventory().addItem(seekerSword);
        }
        
        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "execute as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 2 1");

        //begin hide phase
        plugin.hidePhase(seeker);

        return true;
    }

}
