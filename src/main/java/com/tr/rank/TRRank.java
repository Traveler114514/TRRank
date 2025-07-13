// ==================== 安全更新检测系统 ====================
private static final int PLUGIN_VERSION = 100; // 当前插件版本
private static final String PRIMARY_UPDATE_URL = "https://secure.trrank.com/version.txt"; // 硬编码更新URL
private static final String UPDATE_SIGNATURE = "TRRankSecureUpdate"; // 硬编码更新签名
private String updateMessage = null; // 存储更新消息

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
