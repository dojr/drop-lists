package com.droplists;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

@Singleton
class DropListManager
{
	private static final Type LIST_TYPE = new TypeToken<List<DropList>>() {}.getType();

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private MenuEntrySwapService menuEntrySwapService;

	@Getter
	private final List<DropList> lists = new ArrayList<>();

	void load()
	{
		lists.clear();
		String json = configManager.getConfiguration(DropListsConfig.CONFIG_GROUP, DropListsConfig.LISTS_KEY);
		if (json != null && !json.isEmpty())
		{
			List<DropList> loaded = gson.fromJson(json, LIST_TYPE);
			if (loaded != null)
			{
				lists.addAll(loaded);
			}
		}

		refreshSwaps();
	}

	void save()
	{
		configManager.setConfiguration(DropListsConfig.CONFIG_GROUP, DropListsConfig.LISTS_KEY, gson.toJson(lists));
	}

	DropList createList(String name)
	{
		DropList list = new DropList(UUID.randomUUID().toString(), name, new ArrayList<>(), false);
		lists.add(list);
		save();
		return list;
	}

	void deleteList(String listId)
	{
		lists.removeIf(list -> list.getId().equals(listId));
		save();
		refreshSwaps();
	}

	void renameList(String listId, String name)
	{
		findList(listId).ifPresent(list ->
		{
			list.setName(name);
			save();
		});
	}

	void setListEnabled(String listId, boolean enabled)
	{
		findList(listId).ifPresent(list ->
		{
			list.setEnabled(enabled);
			save();
			refreshSwaps();
		});
	}

	void setItems(String listId, List<Integer> itemIds)
	{
		findList(listId).ifPresent(list ->
		{
			List<Integer> deduped = new ArrayList<>();
			for (int itemId : itemIds)
			{
				if (!deduped.contains(itemId))
				{
					deduped.add(itemId);
				}
			}

			list.setItemIds(deduped);
			save();
			refreshSwaps();
		});
	}

	String getItemName(int itemId)
	{
		return itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName();
	}

	boolean listContainsItem(String listId, int itemId)
	{
		int canonicalId = itemManager.canonicalize(itemId);
		return findList(listId).map(list -> list.getItemIds().contains(canonicalId)).orElse(false);
	}

	void removeItem(String listId, int itemId)
	{
		findList(listId).ifPresent(list ->
		{
			if (list.getItemIds().remove(Integer.valueOf(itemManager.canonicalize(itemId))))
			{
				save();
				refreshSwaps();
			}
		});
	}

	void refreshSwaps()
	{
		Set<Integer> activeItems = lists.stream()
			.filter(DropList::isEnabled)
			.flatMap(list -> list.getItemIds().stream())
			.collect(Collectors.toCollection(HashSet::new));
		menuEntrySwapService.applyActiveItems(activeItems);
	}

	void shutdown()
	{
		menuEntrySwapService.clearAll();
	}

	private java.util.Optional<DropList> findList(String listId)
	{
		return lists.stream().filter(list -> list.getId().equals(listId)).findFirst();
	}
}
