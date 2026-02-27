package com.serverbot.commands.config;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;
import java.util.Set;

/**
 * Compressed permissions management command with all functionality in one root command
 */
public class PermissionsCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.permissions")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need the `admin.permissions` permission to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        // Check special functions first
        OptionMapping targetOption = event.getOption("target");
        if (targetOption != null) {
            String target = targetOption.getAsString();
            if ("list-nodes".equals(target)) {
                handleListNodes(event);
                return;
            } else if ("check".equals(target)) {
                handleCheckPermissions(event);
                return;
            }
        }

        // Check if no arguments provided - show help
        OptionMapping targetEntityOption = event.getOption("target-entity");
        OptionMapping actionOption = event.getOption("action");
        
        if (targetEntityOption == null && actionOption == null) {
            showPermissionsHelp(event);
            return;
        }

        if (targetEntityOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Target [100]", 
                "Please specify a target entity (user, role, or @everyone).\n" +
                "Error Code: **100** - Missing Target Parameter\n" +
                "Use `/error category:1` for full 1XX-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        String action = actionOption != null ? actionOption.getAsString() : "view";

        // Auto-detect target type and handle accordingly
        if (targetEntityOption.getAsUser() != null) {
            // It's a user
            User targetUser = targetEntityOption.getAsUser();
            Member targetMember = event.getGuild().getMember(targetUser);
            if (targetMember == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "User Not Found [301]", 
                    "The specified user is not a member of this server.\n" +
                    "Error Code: **301** - Target Not Found\n" +
                    "Use `/error category:3` for full 3XX-series documentation."
                )).setEphemeral(true).queue();
                return;
            }
            handleUserPermissions(event, targetMember, action);
        } else if (targetEntityOption.getAsRole() != null) {
            Role targetRole = targetEntityOption.getAsRole();
            // Check if this is the @everyone role
            if (targetRole.isPublicRole()) {
                handleEveryonePermissions(event, action);
            } else {
                handleRolePermissions(event, targetRole, action);
            }
        } else {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Target", "Please specify a valid user, role, or @everyone.\n\n" +
                "💡 **Usage:** `/permissions target-entity:@user action:view`\n" +
                "Or: `/permissions target-entity:@role action:set node:<node> value:<true/false>`"
            )).setEphemeral(true).queue();
        }
    }

    private void showPermissionsHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("Permissions Help")
                .setDescription("Simplified permissions management - auto-detects user vs role targets")
                .addField("**Set Permissions**", 
                    "```/permissions target-entity:@user action:set node:mod.ban value:true```\n" +
                    "```/permissions target-entity:@role action:set node:economy.admin value:false```\n" +
                    "```/permissions target-entity:@everyone action:set node:levels.use value:true```", false)
                .addField("**View Permissions**", 
                    "```/permissions target-entity:@user action:view```\n" +
                    "```/permissions target-entity:@role action:view```\n" +
                    "```/permissions action:view``` - View your own permissions", false)
                .addField("**Remove Permissions**", 
                    "```/permissions target-entity:@user action:remove node:mod.ban```\n" +
                    "```/permissions target-entity:@role action:remove node:economy.admin```", false)
                .addField("**Utility Commands**", 
                    "```/permissions target:list-nodes``` - List all available permission nodes\n" +
                    "```/permissions target:check target-entity:@user node:mod.ban``` - Check if user has permission", false)
                .addField("**Notes**", 
                    "• Use @everyone to set server-wide permissions\n" +
                    "• Wildcard permissions: `*` (all), `mod.*` (all moderation), etc.\n" +
                    "• Guild owner automatically has all permissions", false);

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleUserPermissions(SlashCommandInteractionEvent event, Member targetMember, String action) {
        switch (action) {
            case "view" -> viewUserPermissions(event, targetMember);
            case "set" -> setUserPermission(event, targetMember);
            case "remove" -> removeUserPermission(event, targetMember);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Action", "Valid actions: view, set, remove"
            )).setEphemeral(true).queue();
        }
    }

    private void handleRolePermissions(SlashCommandInteractionEvent event, Role targetRole, String action) {
        switch (action) {
            case "view" -> viewRolePermissions(event, targetRole);
            case "set" -> setRolePermission(event, targetRole);
            case "remove" -> removeRolePermission(event, targetRole);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Action", "Valid actions: view, set, remove"
            )).setEphemeral(true).queue();
        }
    }

    private void handleEveryonePermissions(SlashCommandInteractionEvent event, String action) {
        switch (action) {
            case "view" -> viewEveryonePermissions(event);
            case "set" -> setEveryonePermission(event);
            case "remove" -> removeEveryonePermission(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Action", "Valid actions: view, set, remove"
            )).setEphemeral(true).queue();
        }
    }

    private void handleListNodes(SlashCommandInteractionEvent event) {
        Set<String> allNodes = PermissionManager.getAllPermissionNodes();
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("Permission Nodes")
                .setDescription("All available permission nodes in the system:");

        StringBuilder nodeList = new StringBuilder();
        String currentCategory = "";
        
        for (String node : allNodes.stream().sorted().toList()) {
            String category = node.split("\\.")[0];
            if (!category.equals(currentCategory)) {
                if (!currentCategory.isEmpty()) {
                    embed.addField(currentCategory.toUpperCase(), nodeList.toString(), false);
                    nodeList.setLength(0);
                }
                currentCategory = category;
            }
            nodeList.append("`").append(node).append("`\n");
        }
        
        if (!nodeList.isEmpty()) {
            embed.addField(currentCategory.toUpperCase(), nodeList.toString(), false);
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleCheckPermissions(SlashCommandInteractionEvent event) {
        OptionMapping targetEntityOption = event.getOption("target-entity");
        OptionMapping nodeOption = event.getOption("node");
        
        if (targetEntityOption == null || nodeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters [100]", 
                "Please specify both target entity and permission node to check.\n" +
                "Error Code: **100** - Missing Target Parameter\n" +
                "Use `/error category:1` for full 1XX-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        User targetUser = targetEntityOption.getAsUser();
        if (targetUser == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Target", "Permission check only works with user targets."
            )).setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getGuild().getMember(targetUser);
        String permissionNode = nodeOption.getAsString();
        
        if (targetMember == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "User Not Found [301]", 
                "The specified user is not a member of this server.\n" +
                "Error Code: **301** - Target Not Found\n" +
                "Use `/error category:3` for full 3XX-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        boolean hasPermission = PermissionManager.hasPermission(targetMember, permissionNode);
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(hasPermission ? EmbedUtils.SUCCESS_COLOR : EmbedUtils.ERROR_COLOR)
                .setTitle("Permissions")
                .addField("User", targetUser.getAsMention(), true)
                .addField("Permission Node", "`" + permissionNode + "`", true)
                .addField("Has Permission", hasPermission ? CustomEmojis.SUCCESS + " **Yes**" : CustomEmojis.ERROR + " **No**", true);

        event.replyEmbeds(embed.build()).queue();
    }

    private void viewUserPermissions(SlashCommandInteractionEvent event, Member targetMember) {
        try {
            Map<String, Boolean> userPermissions = PermissionManager.getUserPermissions(targetMember.getGuild().getId(), targetMember.getId());
            
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                    .setTitle("User Permissions")
                    .addField("User", targetMember.getAsMention(), true)
                    .addField("Permission Count", String.valueOf(userPermissions.size()), true);

            if (userPermissions.isEmpty()) {
                embed.addField("Permissions", "No specific permissions assigned", false);
            } else {
                StringBuilder permList = new StringBuilder();
                for (Map.Entry<String, Boolean> entry : userPermissions.entrySet()) {
                    String status = entry.getValue() ? CustomEmojis.SUCCESS : CustomEmojis.ERROR;
                    permList.append(status).append(" `").append(entry.getKey()).append("`\n");
                }
                embed.addField("Permissions", permList.toString(), false);
            }

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "View Failed [400]", 
                "Failed to view user permissions: " + e.getMessage() + "\n" +
                "Error Code: **400** - Permission Update Failed\n" +
                "Use `/error category:4` for full 4XX-series documentation."
            )).setEphemeral(true).queue();
        }
    }

    private void setUserPermission(SlashCommandInteractionEvent event, Member targetMember) {
        OptionMapping nodeOption = event.getOption("node");
        if (nodeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Node", "Please specify a permission node to set.\n\n" +
                "💡 **Usage:** `/permissions target-entity:@user action:set node:<node> value:<true/false>`\n" +
                "Use `/permissions target:list-nodes` to see all available nodes."
            )).setEphemeral(true).queue();
            return;
        }

        String permissionNode = nodeOption.getAsString();
        
        // Get the value option (defaults to true if not provided)
        OptionMapping valueOption = event.getOption("value");
        boolean value = valueOption == null || valueOption.getAsBoolean();
        
        try {
            PermissionManager.setUserPermission(targetMember.getGuild().getId(), targetMember.getId(), permissionNode, value);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Permission Set",
                String.format("Permission `%s` set to `%s` for %s", permissionNode, value, targetMember.getAsMention())
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Set Failed", 
                "Failed to set node: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void removeUserPermission(SlashCommandInteractionEvent event, Member targetMember) {
        OptionMapping nodeOption = event.getOption("node");
        if (nodeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Node", "Please specify a permission node to remove."
            )).setEphemeral(true).queue();
            return;
        }

        String permissionNode = nodeOption.getAsString();
        
        try {
            PermissionManager.removeUserPermission(targetMember.getGuild().getId(), targetMember.getId(), permissionNode);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Permission Removed",
                String.format("Permission `%s` set `false` for %s", permissionNode, targetMember.getAsMention())
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Removal Failed", 
                "Failed to remove node: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void viewRolePermissions(SlashCommandInteractionEvent event, Role targetRole) {
        try {
            Map<String, Boolean> rolePermissions = PermissionManager.getRolePermissions(targetRole.getGuild().getId(), targetRole.getId());
            
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                    .setTitle("Role Permissions")
                    .addField("Role", targetRole.getAsMention(), true)
                    .addField("Permission Count", String.valueOf(rolePermissions.size()), true);

            if (rolePermissions.isEmpty()) {
                embed.addField("Permissions", "No specific permissions assigned", false);
            } else {
                StringBuilder permList = new StringBuilder();
                for (Map.Entry<String, Boolean> entry : rolePermissions.entrySet()) {
                    String status = entry.getValue() ? CustomEmojis.SUCCESS : CustomEmojis.ERROR;
                    permList.append(status).append(" `").append(entry.getKey()).append("`\n");
                }
                embed.addField("Permissions", permList.toString(), false);
            }

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "View Failed", 
                "Failed to view role permissions: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void setRolePermission(SlashCommandInteractionEvent event, Role targetRole) {
        OptionMapping nodeOption = event.getOption("node");
        if (nodeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Node", "Please specify a permission node to set.\n\n" +
                "💡 **Usage:** `/permissions target-entity:@role action:set node:<node> value:<true/false>`\n" +
                "Use `/permissions target:list-nodes` to see all available nodes."
            )).setEphemeral(true).queue();
            return;
        }

        String permissionNode = nodeOption.getAsString();
        
        // Get the value option (defaults to true if not provided)
        OptionMapping valueOption = event.getOption("value");
        boolean value = valueOption == null || valueOption.getAsBoolean();
        
        try {
            PermissionManager.setRolePermission(targetRole.getGuild().getId(), targetRole.getId(), permissionNode, value);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Permission Set",
                String.format("Permission `%s` set to `%s` for %s", permissionNode, value, targetRole.getAsMention())
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Set Failed", 
                "Failed to set role permission: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void removeRolePermission(SlashCommandInteractionEvent event, Role targetRole) {
        OptionMapping nodeOption = event.getOption("node");
        if (nodeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Node", "Please specify a permission node to remove."
            )).setEphemeral(true).queue();
            return;
        }

        String permissionNode = nodeOption.getAsString();
        
        try {
            PermissionManager.removeRolePermission(targetRole.getGuild().getId(), targetRole.getId(), permissionNode);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Permission Removed",
                String.format("Permission `%s` set `false` for %s", permissionNode, targetRole.getAsMention())
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Removal Failed", 
                "Failed to remove node: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void viewEveryonePermissions(SlashCommandInteractionEvent event) {
        try {
            Map<String, Boolean> everyonePermissions = PermissionManager.getEveryonePermissions(event.getGuild().getId());
            
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                    .setTitle("Default Permissions")
                    .addField("Server", event.getGuild().getName(), true)
                    .addField("Permission Count", String.valueOf(everyonePermissions.size()), true);

            if (everyonePermissions.isEmpty()) {
                embed.addField("Permissions", "No default permissions set", false);
            } else {
                StringBuilder permList = new StringBuilder();
                for (Map.Entry<String, Boolean> entry : everyonePermissions.entrySet()) {
                    String status = entry.getValue() ? CustomEmojis.SUCCESS : CustomEmojis.ERROR;
                    permList.append(status).append(" `").append(entry.getKey()).append("`\n");
                }
                embed.addField("Permissions", permList.toString(), false);
            }

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "View Failed", 
                "Failed to view everyone permissions: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void setEveryonePermission(SlashCommandInteractionEvent event) {
        OptionMapping nodeOption = event.getOption("node");
        if (nodeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Node", "Please specify a permission node to set.\n\n" +
                "💡 **Usage:** `/permissions target-entity:@everyone action:set node:<node> value:<true/false>`\n" +
                "Use `/permissions target:list-nodes` to see all available nodes."
            )).setEphemeral(true).queue();
            return;
        }

        String permissionNode = nodeOption.getAsString();
        
        // Get the value option (defaults to true if not provided)
        OptionMapping valueOption = event.getOption("value");
        boolean value = valueOption == null || valueOption.getAsBoolean();
        
        try {
            PermissionManager.setEveryonePermission(event.getGuild().getId(), permissionNode, value);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Permission Set",
                String.format("Permission `%s` set to `%s` for everyone in this server", permissionNode, value)
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Set Failed", 
                "Failed to set everyone permission: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void removeEveryonePermission(SlashCommandInteractionEvent event) {
        OptionMapping nodeOption = event.getOption("node");
        if (nodeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Node", "Please specify a permission node to remove."
            )).setEphemeral(true).queue();
            return;
        }

        String permissionNode = nodeOption.getAsString();
        
        try {
            PermissionManager.removeEveryonePermission(event.getGuild().getId(), permissionNode);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Permission Removed",
                String.format("Permission `%s` set `false` for everyone in this server", permissionNode)
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Remove Failed", 
                "Failed to remove everyone permission: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    @Override
    public String getName() {
        return "permissions";
    }

    @Override
    public String getDescription() {
        return "Manage user, role, and server permissions";
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
        OptionData targetOption = new OptionData(OptionType.STRING, "target", "Target type for permission management", false)
                .addChoice("List all nodes", "list-nodes")
                .addChoice("Check permission", "check");

        OptionData actionOption = new OptionData(OptionType.STRING, "action", "Action to perform", false)
                .addChoice("View permissions", "view")
                .addChoice("Set permission", "set")
                .addChoice("Remove permission", "remove");

        return Commands.slash("permissions", "Manage user, role, and server permissions")
                .addOptions(targetOption)
                .addOptions(actionOption)
                .addOption(OptionType.MENTIONABLE, "target-entity", "User or role to manage permissions for (@everyone for server-wide)", false)
                .addOption(OptionType.STRING, "node", "Permission node (e.g., moderation.ban, economy.admin)", false)
                .addOption(OptionType.BOOLEAN, "value", "Allow (true) or deny (false) permission", false);
    }
}
