package com.calcite.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import com.calcite.Main;

public class EndGame implements CommandExecutor {
    private Main plugin;

    //command '/endgame' ends the game

    public EndGame(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender arg0, @NotNull Command arg1, @NotNull String arg2, @NotNull String[] arg3) {
        //end the game
        plugin.endGame();
        return true;
    }
}
