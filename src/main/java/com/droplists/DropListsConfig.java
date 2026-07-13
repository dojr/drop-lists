package com.droplists;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;

@ConfigGroup(DropListsConfig.CONFIG_GROUP)
public interface DropListsConfig extends Config
{
	String CONFIG_GROUP = "drop-lists";
	String LISTS_KEY = "lists";
}
