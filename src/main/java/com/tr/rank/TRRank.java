package com.tr.rank;

import org.bukkit.plugin.java.JavaPlugin;

public class TRRank extends JavaPlugin {

    private RankManager rankManager;

    @Override
    public void onEnable() {
        // 保存默认配置文件
        saveDefaultConfig();
        
        // 初始化管理器
        rankManager = new RankManager(this);
        rankManager.loadRanks();
        rankManager.loadPlayerData();

        // 注册命令执行器
        getCommand("trrank").setExecutor(new RankCommand(this, rankManager));

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new RankListener(rankManager), this);
        
        // 启动每日登录检查任务
        startDailyCheckTask();
        
        getLogger().info("TRRank has been enabled!");
    }

    @Override
    public void onDisable() {
        rankManager.savePlayerData();
        getLogger().info("TRRank has been disabled!");
    }

    private void startDailyCheckTask() {
        // 每5分钟检查一次玩家每日登录状态
        getServer().getScheduler().runTaskTimer(this, rankManager::checkPlayerLogins, 0L, 5 * 60 * 20L);
    }
}
