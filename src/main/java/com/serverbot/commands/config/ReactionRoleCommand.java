package com.serverbot.commands.config;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.ReactionRoleService;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Command for managing reaction role systems
 */
public class ReactionRoleCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.reactionroles")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions [200]", 
                "You need the `admin.reactionroles` permission to manage reaction roles.\n" +
                "Error Code: **200** - Permission Denied\n" +
                "Use `/error category:2` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        
        switch (subcommand) {
            case "create" -> handleCreate(event);
            case "add" -> handleAdd(event);
            case "attach" -> handleAttach(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
            case "delete" -> handleDelete(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Subcommand [101]", 
                "Unknown subcommand: " + subcommand + "\n" +
                "Error Code: **101** - Invalid Value\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        OptionMapping channelOption = event.getOption("channel");
        OptionMapping titleOption = event.getOption("title");
        OptionMapping descriptionOption = event.getOption("description");

        if (channelOption == null || titleOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters [100]",
                "Please specify both channel and title for the reaction role message.\n" +
                "Error Code: **100** - Missing Parameter\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        TextChannel channel = channelOption.getAsChannel().asTextChannel();
        String title = titleOption.getAsString();
        String description = descriptionOption != null ? descriptionOption.getAsString() : 
            "React with the emoji below to get the corresponding role!";

        try {
            // Create the reaction role message
            String messageId = ReactionRoleService.getInstance().createReactionRoleMessage(
                channel, title, description, event.getMember());

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Reactionrole Created",
                String.format(
                    "Created reaction role message in %s\n\n" +
                    "**Message ID:** `%s`\n" +
                    "**Next Steps:**\n" +
                    "• Use `/reactionrole add` to add emoji-role pairs\n" +
                    "• Use `/reactionrole list` to view all reaction roles",
                    channel.getAsMention(), messageId
                )
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Creation Failed [400]",
                "Failed to create reaction role message: " + e.getMessage() + "\n" +
                "Error Code: **400** - Operation Failed\n" +
                "Use `/error category:4` for full documentation."
            )).setEphemeral(true).queue();
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        OptionMapping messageIdOption = event.getOption("message-id");
        OptionMapping emojiOption = event.getOption("emoji");
        OptionMapping roleOption = event.getOption("role");

        if (messageIdOption == null || emojiOption == null || roleOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters [100]",
                "Please specify message ID, emoji, and role for the reaction role.\n" +
                "Error Code: **100** - Missing Parameter\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        String messageId = messageIdOption.getAsString();
        String emojiStr = emojiOption.getAsString();
        Role role = roleOption.getAsRole();

        // Validate that the bot can assign this role
        if (!event.getGuild().getSelfMember().canInteract(role)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Role Hierarchy Error [206]",
                String.format(
                    "I cannot assign the role %s due to role hierarchy.\n" +
                    "Please ensure my highest role is above the target role.\n\n" +
                    "Error Code: **206** - Role Hierarchy Issue\n" +
                    "Use `/error category:2` for full documentation.",
                    role.getAsMention()
                )
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ReactionRoleService.getInstance().addReactionRole(
                event.getGuild().getId(), messageId, emojiStr, role.getId());

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Reactionrole Added",
                String.format(
                    "Successfully added reaction role:\n" +
                    "**Emoji:** %s\n" +
                    "**Role:** %s\n" +
                    "**Message ID:** `%s`",
                    emojiStr, role.getAsMention(), messageId
                )
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Addition Failed [400]",
                "Failed to add reaction role: " + e.getMessage() + "\n" +
                "Error Code: **400** - Operation Failed\n" +
                "Use `/error category:4` for full documentation."
            )).setEphemeral(true).queue();
        }
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        OptionMapping messageIdOption = event.getOption("message-id");
        OptionMapping emojiOption = event.getOption("emoji");

        if (messageIdOption == null || emojiOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters [100]",
                "Please specify both message ID and emoji to remove.\n" +
                "Error Code: **100** - Missing Parameter\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        String messageId = messageIdOption.getAsString();
        String emojiStr = emojiOption.getAsString();

        try {
            boolean removed = ReactionRoleService.getInstance().removeReactionRole(
                event.getGuild().getId(), messageId, emojiStr);

            if (removed) {
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Reactionrole Removed",
                    String.format(
                        "Successfully removed reaction role:\n" +
                        "**Emoji:** %s\n" +
                        "**Message ID:** `%s`",
                        emojiStr, messageId
                    )
                )).queue();
            } else {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Found [300]",
                    "No reaction role found with that emoji and message ID.\n" +
                    "Error Code: **300** - Resource Not Found\n" +
                    "Use `/error category:3` for full documentation."
                )).setEphemeral(true).queue();
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Removal Failed [400]",
                "Failed to remove reaction role: " + e.getMessage() + "\n" +
                "Error Code: **400** - Operation Failed\n" +
                "Use `/error category:4` for full documentation."
            )).setEphemeral(true).queue();
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            String listInfo = ReactionRoleService.getInstance().getReactionRolesList(guildId);

            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                "Reactionrole List", listInfo
            )).setEphemeral(true).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "List Failed [400]",
                "Failed to retrieve reaction roles list: " + e.getMessage() + "\n" +
                "Error Code: **400** - Operation Failed\n" +
                "Use `/error category:4` for full documentation."
            )).setEphemeral(true).queue();
        }
    }

    private void handleAttach(SlashCommandInteractionEvent event) {
        OptionMapping channelOption = event.getOption("channel");
        OptionMapping messageIdOption = event.getOption("message-id");
        OptionMapping emojiOption = event.getOption("emoji");
        OptionMapping roleOption = event.getOption("role");

        if (channelOption == null || messageIdOption == null || emojiOption == null || roleOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters [100]",
                "Please specify channel, message ID, emoji, and role to attach a reaction role.\n" +
                "Error Code: **100** - Missing Parameter\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        TextChannel channel = channelOption.getAsChannel().asTextChannel();
        String messageId = messageIdOption.getAsString();
        String emojiStr = emojiOption.getAsString();
        Role role = roleOption.getAsRole();

        // Validate that the bot can assign this role
        if (!event.getGuild().getSelfMember().canInteract(role)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Role Hierarchy Error [RR05]",
                String.format(
                    "I cannot assign the role %s due to role hierarchy.\n" +
                    "Please ensure my highest role is above the target role.\n\n" +
                    "Error Code: **RR05** - Role Hierarchy Error\n" +
                    "Use `/error category:RR` for full RR-series documentation.",
                    role.getAsMention()
                )
            )).setEphemeral(true).queue();
            return;
        }

        try {
            // First, try to retrieve the message to ensure it exists
            channel.retrieveMessageById(messageId).queue(
                message -> {
                    try {
                        // Attach reaction role to existing message
                        ReactionRoleService.getInstance().attachReactionRoleToExistingMessage(
                            event.getGuild().getId(), channel.getId(), messageId, emojiStr, role.getId(), event.getMember());

                        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "Reactionrole Attached",
                            String.format(
                                "Successfully attached reaction role to existing message:\n" +
                                "**Channel:** %s\n" +
                                "**Message ID:** `%s`\n" +
                                "**Emoji:** %s\n" +
                                "**Role:** %s\n\n" +
                                "Users can now react with %s to get the %s role!",
                                channel.getAsMention(), messageId, emojiStr, 
                                role.getAsMention(), emojiStr, role.getAsMention()
                            )
                        )).queue();

                    } catch (Exception e) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Attachment Failed [400]",
                            "Failed to attach reaction role: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."
                        )).setEphemeral(true).queue();
                    }
                },
                error -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Message Not Found [300]",
                        String.format(
                            "Could not find a message with ID `%s` in %s.\n" +
                            "Please verify the message ID and channel are correct.\n\n" +
                            "Error Code: **300** - Resource Not Found\n" +
                            "Use `/error category:3` for full documentation.",
                            messageId, channel.getAsMention()
                        )
                    )).setEphemeral(true).queue();
                }
            );

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Attachment Failed [400]",
                "Failed to attach reaction role: " + e.getMessage() + "\n" +
                "Error Code: **400** - Operation Failed\n" +
                "Use `/error category:4` for full documentation."
            )).setEphemeral(true).queue();
        }
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        OptionMapping messageIdOption = event.getOption("message-id");

        if (messageIdOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters [100]",
                "Please specify the message ID to delete.\n" +
                "Error Code: **100** - Missing Parameter\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        String messageId = messageIdOption.getAsString();

        try {
            boolean deleted = ReactionRoleService.getInstance().deleteReactionRoleMessage(
                event.getGuild().getId(), messageId);

            if (deleted) {
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Reactionrole Deleted",
                    String.format(
                        "Successfully deleted reaction role message and all associated reactions.\n" +
                        "**Message ID:** `%s`",
                        messageId
                    )
                )).queue();
            } else {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Found [300]",
                    "No reaction role message found with that ID.\n" +
                    "Error Code: **300** - Resource Not Found\n" +
                    "Use `/error category:3` for full documentation."
                )).setEphemeral(true).queue();
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Deletion Failed [400]",
                "Failed to delete reaction role message: " + e.getMessage() + "\n" +
                "Error Code: **400** - Operation Failed\n" +
                "Use `/error category:4` for full documentation."
            )).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("reactionrole", "Manage reaction role systems")
                .addSubcommands(
                    new SubcommandData("create", "Create a new reaction role message")
                        .addOption(OptionType.CHANNEL, "channel", "Channel to send the message", true)
                        .addOption(OptionType.STRING, "title", "Title for the reaction role embed", true)
                        .addOption(OptionType.STRING, "description", "Description for the reaction role embed", false),
                    
                    new SubcommandData("add", "Add an emoji-role pair to an existing bot message")
                        .addOption(OptionType.STRING, "message-id", "ID of the reaction role message", true)
                        .addOption(OptionType.STRING, "emoji", "Emoji to react with", true)
                        .addOption(OptionType.ROLE, "role", "Role to assign when reacted", true),
                    
                    new SubcommandData("attach", "Attach reaction roles to any existing message")
                        .addOption(OptionType.CHANNEL, "channel", "Channel containing the message", true)
                        .addOption(OptionType.STRING, "message-id", "ID of the existing message", true)
                        .addOption(OptionType.STRING, "emoji", "Emoji to react with", true)
                        .addOption(OptionType.ROLE, "role", "Role to assign when reacted", true),
                    
                    new SubcommandData("remove", "Remove an emoji-role pair from a message")
                        .addOption(OptionType.STRING, "message-id", "ID of the reaction role message", true)
                        .addOption(OptionType.STRING, "emoji", "Emoji to remove", true),
                    
                    new SubcommandData("list", "List all reaction role messages in this server"),
                    
                    new SubcommandData("delete", "Delete a reaction role message entirely")
                        .addOption(OptionType.STRING, "message-id", "ID of the message to delete", true)
                );
    }

    @Override
    public String getName() {
        return "reactionrole";
    }

    @Override
    public String getDescription() {
        return "Manage reaction role systems";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.REACTION_ROLES;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}