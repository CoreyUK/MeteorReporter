package com.meteorreporter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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

class MeteorReporterPanel extends PluginPanel
{
	private static final int SPOT_WRAP_WIDTH = 160;
	private static final int STALE_MINUTES = 15;
	private static final int REFRESH_THROTTLE_MS = 5000;
	private static final Color GOLD = new Color(255, 190, 45);
	private static final Color PURPLE = new Color(180, 100, 255);
	private static final Color BLUE = new Color(80, 155, 255);
	private static final Color GREEN = new Color(90, 200, 120);
	private static final Color RED = new Color(255, 85, 85);

	private final JPanel reports = new JPanel();
	private final JLabel status = new JLabel("Shared reports are disabled", SwingConstants.CENTER);
	private final JButton refresh = new JButton("Refresh");
	private final IntConsumer hopHandler;
	private final Runnable refreshHandler;
	private final Consumer<Boolean> activeHandler;
	private boolean showingReports;

	MeteorReporterPanel(IntConsumer hopHandler, Runnable refreshHandler, Consumer<Boolean> activeHandler)
	{
		this.hopHandler = hopHandler;
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
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		header.add(title, BorderLayout.CENTER);
		header.add(refresh, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		reports.setLayout(new BoxLayout(reports, BoxLayout.Y_AXIS));
		reports.setOpaque(false);

		// Anchoring the list to the top stops BoxLayout from centring the cards in the panel.
		JPanel listWrapper = new JPanel(new BorderLayout());
		listWrapper.setOpaque(false);
		listWrapper.add(reports, BorderLayout.NORTH);
		add(listWrapper, BorderLayout.CENTER);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(Color.LIGHT_GRAY);
		status.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		add(status, BorderLayout.SOUTH);
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
		reports.removeAll();
		showingReports = false;
		status.setText("Shared reports are disabled");
		revalidate();
		repaint();
	}

	void setLoading()
	{
		// Only announce a refresh when there is nothing on screen, otherwise the status flickers every cycle.
		if (!showingReports)
		{
			status.setText("Refreshing reports...");
		}
	}

	void setError(String message)
	{
		status.setText(message);
	}

	void setReports(List<MeteorReport> incoming, int currentWorld, int hidden)
	{
		List<MeteorReport> sorted = incoming == null ? new ArrayList<>() : new ArrayList<>(incoming);
		sorted.sort(Comparator.comparingInt(MeteorReport::getTier).reversed()
			.thenComparingInt(MeteorReport::getWorld));
		reports.removeAll();
		if (sorted.isEmpty())
		{
			JLabel empty = new JLabel(hidden > 0 ? "No reports match your tier filter" : "No active reports",
				SwingConstants.CENTER);
			empty.setForeground(Color.LIGHT_GRAY);
			empty.setAlignmentX(CENTER_ALIGNMENT);
			reports.add(Box.createVerticalStrut(12));
			reports.add(empty);
		}
		else
		{
			for (int i = 0; i < sorted.size(); i++)
			{
				if (i > 0)
				{
					reports.add(Box.createVerticalStrut(6));
				}
				reports.add(createReport(sorted.get(i), currentWorld));
			}
		}
		showingReports = !sorted.isEmpty();
		String summary = sorted.size() + (sorted.size() == 1 ? " active report" : " active reports");
		status.setText(hidden > 0 ? summary + " · " + hidden + " hidden" : summary);
		revalidate();
		repaint();
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
		boolean here = currentWorld > 0 && currentWorld == report.getWorld();
		long minutes = ageMinutes(report.getUpdatedAt());
		boolean stale = minutes >= STALE_MINUTES;
		Color accent = stale ? tierColor(report.getTier()).darker().darker() : tierColor(report.getTier());

		JPanel card = new JPanel(new BorderLayout(0, 3));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(6, 7, 6, 7)));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		JLabel world = new JLabel("World " + report.getWorld());
		world.setFont(FontManager.getRunescapeBoldFont());
		world.setForeground(stale ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);
		JLabel tier = new JLabel("Tier " + report.getTier());
		tier.setFont(FontManager.getRunescapeSmallFont());
		tier.setForeground(accent);
		header.add(world, BorderLayout.WEST);
		header.add(tier, BorderLayout.CENTER);
		header.add(worldAction(report.getWorld(), here), BorderLayout.EAST);

		JLabel spot = new JLabel("<html><div width=" + SPOT_WRAP_WIDTH + ">" + escape(report.getSpot()) + "</div></html>");
		spot.setForeground(stale ? Color.GRAY : ColorScheme.LIGHT_GRAY_COLOR);

		JPanel footer = new JPanel(new BorderLayout(6, 0));
		footer.setOpaque(false);
		String reporter = report.getReporterName();
		if (reporter != null && !reporter.isEmpty())
		{
			int count = report.getContributionCount();
			JPanel credit = new JPanel();
			credit.setLayout(new BoxLayout(credit, BoxLayout.X_AXIS));
			credit.setOpaque(false);
			JLabel name = new JLabel(escape(reporter));
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(stale ? Color.GRAY : rankColor(count));
			name.setToolTipText(count + (count == 1 ? " report" : " reports") + " shared");
			credit.add(name);
			if (count > 0)
			{
				JLabel shared = new JLabel("· " + count);
				shared.setFont(FontManager.getRunescapeSmallFont());
				shared.setForeground(Color.GRAY);
				credit.add(Box.createHorizontalStrut(4));
				credit.add(shared);
			}
			footer.add(credit, BorderLayout.WEST);
		}
		JLabel age = new JLabel(age(minutes));
		age.setFont(FontManager.getRunescapeSmallFont());
		age.setForeground(Color.GRAY);
		if (stale)
		{
			age.setToolTipText("Nobody has confirmed this star recently");
		}
		footer.add(age, BorderLayout.EAST);

		card.add(header, BorderLayout.NORTH);
		card.add(spot, BorderLayout.CENTER);
		card.add(footer, BorderLayout.SOUTH);
		// Cards size to their content instead of a hardcoded height, so nothing is clipped.
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JComponent worldAction(int world, boolean here)
	{
		if (here)
		{
			JLabel current = new JLabel("Here");
			current.setFont(FontManager.getRunescapeSmallFont());
			current.setForeground(GREEN);
			current.setToolTipText("You are on this world");
			return current;
		}
		JButton hop = new JButton("Hop");
		hop.setFont(FontManager.getRunescapeSmallFont());
		hop.setToolTipText("Hop to world " + world);
		hop.setFocusable(false);
		hop.setMargin(new Insets(0, 0, 0, 0));
		hop.setPreferredSize(new Dimension(42, 18));
		hop.addActionListener(event -> hopHandler.accept(world));
		return hop;
	}

	static Color tierColor(int tier)
	{
		if (tier >= 8) return GOLD;
		if (tier >= 6) return PURPLE;
		if (tier >= 4) return BLUE;
		if (tier >= 2) return GREEN;
		return ColorScheme.LIGHT_GRAY_COLOR;
	}

	static Color rankColor(int reports)
	{
		if (reports >= 250) return GOLD;
		if (reports >= 100) return PURPLE;
		if (reports >= 50) return BLUE;
		if (reports >= 20) return RED;
		return Color.LIGHT_GRAY;
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
