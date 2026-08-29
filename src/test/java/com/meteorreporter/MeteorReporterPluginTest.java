package com.meteorreporter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MeteorReporterPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MeteorReporterPlugin.class);
		RuneLite.main(args);
	}
}
