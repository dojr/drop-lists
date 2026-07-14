package com.droplists;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
class MenuEntrySwapService
{
	@Inject
	private Client client;

	// Written on the Swing EDT (via applyActiveItems/clearAll) and read on the
	// client thread every tick in onPostMenuSort. Swapped atomically as an
	// immutable snapshot so reads never need to lock.
	private volatile Set<Integer> activeItemIds = Collections.emptySet();

	void applyActiveItems(Set<Integer> itemIds)
	{
		Set<Integer> mapped = new HashSet<>();
		for (Integer itemId : itemIds)
		{
			mapped.add(ItemVariationMapping.map(itemId));
		}
		activeItemIds = Collections.unmodifiableSet(mapped);
		log.debug("Active drop-swap items: {}", activeItemIds);
	}

	void clearAll()
	{
		activeItemIds = Collections.emptySet();
	}

	boolean isActive(int itemId)
	{
		return activeItemIds.contains(ItemVariationMapping.map(itemId));
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort postMenuSort)
	{
		if (client.isMenuOpen() || activeItemIds.isEmpty())
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		if (entries.length == 0)
		{
			return;
		}

		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[i];
			Widget widget = entry.getWidget();
			if (widget == null || WidgetUtil.componentToInterface(widget.getId()) != InterfaceID.INVENTORY)
			{
				continue;
			}

			int itemId = widget.getItemId();
			if (itemId <= 0 || !isActive(itemId))
			{
				continue;
			}

			if (!"Drop".equalsIgnoreCase(Text.removeTags(entry.getOption())))
			{
				continue;
			}

			// Drop is op4/op5 which are CC_OP_LOW_PRIORITY, making them right-click only.
			// Promote to CC_OP so it can become the left-click action.
			if (entry.getType() == MenuAction.CC_OP_LOW_PRIORITY)
			{
				entry.setType(MenuAction.CC_OP);
			}

			int last = entries.length - 1;
			if (i != last)
			{
				MenuEntry topEntry = entries[last];
				if (topEntry.getType() == MenuAction.CC_OP_LOW_PRIORITY)
				{
					topEntry.setType(MenuAction.CC_OP);
				}

				entries[i] = topEntry;
				entries[last] = entry;
			}

			menu.setMenuEntries(entries);
			return;
		}
	}
}
