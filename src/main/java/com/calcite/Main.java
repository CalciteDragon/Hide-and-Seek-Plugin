package com.calcite;

import java.io.File;
import java.nio.file.Paths;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.calcite.commands.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;

public class Main extends JavaPlugin implements Listener {
    private List<Player> seekers;
    private List<Player> intermediates;
    private List<Player> hiders;
    private boolean hidePhase;
    private boolean seekPhase;
    private int time;
    private List<Location> powerupLocations;
    private HashMap<Player, Location> wormholes;
    private HashMap<Player, Integer> powerupCooldowns;

    public final Location LOBBY_SPAWN = new Location(Bukkit.getWorld("world"), -74, 66, -29); //lobby spawn coordinates
    public final Location HIDE_SPAWN = new Location(Bukkit.getWorld("world"), -5, 67, -89); //coordinates for hiders to spawn when game starts (or when someone dies in game)
    public final Location SEEK_SPAWN = new Location(Bukkit.getWorld("world"), -5, 71, -104); //coordinates for seekers to spawn when game starts (inside seeker cage)
    public final String SPAWN_CAGE_GLASS = "-6 71 -102 -4 73 -102"; //coordinates of the corners of the spawn cage glass (to open the seeker once the seek phase starts)

    @Override
    public void onEnable() {

        seekers = new ArrayList<>();
        intermediates = new ArrayList<>();
        hiders = new ArrayList<>();
        hidePhase = false;
        seekPhase = false;
        time = 0;
        powerupLocations = new ArrayList<>();
        wormholes = new HashMap<>();
        powerupCooldowns = new HashMap<>();

        getLogger().info("Hide and seek plugin has been enabled!");
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();

        //initialize scoreboards and teams:
        {
            Bukkit.dispatchCommand(console, "scoreboard objectives add -____Timer____- dummy");
            Bukkit.dispatchCommand(console, "scoreboard objectives setdisplay sidebar -____Timer____-");
            Bukkit.dispatchCommand(console, "scoreboard players set Remaining -____Timer____- 0");
            Bukkit.dispatchCommand(console, "team add hiders");
            Bukkit.dispatchCommand(console, "team modify hiders nametagVisibility hideForOtherTeams");
            Bukkit.dispatchCommand(console, "team add seeker");
            Bukkit.dispatchCommand(console, "team modify seeker color red");
            Bukkit.dispatchCommand(console, "team modify hiders color green");
            Bukkit.dispatchCommand(console, "team add intermediate");
            Bukkit.dispatchCommand(console, "team modify intermediate color yellow");
            Bukkit.dispatchCommand(console, "team modify intermediate nametagVisibility never");
            Bukkit.dispatchCommand(console, "gamerule keepInventory true");
        }

        getCommand("start").setExecutor(new Start(this));
        getCommand("placepowerup").setExecutor(new PlacePowerup(this));
        getCommand("removepowerup").setExecutor(new RemovePowerup(this));
        getCommand("endgame").setExecutor(new EndGame(this));
        getCommand("testcommand").setExecutor(new TestCommand(this));
        Bukkit.getPluginManager().registerEvents(this, this);
        readPowerupLocations();

        //run every second:
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                //no food drain:
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.setFoodLevel(20);
                    player.setSaturation(0);
                }
                //countdown for hiding phase:
                if(hidePhase) {
                    time--;
                    //start seek phase once hiding time is up
                    if(time <= 0) {
                        hidePhase = false;
                        seekPhase = true;
                        time = 600;
                        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "fill " + SPAWN_CAGE_GLASS + " minecraft:air");
                        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "execute as @a at @s run playsound minecraft:entity.generic.explode master @s ~ ~ ~ 2 1");
                        Bukkit.broadcast(Component.text("The seeker has been released!").color(TextColor.color(0xFF0000)).decorate(TextDecoration.BOLD));
                    }
                    Bukkit.dispatchCommand(console, "scoreboard players set Remaining -____Timer____- " + time);
                }
                //countdown for seeking phase:
                if(seekPhase) {
                    time--;
                    if(time <= 0) {
                        Bukkit.broadcast(Component.text("Hiders Win!").color(TextColor.color(0x0000FF)).decorate(TextDecoration.BOLD));
                        endGame();
                    }
                    Bukkit.dispatchCommand(console, "scoreboard players set Remaining -____Timer____- " + time);
                }
                //randomly spawn powerups:
                if(seekPhase || hidePhase) {
                    if(powerupLocations.size() > 0) {
                        int random = (int)(Math.random() * 500);
                        if(random < 30) {
                            Location powerupLocation = powerupLocations.get((int)(Math.random() * powerupLocations.size()));
                            spawnRandomPowerup(new Location(powerupLocation.getWorld(), powerupLocation.getX() + 0.5, powerupLocation.getY() + 1.1, powerupLocation.getZ() + 0.5));
                        }
                    }
                }
                
            }
        }, 0, 20);
        //run every tick:
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                //cooldown for powerups
                for(Player player : powerupCooldowns.keySet()) {
                    if(powerupCooldowns.get(player) > 0) {
                        powerupCooldowns.put(player, powerupCooldowns.get(player) - 1);
                    }
                    else {
                        powerupCooldowns.remove(player);
                    }
                }
                //particles for powerup spawners:
                for(Location loc : powerupLocations) {
                    Location spawnLocation = new Location(loc.getWorld(), loc.getX() + 0.5, loc.getY() + 1.1, loc.getZ() + 0.5);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, spawnLocation, 1, 0, 0, 0, 0.05);
                }
                //particles for wormholes:
                for(Location loc : wormholes.values()) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, loc, 5, 0, 0, 0, 0.05);
                }
            }
        }, 0, 1);
    }

    @Override
    public void onDisable() {
    }

    //start the hide phase
    public void hidePhase(Player seeker) {
        seekers.clear();;
        hiders.clear();;
        intermediates.clear();
        seekers.add(seeker);
        for(Player p : Bukkit.getOnlinePlayers()) {
            if(!seekers.contains(p)) {
                hiders.add(p);
            }
        }
        hidePhase = true;
        seekPhase = false;
        time = 30;
        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "kill @e[type=item]");
    }

    public void endGame() {
        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "team empty hiders");
        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "team empty seeker");
        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "scoreboard players set Remaining -____Timer____- 0");
        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "effect clear @a");
        for(Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(LOBBY_SPAWN);
        }
        hidePhase = false;
        seekPhase = false;
        time = 0;
    }

    public void spawnRandomPowerup(Location location) {
        int random = (int)(Math.random() * 7);
        ItemStack powerup = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta powerupMeta = powerup.getItemMeta();
        switch(random) {
            case 0:
                powerup = new ItemStack(Material.BLAZE_ROD);
                powerupMeta = powerup.getItemMeta();
                powerupMeta.setEnchantmentGlintOverride(true);
                powerupMeta.setCustomModelData(3);
                powerupMeta.displayName(Component.text(ChatColor.YELLOW + "Reveal all hiders"));
                powerup.setItemMeta(powerupMeta);
                break;
            case 1:
                powerup = new ItemStack(Material.GOLDEN_SWORD);
                powerupMeta = powerup.getItemMeta();
                powerupMeta.displayName(Component.text(ChatColor.GOLD + "KB Sword"));
                powerupMeta.addEnchant(Enchantment.KNOCKBACK, 5, true);
                powerupMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                powerup.setItemMeta(powerupMeta);
                powerup.setDurability((short) 32);
                break;
            case 2:
                powerup = new ItemStack(Material.GOLDEN_APPLE);
                powerupMeta = powerup.getItemMeta();
                powerupMeta.setCustomModelData(4);
                powerupMeta.displayName(Component.text(ChatColor.RED + "Insta Heal"));
                powerup.setItemMeta(powerupMeta);
                break;
            case 3:
                powerup = new ItemStack(Material.BOW);
                location.getWorld().dropItem(location, new ItemStack(Material.ARROW, 10)).setVelocity(new Vector(0, 0, 0));
                break; 
            case 4:
                /* powerup = new ItemStack(Material.SPLASH_POTION);
                powerupMeta = powerup.getItemMeta();
                PotionMeta potionMeta = (PotionMeta)powerupMeta;
                PotionEffect invisibilityEffect = new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false);
                potionMeta.addCustomEffect(invisibilityEffect, true);
                potionMeta.displayName(Component.text(ChatColor.DARK_PURPLE + "Invisibility Potion"));
                powerup.setItemMeta(potionMeta); */
                powerup = new ItemStack(Material.GLASS);
                powerupMeta = powerup.getItemMeta();
                powerupMeta.setEnchantmentGlintOverride(true);
                powerupMeta.setCustomModelData(1);
                powerupMeta.displayName(Component.text(ChatColor.DARK_PURPLE + "Invisibility Powerup"));
                powerupMeta.lore(List.of(Component.text(ChatColor.LIGHT_PURPLE + "Right click to become invisible for 10 seconds")));
                powerup.setItemMeta(powerupMeta);
                break;
            case 5:
                powerup = new ItemStack(Material.WOODEN_SWORD);
                powerupMeta = powerup.getItemMeta();
                powerupMeta.displayName(Component.text(ChatColor.RED + "Fire Sword"));
                powerupMeta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
                powerupMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                powerup.setItemMeta(powerupMeta);
                powerup.setDurability((short) 59);
                break;
            case 6:
                powerup = new ItemStack(Material.END_PORTAL_FRAME);
                powerupMeta = powerup.getItemMeta();
                powerupMeta.setEnchantmentGlintOverride(true);
                powerupMeta.setCustomModelData(2);
                powerupMeta.displayName(Component.text(ChatColor.DARK_PURPLE + "Wormhole powerup"));
                powerupMeta.lore(List.of(Component.text(ChatColor.LIGHT_PURPLE + "Right click to set a wormhole"), Component.text(ChatColor.LIGHT_PURPLE + "Right click again to teleport to the wormhole")));
                powerup.setItemMeta(powerupMeta);
            default:
                break;
        }
        location.getWorld().dropItem(location, powerup).setVelocity(new Vector(0, 0, 0));
    }

    //add location to poweruplocations list
    public void addPowerupLocation(Location location) {
        powerupLocations.add(location);
    }

    //get poweruplocations list
    public List<Location> getPowerupLocations() {
        return powerupLocations;
    }

    public void removePowerupLocation(Location location) {
        powerupLocations.remove(location);
    }

    //read poweruplocations from file
    public void readPowerupLocations() {
        if(Files.exists(Paths.get("plugins/hide_and_seek_data")) == false) {
            try {
                Files.createDirectory(Paths.get("plugins/hide_and_seek_data"));
            } catch (Exception e) {
                getLogger().info("Error creating hide_and_seek_data directory");
                e.printStackTrace();
            }
        }
        File f = new File("plugins/hide_and_seek_data/poweruplocations.txt");
        if(!f.exists()) {
            try {
                f.createNewFile();
            } catch (Exception e) {
                getLogger().info("Error creating poweruplocations.txt");
                e.printStackTrace();
            }
        }
        
        try {
            FileReader reader = new FileReader("plugins/hide_and_seek_data/poweruplocations.txt");
            int i = 0;
            String location = "";
            while((i = reader.read()) != -1) {
                if(i == 10) {
                    String[] coords = location.split(",");
                    Location powerupLocation = new Location(Bukkit.getWorld("world"), Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
                    powerupLocations.add(powerupLocation);
                    location = "";
                }
                else {
                    location += (char)i;
                }
            }
            reader.close();
        } catch (Exception e) {
            getLogger().info("Error reading poweruplocations.txt");
            e.printStackTrace();
        }
    }

    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        //teleport to spawn on join if game is not in progress
        if(!seekPhase && !hidePhase) {
            player.teleport(LOBBY_SPAWN);
        }
        //prevent relogging from breaking the plugin
        for(Player p : hiders) {
            if(p.getName().equals(player.getName())) {
                hiders.remove(p);
                hiders.add(player);
            }
        }
        for(Player p : seekers) {
            if(p.getName().equals(player.getName())) {
                seekers.remove(p);
                seekers.add(player);
            }
        }
        for(Player p : intermediates) {
            if(p.getName().equals(player.getName())) {
                intermediates.remove(p);
                intermediates.add(player);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if(!seekPhase) {
            return;
        }
        Player player = event.getPlayer();
        if(hiders.contains(player)) {
            hiders.remove(player);
            intermediates.add(player);
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "team join intermediate " + player.getName());
            player.sendMessage(Component.text("You are intermediate! Kill a hider to join back the hider team, if you die again you become a seeker!").color(TextColor.color(0x88FF00)).decorate(TextDecoration.BOLD));
            //give player sword
            {
                ItemStack sword = new ItemStack(Material.IRON_SWORD);
                ItemMeta swordMeta = sword.getItemMeta();
                swordMeta.displayName(Component.text(ChatColor.YELLOW + "" + ChatColor.BOLD + "INTERMEDIATE WEAPON"));
                sword.setItemMeta(swordMeta);
                player.getInventory().addItem(sword);
                //give player invisibility for 30 seconds
                
            }
            //check if killed by intermediate
            if(event.getEntity().getKiller() != null && intermediates.contains(event.getEntity().getKiller())) {
                intermediates.remove(event.getEntity().getKiller());
                hiders.add(event.getEntity().getKiller());
                Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "team join hiders " + event.getEntity().getKiller().getName());
                event.getEntity().getKiller().sendMessage(Component.text("You are hider!").color(TextColor.color(0x00FF00)).decorate(TextDecoration.BOLD));
                event.getEntity().getKiller().getInventory().remove(Material.IRON_SWORD);
            }
            //check if any hiders left
            if(hiders.isEmpty()) {
                Bukkit.broadcast(Component.text("Seeker Wins!").color(TextColor.color(0xFF0000)).decorate(TextDecoration.BOLD));
                seekPhase = false;
                hidePhase = false;
            }
        }
        else if(intermediates.contains(player)) {
            intermediates.remove(player);
            seekers.add(player);
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "team join seeker " + player.getName());
            player.sendMessage(Component.text("You are seeker!").color(TextColor.color(0xFF0000)).decorate(TextDecoration.BOLD));
            //give player sword
            {
                ItemStack seekerSword = new ItemStack(Material.NETHERITE_SWORD);
                ItemMeta seekerSwordMeta = seekerSword.getItemMeta();
                seekerSwordMeta.displayName(Component.text(ChatColor.RED + "" + ChatColor.BOLD + "SEEKER WEAPON"));
                seekerSword.setItemMeta(seekerSwordMeta);
                player.getInventory().addItem(seekerSword);
            }
        }
        
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if(seekPhase || hidePhase) {
            event.setRespawnLocation(HIDE_SPAWN);
            if(intermediates.contains(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30 * 20, 0, false, false));
            }
        }
        else {
            event.setRespawnLocation(LOBBY_SPAWN);
        }
    }

    //checks if block broken is powerup spawner and cancels the event if so
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        for(Location loc : powerupLocations) {
            if(loc.equals(location)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("use command /removepowerup to remove powerup spawners");
                return;
            }
        }
    }

    //check on right click if player is holding powerup
    @EventHandler
    public void onRightClick(org.bukkit.event.player.PlayerInteractEvent event) {
        if(event == null) {
            return;
        }
        if(event.getAction().toString().equals("RIGHT_CLICK_AIR") || event.getAction().toString().equals("RIGHT_CLICK_BLOCK")) {
            if(event.getItem() == null) {
                return;
            }
            if(event.getItem().getItemMeta() == null) {
                return;
            }
            if(!event.getItem().getItemMeta().hasCustomModelData()) {
                return;
            }
            if(powerupCooldowns.containsKey(event.getPlayer())) {
                return;
            }
            try {
                //check custom model data for invisibility powerup
                if(event.getItem().getItemMeta().getCustomModelData() == 1) {
                    event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 10 * 20, 0, false, false));
                    event.getItem().setAmount(event.getItem().getAmount() - 1);
                    event.getPlayer().playSound(event.getPlayer().getLocation(), org.bukkit.Sound.UI_TOAST_IN, 2, 2);
                    powerupCooldowns.put(event.getPlayer(), 5);
                }
                //check custom model data for wormhole powerup
                if(event.getItem().getItemMeta().getCustomModelData() == 2) {
                    if(wormholes.containsKey(event.getPlayer())) {
                        event.getPlayer().teleport(wormholes.get(event.getPlayer()));
                        wormholes.remove(event.getPlayer());
                        event.getItem().setAmount(event.getItem().getAmount() - 1);
                        event.getPlayer().playSound(event.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1, 2);
                    }
                    else {
                        wormholes.put(event.getPlayer(), event.getPlayer().getLocation());
                        event.getPlayer().playSound(event.getPlayer().getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_HIT, 2, 1);
                    }
                    powerupCooldowns.put(event.getPlayer(), 10);
                }
                //check custom model data for reveal all players powerup
                if(event.getItem().getItemMeta().getCustomModelData() == 3) {
                    for(Player player : Bukkit.getOnlinePlayers()) {
                        if(hiders.contains(player)) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 5 * 20, 0, false, false));
                        }
                    }
                    event.getItem().setAmount(event.getItem().getAmount() - 1);
                    powerupCooldowns.put(event.getPlayer(), 5);
                }
                //check custom model data for insta heal powerup
                if(event.getItem().getItemMeta().getCustomModelData() == 4) {
                    event.getPlayer().setHealth(20);
                    event.getPlayer().getWorld().spawnParticle(org.bukkit.Particle.HEART, event.getPlayer().getLocation(), 10, 0.5, 1, 0.5, 0.1);
                    event.getItem().setAmount(event.getItem().getAmount() - 1);
                    event.getPlayer().playSound(event.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_PLAYER_BURP, 1, 1);
                    powerupCooldowns.put(event.getPlayer(), 5);
                }
            }
            catch(NullPointerException e) {
                return;
            }
        }
    }
}