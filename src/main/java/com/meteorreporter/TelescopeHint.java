package com.meteorreporter;

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

	/** A number and, when it has one, its unit. "32 to 56 minutes" gives the 32 no unit at all. */
	private static final Pattern NUMBER = Pattern.compile("(\\d{1,3})\\s*(hours?|hrs?|minutes?|mins?)?",
		Pattern.CASE_INSENSITIVE);
	/**
	 * What separates the two ends of a window: "32 to 56", "5-14", "between 5 and 14". A bare "and"
	 * is not one, because "1 hour and 20 minutes" is a single duration.
	 */
	private static final Pattern SEPARATOR = Pattern.compile("\\s*(?:\\bto\\b|-|–)\\s*", Pattern.CASE_INSENSITIVE);
	private static final Pattern BETWEEN = Pattern.compile("\\bbetween\\s+(\\d{1,3}(?:\\s*(?:hours?|hrs?))?(?:\\s*\\d{1,3})?)\\s+and\\s+",
		Pattern.CASE_INSENSITIVE);
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
		// The game says "Piscatoris or the Gnome Stronghold"; the paired names are listed with "and".
		String lower = text.toLowerCase().replace(" or the ", " and the ").replace(" or ", " and ");
		for (String region : REGIONS)
		{
			if (lower.contains(region.toLowerCase())) return region;
		}
		return null;
	}

	/**
	 * The window is the part of the text from its first number onward, split once at "to" (or a
	 * dash, or the "and" of "between X and Y") into an earliest and a latest side. Each side is a
	 * duration of its own: hours and minutes are added, and a bare number takes its unit from the
	 * other side, so "32 to 56 minutes" reads as 32 minutes to 56 minutes. A side with no hours
	 * that comes out earlier than the side before it shares that side's hour, so "1 hour 28 to 52
	 * minutes" reads as 1:28 to 1:52. No separator means one duration, and a window of no width.
	 */
	private static TelescopeHint window(String region, String text)
	{
		String clause = BETWEEN.matcher(text).replaceFirst("$1 to ");
		Matcher first = NUMBER.matcher(clause);
		if (!first.find()) return null;
		clause = clause.substring(first.start());
		String[] sides = SEPARATOR.split(clause, 2);
		Duration earliest = Duration.read(sides[0]);
		if (earliest == null) return null;
		if (sides.length == 1) return new TelescopeHint(region, earliest.total(), earliest.total());
		Duration latest = Duration.read(sides[1]);
		if (latest == null) return new TelescopeHint(region, earliest.total(), earliest.total());
		Duration.borrowUnits(earliest, latest);
		int a = earliest.total();
		int b = latest.total();
		return new TelescopeHint(region, Math.min(a, b), Math.max(a, b));
	}

	/** One side of a window: "1 hour 28 minutes", "56 minutes", or a bare "32". */
	private static final class Duration
	{
		private int hours = -1;
		private int minutes = -1;
		private int bare = -1;

		/** @return the duration, or null when the side holds no number at all. */
		static Duration read(String side)
		{
			Duration duration = new Duration();
			Matcher matcher = NUMBER.matcher(side);
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
				String unit = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase();
				if (unit.startsWith("h"))
				{
					duration.hours = value;
				}
				else if (unit.startsWith("m"))
				{
					// Minutes always close a duration; whatever follows is not part of this side.
					duration.minutes = value;
					break;
				}
				else if (duration.bare < 0)
				{
					duration.bare = value;
				}
				else
				{
					// A second unitless number is something else entirely - a world, a price.
					break;
				}
			}
			return duration.hours < 0 && duration.minutes < 0 && duration.bare < 0 ? null : duration;
		}

		/**
		 * A bare number on one side takes the unit its partner spelt out: in "32 to 56 minutes" the
		 * 32 is minutes, in "1 to 2 hours" the 1 is hours. Then a side without an hour that reads
		 * earlier than the side it follows is missing that hour, as in "1 hour 28 to 52 minutes".
		 */
		static void borrowUnits(Duration earliest, Duration latest)
		{
			for (Duration side : new Duration[]{earliest, latest})
			{
				Duration other = side == earliest ? latest : earliest;
				if (side.bare < 0) continue;
				// A side that already has minutes but sits after an hour, "1 hour 28", is 28 minutes.
				if (side.hours >= 0 && side.minutes < 0) side.minutes = side.bare;
				else if (other.minutes >= 0 || other.hours < 0) side.minutes = side.bare;
				else side.hours = side.bare;
				side.bare = -1;
			}
			if (latest.hours < 0 && earliest.hours > 0 && latest.total() < earliest.total())
			{
				latest.hours = earliest.hours;
			}
		}

		int total()
		{
			return Math.max(0, hours) * 60 + Math.max(0, minutes);
		}
	}
}
