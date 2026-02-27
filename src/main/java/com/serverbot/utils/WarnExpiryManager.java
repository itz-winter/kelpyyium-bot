package com.serverbot.utils;

import com.serverbot.ServerBot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages automatic warning expiry and removal
 */
public class WarnExpiryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(WarnExpiryManager.class);
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private static boolean started = false;
    
    /**
     * Start the warn expiry checking system
     */
    public static void start() {
        if (started) return;
        started = true;
        
        // Check for expired warnings every hour
        executor.scheduleWithFixedDelay(() -> {
            try {
                checkExpiredWarnings();
            } catch (Exception e) {
                logger.error("Error checking expired warnings: {}", e.getMessage(), e);
            }
        }, 1, 60, TimeUnit.MINUTES);
        
        logger.info("WarnExpiryManager started - checking for expired warnings every hour");
    }
    
    /**
     * Stop the warn expiry checking system
     */
    public static void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            started = false;
            logger.info("WarnExpiryManager stopped");
        }
    }
    
    /**
     * Check and remove expired warnings across all guilds
     */
    private static void checkExpiredWarnings() {
        try {
            // Get all guild IDs that have warning data
            Map<String, Object> allGuildSettings = ServerBot.getStorageManager().getAllGuildSettings();
            
            for (String guildId : allGuildSettings.keySet()) {
                checkExpiredWarningsForGuild(guildId);
            }
            
        } catch (Exception e) {
            logger.error("Error during expired warnings check: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check and remove expired warnings for a specific guild
     */
    private static void checkExpiredWarningsForGuild(String guildId) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            Object warnExpiryObj = settings.getOrDefault("warnExpiry", 30);
            
            int warnExpiryDays;
            if (warnExpiryObj instanceof Integer) {
                warnExpiryDays = (Integer) warnExpiryObj;
            } else if (warnExpiryObj instanceof Long) {
                warnExpiryDays = ((Long) warnExpiryObj).intValue();
            } else {
                warnExpiryDays = 30; // Default
            }
            
            // If warn expiry is -1, warnings are permanent
            if (warnExpiryDays == -1) {
                return;
            }
            
            long expiryTime = System.currentTimeMillis() - (warnExpiryDays * 24L * 60L * 60L * 1000L);
            
            // Get all users with warnings in this guild
            Map<String, List<Map<String, Object>>> allWarnings = ServerBot.getStorageManager().getAllWarnings(guildId);
            
            for (Map.Entry<String, List<Map<String, Object>>> entry : allWarnings.entrySet()) {
                String userId = entry.getKey();
                List<Map<String, Object>> userWarnings = entry.getValue();
                
                // Check each warning for expiry
                boolean hasExpiredWarnings = false;
                for (int i = userWarnings.size() - 1; i >= 0; i--) {
                    Map<String, Object> warning = userWarnings.get(i);
                    long warningTime = ((Number) warning.get("timestamp")).longValue();
                    
                    if (warningTime < expiryTime) {
                        // Warning has expired, remove it
                        userWarnings.remove(i);
                        hasExpiredWarnings = true;
                        
                        // Log the automatic warning removal
                        logExpiredWarning(guildId, userId, warning);
                    }
                }
                
                if (hasExpiredWarnings) {
                    // Update the user's warnings in storage
                    if (userWarnings.isEmpty()) {
                        ServerBot.getStorageManager().clearWarnings(guildId, userId);
                    } else {
                        // Save the updated warnings list
                        ServerBot.getStorageManager().updateUserWarnings(guildId, userId, userWarnings);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error checking expired warnings for guild {}: {}", guildId, e.getMessage(), e);
        }
    }
    
    /**
     * Log the automatic removal of an expired warning
     */
    private static void logExpiredWarning(String guildId, String userId, Map<String, Object> expiredWarning) {
        try {
            // Create moderation log entry for the automatic unwarn
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", "UNWARN");
            logEntry.put("userId", userId);
            logEntry.put("moderatorId", "[Server]"); // System action
            logEntry.put("reason", "Warn auto removal. Warning: " + expiredWarning.get("reason") + " WarningID: " + expiredWarning.get("timestamp"));
            logEntry.put("removedWarningReason", expiredWarning.get("reason"));
            logEntry.put("timestamp", System.currentTimeMillis());
            logEntry.put("isAutoExpiry", true);
            
            ServerBot.getStorageManager().addModerationLog(guildId, logEntry);
            
            // Try to log to AutoLog channel if possible
            try {
                Guild guild = ServerBot.getJda().getGuildById(guildId);
                if (guild != null) {
                    User user = ServerBot.getJda().getUserById(userId);
                    if (user != null) {
                        // Create a fake "Server" user for the moderator field
                        AutoLogUtils.logUnwarn(guild, user, guild.getSelfMember().getUser(), 
                            "Warn auto removal. Warning: " + expiredWarning.get("reason") + " WarningID: " + expiredWarning.get("timestamp"), 
                            (String) expiredWarning.get("reason"));
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not log expired warning to AutoLog channel: {}", e.getMessage());
            }
            
            logger.debug("Automatically removed expired warning for user {} in guild {}", userId, guildId);
            
        } catch (Exception e) {
            logger.error("Error logging expired warning removal: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Manually trigger expiry check for a specific guild (for testing)
     */
    public static void checkGuild(String guildId) {
        checkExpiredWarningsForGuild(guildId);
    }
    
    /**
     * Manually trigger full expiry check (for testing)
     */
    public static void checkAllGuilds() {
        checkExpiredWarnings();
    }
}
