package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embed builder command for creating custom embeds
 */
public class EmbedCommand implements SlashCommand {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6})$");

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionUtils.hasModeratorPermissions(member)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need moderation permissions to create embeds."
            )).setEphemeral(true).queue();
            return;
        }

        // Check if no required parameters provided - show help
        if (event.getOption("title") == null && event.getOption("description") == null) {
            showEmbedHelp(event);
            return;
        }

        String type = event.getOption("type") != null ? 
                      event.getOption("type").getAsString() : "simple";
        
        switch (type) {
            case "simple" -> handleSimpleEmbed(event);
            case "advanced" -> handleAdvancedEmbed(event);
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Type", 
                    "Invalid type: `" + type + "`\n" +
                    "Valid types: `simple`, `advanced`\n\n" +
                    "Use `/embed` without arguments to see the help guide."
                )).setEphemeral(true).queue();
            }
        }
    }

    private void showEmbedHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📝 Embed Command Help")
                .setDescription("Create custom embeds with various styling options")
                .setColor(0x5865F2)
                .addField("**Basic Usage**",
                    "`/embed title:<text> [description:<text>] [type:simple] [color:#hex]`\n" +
                    "`/embed title:<text> type:advanced [thumbnail:<url>] [image:<url>] [footer:<text>] [author:<name>]`", false)
                .addField("**Parameters**",
                    "• `title` - Embed title (required if no description)\n" +
                    "• `description` - Embed description (required if no title)\n" +
                    "• `type` - Embed type (simple/advanced, default: simple)\n" +
                    "• `color` - Embed color (hex format or preset, default: #0099FF)\n" +
                    "• `thumbnail` - Thumbnail image URL (advanced only)\n" +
                    "• `image` - Main image URL (advanced only)\n" +
                    "• `footer` - Footer text (advanced only)\n" +
                    "• `author` - Author name (advanced only)", false)
                .addField("**Color Presets**",
                    "Red, Green, Blue, Yellow, Purple, Orange, Pink, Cyan, Discord Blurple, Discord Green", false)
                .addField("**Examples**",
                    "`/embed title:Welcome description:Hello everyone!`\n" +
                    "`/embed title:Announcement type:advanced color:#FF0000 image:https://example.com/image.png`\n" +
                    "`/embed description:Use \\\\n for newlines type:advanced footer:Custom footer`", false)
                .setFooter("Use -!help to dismiss future help messages • Requires moderation permissions");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleSimpleEmbed(SlashCommandInteractionEvent event) {
        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : null;
        String description = event.getOption("description") != null ? event.getOption("description").getAsString() : null;
        String colorHex = event.getOption("color") != null ? event.getOption("color").getAsString() : "#0099FF";

        if (title == null && description == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Content [100]", 
                "You must provide at least a title or description.\n" +
                "Error Code: **100** - Missing Parameter\n" +
                "Use `/error category:1` for full documentation."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            Color embedColor = parseColor(colorHex);
            EmbedBuilder embed = new EmbedBuilder();
            
            if (title != null && !title.isEmpty()) {
                embed.setTitle(title);
            }
            
            if (description != null && !description.isEmpty()) {
                embed.setDescription(description);
            }
            
            embed.setColor(embedColor);
            embed.setFooter("Created by " + event.getUser().getEffectiveName(), 
                           event.getUser().getAvatarUrl());

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Embed Creation Failed", 
                "Failed to create embed: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleAdvancedEmbed(SlashCommandInteractionEvent event) {
        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : null;
        String description = event.getOption("description") != null ? event.getOption("description").getAsString() : null;
        String colorHex = event.getOption("color") != null ? event.getOption("color").getAsString() : "#0099FF";
        String thumbnailUrl = event.getOption("thumbnail") != null ? event.getOption("thumbnail").getAsString() : null;
        String imageUrl = event.getOption("image") != null ? event.getOption("image").getAsString() : null;
        String footerText = event.getOption("footer") != null ? event.getOption("footer").getAsString() : null;
        String authorName = event.getOption("author") != null ? event.getOption("author").getAsString() : null;

        if (title == null && description == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Content", "You must provide at least a title or description."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            Color embedColor = parseColor(colorHex);
            EmbedBuilder embed = new EmbedBuilder();
            
            if (title != null && !title.isEmpty()) {
                embed.setTitle(title);
            }
            
            if (description != null && !description.isEmpty()) {
                // Replace \\n with actual newlines
                String processedDescription = description.replace("\\n", "\n");
                embed.setDescription(processedDescription);
            }
            
            embed.setColor(embedColor);
            
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                embed.setThumbnail(thumbnailUrl);
            }
            
            if (imageUrl != null && !imageUrl.isEmpty()) {
                embed.setImage(imageUrl);
            }
            
            if (authorName != null && !authorName.isEmpty()) {
                embed.setAuthor(authorName);
            }
            
            if (footerText != null && !footerText.isEmpty()) {
                embed.setFooter(footerText);
            } else {
                embed.setFooter("Created by " + event.getUser().getEffectiveName(), 
                               event.getUser().getAvatarUrl());
            }

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Embed Creation Failed", 
                "Failed to create embed: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private Color parseColor(String colorHex) {
        if (colorHex == null || colorHex.isEmpty()) {
            return new Color(0x0099FF); // Default blue
        }

        // Ensure hex color format
        if (!colorHex.startsWith("#")) {
            colorHex = "#" + colorHex;
        }

        Matcher matcher = HEX_COLOR_PATTERN.matcher(colorHex);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid hex color format. Use format: #FFFFFF");
        }

        try {
            return Color.decode(colorHex);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex color: " + colorHex);
        }
    }

    public static CommandData getCommandData() {
        OptionData typeOption = new OptionData(OptionType.STRING, "type", "Type of embed to create", false);
        typeOption.addChoice("Simple", "simple");
        typeOption.addChoice("Advanced", "advanced");

        OptionData colorOption = new OptionData(OptionType.STRING, "color", "Embed color (hex format or preset)", false);
        colorOption.addChoice("Red", "#FF0000");
        colorOption.addChoice("Green", "#00FF00");
        colorOption.addChoice("Blue", "#0000FF");
        colorOption.addChoice("Yellow", "#FFFF00");
        colorOption.addChoice("Purple", "#800080");
        colorOption.addChoice("Orange", "#FFA500");
        colorOption.addChoice("Pink", "#FF69B4");
        colorOption.addChoice("Cyan", "#00FFFF");
        colorOption.addChoice("Discord Blurple", "#5865F2");
        colorOption.addChoice("Discord Green", "#57F287");

        return Commands.slash("embed", "Create custom embeds with various styling options")
                .addOptions(
                    new OptionData(OptionType.STRING, "title", "Embed title", false),
                    new OptionData(OptionType.STRING, "description", "Embed description (use \\n for newlines)", false),
                    typeOption,
                    colorOption,
                    new OptionData(OptionType.STRING, "thumbnail", "Thumbnail image URL (advanced only)", false),
                    new OptionData(OptionType.STRING, "image", "Main image URL (advanced only)", false),
                    new OptionData(OptionType.STRING, "footer", "Footer text (advanced only)", false),
                    new OptionData(OptionType.STRING, "author", "Author name (advanced only)", false)
                );
    }

    @Override
    public String getName() {
        return "embed";
    }

    @Override
    public String getDescription() {
        return "Create custom embeds with various styling options";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
