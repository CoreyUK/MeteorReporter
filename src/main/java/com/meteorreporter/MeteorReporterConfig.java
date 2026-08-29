package com.meteorreporter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

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
		keyName = "showReporterName",
		name = "Show my IGN",
		description = "Shows your RuneScape name on meteor reports",
		warning = "This sends your RuneScape name, meteor report data, and IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean showReporterName()
	{
		return false;
	}

	@Range(min = 10, max = 300)
	@Units(Units.SECONDS)
	@ConfigItem(keyName = "refreshSeconds", name = "Refresh interval", description = "How often to refresh the shared report list")
	default int refreshSeconds()
	{
		return 30;
	}
}
