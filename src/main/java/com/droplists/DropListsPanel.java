package com.droplists;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.http.api.item.ItemPrice;

class DropListsPanel extends PluginPanel
{
	private enum View
	{
		OVERVIEW,
		EDITOR
	}

	private static final Color CHECK_COLOR = new Color(218, 165, 32);
	private static final Icon UNCHECKED_ICON = checkboxIcon(false);
	private static final Icon CHECKED_ICON = checkboxIcon(true);

	private final DropListManager dropListManager;
	private final ItemManager itemManager;
	private final Client client;
	private final ClientThread clientThread;

	private View view = View.OVERVIEW;
	private String selectedListId;
	private boolean panelActive;

	private final JPanel contentPanel = new JPanel(new BorderLayout());

	private final JTextField listNameField = new JTextField();
	private final JTextArea itemsArea = new JTextArea(8, 1);

	@Inject
	DropListsPanel(DropListManager dropListManager, ItemManager itemManager, Client client, ClientThread clientThread)
	{
		super(false);
		this.dropListManager = dropListManager;
		this.itemManager = itemManager;
		this.client = client;
		this.clientThread = clientThread;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(contentPanel, BorderLayout.CENTER);

		rebuild();
	}

	boolean isPanelActive()
	{
		return panelActive;
	}

	String getSelectedListId()
	{
		return view == View.EDITOR ? selectedListId : null;
	}

	void reload()
	{
		if (selectedListId != null && dropListManager.getLists().stream().noneMatch(l -> l.getId().equals(selectedListId)))
		{
			selectedListId = null;
			view = View.OVERVIEW;
		}
		rebuild();
	}

	@Override
	public void onActivate()
	{
		panelActive = true;
	}

	@Override
	public void onDeactivate()
	{
		panelActive = false;
	}

	private void rebuild()
	{
		contentPanel.removeAll();
		if (view == View.EDITOR && selectedListId != null)
		{
			contentPanel.add(buildEditorView(), BorderLayout.CENTER);
		}
		else
		{
			contentPanel.add(buildOverviewView(), BorderLayout.CENTER);
		}
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel buildOverviewView()
	{
		JPanel wrapper = new JPanel(new BorderLayout(0, 8));
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(0, 8));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Drop Lists");
		title.setForeground(Color.WHITE);

		JButton newListButton = new JButton("New list");
		newListButton.addActionListener(e ->
		{
			DropList list = dropListManager.createList("New list");
			openEditor(list.getId());
		});

		header.add(title, BorderLayout.NORTH);
		header.add(newListButton, BorderLayout.SOUTH);

		JPanel listsContainer = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		listsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (DropList list : dropListManager.getLists())
		{
			listsContainer.add(buildOverviewRow(list));
		}

		if (dropListManager.getLists().isEmpty())
		{
			JLabel empty = new JLabel("No lists yet. Create one to get started.");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			listsContainer.add(empty);
		}

		JPanel north = new JPanel(new BorderLayout());
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);
		north.add(listsContainer, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(north);
		scrollPane.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		wrapper.add(header, BorderLayout.NORTH);
		wrapper.add(scrollPane, BorderLayout.CENTER);
		return wrapper;
	}

	private JPanel buildOverviewRow(DropList list)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 6, 2, 6));

		int itemCount = list.getItemIds().size();
		JButton editButton = new JButton("<html><b>" + escape(list.getName()) + "</b><br>"
			+ itemCount + (itemCount == 1 ? " item" : " items") + "</html>");
		editButton.setHorizontalAlignment(SwingConstants.LEFT);
		editButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
		editButton.setFocusPainted(false);
		editButton.setContentAreaFilled(false);
		editButton.setOpaque(false);
		editButton.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
			new EmptyBorder(4, 6, 4, 6)));
		editButton.setToolTipText("Edit this list's items");
		editButton.addActionListener(e -> openEditor(list.getId()));

		JCheckBox enabledBox = new JCheckBox();
		enabledBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		enabledBox.setOpaque(false);
		enabledBox.setIcon(UNCHECKED_ICON);
		enabledBox.setSelectedIcon(CHECKED_ICON);
		enabledBox.setSelected(list.isEnabled());
		enabledBox.setToolTipText("Toggle left-click drop on/off");
		enabledBox.addActionListener(e -> dropListManager.setListEnabled(list.getId(), enabledBox.isSelected()));

		JPopupMenu popupMenu = new JPopupMenu();
		JMenuItem deleteItem = new JMenuItem("Delete list");
		deleteItem.addActionListener(e ->
		{
			dropListManager.deleteList(list.getId());
			if (list.getId().equals(selectedListId))
			{
				selectedListId = null;
			}
			rebuild();
		});
		popupMenu.add(deleteItem);
		row.setComponentPopupMenu(popupMenu);
		editButton.setComponentPopupMenu(popupMenu);

		row.add(enabledBox, BorderLayout.WEST);
		row.add(editButton, BorderLayout.CENTER);
		return row;
	}

	private JPanel buildEditorView()
	{
		JPanel wrapper = new JPanel(new BorderLayout(0, 8));
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		DropList list = dropListManager.getLists().stream()
			.filter(l -> l.getId().equals(selectedListId))
			.findFirst()
			.orElse(null);

		if (list == null)
		{
			showOverview();
			return wrapper;
		}

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton backButton = new JButton("<");
		backButton.setPreferredSize(new Dimension(32, 24));
		backButton.setToolTipText("Back to lists");
		backButton.addActionListener(e ->
		{
			saveItems();
			showOverview();
		});

		JLabel heading = new JLabel("Edit list");
		heading.setForeground(Color.WHITE);

		header.add(backButton, BorderLayout.WEST);
		header.add(heading, BorderLayout.CENTER);

		JPanel body = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		listNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		listNameField.setForeground(Color.WHITE);
		listNameField.setCaretColor(Color.WHITE);
		listNameField.setText(list.getName());
		removeListeners(listNameField);
		listNameField.addActionListener(e -> saveListName());
		listNameField.addFocusListener(new NameFocusListener());

		itemsArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		itemsArea.setForeground(Color.WHITE);
		itemsArea.setCaretColor(Color.WHITE);
		itemsArea.setLineWrap(true);
		itemsArea.setWrapStyleWord(true);
		itemsArea.setBorder(new EmptyBorder(4, 6, 4, 6));
		itemsArea.setText("");
		removeFocusListeners(itemsArea);
		itemsArea.addFocusListener(new ItemsFocusListener());
		loadItemsText(list.getId());

		JLabel nameLabel = new JLabel("List name");
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel itemsLabel = new JLabel("Items (comma separated)");
		itemsLabel.setFont(FontManager.getRunescapeSmallFont());
		itemsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel hint = new JLabel("<html>Type item names separated by commas.<br>Or right-click inventory items to append.</html>");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		body.add(nameLabel);
		body.add(listNameField);
		body.add(itemsLabel);

		JScrollPane itemsScroll = new JScrollPane(itemsArea);
		itemsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		itemsScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 160));

		JPanel bottom = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		bottom.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottom.add(hint);

		JPanel center = new JPanel(new BorderLayout(0, 6));
		center.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.add(body, BorderLayout.NORTH);
		center.add(itemsScroll, BorderLayout.CENTER);
		center.add(bottom, BorderLayout.SOUTH);

		wrapper.add(header, BorderLayout.NORTH);
		wrapper.add(center, BorderLayout.CENTER);
		return wrapper;
	}

	private void openEditor(String listId)
	{
		selectedListId = listId;
		view = View.EDITOR;
		rebuild();
		SwingUtilities.invokeLater(itemsArea::requestFocusInWindow);
	}

	private void showOverview()
	{
		view = View.OVERVIEW;
		rebuild();
	}

	private void saveListName()
	{
		if (selectedListId == null)
		{
			return;
		}

		String name = listNameField.getText().trim();
		if (name.isEmpty())
		{
			return;
		}

		dropListManager.renameList(selectedListId, name);
	}

	private void saveItems()
	{
		if (selectedListId == null)
		{
			return;
		}

		final String listId = selectedListId;
		final String raw = itemsArea.getText();

		clientThread.invoke(() ->
		{
			List<Integer> resolved = new ArrayList<>();
			List<String> displayNames = new ArrayList<>();

			for (String token : raw.split(","))
			{
				String name = token.trim();
				if (name.isEmpty())
				{
					continue;
				}

				Integer itemId = resolveItemId(name);
				if (itemId == null)
				{
					continue;
				}

				int canonicalId = itemManager.canonicalize(itemId);
				if (resolved.contains(canonicalId))
				{
					continue;
				}

				resolved.add(canonicalId);
				String canonicalName = itemManager.getItemComposition(canonicalId).getName();
				displayNames.add(canonicalName != null ? canonicalName : name);
			}

			final String displayText = String.join(", ", displayNames);

			SwingUtilities.invokeLater(() ->
			{
				if (!listId.equals(selectedListId))
				{
					return;
				}

				dropListManager.setItems(listId, resolved);
				itemsArea.setText(displayText);
			});
		});
	}

	private void loadItemsText(String listId)
	{
		clientThread.invoke(() ->
		{
			DropList list = dropListManager.getLists().stream()
				.filter(l -> l.getId().equals(listId))
				.findFirst()
				.orElse(null);
			if (list == null)
			{
				return;
			}

			final String text = list.getItemIds().stream()
				.map(id -> itemManager.getItemComposition(id).getName())
				.filter(name -> name != null)
				.collect(Collectors.joining(", "));

			SwingUtilities.invokeLater(() ->
			{
				if (listId.equals(selectedListId))
				{
					itemsArea.setText(text);
				}
			});
		});
	}

	private Integer resolveItemId(String query)
	{
		String lowerQuery = query.toLowerCase();

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() <= 0)
				{
					continue;
				}

				int itemId = itemManager.canonicalize(item.getId());
				String name = itemManager.getItemComposition(itemId).getName();
				if (name != null && name.equalsIgnoreCase(query))
				{
					return itemId;
				}
			}
		}

		List<ItemPrice> matches = new ArrayList<>();
		for (ItemPrice itemPrice : itemManager.search(query))
		{
			if (itemPrice.getName() != null && itemPrice.getName().equalsIgnoreCase(query))
			{
				return itemManager.canonicalize(itemPrice.getId());
			}
			matches.add(itemPrice);
		}

		List<ItemPrice> contains = new ArrayList<>();
		for (ItemPrice itemPrice : matches)
		{
			if (itemPrice.getName() != null && itemPrice.getName().toLowerCase().contains(lowerQuery))
			{
				contains.add(itemPrice);
			}
		}

		if (contains.size() == 1)
		{
			return itemManager.canonicalize(contains.get(0).getId());
		}

		return null;
	}

	void removeItemFromInventory(int itemId)
	{
		if (selectedListId == null || view != View.EDITOR)
		{
			return;
		}

		final String listId = selectedListId;
		dropListManager.removeItem(listId, itemId);
		loadItemsText(listId);
	}

	void addItemFromInventory(int itemId)
	{
		if (selectedListId == null || view != View.EDITOR)
		{
			return;
		}

		final String name = dropListManager.getItemName(itemId);
		if (name == null)
		{
			return;
		}

		SwingUtilities.invokeLater(() ->
		{
			String current = itemsArea.getText().trim();
			if (current.isEmpty())
			{
				itemsArea.setText(name);
			}
			else
			{
				itemsArea.setText(current + ", " + name);
			}
			saveItems();
		});
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static void removeListeners(JTextField field)
	{
		for (java.awt.event.ActionListener al : field.getActionListeners())
		{
			field.removeActionListener(al);
		}
		removeFocusListeners(field);
	}

	private static void removeFocusListeners(java.awt.Component component)
	{
		for (java.awt.event.FocusListener fl : component.getFocusListeners())
		{
			if (fl instanceof NameFocusListener || fl instanceof ItemsFocusListener)
			{
				component.removeFocusListener(fl);
			}
		}
	}

	private static Icon checkboxIcon(boolean checked)
	{
		int size = 14;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(ColorScheme.DARK_GRAY_COLOR);
		g.fillRoundRect(0, 0, size - 1, size - 1, 4, 4);
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g.drawRoundRect(0, 0, size - 1, size - 1, 4, 4);

		if (checked)
		{
			g.setColor(CHECK_COLOR);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawPolyline(new int[]{3, 6, 11}, new int[]{7, 10, 3}, 3);
		}

		g.dispose();
		return new ImageIcon(image);
	}

	private final class NameFocusListener extends java.awt.event.FocusAdapter
	{
		@Override
		public void focusLost(java.awt.event.FocusEvent e)
		{
			saveListName();
		}
	}

	private final class ItemsFocusListener extends java.awt.event.FocusAdapter
	{
		@Override
		public void focusLost(java.awt.event.FocusEvent e)
		{
			saveItems();
		}
	}
}
