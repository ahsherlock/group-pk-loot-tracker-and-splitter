package com.alecsherlock.grouppkloottracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GroupPkLootTrackerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(GroupPkLootTrackerPlugin.class);
        RuneLite.main(args);
    }
}