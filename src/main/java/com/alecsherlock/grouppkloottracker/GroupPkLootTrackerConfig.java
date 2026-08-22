package com.alecsherlock.grouppkloottracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Configuration interface for the PK Key Tracker plugin.
 * The ConfigGroup annotation assigns a unique group ID for storing these settings.
 */
@ConfigGroup("grouppkloottracker")
public interface GroupPkLootTrackerConfig extends Config
{
    @ConfigItem(
            keyName = "discordWebhook",
            name = "Discord Webhook URL",
            description = "The full Discord Webhook URL where end-of-session reports will be sent.",
            position = 1
    )
    default String discordWebhook()
    {
        return "";
    }

    @ConfigItem(
            keyName = "defaultInclude",
            name = "Include New Members by Default",
            description = "Automatically check the box to include new party members in the final split calculation.",
            position = 2
    )
    default boolean defaultInclude()
    {
        return true;
    }
}