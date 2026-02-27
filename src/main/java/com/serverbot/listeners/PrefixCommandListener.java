package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.services.PrefixCommandService;
import com.serverbot.services.CommandManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Listener for handling prefix commands
 * Routes prefix commands to PrefixCommandService for processing
 */
public class PrefixCommandListener extends ListenerAdapter {
    private final PrefixCommandService prefixCommandService;
    
    public PrefixCommandListener(CommandManager commandManager) {
        this.prefixCommandService = new PrefixCommandService(commandManager);
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }
        
        // Only process messages from guilds
        if (!event.isFromGuild()) {
            return;
        }
        
        // Check if message starts with the guild's configured prefix or the default "!"
        String content = event.getMessage().getContentRaw();
        String guildPrefix = ServerBot.getStorageManager().getPrefix(event.getGuild().getId());
        
        if (content.startsWith(guildPrefix) || content.startsWith("!") || content.startsWith("px;")) {
            prefixCommandService.handlePrefixCommand(event);
        }
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        if (buttonId.equals("dismiss_help")) {
            // Delete the help message when dismiss button is clicked
            event.getMessage().delete().queue();
            event.deferEdit().queue(); // Acknowledge the interaction
        }
    }
}