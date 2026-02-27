package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;

/**
 * Compressed settings management command with all functionality in one root command
 */
public class SettingsCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.settings")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need the `admin.settings` permission to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        // Check if no arguments provided - show help
        OptionMapping settingOption = event.getOption("setting");
        if (settingOption == null) {
            showSettingsHelp(event);
            return;
        }

        String setting = settingOption.getAsString();
        
        // Check if any value option is provided
        boolean hasValue = event.getOption("value") != null || 
                          event.getOption("text") != null || 
                          event.getOption("enabled") != null || 
                          event.getOption("role") != null || 
                          event.getOption("channel") != null;
        
        // If no value provided, show current setting
        if (!hasValue) {
            showCurrentSetting(event, setting);
            return;
        }

        // Update setting with provided value
        updateSetting(event, setting, null);
    }

    private void showSettingsHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle(CustomEmojis.SETTING + " Settings Command Help")
                .setDescription("Configure server settings for various bot features")
                .addField("**Economy Settings**", 
                    "```/settings setting:daily-reward value:100```\n" +
                    "```/settings setting:work-reward text:50-150```\n" +
                    "```/settings setting:work-cooldown value:4```\n" +
                    "```/settings setting:points-per-message value:5```", false)
                .addField("**Leveling Settings**", 
                    "```/settings setting:xp-per-message value:15```", false)
                .addField("**Moderation Settings**", 
                    "```/settings setting:max-warnings value:3```\n" +
                    "```/settings setting:warn-expiry value:30```\n" +
                    "```/settings setting:mute-role role:@Muted```", false)
                .addField("**Channel Settings**", 
                    "```/settings setting:log-channel channel:#logs```\n" +
                    "```/settings setting:welcome-channel channel:#welcome```", false)
                .addField("**Feature Toggles**", 
                    "```/settings setting:enable-economy enabled:true```\n" +
                    "```/settings setting:enable-leveling enabled:true```\n" +
                    "```/settings setting:enable-automod enabled:true```\n" +
                    "```/settings setting:pronouns enabled:true```\n" +
                    "```/settings setting:dm-notifications enabled:true```", false)
                .addField("**Error Codes**", 
                    "**S-Series (Settings):** S01-S99\n" +
                    "See full error code guide with `/help error-codes`", false)
                .addField("**View Settings**", 
                    "```/settings setting:daily-reward``` - View current daily reward\n" +
                    "```/settings``` - Show this help", false)
                .setFooter("Use -!help to dismiss future help messages");

        event.replyEmbeds(embed.build()).queue();
    }

    private void showCurrentSetting(SlashCommandInteractionEvent event, String setting) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> guildData = ServerBot.getStorageManager().getGuildSettings(guildId);
            
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                    .setTitle(CustomEmojis.SETTING + " Current Setting")
                    .addField("Setting", "`" + setting + "`", true);

            Object value = null;
            String displayValue = "Not Set";

            switch (setting) {
                case "daily-reward" -> {
                    value = guildData.get("economy.dailyReward");
                    displayValue = value != null ? String.valueOf(((Number) value).intValue()) + " points" : "100 points (default)";
                }
                case "work-reward" -> {
                    int min = ((Number) guildData.getOrDefault("economy.workRewardMin", 50)).intValue();
                    int max = ((Number) guildData.getOrDefault("economy.workRewardMax", 150)).intValue();
                    displayValue = min + "-" + max + " points";
                }
                case "work-cooldown" -> {
                    value = guildData.get("economy.workCooldown");
                    displayValue = value != null ? String.valueOf(((Number) value).intValue()) + " hours" : "4 hours (default)";
                }
                case "points-per-message" -> {
                    value = guildData.get("economy.pointsPerMessage");
                    displayValue = value != null ? String.valueOf(((Number) value).intValue()) + " points" : "5 points (default)";
                }
                case "xp-per-message" -> {
                    value = guildData.get("leveling.xpPerMessage");
                    displayValue = value != null ? String.valueOf(((Number) value).intValue()) + " XP" : "15 XP (default)";
                }
                case "max-warnings" -> {
                    value = guildData.get("maxWarnings");
                    displayValue = value != null ? String.valueOf(((Number) value).intValue()) + " warnings" : "3 warnings (default)";
                }
                case "warn-expiry" -> {
                    value = guildData.get("warnExpiry");
                    displayValue = value != null ? String.valueOf(((Number) value).intValue()) + " days" : "30 days (default)";
                }
                case "mute-role" -> {
                    String roleId = (String) guildData.get("muteRoleId");
                    if (roleId != null) {
                        Role role = event.getGuild().getRoleById(roleId);
                        displayValue = role != null ? role.getAsMention() : "Role Deleted";
                    }
                }
                case "log-channel" -> {
                    String channelId = (String) guildData.get("logChannelId");
                    if (channelId != null) {
                        TextChannel channel = event.getGuild().getTextChannelById(channelId);
                        displayValue = channel != null ? channel.getAsMention() : "Channel Deleted";
                    }
                }
                case "welcome-channel" -> {
                    String channelId = (String) guildData.get("welcomeChannelId");
                    if (channelId != null) {
                        TextChannel channel = event.getGuild().getTextChannelById(channelId);
                        displayValue = channel != null ? channel.getAsMention() : "Channel Deleted";
                    }
                }
                case "enable-economy" -> {
                    Boolean enabled = (Boolean) guildData.get("enableEconomy");
                    displayValue = enabled != null ? (enabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") : CustomEmojis.ON + " Enabled (default)";
                }
                case "enable-leveling" -> {
                    Boolean enabled = (Boolean) guildData.get("enableLeveling");
                    displayValue = enabled != null ? (enabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") : CustomEmojis.ON + " Enabled (default)";
                }
                case "enable-automod" -> {
                    Boolean enabled = (Boolean) guildData.get("enableAutomod");
                    displayValue = enabled != null ? (enabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") : CustomEmojis.OFF + " Disabled (default)";
                }
                case "pronouns" -> {
                    Boolean enabled = (Boolean) guildData.get("enablePronouns");
                    displayValue = enabled != null ? (enabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") : CustomEmojis.OFF + " Disabled (default)";
                }
                case "dm-notifications" -> {
                    Boolean enabled = (Boolean) guildData.get("dmNotifications");
                    displayValue = enabled == null || enabled ? CustomEmojis.ON + " Enabled (default)" : CustomEmojis.OFF + " Disabled";
                }
                default -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Setting", "Setting `" + setting + "` not found. Use `/settings` to see available settings."
                    )).setEphemeral(true).queue();
                    return;
                }
            }

            embed.addField("Current Value", displayValue, true);
            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "View Failed", 
                "Failed to view setting: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void updateSetting(SlashCommandInteractionEvent event, String setting, OptionMapping valueOption) {
        // Get the appropriate option based on the setting type
        OptionMapping actualOption = getCorrectOptionForSetting(event, setting);
        
        switch (setting) {
            case "daily-reward" -> handleDailyReward(event, actualOption);
            case "work-reward" -> handleWorkReward(event, actualOption);
            case "work-cooldown" -> handleWorkCooldown(event, actualOption);
            case "points-per-message" -> handlePointsPerMessage(event, actualOption);
            case "xp-per-message" -> handleXpPerMessage(event, actualOption);
            case "max-warnings" -> handleMaxWarnings(event, actualOption);
            case "warn-expiry" -> handleWarnExpiry(event, actualOption);
            case "mute-role" -> handleMuteRole(event, actualOption);
            case "log-channel" -> handleLogChannel(event, actualOption);
            case "welcome-channel" -> handleWelcomeChannel(event, actualOption);
            case "enable-economy" -> handleEnableEconomy(event, actualOption);
            case "enable-leveling" -> handleEnableLeveling(event, actualOption);
            case "enable-automod" -> handleEnableAutomod(event, actualOption);
            case "pronouns" -> handlePronouns(event, actualOption);
            case "dm-notifications" -> handleDmNotifications(event, actualOption);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Setting", "Setting `" + setting + "` not found. Use `/settings` to see available settings."
            )).setEphemeral(true).queue();
        }
    }

    private OptionMapping getCorrectOptionForSetting(SlashCommandInteractionEvent event, String setting) {
        return switch (setting) {
            case "work-reward" -> event.getOption("text");
            case "mute-role" -> event.getOption("role");
            case "log-channel", "welcome-channel" -> event.getOption("channel");
            case "enable-economy", "enable-leveling", "enable-automod", "pronouns", "dm-notifications" -> event.getOption("enabled");
            default -> event.getOption("value");
        };
    }

    private void handleDailyReward(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "S01", "Missing parameter: Please specify the daily reward amount."
            )).setEphemeral(true).queue();
            return;
        }

        long amount = valueOption.getAsLong();
        
        if (amount < 1 || amount > 10000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "S02", "Invalid amount: Daily reward must be between 1 and 10,000 points."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "economy.dailyReward", amount);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Daily Reward Updated", 
                "Daily reward has been set to **" + amount + " points**."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "S03", "Update failed: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleWorkReward(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "S04", "Missing parameter: Please specify work reward range using the 'text' parameter (format: min-max, e.g., 50-150)."
            )).setEphemeral(true).queue();
            return;
        }

        String rewardRange = valueOption.getAsString();
        String[] parts = rewardRange.split("-");
        
        if (parts.length != 2) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "S05", "Invalid format: Use format 'min-max' (e.g., 50-150)."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            int minReward = Integer.parseInt(parts[0].trim());
            int maxReward = Integer.parseInt(parts[1].trim());
            
            if (minReward < 1 || maxReward < 1 || minReward > maxReward || maxReward > 10000) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "S06", "Invalid range: Min must be ≥1, max must be ≤10,000, and min ≤ max."
                )).setEphemeral(true).queue();
                return;
            }

            String guildId = event.getGuild().getId();
            ServerBot.getStorageManager().updateGuildSettings(guildId, "economy.workRewardMin", minReward);
            ServerBot.getStorageManager().updateGuildSettings(guildId, "economy.workRewardMax", maxReward);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Work Reward Updated", 
                "Work reward range set to **" + minReward + "-" + maxReward + " points**."
            )).queue();

        } catch (NumberFormatException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "S07", "Invalid numbers: Please use valid integers (e.g., 50-150)."
            )).setEphemeral(true).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "S08", "Update failed: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleWorkCooldown(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption.getType() != OptionType.INTEGER) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please provide an integer value for work cooldown in hours."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            int cooldown = Math.abs(valueOption.getAsInt());
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "economy.workCooldown", cooldown);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Work cooldown set to **%d** hours", cooldown)
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update work cooldown: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handlePointsPerMessage(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption.getType() != OptionType.INTEGER) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please provide an integer value for points per message."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            int points = Math.abs(valueOption.getAsInt());
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "economy.pointsPerMessage", points);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Points per message set to **%d**", points)
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update points per message: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleXpPerMessage(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption.getType() != OptionType.INTEGER) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please provide an integer value for XP per message."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            int xp = Math.abs(valueOption.getAsInt());
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "leveling.xpPerMessage", xp);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("XP per message set to **%d**", xp)
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update XP per message: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleMaxWarnings(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption.getType() != OptionType.INTEGER) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please provide an integer value for maximum warnings."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            int maxWarnings = Math.abs(valueOption.getAsInt());
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "maxWarnings", maxWarnings);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Maximum warnings set to **%d**", maxWarnings)
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update maximum warnings: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleWarnExpiry(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption.getType() != OptionType.INTEGER) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please provide an integer value for warning expiry in days."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            int expiry = Math.abs(valueOption.getAsInt());
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "warnExpiry", expiry);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Warning expiry set to **%d** days", expiry)
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update warning expiry: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleMuteRole(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please provide a role for the mute role setting using the 'role' parameter."
            )).setEphemeral(true).queue();
            return;
        }
        
        if (valueOption.getType() != OptionType.ROLE) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please provide a role for the mute role setting."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            Role muteRole = valueOption.getAsRole();
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "muteRoleId", muteRole.getId());
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Mute role set to %s", muteRole.getAsMention())
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update mute role: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleLogChannel(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please provide a channel for the log channel setting using the 'channel' parameter."
            )).setEphemeral(true).queue();
            return;
        }
        
        if (valueOption.getType() != OptionType.CHANNEL) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please provide a channel for the log channel setting."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            TextChannel logChannel = valueOption.getAsChannel().asTextChannel();
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "logChannelId", logChannel.getId());
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Log channel set to %s", logChannel.getAsMention())
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update log channel: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleWelcomeChannel(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please provide a channel for the welcome channel setting using the 'channel' parameter."
            )).setEphemeral(true).queue();
            return;
        }
        
        if (valueOption.getType() != OptionType.CHANNEL) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please provide a channel for the welcome channel setting."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            TextChannel welcomeChannel = valueOption.getAsChannel().asTextChannel();
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeChannelId", welcomeChannel.getId());
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Welcome channel set to %s", welcomeChannel.getAsMention())
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update welcome channel: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleEnableEconomy(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify true/false for economy feature using the 'enabled' parameter."
            )).setEphemeral(true).queue();
            return;
        }
        
        if (valueOption.getType() != OptionType.BOOLEAN) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please specify true/false for economy feature."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            boolean enabled = valueOption.getAsBoolean();
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "enableEconomy", enabled);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Economy system %s", enabled ? "**enabled**" : "**disabled**")
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update economy feature: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleEnableLeveling(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify true/false for leveling feature using the 'enabled' parameter."
            )).setEphemeral(true).queue();
            return;
        }
        
        if (valueOption.getType() != OptionType.BOOLEAN) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please specify true/false for leveling feature."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            boolean enabled = valueOption.getAsBoolean();
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "enableLeveling", enabled);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Leveling system %s", enabled ? "**enabled**" : "**disabled**")
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update leveling feature: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleEnableAutomod(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify true/false for automod feature using the 'enabled' parameter."
            )).setEphemeral(true).queue();
            return;
        }
        
        if (valueOption.getType() != OptionType.BOOLEAN) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please specify true/false for automod feature."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            boolean enabled = valueOption.getAsBoolean();
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "enableAutomod", enabled);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Automod system %s", enabled ? "**enabled**" : "**disabled**")
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update automod feature: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handlePronouns(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify true/false for pronouns system using the 'enabled' parameter."
            )).setEphemeral(true).queue();
            return;
        }
        
        if (valueOption.getType() != OptionType.BOOLEAN) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please specify true/false for pronouns system."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            boolean enabled = valueOption.getAsBoolean();
            String guildId = event.getGuild().getId();
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "enablePronouns", enabled);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("Pronouns system %s", enabled ? "**enabled**" : "**disabled**")
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update pronouns system: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleDmNotifications(SlashCommandInteractionEvent event, OptionMapping valueOption) {
        if (valueOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify true/false for DM notifications using the 'enabled' parameter."
            )).setEphemeral(true).queue();
            return;
        }

        if (valueOption.getType() != OptionType.BOOLEAN) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Value Type", "Please specify true/false for DM notifications."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            boolean enabled = valueOption.getAsBoolean();
            String guildId = event.getGuild().getId();

            ServerBot.getStorageManager().updateGuildSettings(guildId, "dmNotifications", enabled);

            String note = enabled ? "" : "\n\n*Note: Punishment DMs to the punished user will still be sent.*";
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Setting Updated",
                String.format("DM notifications %s%s", enabled ? "**enabled**" : "**disabled**", note)
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed",
                "Failed to update DM notifications: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    @Override
    public String getName() {
        return "settings";
    }

    @Override
    public String getDescription() {
        return "Configure server settings";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        OptionData settingOption = new OptionData(OptionType.STRING, "setting", "Setting to configure", false)
                .addChoice("Daily Reward", "daily-reward")
                .addChoice("Work Reward", "work-reward")
                .addChoice("Work Cooldown", "work-cooldown")
                .addChoice("Points per Message", "points-per-message")
                .addChoice("XP per Message", "xp-per-message")
                .addChoice("Max Warnings", "max-warnings")
                .addChoice("Warning Expiry", "warn-expiry")
                .addChoice("Mute Role", "mute-role")
                .addChoice("Log Channel", "log-channel")
                .addChoice("Welcome Channel", "welcome-channel")
                .addChoice("Enable Economy", "enable-economy")
                .addChoice("Enable Leveling", "enable-leveling")
                .addChoice("Enable Automod", "enable-automod")
                .addChoice("Pronouns System", "pronouns");

        return Commands.slash("settings", "Configure server settings")
                .addOptions(settingOption)
                .addOption(OptionType.INTEGER, "value", "Numeric value for the setting", false)
                .addOption(OptionType.STRING, "text", "Text value for the setting", false)
                .addOption(OptionType.BOOLEAN, "enabled", "Enable/disable feature", false)
                .addOption(OptionType.ROLE, "role", "Role for the setting", false)
                .addOption(OptionType.CHANNEL, "channel", "Channel for the setting", false);
    }
}
