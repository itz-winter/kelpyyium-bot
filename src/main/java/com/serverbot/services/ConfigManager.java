package com.serverbot.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.serverbot.utils.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Manages bot configuration loading and saving
 */
public class ConfigManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "config.json";
    private final Gson gson;
    private BotConfig config;
    
    public ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadConfig();
    }
    
    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        
        if (!configFile.exists()) {
            logger.info("Config file not found, creating default configuration...");
            config = new BotConfig();
            saveConfig();
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            config = gson.fromJson(reader, BotConfig.class);
            logger.info("Configuration loaded successfully from {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to load configuration file", e);
            config = new BotConfig();
        }
    }
    
    public void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
            logger.info("Configuration saved to {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save configuration file", e);
        }
    }
    
    public BotConfig getConfig() {
        return config;
    }
    
    public void reloadConfig() {
        logger.info("Reloading configuration...");
        loadConfig();
    }
}
