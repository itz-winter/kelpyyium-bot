package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;

/**
 * Welcome system configuration command
 */
public class WelcomeCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.welcome")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need the `admin.welcome` permission to configure welcome settings."
            )).setEphemeral(true).queue();
            return;
        }

        // Check if no required parameters provided - show help
        if (event.getOption("action") == null) {
            showWelcomeHelp(event);
            return;
        }

        String action = event.getOption("action").getAsString();
        
        switch (action) {
            case "view" -> handleView(event);
            case "message" -> handleMessage(event);
            case "embed-color" -> handleEmbedColor(event);
            case "auto-role" -> handleAutoRole(event);
            case "enable" -> handleEnable(event);
            case "test" -> handleTest(event);
            case "dm-message" -> handleDMMessage(event);
            case "dm-enable" -> handleDMEnable(event);
            case "dm-test" -> handleDMTest(event);
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Action", 
                    "Invalid action: `" + action + "`\n" +
                    "Valid actions: `view`, `message`, `embed-color`, `auto-role`, `enable`, `test`, `dm-message`, `dm-enable`, `dm-test`\n\n" +
                    "Use `/welcome` without arguments to see the help guide."
                )).setEphemeral(true).queue();
            }
        }
    }

    private void showWelcomeHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(CustomEmojis.INFO + " Welcome Command Help")
                .setDescription("Configure welcome messages and auto-roles for new members")
                .setColor(0x00FF00)
                .addField("**Basic Usage**",
                    "`/welcome action:view` - View current settings\n" +
                    "`/welcome action:message text:<message>` - Set welcome message\n" +
                    "`/welcome action:embed-color color:#hex` - Set embed color\n" +
                    "`/welcome action:auto-role [role:@role]` - Set auto-role\n" +
                    "`/welcome action:enable enabled:true/false` - Toggle system\n" +
                    "`/welcome action:test` - Test welcome message\n" +
                    "`/welcome action:dm-message text:<message>` - Set DM welcome message\n" +
                    "`/welcome action:dm-enable enabled:true/false` - Toggle DM system\n" +
                    "`/welcome action:dm-test` - Test DM welcome message", false)
                .addField("**Parameters**",
                    "• `action` - What to configure (required)\n" +
                    "• `text` - Welcome message (for message/dm-message actions)\n" +
                    "• `color` - Hex color code like #FF0000 (for embed-color action)\n" +
                    "• `role` - Role to assign new members (for auto-role action)\n" +
                    "• `enabled` - Enable/disable system (for enable/dm-enable actions)", false)
                .addField("**Message Placeholders**",
                    "• `{user}` - Mentions the new member\n" +
                    "• `{username}` - Member's display name\n" +
                    "• `{server}` - Server name\n" +
                    "• `{membercount}` - Total member count", false)
                .addField("**Examples**",
                    "`/welcome action:message text:Welcome {user} to {server}! You're member #{membercount}`\n" +
                    "`/welcome action:embed-color color:#FF69B4`\n" +
                    "`/welcome action:auto-role role:@Member`\n" +
                    "`/welcome action:enable enabled:true`\n" +
                    "`/welcome action:dm-message text:Welcome to {server}! Check out our rules channel.`\n" +
                    "`/welcome action:dm-enable enabled:true`", false)
                .setFooter("Use -!help to dismiss future help messages • Requires admin.welcome permission");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleView(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle(CustomEmojis.INFO + " Welcome System Configuration")
                    .setDescription("Current welcome settings for **" + event.getGuild().getName() + "**");

            // Channel and status
            String welcomeChannelId = (String) settings.get("welcomeChannelId");
            Boolean welcomeEnabled = (Boolean) settings.get("welcomeEnabled");
            
            embed.addField(CustomEmojis.INFO + " Channel & Status", 
                          "**Channel:** " + (welcomeChannelId != null ? "<#" + welcomeChannelId + ">" : "Not set") + "\n" +
                          "**Enabled:** " + (Boolean.TRUE.equals(welcomeEnabled) ? CustomEmojis.ON : CustomEmojis.OFF), 
                          false);

            // Message settings
            String welcomeMessage = (String) settings.getOrDefault("welcomeMessage", "Welcome to {server}, {user}! We're glad to have you here. 🎉");
            String embedColor = (String) settings.getOrDefault("welcomeEmbedColor", "#00FF00");
            
            embed.addField(CustomEmojis.INFO + " Message Settings", 
                          "**Custom Message:** " + (welcomeMessage.length() > 100 ? welcomeMessage.substring(0, 97) + "..." : welcomeMessage) + "\n" +
                          "**Embed Color:** " + embedColor, 
                          false);

            // Auto-role
            String autoRoleId = (String) settings.get("welcomeAutoRoleId");
            embed.addField(CustomEmojis.INFO + " Auto-Role", 
                          "**Role:** " + (autoRoleId != null ? "<@&" + autoRoleId + ">" : "Not set"), 
                          false);

            // DM Settings
            Boolean dmEnabled = (Boolean) settings.get("welcomeDMEnabled");
            String dmMessage = (String) settings.get("welcomeDMMessage");
            embed.addField(CustomEmojis.INFO + " DM Welcome Settings",
                          "**DM Enabled:** " + (Boolean.TRUE.equals(dmEnabled) ? CustomEmojis.ON : CustomEmojis.OFF) + "\n" +
                          "**DM Message:** " + (dmMessage != null ? 
                              (dmMessage.length() > 80 ? dmMessage.substring(0, 77) + "..." : dmMessage) : 
                              "Uses default or channel message"),
                          false);

            // Placeholders
            embed.addField(CustomEmojis.INFO + " Available Placeholders", 
                          "`{user}` - Mentions the user\n" +
                          "`{username}` - User's display name\n" +
                          "`{server}` - Server name\n" +
                          "`{membercount}` - Total member count", 
                          false);

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Settings Error", 
                "Failed to retrieve welcome settings: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleMessage(SlashCommandInteractionEvent event) {
        OptionMapping messageOption = event.getOption("text");
        if (messageOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter [100]", 
                "Please specify the welcome message text.\n" +
                "Error Code: **100** - Missing Parameter\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        String message = messageOption.getAsString();
        
        if (message.length() > 1000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Message Too Long [105]", 
                "Welcome message must be 1000 characters or less.\n" +
                "Error Code: **105** - Input Too Long\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "welcomeMessage", message);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Welcome Message Updated", 
                "**New Message:** " + (message.length() > 200 ? message.substring(0, 197) + "..." : message) + "\n\n" +
                "**Available placeholders:** `{user}`, `{username}`, `{server}`, `{membercount}`"
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update welcome message: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleEmbedColor(SlashCommandInteractionEvent event) {
        OptionMapping colorOption = event.getOption("color");
        if (colorOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the embed color (hex format like #FF0000)."
            )).setEphemeral(true).queue();
            return;
        }

        String color = colorOption.getAsString();
        
        if (!color.matches("#[0-9A-Fa-f]{6}")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Color", "Color must be in hex format (e.g., #FF0000 for red, #00FF00 for green)."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "welcomeEmbedColor", color.toUpperCase());
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Embed Color Updated", 
                "Welcome message embed color has been set to **" + color.toUpperCase() + "**."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update embed color: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleAutoRole(SlashCommandInteractionEvent event) {
        OptionMapping roleOption = event.getOption("role");
        
        try {
            String guildId = event.getGuild().getId();
            
            if (roleOption == null) {
                // Remove auto-role
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeAutoRoleId", null);
                
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Auto-Role Removed", 
                    "New members will no longer automatically receive a role."
                )).queue();
            } else {
                // Set auto-role
                String roleId = roleOption.getAsRole().getId();
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeAutoRoleId", roleId);
                
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Auto-Role Updated", 
                    "New members will automatically receive the " + roleOption.getAsRole().getAsMention() + " role."
                )).queue();
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update auto-role: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleEnable(SlashCommandInteractionEvent event) {
        OptionMapping enableOption = event.getOption("enabled");
        if (enableOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify whether to enable or disable the welcome system."
            )).setEphemeral(true).queue();
            return;
        }

        boolean enabled = enableOption.getAsBoolean();
        
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            String welcomeChannelId = (String) settings.get("welcomeChannelId");
            
            if (enabled && welcomeChannelId == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "No Welcome Channel", 
                    "Please set a welcome channel first using `/settings welcome-channel`."
                )).setEphemeral(true).queue();
                return;
            }
            
            ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeEnabled", enabled);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Welcome System " + (enabled ? "Enabled" : "Disabled"), 
                "The welcome system has been " + (enabled ? "**enabled**" : "**disabled**") + "."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update welcome system: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleTest(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            
            String welcomeMessage = (String) settings.getOrDefault("welcomeMessage", "Welcome to {server}, {user}! We're glad to have you here. 🎉");
            String embedColor = (String) settings.getOrDefault("welcomeEmbedColor", "#00FF00");
            
            // Replace placeholders for test
            String testMessage = welcomeMessage
                    .replace("{user}", event.getUser().getAsMention())
                    .replace("{username}", event.getUser().getEffectiveName())
                    .replace("{server}", event.getGuild().getName())
                    .replace("{membercount}", String.valueOf(event.getGuild().getMemberCount()));
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Integer.parseInt(embedColor.substring(1), 16))
                    .setTitle("👋 Welcome!")
                    .setDescription(testMessage)
                    .setThumbnail(event.getUser().getAvatarUrl())
                    .setFooter("Member #" + event.getGuild().getMemberCount());
            
            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Test Failed", 
                "Failed to generate test welcome message: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleDMMessage(SlashCommandInteractionEvent event) {
        OptionMapping messageOption = event.getOption("text");
        if (messageOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter [100]", 
                "Please specify the DM welcome message text.\n" +
                "Error Code: **100** - Missing Parameter\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        String message = messageOption.getAsString();
        
        if (message.length() > 1000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Message Too Long [105]", 
                "DM welcome message must be 1000 characters or less.\n" +
                "Error Code: **105** - Input Too Long\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "welcomeDMMessage", message);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "DM Welcome Message Updated", 
                "**New DM Message:** " + (message.length() > 200 ? message.substring(0, 197) + "..." : message) + "\n\n" +
                "**Available placeholders:** `{user}`, `{username}`, `{server}`, `{membercount}`\n" +
                "**Note:** If no custom DM message is set, the channel welcome message will be used."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update DM welcome message: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleDMEnable(SlashCommandInteractionEvent event) {
        OptionMapping enableOption = event.getOption("enabled");
        if (enableOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify whether to enable or disable the DM welcome system."
            )).setEphemeral(true).queue();
            return;
        }

        boolean enabled = enableOption.getAsBoolean();
        
        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "welcomeDMEnabled", enabled);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "DM Welcome System " + (enabled ? "Enabled" : "Disabled"), 
                "DM welcome messages have been " + (enabled ? "**enabled**" : "**disabled**") + ".\n" +
                (enabled ? "New members will receive a direct message when they join." : 
                          "New members will no longer receive direct messages.")
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update DM welcome system: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleDMTest(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            
            // Get DM message or fall back to channel message
            String dmMessage = (String) settings.get("welcomeDMMessage");
            if (dmMessage == null) {
                dmMessage = (String) settings.getOrDefault("welcomeMessage", 
                    "Welcome to {server}, {user}! We're glad to have you here. 🎉");
            }
            
            String embedColor = (String) settings.getOrDefault("welcomeEmbedColor", "#00FF00");
            
            // Replace placeholders for test
            String testMessage = dmMessage
                    .replace("{user}", event.getUser().getAsMention())
                    .replace("{username}", event.getUser().getEffectiveName())
                    .replace("{server}", event.getGuild().getName())
                    .replace("{membercount}", String.valueOf(event.getGuild().getMemberCount()));
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Integer.parseInt(embedColor.substring(1), 16))
                    .setTitle("👋 Welcome DM Test!")
                    .setDescription(testMessage)
                    .setThumbnail(event.getUser().getAvatarUrl())
                    .setFooter("Member #" + event.getGuild().getMemberCount() + " • This is a test DM");
            
            // Send DM to user
            event.getUser().openPrivateChannel()
                .flatMap(channel -> channel.sendMessageEmbeds(embed.build()))
                .queue(
                    success -> {
                        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "DM Test Sent", 
                            "A test DM welcome message has been sent to your direct messages."
                        )).setEphemeral(true).queue();
                    },
                    failure -> {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "DM Test Failed", 
                            "Failed to send test DM. Make sure your DMs are open: " + failure.getMessage()
                        )).setEphemeral(true).queue();
                    }
                );

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Test Failed", 
                "Failed to generate test DM welcome message: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    @Override
    public String getName() {
        return "welcome";
    }

    @Override
    public String getDescription() {
        return "Configure welcome messages and auto-roles";
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
        OptionData actionOption = new OptionData(OptionType.STRING, "action", "Welcome configuration action", true);
        actionOption.addChoice("View Settings", "view");
        actionOption.addChoice("Set Message", "message");
        actionOption.addChoice("Set Embed Color", "embed-color");
        actionOption.addChoice("Set Auto-Role", "auto-role");
        actionOption.addChoice("Enable/Disable", "enable");
        actionOption.addChoice("Test Message", "test");
        actionOption.addChoice("Set DM Message", "dm-message");
        actionOption.addChoice("Enable/Disable DM", "dm-enable");
        actionOption.addChoice("Test DM Message", "dm-test");

        return Commands.slash("welcome", "Configure welcome messages and auto-roles")
                .addOptions(
                    actionOption,
                    new OptionData(OptionType.STRING, "text", "Welcome message text (use {user}, {username}, {server}, {membercount})", false),
                    new OptionData(OptionType.STRING, "color", "Hex color code (e.g., #FF0000)", false),
                    new OptionData(OptionType.ROLE, "role", "Role to assign new members (leave empty to disable)", false),
                    new OptionData(OptionType.BOOLEAN, "enabled", "Enable/disable welcome system", false)
                );
    }
}
