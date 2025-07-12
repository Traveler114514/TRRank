package com.tr.rank;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RankManager {

    private final TRRank plugin;
    private final Map<String, RankData> ranks = new HashMap<>();
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Map<UUID, String> lastLoginDates = new HashMap<>();
    private File playerDataFile;

    public RankManager(TRRank plugin) {
        this.plugin = plugin;
    }

    public void loadRanks() {
        FileConfiguration config = plugin.getConfig();
        if (config.isConfigurationSection("ranks")) {
            for (String key : config.getConfigurationSection("ranks").getKeys(false)) {
                String display = config.getString("ranks." + key + ".display", "[RANK]");
                int cost = config.getInt("ranks." + key + ".cost", 0);
                ranks.put(key.toLowerCase(), new RankData(display, cost));
            }
            plugin.getLogger().info("Loaded " + ranks.size() + " ranks");
            
            // 确保默认玩家等级存在
            if (!ranks.containsKey("player")) {
                ranks.put("player", new RankData("§7[Player] ", 0));
            }
        }
    }

    // 其他方法保持不变...
}
