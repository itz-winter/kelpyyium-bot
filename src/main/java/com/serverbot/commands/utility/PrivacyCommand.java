package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Displays the bot's privacy policy, data collection practices, and user rights.
 * Required for compliance with the Discord Developer Terms of Service.
 */
public class PrivacyCommand implements SlashCommand {

    @Override
    public String getName() {
        return "privacy";
    }

    @Override
    public String getDescription() {
        return "View the bot's privacy policy and data practices";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🔒 Privacy Policy")
                .setDescription("This bot is committed to protecting your privacy. " +
                        "Below is a summary of what data we collect, why, and your rights.")

                .addField("📋 Data We Collect",
                        "• **User IDs** — to track economy balances, levels, warnings, and permissions.\n" +
                        "• **Guild IDs** — to store per-server configuration and settings.\n" +
                        "• **Message Content** — processed in real-time for commands, proxy tags, " +
                        "global chat relay, and auto-moderation. **Message content is not stored persistently** " +
                        "unless part of a moderation action (e.g. warning reason).\n" +
                        "• **Moderation Records** — warning reasons, ban/mute durations, and moderator IDs.\n" +
                        "• **Economy Data** — balances, transaction history.\n" +
                        "• **Leveling Data** — XP totals and level progress.\n" +
                        "• **Proxy Data** — proxy member names, tags, and avatar URLs (user-provided).\n" +
                        "• **Role Persistence** — role IDs for users who leave and rejoin.", false)

                .addField("💾 How Data Is Stored",
                        "All data is stored locally on the bot host as JSON files. " +
                        "Data is **not** shared with third parties, sold, or used for advertising. " +
                        "No data is sent to external services or APIs beyond Discord's own API.", false)

                .addField("⏱️ Data Retention",
                        "• Economy and leveling data is kept as long as the bot is in the server.\n" +
                        "• Moderation records are kept for server admin reference.\n" +
                        "• Proxy data is kept until the user deletes it.\n" +
                        "• Role persistence data expires automatically after server-configured periods.\n" +
                        "• When the bot is removed from a server, server-specific data may be retained " +
                        "but is not actively used.", false)

                .addField("🗑️ Your Rights",
                        "You have the right to:\n" +
                        "• **View** what data is stored about you (economy, levels, warnings).\n" +
                        "• **Request deletion** of your data using `/deletedata`.\n" +
                        "• **Opt out** of features that process your messages (ask a server admin " +
                        "to adjust permissions).\n\n" +
                        "Server administrators can also request bulk data deletion by removing the " +
                        "bot from their server and contacting the bot owner.", false)

                .addField("🔑 Privileged Intents",
                        "This bot uses the following privileged intents, as approved by Discord:\n" +
                        "• **Guild Members** — for role persistence, suspicious account detection, and welcome messages.\n" +
                        "• **Message Content** — for proxy tag matching, prefix commands, global chat relay, and auto-moderation.\n" +
                        "• **Presence** — for suspicious account detection (new account age checks).", false)

                .addField("📬 Contact",
                        "For privacy concerns or data requests, contact the bot owner " +
                        "through the support server or directly via Discord.\n" +
                        "Use `/deletedata` to erase your personal data at any time.", false)

                .setFooter("Last updated: February 2026 • Compliant with Discord Developer Terms of Service");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("privacy", "View the bot's privacy policy and data practices");
    }
}
