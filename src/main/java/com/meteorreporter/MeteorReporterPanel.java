package com.meteorreporter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.LinkBrowser;

class MeteorReporterPanel extends PluginPanel
{
	private static final int SPOT_WRAP_WIDTH = 160;
	private static final int STALE_MINUTES = 7;
	private static final int REFRESH_THROTTLE_MS = 5000;
	private static final String DISABLED = "Shared reports are disabled";
	private static final String WEBSITE = "https://meteors.cukservers.net";
	private static final Color GOLD = new Color(255, 190, 45);
	private static final Color PURPLE = new Color(180, 100, 255);
	private static final Color BLUE = new Color(80, 155, 255);
	private static final Color GREEN = new Color(90, 200, 120);
	private static final Color RED = new Color(255, 85, 85);
	/**
	 * One colour per star size, cool for a nearly spent star through to gold for a fresh one.
	 * The same scale the website uses, so a tier reads the same in both places.
	 */
	private static final Color[] TIER_COLORS =
	{
		new Color(159, 176, 194),
		new Color(116, 195, 157),
		new Color(90, 200, 120),
		new Color(79, 182, 232),
		new Color(80, 155, 255),
		new Color(143, 123, 255),
		new Color(180, 100, 255),
		new Color(255, 143, 58),
		new Color(255, 190, 45)
	};

	private final JPanel live = new JPanel();
	private final JPanel scouted = new JPanel();
	private final JLabel status = new JLabel(DISABLED, SwingConstants.CENTER);
	private final JButton refresh = new JButton("Refresh");
	private final Runnable refreshHandler;
	private final Consumer<Boolean> activeHandler;
	private final MaterialTab liveTab;
	private final MaterialTab scoutedTab;
	private String liveStatus = DISABLED;
	private String scoutedStatus = "No scouted stars";
	private boolean showingReports;

	MeteorReporterPanel(Runnable refreshHandler, Consumer<Boolean> activeHandler)
	{
		this.refreshHandler = refreshHandler;
		this.activeHandler = activeHandler;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Meteor Reporter");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.PLAIN, 18f));
		title.setForeground(Color.WHITE);
		refresh.setFont(FontManager.getRunescapeSmallFont());
		refresh.setToolTipText("Refresh now");
		refresh.setFocusable(false);
		refresh.setMargin(new Insets(0, 0, 0, 0));
		refresh.setPreferredSize(new Dimension(56, 20));
		refresh.addActionListener(event -> requestRefresh());

		JPanel titleRow = new JPanel(new BorderLayout(6, 0));
		titleRow.setOpaque(false);
		titleRow.add(title, BorderLayout.CENTER);
		titleRow.add(refresh, BorderLayout.EAST);

		JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		liveTab = new MaterialTab("Live", tabs, listWrapper(live));
		scoutedTab = new MaterialTab("Scouted", tabs, listWrapper(scouted));
		liveTab.setToolTipText("Crashed stars other players have reported");
		scoutedTab.setToolTipText("Telescope readings for upcoming stars");
		liveTab.setOnSelectEvent(() -> showStatus(liveStatus));
		scoutedTab.setOnSelectEvent(() -> showStatus(scoutedStatus));
		tabs.addTab(liveTab);
		tabs.addTab(scoutedTab);

		JPanel header = new JPanel(new BorderLayout(0, 8));
		header.setOpaque(false);
		header.add(titleRow, BorderLayout.NORTH);
		header.add(tabs, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(Color.LIGHT_GRAY);
		status.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

		JLabel site = new JLabel("meteors.cukservers.net", SwingConstants.CENTER);
		site.setFont(FontManager.getRunescapeSmallFont());
		site.setForeground(GOLD);
		site.setToolTipText("Open the star map and reporter leaderboard in your browser");
		site.setCursor(new Cursor(Cursor.HAND_CURSOR));
		site.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				LinkBrowser.browse(WEBSITE);
			}
		});

		JPanel footer = new JPanel(new BorderLayout(0, 2));
		footer.setOpaque(false);
		footer.add(status, BorderLayout.NORTH);
		footer.add(site, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);

		tabs.select(liveTab);
	}

	@Override
	public void onActivate()
	{
		activeHandler.accept(true);
	}

	@Override
	public void onDeactivate()
	{
		activeHandler.accept(false);
	}

	void setDisabled()
	{
		live.removeAll();
		scouted.removeAll();
		showingReports = false;
		liveStatus = DISABLED;
		scoutedStatus = DISABLED;
		showStatus(DISABLED);
		revalidate();
		repaint();
	}

	void setLoading()
	{
		// Only announce a refresh when there is nothing on screen, otherwise the status flickers every cycle.
		if (!showingReports)
		{
			liveStatus = "Refreshing reports...";
			if (liveTab.isSelected()) showStatus(liveStatus);
		}
	}

	void setError(String message)
	{
		liveStatus = message;
		if (liveTab.isSelected()) showStatus(message);
	}

	void setScoutError(String message)
	{
		scoutedStatus = message;
		scouted.removeAll();
		JLabel label = new JLabel(message, SwingConstants.CENTER);
		label.setForeground(Color.LIGHT_GRAY);
		label.setAlignmentX(CENTER_ALIGNMENT);
		scouted.add(Box.createVerticalStrut(12));
		scouted.add(label);
		if (scoutedTab.isSelected()) showStatus(message);
		revalidate();
		repaint();
	}

	void setReports(List<MeteorReport> incoming, int currentWorld, int hidden)
	{
		List<MeteorReport> sorted = incoming == null ? new ArrayList<>() : new ArrayList<>(incoming);
		sorted.sort(Comparator.comparingInt(MeteorReport::getTier).reversed()
			.thenComparingInt(MeteorReport::getWorld));
		live.removeAll();
		if (sorted.isEmpty())
		{
			live.add(Box.createVerticalStrut(12));
			live.add(placeholder(hidden > 0 ? "No reports match your tier filter" : "No active reports"));
		}
		else
		{
			for (int i = 0; i < sorted.size(); i++)
			{
				if (i > 0) live.add(Box.createVerticalStrut(6));
				live.add(createReport(sorted.get(i), currentWorld));
			}
		}
		showingReports = !sorted.isEmpty();
		String summary = sorted.size() + (sorted.size() == 1 ? " active report" : " active reports");
		liveStatus = hidden > 0 ? summary + " · " + hidden + " hidden" : summary;
		if (liveTab.isSelected()) showStatus(liveStatus);
		revalidate();
		repaint();
	}

	void setScouts(List<StarScout> incoming, int currentWorld)
	{
		List<StarScout> sorted = incoming == null ? new ArrayList<>() : new ArrayList<>(incoming);
		sorted.sort(Comparator.comparingLong(StarScout::getEarliestAt)
			.thenComparingInt(StarScout::getWorld));
		scouted.removeAll();
		if (sorted.isEmpty())
		{
			scouted.add(Box.createVerticalStrut(12));
			scouted.add(placeholder("No scouted stars"));
		}
		else
		{
			for (int i = 0; i < sorted.size(); i++)
			{
				if (i > 0) scouted.add(Box.createVerticalStrut(6));
				scouted.add(createScout(sorted.get(i), currentWorld));
			}
		}
		scoutedStatus = sorted.size() + (sorted.size() == 1 ? " scouted star" : " scouted stars");
		if (scoutedTab.isSelected()) showStatus(scoutedStatus);
		revalidate();
		repaint();
	}

	private JPanel listWrapper(JPanel list)
	{
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		// Anchoring the list to the top stops BoxLayout from centring the cards in the panel.
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(list, BorderLayout.NORTH);
		return wrapper;
	}

	private JLabel placeholder(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setForeground(Color.LIGHT_GRAY);
		label.setAlignmentX(CENTER_ALIGNMENT);
		return label;
	}

	private boolean showStatus(String text)
	{
		status.setText(text);
		return true;
	}

	private void requestRefresh()
	{
		// Throttle manual refreshes so the button cannot be used to hammer the report server.
		refresh.setEnabled(false);
		Timer unlock = new Timer(REFRESH_THROTTLE_MS, event -> refresh.setEnabled(true));
		unlock.setRepeats(false);
		unlock.start();
		refreshHandler.run();
	}

	private JPanel createReport(MeteorReport report, int currentWorld)
	{
		long minutes = ageMinutes(report.getUpdatedAt());
		boolean stale = minutes >= STALE_MINUTES;
		Color accent = stale ? tierColor(report.getTier()).darker().darker() : tierColor(report.getTier());

		JLabel tier = new JLabel("Tier " + report.getTier());
		tier.setFont(FontManager.getRunescapeSmallFont());
		tier.setForeground(accent);

		JLabel spot = new JLabel("<html><div width=" + SPOT_WRAP_WIDTH + ">" + escape(report.getSpot()) + "</div></html>");
		spot.setForeground(stale ? Color.GRAY : ColorScheme.LIGHT_GRAY_COLOR);

		JLabel age = new JLabel(age(minutes));
		age.setFont(FontManager.getRunescapeSmallFont());
		age.setForeground(Color.GRAY);
		if (stale)
		{
			age.setToolTipText("Stars shrink a size every 7 minutes - this one may be smaller now");
		}

		return card(report.getWorld(), currentWorld, accent, stale, tier, spot, age,
			report.getReporterName(), report.getContributionCount());
	}

	private JPanel createScout(StarScout scout, int currentWorld)
	{
		long now = Instant.now().toEpochMilli();
		boolean due = scout.getEarliestAt() <= now;
		boolean expired = scout.getLatestAt() < now;
		Color accent = expired ? Color.GRAY : (due ? GOLD : GREEN);

		JLabel window = new JLabel(window(scout, now));
		window.setFont(FontManager.getRunescapeSmallFont());
		window.setForeground(accent);

		JLabel region = new JLabel("<html><div width=" + SPOT_WRAP_WIDTH + ">"
			+ escape(scout.getRegion() == null ? "Unknown region" : scout.getRegion()) + "</div></html>");
		region.setForeground(expired ? Color.GRAY : ColorScheme.LIGHT_GRAY_COLOR);

		JLabel age = new JLabel(age(ageMinutes(scout.getUpdatedAt())));
		age.setFont(FontManager.getRunescapeSmallFont());
		age.setForeground(Color.GRAY);
		age.setToolTipText("When this telescope reading was shared");

		return card(scout.getWorld(), currentWorld, accent, expired, window, region, age,
			scout.getReporterName(), scout.getContributionCount());
	}

	private JPanel card(int world, int currentWorld, Color accent, boolean dim, JLabel detail, JLabel body,
		JLabel age, String reporter, int contributions)
	{
		JPanel card = new JPanel(new BorderLayout(0, 3));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(6, 7, 6, 7)));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		JLabel worldLabel = new JLabel("World " + world);
		worldLabel.setFont(FontManager.getRunescapeBoldFont());
		worldLabel.setForeground(dim ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);
		header.add(worldLabel, BorderLayout.WEST);
		header.add(detail, BorderLayout.CENTER);
		if (currentWorld > 0 && currentWorld == world)
		{
			header.add(currentWorldLabel(), BorderLayout.EAST);
		}

		JPanel footer = new JPanel(new BorderLayout(6, 0));
		footer.setOpaque(false);
		if (reporter != null && !reporter.isEmpty())
		{
			JPanel credit = new JPanel();
			credit.setLayout(new BoxLayout(credit, BoxLayout.X_AXIS));
			credit.setOpaque(false);
			JLabel name = new JLabel(escape(reporter));
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(dim ? Color.GRAY : rankColor(contributions));
			name.setToolTipText(rankName(contributions) + " - "
				+ contributions + (contributions == 1 ? " find" : " finds") + " shared");
			credit.add(name);
			if (contributions > 0)
			{
				JLabel shared = new JLabel("· " + contributions);
				shared.setFont(FontManager.getRunescapeSmallFont());
				shared.setForeground(Color.GRAY);
				credit.add(Box.createHorizontalStrut(4));
				credit.add(shared);
			}
			footer.add(credit, BorderLayout.WEST);
		}
		footer.add(age, BorderLayout.EAST);

		card.add(header, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		card.add(footer, BorderLayout.SOUTH);
		// Cards size to their content instead of a hardcoded height, so nothing is clipped.
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JComponent currentWorldLabel()
	{
		JLabel current = new JLabel("Here");
		current.setFont(FontManager.getRunescapeSmallFont());
		current.setForeground(GREEN);
		current.setToolTipText("You are on this world");
		return current;
	}

	static String window(StarScout scout, long now)
	{
		long latest = Math.round((scout.getLatestAt() - now) / 60000d);
		if (latest < 0) return "overdue";
		long earliest = Math.round((scout.getEarliestAt() - now) / 60000d);
		if (earliest <= 0) return latest == 0 ? "due now" : "due within " + latest + "m";
		return earliest == latest ? "in ~" + earliest + "m" : "in " + earliest + "-" + latest + "m";
	}

	static Color tierColor(int tier)
	{
		return TIER_COLORS[Math.min(TIER_COLORS.length, Math.max(1, tier)) - 1];
	}

	static Color rankColor(int reports)
	{
		if (reports >= 250) return GOLD;
		if (reports >= 100) return PURPLE;
		if (reports >= 50) return BLUE;
		if (reports >= 20) return RED;
		return Color.LIGHT_GRAY;
	}

	/** The same thresholds and names the website's leaderboard uses. */
	static String rankName(int reports)
	{
		if (reports >= 250) return "Legend";
		if (reports >= 100) return "Master";
		if (reports >= 50) return "Veteran";
		if (reports >= 20) return "Scout";
		return "Reporter";
	}

	private static long ageMinutes(long timestamp)
	{
		return Math.max(0, ChronoUnit.MINUTES.between(Instant.ofEpochMilli(timestamp), Instant.now()));
	}

	private static String age(long minutes)
	{
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		long hours = minutes / 60;
		long remainder = minutes % 60;
		return remainder == 0 ? hours + "h ago" : hours + "h " + remainder + "m ago";
	}

	private static String escape(String value)
	{
		return value == null ? "Unknown spot" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
