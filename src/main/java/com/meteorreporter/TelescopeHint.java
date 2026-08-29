package com.meteorreporter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * Reads a player-owned house telescope message.
 * <p>
 * The exact wording is matched loosely on purpose: a region name and a duration are pulled out of
 * the message rather than the sentence around them, so a wording change does not silently stop
 * scouting from working. Telescopes report a window whose width depends on their material - oak is
 * accurate to 24 minutes, teak to 9 and mahogany to 2 - so a message may carry one duration or two.
 */
@Getter
final class TelescopeHint
{
	/**
	 * Region names, longest first so that "Kharidian Desert" is preferred over "Desert".
	 */
	private static final String[] REGIONS =
	{
		"Piscatoris and the Gnome Stronghold",
		"Fossil Island and Mos Le'Harmless",
		"Feldip Hills and the Isle of Souls",
		"Fremennik Lands and Lunar Isle",
		"Crandor and Karamja",
		"Kharidian Desert",
		"Gnome Stronghold",
		"Mos Le'Harmless",
		"Kebos Lowlands",
		"Great Kourend",
		"Fossil Island",
		"Isle of Souls",
		"Feldip Hills",
		"Fremennik",
		"Piscatoris",
		"Lunar Isle",
		"Wilderness",
		"Morytania",
		"Misthalin",
		"Varlamore",
		"Asgarnia",
		"Tirannwn",
		"Kandarin",
		"Karamja",
		"Kourend",
		"Crandor",
		"Desert",
		"Kebos"
	};

	private static final Pattern DURATION = Pattern.compile("(\\d{1,3})\\s*(hours?|hrs?|minutes?|mins?)",
		Pattern.CASE_INSENSITIVE);
	/**
	 * "between 5 and 14 minutes", "5-14 minutes", "5 to 14 minutes". The first number carries no
	 * unit of its own, so it is invisible to {@link #DURATION}.
	 */
	private static final Pattern RANGE = Pattern.compile(
		"(\\d{1,3})\\s*(?:-|–|to|and)\\s*(\\d{1,3})\\s*(?:minutes?|mins?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern MARKUP = Pattern.compile("<[^>]*>");

	private final String region;
	private final int earliestMinutes;
	private final int latestMinutes;

	private TelescopeHint(String region, int earliestMinutes, int latestMinutes)
	{
		this.region = region;
		this.earliestMinutes = earliestMinutes;
		this.latestMinutes = latestMinutes;
	}

	/**
	 * @return the reading, or null when the message is not a telescope sighting we can use.
	 */
	static TelescopeHint parse(String message)
	{
		if (message == null) return null;
		String text = MARKUP.matcher(message).replaceAll(" ").replace('\u00a0', ' ');
		String lower = text.toLowerCase();
		// A sighting mentions the telescope or the star; "you don't see anything interesting"
		// carries no region and falls out below.
		if (!lower.contains("telescope") && !lower.contains("star")) return null;
		String region = findRegion(text);
		if (region == null) return null;
		return window(region, text);
	}

	private static String findRegion(String text)
	{
		String lower = text.toLowerCase();
		for (String region : REGIONS)
		{
			if (lower.contains(region.toLowerCase())) return region;
		}
		return null;
	}

	private static TelescopeHint window(String region, String text)
	{
		Matcher range = RANGE.matcher(text);
		if (range.find())
		{
			try
			{
				int first = Integer.parseInt(range.group(1));
				int second = Integer.parseInt(range.group(2));
				return new TelescopeHint(region, Math.min(first, second), Math.max(first, second));
			}
			catch (NumberFormatException ignored)
			{
				// Fall through to the single durations below.
			}
		}
		List<Integer> minutes = new ArrayList<>();
		boolean hours = false;
		Matcher matcher = DURATION.matcher(text);
		while (matcher.find())
		{
			int value;
			try
			{
				value = Integer.parseInt(matcher.group(1));
			}
			catch (NumberFormatException ignored)
			{
				continue;
			}
			boolean hour = matcher.group(2).toLowerCase().startsWith("h");
			hours |= hour;
			minutes.add(hour ? value * 60 : value);
		}
		if (minutes.isEmpty()) return null;
		if (hours)
		{
			// "1 hour and 20 minutes" is one duration split across two numbers, not a range.
			int total = 0;
			for (int value : minutes) total += value;
			return new TelescopeHint(region, total, total);
		}
		int earliest = minutes.get(0);
		int latest = earliest;
		for (int value : minutes)
		{
			earliest = Math.min(earliest, value);
			latest = Math.max(latest, value);
		}
		return new TelescopeHint(region, earliest, latest);
	}
}
