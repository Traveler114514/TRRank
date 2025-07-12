package me.yourname.trrank;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class RankListener implements Listener {
    
    private final RankManager manager;

    public RankListener(RankManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 初始化登录日期记录
        String today = TimeTracker.getTodayDate();
        manager.getPlayerData(player.getUniqueId()); // Ensure data exists
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String prefix = manager.getDisplayPrefix(player);
        event.setFormat(prefix + " %s: %s");
    }
}
