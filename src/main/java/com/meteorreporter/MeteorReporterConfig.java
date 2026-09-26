package com.meteorreporter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(MeteorReporterConfig.GROUP)
public interface MeteorReporterConfig extends Config
{
	String GROUP = "meteor-reporter";

	@ConfigItem(
		keyName = "sharingEnabled",
		name = "Enable shared reports",
		description = "Allows reports to be sent to and read from the configured server",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean sharingEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "autoReport",
		name = "Report stars automatically",
		description = "Shares a crashed star the moment you see one, without using the Report option",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean autoReport()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showReporterName",
		name = "Show my IGN",
		description = "Shows your RuneScape name on the stars and telescope readings you share, and ranks you on the contributor leaderboard. With automatic reporting on, this publishes where you are whenever you pass a star",
		warning = "This sends your RuneScape name, meteor report data, and IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean showReporterName()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareTelescope",
		name = "Share telescope readings",
		description = "Shares the region and time window your house telescope reports for your current world",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean shareTelescope()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minimumTier",
		name = "Minimum tier",
		description = "Hides shared reports below this star tier"
	)
	@Range(min = 1, max = 9)
	default int minimumTier()
	{
		return 1;
	}
}
