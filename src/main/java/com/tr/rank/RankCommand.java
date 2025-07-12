package com.tr.rank;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class RankCommand implements CommandExecutor {
    
    private final TRRank plugin;
    private final RankManager manager;

    public RankCommand(TRRank plugin, RankManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) return showHelp(sender);
        
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "give": return handleGive(sender, args);
            case "buy": return handleBuy(sender, args);
            case "list": return handleList(sender);
            case "help": return showHelp(sender);
            default: 
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /trrank help");
                return true;
        }
    }

    private void sendAvailableRanks(Player player) {
        PlayerData data = manager.getPlayerData(player.getUniqueId());
        Map<String, RankData> ranks = manager.getRanks();
        
        player.sendMessage(ChatColor.GOLD + "Available Ranks:");
        ranks.forEach((id, rank) -> {
            String color = "";
            switch (id.toLowerCase()) {
                case "admin": color = "§c"; break;
                case "player": color = "§7"; break;
                case "vip": color = "§a"; break;
                case "svip": color = "§6"; break;
                case "svip_plus": color = "§e"; break;
            }
            
            if (!data.hasRank(id)) {
                player.sendMessage(color + "- " + id + " §7(" + rank.getCost() + " days)");
            }
        });
    }

    private boolean handleList(CommandSender sender) {
        Map<String, RankData> ranks = manager.getRanks();
        if (ranks.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No ranks configured!");
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "Rank List:");
        ranks.forEach((id, rank) -> {
            String color = "";
            switch (id.toLowerCase()) {
                case "admin": color = "§c"; break;
                case "player": color = "§7"; break;
                case "vip": color = "§a"; break;
                case "svip": color = "§6"; break;
                case "svip_plus": color = "§e"; break;
            }
            
            sender.sendMessage(color + "- ID: " + id);
            sender.sendMessage("  Display: " + rank.getDisplay());
            sender.sendMessage("  Cost: " + rank.getCost() + " days");
        });
        return true;
    }

    // 其他方法保持不变...
}
