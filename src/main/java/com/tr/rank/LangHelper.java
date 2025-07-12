package com.tr.rank;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LangHelper {
    private static final Map<String, YamlConfiguration> languages = new HashMap<>();
    private static String currentLanguage = "zh_cn";
    private static JavaPlugin plugin;

    public static void initialize(TRRank pluginInstance) {
        plugin = pluginInstance;
        loadLanguage("en");
        loadLanguage("zh_cn");
    }

    public static void setLanguage(String lang) {
        if (languages.containsKey(lang)) {
            currentLanguage = lang;
            plugin.getLogger().info("Language set to: " + lang);
        } else {
            plugin.getLogger().warning("Language not available: " + lang + ". Using default.");
        }
    }

    public static String getMessage(String key) {
        YamlConfiguration langConfig = languages.get(currentLanguage);
        String message = langConfig.getString(key);
        
        if (message == null) {
            // 尝试从默认语言获取
            langConfig = languages.get("zh_cn");
            message = langConfig.getString(key);
            
            if (message == null) {
                plugin.getLogger().warning("Missing language key: " + key);
                return key; // 返回键名作为回退
            }
        }
        
        // 翻译颜色代码
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static void loadLanguage(String langCode) {
        File langFile = new File(plugin.getDataFolder() + "/lang", langCode + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + langCode + ".yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
        languages.put(langCode, config);
    }
}
