package com.serverbot.commands.leveling;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Short form of leaderboard command - temporarily disabled
 */
public class LbCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Delegate to the leaderboard command
        LeaderboardCommand leaderboardCommand = new LeaderboardCommand();
        leaderboardCommand.execute(event);
    }

    public static CommandData getCommandData() {
        return Commands.slash("lb", "Show the XP/level leaderboard (short form)");
    }

    @Override
    public String getName() {
        return "lb";
    }

    @Override
    public String getDescription() {
        return "Show the XP/level leaderboard (short form)";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.LEVELING;
    }
}
