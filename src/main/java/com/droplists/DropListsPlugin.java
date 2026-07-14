package com.droplists;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Drop Lists",
	description = "Manage toggleable item lists that swap inventory left-click to Drop",
	tags = {"inventory", "drop", "menu", "items"}
)
public class DropListsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EventBus eventBus;

	@Inject
	private DropListManager dropListManager;

	@Inject
	private MenuEntrySwapService menuEntrySwapService;

	private DropListsPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		eventBus.register(menuEntrySwapService);
		dropListManager.load();

		panel = injector.getInstance(DropListsPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(DropListsPlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Drop Lists")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		log.debug("Drop Lists started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		dropListManager.shutdown();
		eventBus.unregister(menuEntrySwapService);
		log.debug("Drop Lists stopped");
	}

	@Provides
	DropListsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropListsConfig.class);
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		clientThread.invokeLater(() ->
		{
			dropListManager.load();
			panel.reload();
		});
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		final String listId = panel.getSelectedListId();
		if (listId == null)
		{
			return;
		}

		MenuEntry[] entries = event.getMenuEntries();
		for (int idx = entries.length - 1; idx >= 0; idx--)
		{
			MenuEntry entry = entries[idx];
			Widget widget = entry.getWidget();
			if (widget == null)
			{
				continue;
			}

			if (WidgetUtil.componentToInterface(widget.getId()) != InterfaceID.INVENTORY)
			{
				continue;
			}

			if (!"Examine".equals(entry.getOption()) || entry.getIdentifier() != 10)
			{
				continue;
			}

			int itemId = entry.getItemId();
			if (itemId <= 0)
			{
				itemId = widget.getItemId();
			}
			if (itemId <= 0)
			{
				continue;
			}

			final int finalItemId = itemId;
			String target = entry.getTarget();

			if (dropListManager.listContainsItem(listId, itemId))
			{
				client.createMenuEntry(idx)
					.setOption("Remove from drop list")
					.setTarget(target)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> panel.removeItemFromInventory(finalItemId));
			}
			else
			{
				client.createMenuEntry(idx)
					.setOption("Add to drop list")
					.setTarget(target)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> panel.addItemFromInventory(finalItemId));
			}
			break;
		}
	}
}
