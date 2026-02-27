package com.serverbot.commands.moderation;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Purge command for bulk message operations
 */
public class PurgeCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "mod.purge")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need moderation permissions to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        String purgeType = event.getOption("type").getAsString();
        TextChannel channel = event.getChannel().asTextChannel();
        
        // Check bot permissions
        if (!event.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Permissions", "I need **Manage Messages** and **Read Message History** permissions to purge messages."
            )).setEphemeral(true).queue();
            return;
        }

        // Acknowledge the command immediately
        event.deferReply(true).queue();

        switch (purgeType.toLowerCase()) {
            case "reactions" -> purgeReactions(event, channel);
            case "replies" -> purgeReplies(event, channel);
            case "threads" -> purgeThreads(event, channel);
            case "messages" -> purgeMessages(event, channel);
            default -> {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Type", "Invalid purge type: " + purgeType
                )).queue();
            }
        }
    }

    private void purgeReactions(SlashCommandInteractionEvent event, TextChannel channel) {
        try {
            channel.getHistory().retrievePast(100).queue(messages -> {
                CompletableFuture<Void> futures = CompletableFuture.completedFuture(null);
                int reactionCount = 0;
                
                for (Message message : messages) {
                    if (!message.getReactions().isEmpty()) {
                        reactionCount++;
                        futures = futures.thenCompose(v -> message.clearReactions().submit());
                    }
                }
                
                final int finalCount = reactionCount;
                futures.thenRun(() -> {
                    event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "Reactions Purged", 
                        "Cleared reactions from **" + finalCount + "** messages in the last 100 messages."
                    )).queue();
                }).exceptionally(throwable -> {
                    event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Purge Failed", "Failed to clear reactions: " + throwable.getMessage()
                    )).queue();
                    return null;
                });
            }, error -> {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "History Error", "Failed to retrieve message history: " + error.getMessage()
                )).queue();
            });
        } catch (Exception e) {
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                "Purge Error", "An error occurred: " + e.getMessage()
            )).queue();
        }
    }

    private void purgeReplies(SlashCommandInteractionEvent event, TextChannel channel) {
        try {
            channel.getHistory().retrievePast(100).queue(messages -> {
                List<Message> repliesToDelete = messages.stream()
                        .filter(message -> message.getMessageReference() != null)
                        .filter(message -> message.getTimeCreated().isAfter(OffsetDateTime.now().minusWeeks(2)))
                        .collect(Collectors.toList());

                if (repliesToDelete.isEmpty()) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.createInfoEmbed(
                        "No Replies Found", "No reply messages found in the last 100 messages."
                    )).queue();
                    return;
                }

                if (repliesToDelete.size() == 1) {
                    repliesToDelete.get(0).delete().queue(
                        success -> {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Replies Purged", "Deleted **1** reply message."
                            )).queue();
                        },
                        error -> {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Delete Failed", "Failed to delete reply: " + error.getMessage()
                            )).queue();
                        }
                    );
                } else {
                    channel.deleteMessages(repliesToDelete).queue(
                        success -> {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Replies Purged", "Deleted **" + repliesToDelete.size() + "** reply messages."
                            )).queue();
                        },
                        error -> {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Bulk Delete Failed", "Failed to delete replies: " + error.getMessage()
                            )).queue();
                        }
                    );
                }
            }, error -> {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "History Error", "Failed to retrieve message history: " + error.getMessage()
                )).queue();
            });
        } catch (Exception e) {
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                "Purge Error", "An error occurred: " + e.getMessage()
            )).queue();
        }
    }

    private void purgeThreads(SlashCommandInteractionEvent event, TextChannel channel) {
        try {
            // Get active threads
            channel.retrieveArchivedPrivateThreadChannels().queue(privateThreads -> {
                channel.retrieveArchivedPublicThreadChannels().queue(publicThreads -> {
                    int totalThreads = privateThreads.size() + publicThreads.size();
                    
                    if (totalThreads == 0) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createInfoEmbed(
                            "No Threads Found", "No archived threads found in this channel."
                        )).queue();
                        return;
                    }
                    
                    // Delete threads (Note: This may require additional permissions)
                    event.getHook().editOriginalEmbeds(EmbedUtils.createWarningEmbed(
                        "Thread Purge", 
                        "Found **" + totalThreads + "** archived threads. " +
                        "Note: Deleting threads requires special permissions and may not be available."
                    )).queue();
                    
                }, error -> {
                    event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Thread Retrieval Error", "Failed to retrieve public threads: " + error.getMessage()
                    )).queue();
                });
            }, error -> {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Thread Retrieval Error", "Failed to retrieve private threads: " + error.getMessage()
                )).queue();
            });
        } catch (Exception e) {
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                "Purge Error", "An error occurred: " + e.getMessage()
            )).queue();
        }
    }

    private void purgeMessages(SlashCommandInteractionEvent event, TextChannel channel) {
        try {
            channel.getHistory().retrievePast(100).queue(messages -> {
                List<Message> messagesToDelete = messages.stream()
                        .filter(message -> message.getTimeCreated().isAfter(OffsetDateTime.now().minusWeeks(2)))
                        .collect(Collectors.toList());

                if (messagesToDelete.isEmpty()) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.createInfoEmbed(
                        "No Messages", "No messages found that can be deleted (messages must be less than 2 weeks old)."
                    )).queue();
                    return;
                }

                if (messagesToDelete.size() == 1) {
                    messagesToDelete.get(0).delete().queue(
                        success -> {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Messages Purged", "Deleted **1** message."
                            )).queue();
                        },
                        error -> {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Delete Failed", "Failed to delete message: " + error.getMessage()
                            )).queue();
                        }
                    );
                } else {
                    channel.deleteMessages(messagesToDelete).queue(
                        success -> {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Messages Purged", "Deleted **" + messagesToDelete.size() + "** messages."
                            )).queue();
                        },
                        error -> {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Bulk Delete Failed", "Failed to delete messages: " + error.getMessage()
                            )).queue();
                        }
                    );
                }
            }, error -> {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "History Error", "Failed to retrieve message history: " + error.getMessage()
                )).queue();
            });
        } catch (Exception e) {
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                "Purge Error", "An error occurred: " + e.getMessage()
            )).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("purge", "Bulk delete different types of content")
                .addOptions(
                    new OptionData(OptionType.STRING, "type", "Type of content to purge", true)
                        .addChoice("Reactions", "reactions")
                        .addChoice("Replies", "replies")
                        .addChoice("Threads", "threads")
                        .addChoice("Messages", "messages")
                );
    }

    @Override
    public String getName() {
        return "purge";
    }

    @Override
    public String getDescription() {
        return "Bulk delete different types of content";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
