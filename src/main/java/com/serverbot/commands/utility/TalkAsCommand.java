package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;

/**
 * TalkAs command for sending webhook messages with custom names and avatars
 */
public class TalkAsCommand implements SlashCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(TalkAsCommand.class);

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "talkas.use")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", 
                "You need the `talkas.use` permission to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        OptionMapping nameOption = event.getOption("name");
        OptionMapping messageOption = event.getOption("message");
        OptionMapping avatarOption = event.getOption("avatar");
        
        if (nameOption == null || messageOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters [T01]", 
                "Both `name` and `message` are required.\n" +
                "Error Code: **T01** - Missing TalkAs Parameters\n" +
                "Use `/error category:T` for full T-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        String name = nameOption.getAsString();
        String message = messageOption.getAsString();
        String avatarUrl = avatarOption != null ? avatarOption.getAsString() : null;

        // Validate name length
        if (name.length() < 1 || name.length() > 80) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Name [T02]", 
                "Name must be between 1 and 80 characters.\n" +
                "Error Code: **T02** - Invalid TalkAs Name\n" +
                "Use `/error category:T` for full T-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        // Validate message length
        if (message.length() < 1 || message.length() > 2000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Message [T03]", 
                "Message must be between 1 and 2000 characters.\n" +
                "Error Code: **T03** - Invalid TalkAs Message\n" +
                "Use `/error category:T` for full T-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        // Validate avatar URL if provided
        if (avatarUrl != null && !isValidImageUrl(avatarUrl)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Avatar URL [T04]", 
                "Avatar must be a valid image URL (jpg, jpeg, png, gif, webp).\n" +
                "Error Code: **T04** - Invalid Avatar URL\n" +
                "Use `/error category:T` for full T-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        // Check for attachment avatar - slash commands don't support attachments
        // This will be handled via prefix commands only

        TextChannel channel = event.getChannel().asTextChannel();
        
        // Send initial response
        event.reply("🔄 Preparing webhook message...").setEphemeral(true).queue();

        // Find or create webhook
        findOrCreateWebhook(channel, name, avatarUrl, message, event);
    }

    private void findOrCreateWebhook(TextChannel channel, String name, String avatarUrl, String message, SlashCommandInteractionEvent event) {
        channel.retrieveWebhooks().queue(
            webhooks -> {
                // Look for existing webhook for this bot
                Webhook webhook = webhooks.stream()
                    .filter(w -> w.getName().equals("ServerBot TalkAs"))
                    .findFirst()
                    .orElse(null);

                if (webhook != null) {
                    sendWebhookMessage(webhook, name, avatarUrl, message, event);
                } else {
                    // Create new webhook
                    channel.createWebhook("ServerBot TalkAs")
                        .queue(
                            newWebhook -> sendWebhookMessage(newWebhook, name, avatarUrl, message, event),
                            throwable -> {
                                logger.warn("Failed to create webhook: {}", throwable.getMessage());
                                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                    "Webhook Creation Failed [T05]", 
                                    "Failed to create webhook. Make sure I have 'Manage Webhooks' permission.\n" +
                                    "Error Code: **T05** - Webhook Creation Failed\n" +
                                    "Use `/error category:T` for full T-series documentation."
                                )).queue();
                            }
                        );
                }
            },
            throwable -> {
                logger.warn("Failed to retrieve webhooks: {}", throwable.getMessage());
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Webhook Access Failed [T06]", 
                    "Failed to access webhooks. Make sure I have 'Manage Webhooks' permission.\n" +
                    "Error Code: **T06** - Webhook Access Failed\n" +
                    "Use `/error category:T` for full T-series documentation."
                )).queue();
            }
        );
    }

    private void sendWebhookMessage(Webhook webhook, String name, String avatarUrl, String message, SlashCommandInteractionEvent event) {
        try {
            // Create message using MessageCreateBuilder
            MessageCreateData messageData = new MessageCreateBuilder()
                .setContent(message)
                .build();

            // Send webhook message with custom name and avatar
            var messageAction = webhook.sendMessage(messageData)
                .setUsername(name);

            if (avatarUrl != null) {
                messageAction = messageAction.setAvatarUrl(avatarUrl);
            }

            messageAction.queue(
                success -> {
                    event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "Message Sent", 
                        "✅ Webhook message sent successfully as **" + name + "**!"
                    )).queue();
                },
                throwable -> {
                    logger.warn("Failed to send webhook message: {}", throwable.getMessage());
                    event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Message Send Failed [T07]", 
                        "Failed to send webhook message: " + throwable.getMessage() + "\n" +
                        "Error Code: **T07** - Message Send Failed\n" +
                        "Use `/error category:T` for full T-series documentation."
                    )).queue();
                }
            );

        } catch (Exception e) {
            logger.warn("Error preparing webhook message: {}", e.getMessage());
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                "Message Preparation Failed [T08]", 
                "Failed to prepare webhook message: " + e.getMessage() + "\n" +
                "Error Code: **T08** - Message Preparation Failed\n" +
                "Use `/error category:T` for full T-series documentation."
            )).queue();
        }
    }

    private boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            URL urlObj = new URL(url);
            String path = urlObj.getPath().toLowerCase();
            return path.endsWith(".jpg") || path.endsWith(".jpeg") || 
                   path.endsWith(".png") || path.endsWith(".gif") || 
                   path.endsWith(".webp");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "talkas";
    }

    @Override
    public String getDescription() {
        return "Send a message as a webhook with custom name and avatar";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        return Commands.slash("talkas", "Send a message as a webhook with custom name and avatar")
                .addOptions(
                    new OptionData(OptionType.STRING, "name", "The name to display for the webhook message", true),
                    new OptionData(OptionType.STRING, "message", "The message content to send", true),
                    new OptionData(OptionType.STRING, "avatar", "Avatar URL for the webhook (optional)", false)
                );
    }
}