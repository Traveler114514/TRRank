package com.tr.rank;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TRRank extends JavaPlugin implements Listener, CommandExecutor {

    // ==================== 插件核心字段 ====================
    private final Map<String, RankData> ranks = new HashMap<>();
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastLoginDates = new ConcurrentHashMap<>();
    private File playerDataFile;
    private static TRRank instance;
    
    // ==================== Tab列表团队管理 ====================
    private final Map<String, Team> rankTeams = new HashMap<>();
    private Scoreboard scoreboard;
    
    // ==================== 语言系统 ====================
    private static final Map<String, YamlConfiguration> languages = new HashMap<>();
    private static String currentLanguage = "zh_cn";
    
    // ==================== 安全更新检测系统 ====================
    private static final int PLUGIN_VERSION = 100; // 当前插件版本
    private static final String PRIMARY_UPDATE_URL = "https://raw.githubusercontent.com/Traveler114514/FileCloud/refs/heads/main/TRRank/version.txt";
    private static final String UPDATE_SIGNATURE = "TRRankSecureUpdate"; // 更新签名
    private String updateMessage = null; // 存储更新消息
    
    // ==================== 默认称号系统 ====================
    private String defaultRankId = "player"; // 默认称号ID

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
        
        // 加载默认称号配置
        loadDefaultRankConfig();
        
        // 初始化Tab列表团队
        initializeTabListTeams();
        
        // 注册命令执行器
        getCommand("trrank").setExecutor(this);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 启动每日登录检查任务
        startDailyCheckTask();
        
        // 异步检查更新
        checkForUpdates();
        
        getLogger().info(getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info(getMessage("plugin.disabled"));
    }

    // ==================== 默认称号配置 ====================
    private void loadDefaultRankConfig() {
        // 从配置文件中读取默认称号
        String configDefaultRank = getConfig().getString("default-rank", "player").toLowerCase();
        
        // 验证默认称号是否存在
        if (ranks.containsKey(configDefaultRank)) {
            defaultRankId = configDefaultRank;
            getLogger().info("Default rank set to: " + defaultRankId);
        } else {
            getLogger().warning("Default rank '" + configDefaultRank + "' not found in ranks configuration!");
            
            // 尝试使用player作为备选
            if (ranks.containsKey("player")) {
                defaultRankId = "player";
                getLogger().warning("Using 'player' as fallback default rank");
            } else if (!ranks.isEmpty()) {
                // 使用第一个可用称号
                defaultRankId = ranks.keySet().iterator().next();
                getLogger().warning("Using first available rank '" + defaultRankId + "' as default");
            } else {
                getLogger().severe("No ranks available! Plugin may not function properly");
            }
        }
    }
    
    // ==================== Tab列表团队初始化 ====================
    private void initializeTabListTeams() {
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        
        // 清理旧的团队（如果存在）
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith("trrank_")) {
                team.unregister();
            }
        }
        
        // 为每个等级创建团队
        for (String rankId : ranks.keySet()) {
            String teamName = "trrank_" + rankId;
            Team team = scoreboard.registerNewTeam(teamName);
            
            // 设置团队前缀（等级显示）
            String prefix = getColoredPrefix(rankId);
            if (prefix.length() > 16) {
                prefix = prefix.substring(0, 16);
            }
            team.setPrefix(prefix);
            
            // 保存团队引用
            rankTeams.put(rankId, team);
        }
        
        // 为所有在线玩家分配团队
        for (Player player : Bukkit.getOnlinePlayers()) {
            assignPlayerToTeam(player);
        }
    }
    
    // 获取带颜色的前缀（仅前缀部分有颜色）
    private String getColoredPrefix(String rankId) {
        RankData rank = ranks.get(rankId);
        if (rank == null) return "";
        
        // 提取颜色代码
        String colorCode = getRankColorCode(rankId);
        
        // 创建带颜色的前缀
        return colorCode + rank.getDisplay().replace(colorCode, "") + ChatColor.RESET;
    }
    
    private void assignPlayerToTeam(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;
        
        // 获取玩家当前激活的等级
        String activeRank = data.getActiveRank();
        if (activeRank == null) {
            // 如果没有激活的等级，使用第一个称号
            if (!data.getRanks().isEmpty()) {
                activeRank = data.getRanks().iterator().next();
                data.setActiveRank(activeRank);
            } else {
                return;
            }
        }
        
        Team team = rankTeams.get(activeRank);
        if (team != null) {
            // 如果玩家在其他团队中，先移除
            Team currentTeam = scoreboard.getEntryTeam(player.getName());
            if (currentTeam != null && !currentTeam.equals(team)) {
                currentTeam.removeEntry(player.getName());
            }
            
            // 添加到新团队
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        }
    }
    
    // ==================== 安全更新检测方法 ====================
    private void checkForUpdates() {
        // 使用硬编码的主更新URL
        String updateUrl = PRIMARY_UPDATE_URL;
        
        // 异步执行更新检查
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String versionData = fetchVersionData(updateUrl);
                
                // 验证更新数据签名
                if (!isValidUpdateData(versionData)) {
                    getLogger().warning("Invalid update signature detected");
                    return;
                }
                
                int latestVersion = parseVersion(versionData);
                
                if (latestVersion > PLUGIN_VERSION) {
                    // 有新版本可用
                    String msg = getMessage("update.available")
                        .replace("%current%", String.valueOf(PLUGIN_VERSION))
                        .replace("%latest%", String.valueOf(latestVersion));
                    
                    // 记录更新消息
                    updateMessage = msg;
                    
                    // 在控制台输出
                    getLogger().info(msg);
                    getLogger().info(getMessage("update.download"));
                } else if (latestVersion > 0) {
                    getLogger().info(getMessage("update.latest"));
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, getMessage("update.failed"), e);
            }
        });
    }
    
    private String fetchVersionData(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(5000); // 5秒超时
        connection.setReadTimeout(5000);   // 5秒超时
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            return content.toString();
        }
    }
    
    private boolean isValidUpdateData(String data) {
        // 简单签名验证：数据必须以特定签名开头
        return data != null && data.startsWith(UPDATE_SIGNATURE);
    }
    
    private int parseVersion(String data) {
        try {
            // 数据格式: TRRankSecureUpdate|101
            String[] parts = data.split("\\|");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1].trim());
            }
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid version format: " + data);
        }
        return -1;
    }
    
    private void notifyUpdate(Player player) {
        if (updateMessage != null && player.isOp()) {
            player.sendMessage(ChatColor.GOLD + "====================================");
            player.sendMessage(updateMessage);
            player.sendMessage(getMessage("update.download"));
            player.sendMessage(ChatColor.GOLD + "====================================");
        }
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
        Object messageObj = langConfig.get(key);
        
        // 处理不同类型的返回值
        if (messageObj instanceof String) {
            String message = (String) messageObj;
            return ChatColor.translateAlternateColorCodes('&', message);
        } else if (messageObj instanceof MemorySection) {
            // 处理错误的配置格式
            instance.getLogger().warning("Invalid message format for key: " + key);
            return key;
        }
        
        // 尝试从默认语言获取
        langConfig = languages.get("zh_cn");
        messageObj = langConfig.get(key);
        
        if (messageObj instanceof String) {
            String message = (String) messageObj;
            return ChatColor.translateAlternateColorCodes('&', message);
        }
        
        // 最终回退
        instance.getLogger().warning("Missing language key: " + key);
        return key;
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
        
        // 如果这是玩家的第一个称号，设置为激活称号
        if (data.getActiveRank() == null) {
            data.setActiveRank(rankId.toLowerCase());
        }
        
        // 更新Tab列表团队
        assignPlayerToTeam(player);
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
        
        // 如果这是玩家的第一个称号，设置为激活称号
        if (data.getActiveRank() == null) {
            data.setActiveRank(rankId);
        }
        
        // 更新Tab列表团队
        assignPlayerToTeam(player);
        
        return true;
    }
    
    // 获取激活称号的显示前缀
    private String getActiveDisplayPrefix(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return "";
        
        String activeRankId = data.getActiveRank();
        if (activeRankId == null) return "";
        
        RankData rank = ranks.get(activeRankId);
        if (rank != null) return rank.getDisplay();
        
        return "";
    }
    
    // 获取等级对应的颜色代码
    private String getRankColorCode(String rankId) {
        switch (rankId.toLowerCase()) {
            case "admin": return "§c";
            case "player": return "§7";
            case "vip": return "§a";
            case "svip": return "§6";
            case "svip_plus": return "§e";
            default: return "§f";
        }
    }
    
    // ==================== 修复：添加缺失的getRanks方法 ====================
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
                String activeRank = dataConfig.getString(key + ".activeRank");
                
                PlayerData playerData = new PlayerData(playDays, new HashSet<>(ranks));
                playerData.setActiveRank(activeRank);
                
                this.playerData.put(uuid, playerData);
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
            dataConfig.set(uuid + ".activeRank", entry.getValue().getActiveRank());
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
            case "use": return handleUse(sender, args);
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
            sender.sendMessage(getMessage("command.usage.give"));
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
            return true;
        }
        
        String rankId = args[1];
        return buyRank(player, rankId);
    }
    
    private void sendAvailableRanks(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        Map<String, RankData> allRanks = getRanks();
        
        player.sendMessage(getMessage("rank.list-header"));
        
        boolean hasAvailableRanks = false;
        
        // 发送可购买的等级列表
        for (Map.Entry<String, RankData> entry : allRanks.entrySet()) {
            String id = entry.getKey();
            RankData rank = entry.getValue();
            
            if (!data.hasRank(id)) {
                hasAvailableRanks = true;
                String color = getRankColorCode(id);
                
                String line = getMessage("rank.list-item")
                    .replace("%color%", color)
                    .replace("%id%", id)
                    .replace("%cost%", String.valueOf(rank.getCost()));
                player.sendMessage(line);
            }
        }
        
        // 如果没有可用等级，显示提示
        if (!hasAvailableRanks) {
            player.sendMessage(getMessage("rank.no-available"));
        }
        
        // 在列表后添加用法提示
        String usageMsg = getMessage("command.usage.buy")
            .replace("%usage%", "/trrank buy <rank>");
        player.sendMessage(usageMsg);
    }
    
    private boolean handleUse(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage(getMessage("command.usage.use"));
            return true;
        }
        
        String rankId = args[1].toLowerCase();
        PlayerData data = getPlayerData(player.getUniqueId());
        
        // 检查玩家是否拥有该称号
        if (!data.hasRank(rankId)) {
            player.sendMessage(getMessage("rank.not-owned"));
            return true;
        }
        
        // 检查称号是否存在
        if (!ranks.containsKey(rankId)) {
            player.sendMessage(getMessage("rank.not-found"));
            return true;
        }
        
        // 设置激活称号
        data.setActiveRank(rankId);
        
        // 更新Tab列表团队
        assignPlayerToTeam(player);
        
        // 通知玩家
        String msg = getMessage("rank.activated")
            .replace("%rank%", rankId);
        player.sendMessage(msg);
        
        return true;
    }



    
    private boolean handleList(CommandSender sender) {
        Map<String, RankData> allRanks = getRanks();
        if (allRanks.isEmpty()) {
            sender.sendMessage(getMessage("rank.no-ranks"));
            return true;
        }
        
        sender.sendMessage(getMessage("rank.list-details-header"));
        allRanks.forEach((id, rank) -> {
            String color = getRankColorCode(id);
            
            String detail = getMessage("rank.detail")
                .replace("%color%", color)
                .replace("%id%", id)
                .replace("%display%", rank.getDisplay())
                .replace("%cost%", String.valueOf(rank.getCost()));
            
            sender.sendMessage(detail);
        });
        return true;
    }
    
    private boolean showHelp(CommandSender sender) {
        sender.sendMessage(getMessage("help.header"));
        
        if (sender.hasPermission("trrank.admin")) {
            sender.sendMessage(getMessage("help.admin.give"));
        }
        
        sender.sendMessage(getMessage("help.player.buy"));
        sender.sendMessage(getMessage("help.player.use"));
        sender.sendMessage(getMessage("help.player.list"));
        sender.sendMessage(getMessage("help.player.help"));
        return true;
    }

    // ==================== 事件处理 ====================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // 获取玩家数据（如果不存在则创建）
        PlayerData data = getPlayerData(uuid);
        
        // 检查玩家是否拥有任何称号
        if (data.getRanks().isEmpty()) {
            // 给予默认称号
            data.addRank(defaultRankId);
            getLogger().info("Assigned default rank '" + defaultRankId + "' to " + player.getName());
            
            // 设置激活称号
            data.setActiveRank(defaultRankId);
            
            // 通知玩家
            notifyPlayerOfDefaultRank(player);
            
            // 保存玩家数据
            savePlayerData();
        } else if (data.getActiveRank() == null) {
            // 如果没有激活称号，设置第一个称号为激活状态
            String firstRank = data.getRanks().iterator().next();
            data.setActiveRank(firstRank);
        }
        
        // 初始化登录日期记录
        String today = TimeTracker.getTodayDate();
        String lastDate = lastLoginDates.get(uuid);
        
        if (!today.equals(lastDate)) {
            data.incrementPlayDay();
            lastLoginDates.put(uuid, today);
        }
        
        // 通知OP更新
        notifyUpdate(player);
        
        // 分配Tab列表团队
        assignPlayerToTeam(player);
    }
    
    private void notifyPlayerOfDefaultRank(Player player) {
        String message = getMessage("rank.default-assigned")
            .replace("%rank%", defaultRankId);
        player.sendMessage(message);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 玩家退出时从团队中移除
        Team team = scoreboard.getEntryTeam(player.getName());
        if (team != null && team.getName().startsWith("trrank_")) {
            team.removeEntry(player.getName());
        }
    }

    // ==================== 修复聊天显示问题 ====================
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // 获取激活称号的显示前缀
        String prefix = getActiveDisplayPrefix(player);
        
        // 重置玩家名称和消息内容的颜色
        // 格式: [称号前缀] 玩家名称: 消息内容
        event.setFormat(prefix + "%s§f: %s");
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
        private String activeRank; // 当前激活的称号
        
        public PlayerData() {
            this(0, new HashSet<>());
        }
        
        public PlayerData(int playDays, Set<String> ranks) {
            this.playDays = playDays;
            this.ranks = new HashSet<>(ranks);
            this.activeRank = null;
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
        
        public String getActiveRank() {
            return activeRank;
        }
        
        public void setActiveRank(String activeRank) {
            this.activeRank = activeRank;
        }
    }
    
    private static class TimeTracker {
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
        
        public static String getTodayDate() {
            return DATE_FORMAT.format(new Date());
        }
    }
}
