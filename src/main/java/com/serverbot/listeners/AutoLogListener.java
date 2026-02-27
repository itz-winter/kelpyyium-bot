package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.SafeRestAction;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles automatic logging of Discord events to configured log channels
 */
public class AutoLogListener extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(AutoLogListener.class);
    
    // Track message IDs that are sent by the bot to log channels to avoid logging their deletions
    private static final Set<String> logMessageIds = ConcurrentHashMap.newKeySet();
    
    // Track the content hash of recently sent messages to detect embed-only updates
    private static final Map<String, Integer> messageContentHashes = new ConcurrentHashMap<>();
    
    /**
     * Track a log message ID to prevent logging its deletion.
     * This is called when the bot sends a log message.
     * 
     * @param messageId The ID of the log message to track
     */
    public static void trackLogMessage(String messageId) {
        logMessageIds.add(messageId);
        
        // Prevent memory leak - keep only the last 5000 log message IDs
        if (logMessageIds.size() > 5000) {
            Iterator<String> iterator = logMessageIds.iterator();
            for (int i = 0; i < 1000 && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
        }
    }
    
    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        
        Message message = event.getMessage();
        String messageId = message.getId();
        String contentRaw = message.getContentRaw();
        
        // Calculate hash of current content
        int currentHash = contentRaw.hashCode();
        
        // Check if we've seen this message before
        Integer previousHash = messageContentHashes.get(messageId);
        
        if (previousHash != null && previousHash == currentHash) {
            // Content hasn't changed - this is just an embed update, pin, or flag change
            return;
        }
        
        // Update the hash for future comparisons
        messageContentHashes.put(messageId, currentHash);
        
        // Clean up old entries (keep only last 1000)
        if (messageContentHashes.size() > 1000) {
            // Remove oldest entries
            Iterator<String> iterator = messageContentHashes.keySet().iterator();
            for (int i = 0; i < 100 && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
        }
        
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "message", "edits")) {
            return;
        }
        
        TextChannel logChannel = getLogChannel(event.getGuild(), "message");
        if (logChannel == null) return;
        
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle(CustomEmojis.NOTE + " Message Edited")
                .addField("User", event.getAuthor().getAsMention(), true)
                .addField("Channel", event.getChannel().getAsMention(), true)
                .addField("Message ID", event.getMessageId(), true)
                .addField("New Content", 
                    contentRaw.isEmpty() ? "*No text content*" : 
                    truncateText(contentRaw, 1024), false)
                .setFooter("User ID: " + event.getAuthor().getId())
                .setTimestamp(OffsetDateTime.now());
        
        SafeRestAction.queue(
            logChannel.sendMessageEmbeds(embed.build()), 
            "log message edit event",
            success -> trackLogMessage(success.getId())
        );
    }
    
    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        
        String messageId = event.getMessageId();
        
        // Check if this was a log message sent by the bot - don't log deletion of our own log messages
        if (logMessageIds.remove(messageId)) {
            return; // This was a log message, don't log its deletion
        }
        
        // Check if this was a proxy system deletion
        // If the message is being tracked by ProxyService as an original message, don't log it
        if (ServerBot.getProxyService().isOriginalMessageBeingProxied(messageId)) {
            return; // This is a proxy deletion, don't log it
        }
        
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "message", "deletes")) {
            return;
        }
        
        TextChannel logChannel = getLogChannel(event.getGuild(), "message");
        if (logChannel == null) return;
        
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle(CustomEmojis.TRASH + " Message Deleted")
                .addField("Channel", event.getChannel().getAsMention(), true)
                .addField("Message ID", messageId, true)
                .setFooter("Deleted at")
                .setTimestamp(OffsetDateTime.now());
        
        // Try to get cached message info from audit logs
        SafeRestAction.queue(
            event.getGuild().retrieveAuditLogs().type(ActionType.MESSAGE_DELETE).limit(1),
            "retrieve audit logs for message delete",
            auditLogs -> {
                if (!auditLogs.isEmpty()) {
                    AuditLogEntry entry = auditLogs.get(0);
                    if (entry.getTargetIdLong() == event.getMessageIdLong()) {
                        User deletedBy = entry.getUser();
                        if (deletedBy != null) {
                            embed.addField("Deleted by", deletedBy.getAsMention(), true);
                            embed.setFooter("Deleted by " + deletedBy.getName() + " | User ID: " + deletedBy.getId());
                        }
                    }
                }
                SafeRestAction.queue(
                    logChannel.sendMessageEmbeds(embed.build()),
                    "log message delete with moderator",
                    success -> trackLogMessage(success.getId())
                );
            }
        );
    }
    
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "member", "joins")) {
            return;
        }
        
        TextChannel logChannel = getLogChannel(event.getGuild(), "member");
        if (logChannel == null) return;
        
        User user = event.getUser();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("📥 Member Joined")
                .setThumbnail(user.getAvatarUrl())
                .addField("User", user.getAsMention(), true)
                .addField("Username", user.getName(), true)
                .addField("Account Created", 
                    user.getTimeCreated().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")), true)
                .addField("Member Count", String.valueOf(event.getGuild().getMemberCount()), true)
                .setFooter("User ID: " + user.getId())
                .setTimestamp(OffsetDateTime.now());
        
        SafeRestAction.queue(
            logChannel.sendMessageEmbeds(embed.build()), 
            "log member join event",
            success -> trackLogMessage(success.getId())
        );
    }
    
    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "member", "leaves")) {
            return;
        }
        
        TextChannel logChannel = getLogChannel(event.getGuild(), "member");
        if (logChannel == null) return;
        
        User user = event.getUser();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("📤 Member Left")
                .setThumbnail(user.getAvatarUrl())
                .addField("User", user.getAsMention(), true)
                .addField("Username", user.getName(), true)
                .addField("Member Count", String.valueOf(event.getGuild().getMemberCount()), true)
                .setFooter("User ID: " + user.getId())
                .setTimestamp(OffsetDateTime.now());
        
        SafeRestAction.queue(
            logChannel.sendMessageEmbeds(embed.build()), 
            "log member leave event",
            success -> trackLogMessage(success.getId())
        );
    }
    
    @Override
    public void onGuildBan(GuildBanEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "moderation", "bans")) {
            return;
        }
        
        TextChannel logChannel = getLogChannel(event.getGuild(), "moderation");
        if (logChannel == null) return;
        
        User user = event.getUser();
        
        // Check audit logs to see who issued the ban.
        // If it was the bot itself, skip logging here because the command
        // (e.g. BanCommand) already logged via AutoLogUtils.logBan().
        SafeRestAction.queue(
            event.getGuild().retrieveAuditLogs().type(ActionType.BAN).limit(1),
            "retrieve audit logs for ban",
            auditLogs -> {
                if (!auditLogs.isEmpty()) {
                    AuditLogEntry entry = auditLogs.get(0);
                    if (entry.getTargetIdLong() == user.getIdLong()) {
                        User bannedBy = entry.getUser();
                        // If the ban was issued by the bot, the command already logged it
                        if (bannedBy != null && bannedBy.getIdLong() == event.getGuild().getSelfMember().getIdLong()) {
                            return; // Skip duplicate log
                        }
                    }
                }
                
                // This ban was not issued by the bot (e.g. manual Discord ban), so log it
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.RED)
                        .setTitle(CustomEmojis.ERROR + " Member Banned")
                        .setThumbnail(user.getAvatarUrl())
                        .addField("User", user.getAsMention(), true)
                        .addField("Username", user.getName(), true)
                        .setFooter("User ID: " + user.getId())
                        .setTimestamp(OffsetDateTime.now());
                
                if (!auditLogs.isEmpty()) {
                    AuditLogEntry entry = auditLogs.get(0);
                    if (entry.getTargetIdLong() == user.getIdLong()) {
                        User bannedBy = entry.getUser();
                        String reason = entry.getReason();
                        
                        if (bannedBy != null) {
                            embed.addField("Banned by", bannedBy.getAsMention(), true);
                        }
                        if (reason != null && !reason.isEmpty()) {
                            embed.addField("Reason", reason, false);
                        }
                    }
                }
                SafeRestAction.queue(
                    logChannel.sendMessageEmbeds(embed.build()), 
                    "log ban with moderator",
                    success -> trackLogMessage(success.getId())
                );
            }
        );
    }
    
    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "moderation", "bans")) {
            return;
        }
        
        TextChannel logChannel = getLogChannel(event.getGuild(), "moderation");
        if (logChannel == null) return;
        
        User user = event.getUser();
        
        // Check audit logs to see who issued the unban.
        // If it was the bot itself, skip logging here because the command
        // (e.g. UnbanCommand) already logged via AutoLogUtils.logUnban().
        SafeRestAction.queue(
            event.getGuild().retrieveAuditLogs().type(ActionType.UNBAN).limit(1),
            "retrieve audit logs for unban",
            auditLogs -> {
                if (!auditLogs.isEmpty()) {
                    AuditLogEntry entry = auditLogs.get(0);
                    if (entry.getTargetIdLong() == user.getIdLong()) {
                        User unbannedBy = entry.getUser();
                        if (unbannedBy != null && unbannedBy.getIdLong() == event.getGuild().getSelfMember().getIdLong()) {
                            return; // Skip duplicate log
                        }
                    }
                }
                
                // This unban was not issued by the bot, so log it
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle(CustomEmojis.SUCCESS + " Member Unbanned")
                        .setThumbnail(user.getAvatarUrl())
                        .addField("User", user.getAsMention(), true)
                        .addField("Username", user.getName(), true)
                        .setFooter("User ID: " + user.getId())
                        .setTimestamp(OffsetDateTime.now());
                
                if (!auditLogs.isEmpty()) {
                    AuditLogEntry entry = auditLogs.get(0);
                    if (entry.getTargetIdLong() == user.getIdLong()) {
                        User unbannedBy = entry.getUser();
                        if (unbannedBy != null) {
                            embed.addField("Unbanned by", unbannedBy.getAsMention(), true);
                        }
                    }
                }
                SafeRestAction.queue(
                    logChannel.sendMessageEmbeds(embed.build()), 
                    "log unban with moderator",
                    success -> trackLogMessage(success.getId())
                );
            }
        );
    }
    
    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "moderation", "mutes")) {
            return;
        }
        
        TextChannel logChannel = getLogChannel(event.getGuild(), "moderation");
        if (logChannel == null) return;
        
        Member member = event.getMember();
        User user = member.getUser();
        
        // Check audit logs to see who issued the timeout.
        // If it was the bot itself, skip logging here because the command
        // (e.g. TimeoutCommand) already logged via AutoLogUtils.logTimeout().
        SafeRestAction.queue(
            event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).limit(5),
            "retrieve audit logs for timeout",
            auditLogs -> {
                for (AuditLogEntry entry : auditLogs) {
                    if (entry.getTargetIdLong() == user.getIdLong()) {
                        User moderator = entry.getUser();
                        // If issued by the bot, the command already logged it
                        if (moderator != null && moderator.getIdLong() == event.getGuild().getSelfMember().getIdLong()) {
                            return; // Skip duplicate log
                        }
                        break;
                    }
                }
                
                // This timeout was not issued by the bot, so log it
                EmbedBuilder embed = new EmbedBuilder()
                        .setThumbnail(user.getAvatarUrl())
                        .addField("User", user.getAsMention(), true)
                        .addField("Username", user.getName(), true)
                        .setFooter("User ID: " + user.getId())
                        .setTimestamp(OffsetDateTime.now());
                
                if (event.getNewTimeOutEnd() != null) {
                    embed.setColor(Color.RED)
                         .setTitle(CustomEmojis.WARN + " Member Timed Out")
                         .addField("Timeout Until", 
                             event.getNewTimeOutEnd().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")), true);
                } else {
                    embed.setColor(Color.GREEN)
                         .setTitle(CustomEmojis.SUCCESS + " Member Timeout Removed");
                }
                
                for (AuditLogEntry entry : auditLogs) {
                    if (entry.getTargetIdLong() == user.getIdLong()) {
                        User moderator = entry.getUser();
                        String reason = entry.getReason();
                        
                        if (moderator != null) {
                            embed.addField("Moderator", moderator.getAsMention(), true);
                        }
                        if (reason != null && !reason.isEmpty()) {
                            embed.addField("Reason", reason, false);
                        }
                        break;
                    }
                }
                SafeRestAction.queue(
                    logChannel.sendMessageEmbeds(embed.build()), 
                    "log timeout with moderator",
                    success -> trackLogMessage(success.getId())
                );
            }
        );
    }
    
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "voice", null)) {
            return;
        }
        
        TextChannel logChannel = getLogChannel(event.getGuild(), "voice");
        if (logChannel == null) return;
        
        Member member = event.getMember();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.CYAN)
                .addField("User", member.getAsMention(), true)
                .setFooter("User ID: " + member.getId())
                .setTimestamp(OffsetDateTime.now());
        
        if (event.getChannelJoined() != null && event.getChannelLeft() == null) {
            // User joined a voice channel
            embed.setTitle("🔊 Voice Channel Joined")
                 .addField("Channel", event.getChannelJoined().getName(), true);
        } else if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            // User left a voice channel
            embed.setTitle("🔇 Voice Channel Left")
                 .addField("Channel", event.getChannelLeft().getName(), true);
        } else if (event.getChannelJoined() != null && event.getChannelLeft() != null) {
            // User moved between voice channels
            embed.setTitle(CustomEmojis.REFRESH + " Voice Channel Moved")
                 .addField("From", event.getChannelLeft().getName(), true)
                 .addField("To", event.getChannelJoined().getName(), true);
        }
        
        SafeRestAction.queue(
            logChannel.sendMessageEmbeds(embed.build()), 
            "log voice channel event",
            success -> trackLogMessage(success.getId())
        );
    }
    
    /**
     * Checks if auto-logging is enabled for a specific event type
     */
    private boolean isAutoLogEnabled(String guildId, String action, String actionType) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            
            // Check for specific setting first (e.g., "autoLog_message_deletes")
            if (actionType != null) {
                String specificKey = "autoLog_" + action.toLowerCase() + "_" + actionType.toLowerCase();
                Boolean specificSetting = (Boolean) settings.get(specificKey);
                if (specificSetting != null) {
                    return specificSetting;
                }
            }
            
            // Check for general setting (e.g., "autoLog_message")
            String generalKey = "autoLog_" + action.toLowerCase();
            Boolean generalSetting = (Boolean) settings.get(generalKey);
            if (generalSetting != null) {
                return generalSetting;
            }
            
            // Check for "all events" setting
            Boolean allSetting = (Boolean) settings.get("autoLog_all");
            if (allSetting != null) {
                return allSetting;
            }
            
            return false; // Default to disabled
            
        } catch (Exception e) {
            logger.warn("Error checking auto-log settings for guild {}: {}", guildId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the configured log channel for a guild and specific log type
     */
    private TextChannel getLogChannel(Guild guild, String logType) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guild.getId());
            
            // Check for type-specific log channel first (from /logging command)
            if (logType != null) {
                String typeChannelId = (String) settings.get(logType + "_log_channel");
                if (typeChannelId != null) {
                    TextChannel logChannel = guild.getTextChannelById(typeChannelId);
                    if (logChannel != null && logChannel.canTalk()) {
                        return logChannel;
                    }
                }
            }
            
            // Fall back to global log channel (legacy)
            String logChannelId = (String) settings.get("logChannelId");
            if (logChannelId == null) {
                return null;
            }
            
            TextChannel logChannel = guild.getTextChannelById(logChannelId);
            if (logChannel == null || !logChannel.canTalk()) {
                return null;
            }
            
            return logChannel;
            
        } catch (Exception e) {
            logger.warn("Error getting log channel for guild {}: {}", guild.getId(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Truncates text to a maximum length for embed fields
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
