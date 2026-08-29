package com.meteorreporter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScoutWindowTest
{
	private static final long NOW = 1_780_000_000_000L;
	private static final long MINUTE = 60_000L;

	@Test
	public void showsARangeWhileTheStarIsStillDue()
	{
		assertEquals("in 5-14m", window(NOW + 5 * MINUTE, NOW + 14 * MINUTE));
	}

	@Test
	public void showsASingleEstimateWhenTheWindowHasNoWidth()
	{
		assertEquals("in ~14m", window(NOW + 14 * MINUTE, NOW + 14 * MINUTE));
	}

	@Test
	public void countsDownOnceTheWindowHasOpened()
	{
		assertEquals("due within 3m", window(NOW - MINUTE, NOW + 3 * MINUTE));
	}

	@Test
	public void saysDueNowOnTheLastMinuteOfTheWindow()
	{
		assertEquals("due now", window(NOW - 2 * MINUTE, NOW));
	}

	@Test
	public void marksAPassedWindowOverdue()
	{
		assertEquals("overdue", window(NOW - 10 * MINUTE, NOW - MINUTE));
	}

	private static String window(long earliestAt, long latestAt)
	{
		return MeteorReporterPanel.window(
			new StarScout(302, "Asgarnia", earliestAt, latestAt, NOW, null, null, 0), NOW);
	}
}
