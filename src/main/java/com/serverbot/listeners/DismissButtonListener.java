package com.serverbot.listeners;

import com.serverbot.utils.DismissibleMessage;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Listener for handling dismiss button interactions
 */
public class DismissButtonListener extends ListenerAdapter {
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        // Check if this is a dismiss button
        if (!DismissibleMessage.isDismissButton(buttonId)) {
            return;
        }
        
        String allowedUserId = DismissibleMessage.extractSenderId(buttonId);
        String clickerId = event.getUser().getId();
        
        // Only allow the original command sender to dismiss
        if (allowedUserId != null && allowedUserId.equals(clickerId)) {
            // Delete the message
            event.getMessage().delete().queue(
                success -> {},
                failure -> {
                    // Message might already be deleted
                    event.reply("Unable to delete message.").setEphemeral(true).queue();
                }
            );
            // Acknowledge the interaction silently
            event.deferEdit().queue();
        } else {
            // Someone else tried to dismiss - tell them they can't
            event.reply("Only the person who ran this command can dismiss this message.")
                .setEphemeral(true)
                .queue();
        }
    }
}
