package com.meteorreporter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class MeteorReporterPanel extends PluginPanel
{
	private final JPanel reports = new JPanel();
	private final JLabel status = new JLabel("Shared reports are disabled", SwingConstants.CENTER);
	private final IntConsumer hopHandler;

	MeteorReporterPanel(IntConsumer hopHandler)
	{
		this.hopHandler = hopHandler;
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		JLabel title = new JLabel("Meteor Reporter");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.PLAIN, 18f));
		title.setForeground(Color.WHITE);
		add(title, BorderLayout.NORTH);
		reports.setLayout(new BoxLayout(reports, BoxLayout.Y_AXIS));
		reports.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(reports, BorderLayout.CENTER);
		status.setForeground(Color.LIGHT_GRAY);
		status.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		add(status, BorderLayout.SOUTH);
	}

	void setDisabled()
	{
		reports.removeAll();
		status.setText("Shared reports are disabled");
		revalidate();
		repaint();
	}

	void setLoading()
	{
		status.setText("Refreshing reports...");
	}

	void setError(String message)
	{
		status.setText(message);
	}

	void setReports(List<MeteorReport> incoming)
	{
		List<MeteorReport> sorted = incoming == null ? new ArrayList<>() : new ArrayList<>(incoming);
		sorted.sort(Comparator.comparingInt(MeteorReport::getWorld));
		reports.removeAll();
		if (sorted.isEmpty())
		{
			JLabel empty = new JLabel("No active reports", SwingConstants.CENTER);
			empty.setForeground(Color.LIGHT_GRAY);
			empty.setAlignmentX(CENTER_ALIGNMENT);
			reports.add(Box.createVerticalStrut(12));
			reports.add(empty);
		}
		else
		{
			for (MeteorReport report : sorted)
			{
				reports.add(createReport(report));
				reports.add(Box.createVerticalStrut(6));
			}
		}
		status.setText(sorted.size() + (sorted.size() == 1 ? " active report" : " active reports"));
		revalidate();
		repaint();
	}

	private JPanel createReport(MeteorReport report)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, report.getReporterName() == null ? 76 : 94));
		JPanel headingRow = new JPanel(new BorderLayout());
		headingRow.setOpaque(false);
		JLabel heading = new JLabel("World " + report.getWorld() + "  |  Tier " + report.getTier());
		heading.setForeground(Color.WHITE);
		heading.setFont(FontManager.getRunescapeBoldFont());
		JButton hop = new JButton("Hop");
		hop.setToolTipText("Hop to World " + report.getWorld());
		hop.setFocusable(false);
		hop.addActionListener(event -> hopHandler.accept(report.getWorld()));
		headingRow.add(heading, BorderLayout.CENTER);
		headingRow.add(hop, BorderLayout.EAST);
		JLabel spot = new JLabel("<html>" + escape(report.getSpot()) + "</html>");
		spot.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JLabel age = new JLabel(age(report.getUpdatedAt()));
		age.setForeground(Color.GRAY);
		card.add(headingRow);
		card.add(spot);
		if (report.getReporterName() != null && !report.getReporterName().isEmpty())
		{
			int count = report.getContributionCount();
			JLabel reporter = new JLabel("Reported by " + escape(report.getReporterName())
				+ " - " + count + (count == 1 ? " report" : " reports"));
			reporter.setForeground(rankColor(report.getContributionCount()));
			card.add(reporter);
		}
		card.add(age);
		return card;
	}

	static Color rankColor(int reports)
	{
		if (reports >= 250) return new Color(255, 190, 45);
		if (reports >= 100) return new Color(180, 100, 255);
		if (reports >= 50) return new Color(80, 155, 255);
		if (reports >= 20) return new Color(255, 85, 85);
		return Color.LIGHT_GRAY;
	}

	private static String age(long timestamp)
	{
		long minutes = Math.max(0, ChronoUnit.MINUTES.between(Instant.ofEpochMilli(timestamp), Instant.now()));
		return minutes == 0 ? "just now" : minutes + (minutes == 1 ? " minute ago" : " minutes ago");
	}

	private static String escape(String value)
	{
		return value == null ? "Unknown spot" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
