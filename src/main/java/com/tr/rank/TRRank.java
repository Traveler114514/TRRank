package com.tr.rank;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TRRank extends JavaPlugin implements Listener, CommandExecutor {

    // ==================== 插件核心字段 ====================
    private final Map<String, RankData> ranks = new HashMap<>();
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastLoginDates = new ConcurrentHashMap<>();
    private File playerDataFile;
    private static TRRank instance;
    
    // ==================== 语言系统 ====================
    private static final Map<String, YamlConfiguration> languages = new HashMap<>();
    private static String currentLanguage = "zh_cn";
    
    // ==================== 插件生命周期 ====================
    @Override
    public void onEnable() {
        instance = this;
        
        // 确保数据文件夹存在
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        
        // 保存默认配置文件
        saveDefaultConfig();
        reloadConfig();
        
        // 初始化语言系统
        initializeLanguageSystem();
        
        // 加载等级配置
        loadRanks();
        
        // 加载玩家数据
        loadPlayerData();
        
        // 注册命令执行器
        getCommand("trrank").setExecutor(this);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 启动每日登录检查任务
        startDailyCheckTask();
        
        getLogger().info(getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info(getMessage("plugin.disabled"));
    }

    // ==================== 语言系统方法 ====================
    private void initializeLanguageSystem() {
        // 确保语言文件夹存在
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) langFolder.mkdirs();
        
        // 保存默认语言文件
        saveResource("lang/en.yml", false);
        saveResource("lang/zh_cn.yml", false);
        
        // 加载语言文件
        loadLanguage("en");
        loadLanguage("zh_cn");
        
        // 设置当前语言
        String configLang = getConfig().getString("language", "zh_cn");
        if (languages.containsKey(configLang)) {
            currentLanguage = configLang;
            getLogger().info("Language set to: " + configLang);
        } else {
            getLogger().warning("Language not available: " + configLang + ". Using default.");
        }
    }
    
    private void loadLanguage(String langCode) {
        File langFile = new File(getDataFolder() + "/lang", langCode + ".yml");
        if (!langFile.exists()) {
            saveResource("lang/" + langCode + ".yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
        languages.put(langCode, config);
    }
    
    public static String getMessage(String key) {
        YamlConfiguration langConfig = languages.get(currentLanguage);
        String message = langConfig.getString(key);
        
        if (message == null) {
            // 尝试从默认语言获取
            langConfig = languages.get("zh_cn");
            message = langConfig.getString(key);
            
            if (message == null) {
                instance.getLogger().warning("Missing language key: " + key);
                return key; // 返回键名作为回退
            }
        }
        
        // 翻译颜色代码
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // ==================== 等级管理方法 ====================
    private void loadRanks() {
        FileConfiguration config = getConfig();
        getLogger().info(getMessage("load.ranks.start"));
        
        if (config.isConfigurationSection("ranks")) {
            for (String key : config.getConfigurationSection("ranks").getKeys(false)) {
                String display = config.getString("ranks." + key + ".display", "[RANK]");
                int cost = config.getInt("ranks." + key + ".cost", 0);
                ranks.put(key.toLowerCase(), new RankData(display, cost));
            }
            
            String msg = getMessage("load.ranks.success")
                .replace("%count%", String.valueOf(ranks.size()));
            getLogger().info(msg);
            
            // 确保默认玩家等级存在
            if (!ranks.containsKey("player")) {
                ranks.put("player", new RankData("§7[Player] ", 0));
            }
        } else {
            getLogger().warning(getMessage("load.ranks.empty"));
        }
    }
    
    private void giveRank(Player player, String rankId) {
        PlayerData data = getPlayerData(player.getUniqueId());
        data.addRank(rankId.toLowerCase());
    }
    
    private boolean buyRank(Player player, String rankId) {
        rankId = rankId.toLowerCase();
        RankData rankInfo = ranks.get(rankId);
        
        if (rankInfo == null) {
            player.sendMessage(getMessage("rank.not-found"));
            return false;
        }
        
        PlayerData data = getPlayerData(player.getUniqueId());
        
        if (data.hasRank(rankId)) {
            player.sendMessage(getMessage("rank.already-have"));
            return false;
        }
        
        if (data.getPlayDays() < rankInfo.getCost()) {
            String msg = getMessage("rank.insufficient-days")
                .replace("%days%", String.valueOf(rankInfo.getCost()))
                .replace("%current%", String.valueOf(data.getPlayDays()));
            player.sendMessage(msg);
            return false;
        }
        
        data.addRank(rankId);
        player.sendMessage(getMessage("rank.purchased")
            .replace("%rank%", rankId));
        return true;
    }
    
    private String getDisplayPrefix(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return "";
        
        // 返回优先级最高的等级（按加入顺序）
        for (String rankId : data.getRanks()) {
            RankData rank = ranks.get(rankId);
            if (rank != null) return rank.getDisplay();
        }
        return "";
    }
    
    private Map<String, RankData> getRanks() {
        return Collections.unmodifiableMap(ranks);
    }

    // ==================== 玩家数据管理 ====================
    private void loadPlayerData() {
        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create playerdata.yml!");
            }
        }

        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int playDays = dataConfig.getInt(key + ".days");
                List<String> ranks = dataConfig.getStringList(key + ".ranks");
                playerData.put(uuid, new PlayerData(playDays, new HashSet<>(ranks)));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid UUID format: " + key);
            }
        }
    }
    
    private void savePlayerData() {
        FileConfiguration dataConfig = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> entry : playerData.entrySet()) {
            String uuid = entry.getKey().toString();
            dataConfig.set(uuid + ".days", entry.getValue().getPlayDays());
            dataConfig.set(uuid + ".ranks", new ArrayList<>(entry.getValue().getRanks()));
        }
        
        try {
            dataConfig.save(playerDataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save playerdata.yml!");
        }
    }
    
    private PlayerData getPlayerData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, k -> new PlayerData());
    }
    
    // ==================== 定时任务 ====================
    private void startDailyCheckTask() {
        // 每5分钟检查一次玩家每日登录状态
        getServer().getScheduler().runTaskTimer(
            this, 
            this::checkPlayerLogins, 
            0L, 
            5 * 60 * 20L
        );
    }
    
    private void checkPlayerLogins() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            String currentDate = TimeTracker.getTodayDate();
            String lastDate = lastLoginDates.get(uuid);
            
            if (!currentDate.equals(lastDate)) {
                PlayerData data = getPlayerData(uuid);
                data.incrementPlayDay();
                lastLoginDates.put(uuid, currentDate);
            }
        }
    }

    // ==================== 命令处理 ====================
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
                sender.sendMessage(getMessage("command.unknown"));
                return true;
        }
    }
    
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("trrank.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(getMessage("command.usage")
                .replace("%usage%", "/trrank give <player> <rank>"));
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(getMessage("player.not-found"));
            return true;
        }
        
        String rankId = args[2];
        giveRank(target, rankId);
        
        String msg = getMessage("rank.given")
            .replace("%player%", target.getName())
            .replace("%rank%", rankId);
        sender.sendMessage(msg);
        return true;
    }
    
    private boolean handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            sendAvailableRanks(player);
            player.sendMessage(getMessage("command.usage")
                .replace("%usage%", "/trrank buy <rank>"));
            return true;
        }
        
        String rankId = args[1];
        return buyRank(player, rankId);
    }
    
    private void sendAvailableRanks(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        Map<String, RankData> ranks = getRanks();
        
        player.sendMessage(getMessage("rank.list-header"));
        ranks.forEach((id, rank) -> {
            String color = getRankColor(id);
            
            if (!data.hasRank(id)) {
                String line = getMessage("rank.list-item")
                    .replace("%color%", color)
                    .replace("%id%", id)
                    .replace("%cost%", String.valueOf(rank.getCost()));
                player.sendMessage(line);
            }
        });
    }
    
    private boolean handleList(CommandSender sender) {
        Map<String, RankData> ranks = getRanks();
        if (ranks.isEmpty()) {
            sender.sendMessage(getMessage("rank.no-ranks"));
            return true;
        }
        
        sender.sendMessage(getMessage("rank.list-details-header"));
        ranks.forEach((id, rank) -> {
            String color = getRankColor(id);
            
            String detail = getMessage("rank.detail")
                .replace("%color%", color)
                .replace("%id%", id)
                .replace("%display%", rank.getDisplay())
                .replace("%cost%", String.valueOf(rank.getCost()));
            
            sender.sendMessage(detail);
        });
        return true;
    }
    
    private String getRankColor(String rankId) {
        switch (rankId.toLowerCase()) {
            case "admin": return "§c";
            case "player": return "§7";
            case "vip": return "§a";
            case "svip": return "§6";
            case "svip_plus": return "§e";
            default: return "§f";
        }
    }
    
    private boolean showHelp(CommandSender sender) {
        sender.sendMessage(getMessage("help.header"));
        
        if (sender.hasPermission("trrank.admin")) {
            sender.sendMessage(getMessage("help.admin.give"));
        }
        
        sender.sendMessage(getMessage("help.player.buy"));
        sender.sendMessage(getMessage("help.player.list"));
        sender.sendMessage(getMessage("help.player.help"));
        return true;
    }

    // ==================== 事件处理 ====================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 初始化登录日期记录
        String today = TimeTracker.getTodayDate();
        getPlayerData(player.getUniqueId()); // 确保数据存在
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String prefix = getDisplayPrefix(player);
        event.setFormat(prefix + " %s: %s");
    }

    // ==================== 内部数据类 ====================
    private static class RankData {
        private final String display;
        private final int cost;
        
        public RankData(String display, int cost) {
            this.display = display;
            this.cost = cost;
        }
        
        public String getDisplay() {
            return display;
        }
        
        public int getCost() {
            return cost;
        }
    }
    
    private static class PlayerData {
        private int playDays;
        private final Set<String> ranks;
        
        public PlayerData() {
            this(0, new HashSet<>());
        }
        
        public PlayerData(int playDays, Set<String> ranks) {
            this.playDays = playDays;
            this.ranks = new HashSet<>(ranks);
        }
        
        public boolean hasRank(String rankId) {
            return ranks.contains(rankId.toLowerCase());
        }
        
        public void addRank(String rankId) {
            ranks.add(rankId.toLowerCase());
        }
        
        public Set<String> getRanks() {
            return Collections.unmodifiableSet(ranks);
        }
        
        public int getPlayDays() {
            return playDays;
        }
        
        public void incrementPlayDay() {
            playDays++;
        }
    }
    
    private static class TimeTracker {
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
        
        public static String getTodayDate() {
            return DATE_FORMAT.format(new Date());
        }
    }
}
